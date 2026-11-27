import { describe, expect, it } from 'vitest'
import {
  cortarEn,
  detectarFranjas,
  muestrearFondo,
  perfilFilas,
  recortarFranjaTransparente,
  recortarSobreBlanco,
  sugerirCortes,
  unirConSiguiente,
} from './procesarImagen.js'

/** Imagen de prueba: fondo de un color y rectángulos pintados encima. */
function imagen(width, height, fondo, rectangulos = [], negroArribaYAbajo = 0) {
  const data = new Uint8ClampedArray(width * height * 4)
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const negro = y < negroArribaYAbajo || y >= height - negroArribaYAbajo
      let c = negro ? [0, 0, 0] : fondo
      for (const r of rectangulos) {
        if (x >= r.x0 && x <= r.x1 && y >= r.y0 && y <= r.y1) c = r.color
      }
      const o = (y * width + x) * 4
      data.set([...c, 255], o)
    }
  }
  return { width, height, data }
}

const GRIS = [240, 240, 236]
const PAN = [200, 140, 60]
const LECHUGA = [90, 170, 60]
const CARNE = [90, 50, 40]

describe('muestrearFondo', () => {
  it('toma el color del borde', () => {
    expect(muestrearFondo(imagen(60, 60, GRIS, [{ x0: 20, x1: 40, y0: 20, y1: 40, color: PAN }]))).toEqual(GRIS)
  })

  it('ignora las bandas negras que agrega el generador', () => {
    expect(muestrearFondo(imagen(60, 100, [255, 255, 255], [], 30))).toEqual([255, 255, 255])
  })
})

describe('detectarFranjas', () => {
  const plato = imagen(80, 120, GRIS, [
    { x0: 10, x1: 70, y0: 10, y1: 30, color: PAN },
    { x0: 5, x1: 75, y0: 45, y1: 60, color: LECHUGA },
    { x0: 12, x1: 68, y0: 80, y1: 100, color: CARNE },
  ])

  it('encuentra una franja por capa separada por espacio vacío', () => {
    expect(detectarFranjas(plato, GRIS)).toEqual([
      { y0: 10, y1: 30 },
      { y0: 45, y1: 60 },
      { y0: 80, y1: 100 },
    ])
  })

  it('dos capas que se tocan salen juntas (para eso existe cortarEn)', () => {
    const pegadas = imagen(80, 80, GRIS, [
      { x0: 10, x1: 70, y0: 10, y1: 30, color: PAN },
      { x0: 10, x1: 70, y0: 31, y1: 50, color: CARNE },
    ])
    expect(detectarFranjas(pegadas, GRIS)).toEqual([{ y0: 10, y1: 50 }])
  })

  it('descarta manchas demasiado bajas (migas, ruido)', () => {
    const conMiga = imagen(80, 80, GRIS, [
      { x0: 10, x1: 70, y0: 10, y1: 30, color: PAN },
      { x0: 40, x1: 45, y0: 60, y1: 63, color: CARNE },
    ])
    expect(detectarFranjas(conMiga, GRIS)).toEqual([{ y0: 10, y1: 30 }])
  })
})

describe('cortarEn y unirConSiguiente', () => {
  const franjas = [
    { y0: 10, y1: 50 },
    { y0: 70, y1: 90 },
  ]

  it('parte la franja que contiene la fila', () => {
    expect(cortarEn(franjas, 30)).toEqual([
      { y0: 10, y1: 30 },
      { y0: 31, y1: 50 },
      { y0: 70, y1: 90 },
    ])
  })

  it('un corte en el espacio vacío no cambia nada', () => {
    expect(cortarEn(franjas, 60)).toBe(franjas)
  })

  it('unir deshace el corte', () => {
    expect(unirConSiguiente(cortarEn(franjas, 30), 0)).toEqual(franjas)
  })

  it('la última franja no tiene con qué unirse', () => {
    expect(unirConSiguiente(franjas, 1)).toBe(franjas)
  })
})

describe('sugerirCortes', () => {
  it('propone cortar en la "cintura" entre dos capas que se tocan', () => {
    // Pan ancho, un cuello angosto en y=30 y carne ancha.
    const img = imagen(100, 70, GRIS, [
      { x0: 5, x1: 95, y0: 5, y1: 28, color: PAN },
      { x0: 45, x1: 55, y0: 29, y1: 31, color: PAN },
      { x0: 5, x1: 95, y0: 32, y1: 60, color: CARNE },
    ])
    const cuenta = perfilFilas(img, GRIS)
    const [corte] = sugerirCortes(cuenta, { y0: 5, y1: 60 }, { ventana: 4 })
    expect(corte).toBeGreaterThanOrEqual(29)
    expect(corte).toBeLessThanOrEqual(31)
  })
})

describe('recortarFranjaTransparente', () => {
  it('recorta los costados y deja el fondo transparente', () => {
    const img = imagen(80, 40, GRIS, [{ x0: 20, x1: 59, y0: 5, y1: 30, color: PAN }])
    const capa = recortarFranjaTransparente(img, { y0: 5, y1: 30 }, GRIS)
    expect(capa.width).toBe(44) // 40 px de pan + 2 de margen por lado
    expect(capa.height).toBe(26)
    // Esquina: fondo -> alfa 0. Centro: pan -> opaco y con su color.
    expect(capa.data[3]).toBe(0)
    const centro = (13 * capa.width + 22) * 4
    expect(Array.from(capa.data.slice(centro, centro + 4))).toEqual([...PAN, 255])
  })

  it('una franja sin contenido devuelve null', () => {
    expect(recortarFranjaTransparente(imagen(20, 20, GRIS), { y0: 0, y1: 19 }, GRIS)).toBeNull()
  })
})

describe('recortarSobreBlanco', () => {
  it('recorta al contenido, ignora bandas negras y pasa el fondo a blanco puro', () => {
    const img = imagen(100, 200, [245, 245, 245], [{ x0: 30, x1: 69, y0: 90, y1: 109, color: CARNE }], 50)
    const foto = recortarSobreBlanco(img)
    // 40x20 de contenido + margen de 40/14 ≈ 3 px por lado; nada de lo negro.
    expect(foto.width).toBe(46)
    expect(foto.height).toBe(26)
    expect(Array.from(foto.data.slice(0, 4))).toEqual([255, 255, 255, 255])
  })
})
