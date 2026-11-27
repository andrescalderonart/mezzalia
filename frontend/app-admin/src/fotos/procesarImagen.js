// Procesamiento de fotos para la vista de ingredientes (2026-11-27), 100% en
// el navegador del dueño: sin servicios externos ni costo por foto.
//
// Todas las funciones de este archivo son PURAS: reciben y devuelven imágenes
// como { width, height, data } (data = RGBA, 4 bytes por píxel, igual que un
// ImageData del canvas). Así se prueban con Vitest sin canvas (ver
// procesarImagen.test.js); la parte que sí necesita canvas vive en canvas.js.

const UMBRAL_CONTENIDO = 30 // distancia al fondo a partir de la cual un píxel es "comida"
const BORDE_SUAVE_DESDE = 8 // por debajo: fondo puro (transparente / blanco)

export function pixel(img, x, y) {
  const i = (y * img.width + x) * 4
  return [img.data[i], img.data[i + 1], img.data[i + 2]]
}

function esNegro([r, g, b]) {
  return r < 18 && g < 18 && b < 18
}

/** Distancia (canal máximo) de un color al color de fondo. */
export function distancia([r, g, b], fondo) {
  return Math.max(Math.abs(r - fondo[0]), Math.abs(g - fondo[1]), Math.abs(b - fondo[2]))
}

/**
 * Color de fondo = mediana de los píxeles de los bordes izquierdo y derecho,
 * ignorando las bandas negras que a veces agrega el generador de imágenes.
 */
export function muestrearFondo(img) {
  const muestras = []
  const pasos = Math.min(400, img.height)
  for (let k = 0; k < pasos; k++) {
    const y = Math.floor((k * (img.height - 1)) / Math.max(1, pasos - 1))
    for (const x of [2, img.width - 3]) {
      const c = pixel(img, Math.max(0, Math.min(img.width - 1, x)), y)
      if (!esNegro(c)) muestras.push(c)
    }
  }
  if (muestras.length === 0) return [255, 255, 255]
  return [0, 1, 2].map((canal) => {
    const valores = muestras.map((m) => m[canal]).sort((a, b) => a - b)
    return valores[Math.floor(valores.length / 2)]
  })
}

/** Cuántos píxeles con contenido tiene cada fila. */
export function perfilFilas(img, fondo, umbral = UMBRAL_CONTENIDO) {
  const cuenta = new Array(img.height).fill(0)
  for (let y = 0; y < img.height; y++) {
    for (let x = 0; x < img.width; x++) {
      const c = pixel(img, x, y)
      if (!esNegro(c) && distancia(c, fondo) > umbral) cuenta[y]++
    }
  }
  return cuenta
}

/**
 * Franjas horizontales de un plato desarmado: grupos de filas con contenido
 * separados por al menos `gapMin` filas vacías. Devuelve [{ y0, y1 }].
 * Las capas que se tocan (sin fila vacía entre ellas) salen juntas: para eso
 * están `cortarEn` y `unirConSiguiente`, que el dueño usa desde el panel.
 */
export function detectarFranjas(img, fondo, { gapMin = 6, minAlto = 12, minPixeles = 3 } = {}) {
  const cuenta = perfilFilas(img, fondo)
  const franjas = []
  let inicio = -1
  let vacias = 0
  for (let y = 0; y < img.height; y++) {
    if (cuenta[y] > minPixeles) {
      if (inicio < 0) inicio = y
      vacias = 0
    } else if (inicio >= 0 && ++vacias >= gapMin) {
      franjas.push({ y0: inicio, y1: y - vacias })
      inicio = -1
      vacias = 0
    }
  }
  if (inicio >= 0) franjas.push({ y0: inicio, y1: img.height - 1 })
  return franjas.filter((f) => f.y1 - f.y0 + 1 >= minAlto)
}

/**
 * Parte en dos la franja que contiene la fila `y`. Si `y` cae en un espacio
 * vacío (o en el borde de una franja), devuelve las franjas igual.
 */
export function cortarEn(franjas, y) {
  const i = franjas.findIndex((f) => y > f.y0 && y < f.y1)
  if (i < 0) return franjas
  const f = franjas[i]
  return [...franjas.slice(0, i), { y0: f.y0, y1: y }, { y0: y + 1, y1: f.y1 }, ...franjas.slice(i + 1)]
}

/** Une la franja `i` con la siguiente (para deshacer un corte o una detección de más). */
export function unirConSiguiente(franjas, i) {
  if (i < 0 || i >= franjas.length - 1) return franjas
  return [...franjas.slice(0, i), { y0: franjas[i].y0, y1: franjas[i + 1].y1 }, ...franjas.slice(i + 2)]
}

/**
 * Sugerencia de cortes dentro de una franja demasiado alta (capas que se
 * tocan): filas con muy poco contenido respecto a sus vecinas. Devuelve las
 * filas candidatas, ordenadas. Es solo una ayuda: el dueño confirma.
 */
export function sugerirCortes(cuenta, franja, { ventana = 12, proporcion = 0.35 } = {}) {
  const cortes = []
  const max = Math.max(...cuenta.slice(franja.y0, franja.y1 + 1))
  for (let y = franja.y0 + ventana; y <= franja.y1 - ventana; y++) {
    const v = cuenta[y]
    if (v > max * proporcion) continue
    const esMinimo = cuenta.slice(y - ventana, y + ventana + 1).every((otro) => otro >= v)
    if (esMinimo && (cortes.length === 0 || y - cortes[cortes.length - 1] > ventana * 2)) cortes.push(y)
  }
  return cortes
}

function alfaPorDistancia(d) {
  if (d <= BORDE_SUAVE_DESDE) return 0
  if (d >= UMBRAL_CONTENIDO) return 255
  return Math.round(((d - BORDE_SUAVE_DESDE) * 255) / (UMBRAL_CONTENIDO - BORDE_SUAVE_DESDE))
}

/**
 * Recorta una franja con FONDO TRANSPARENTE y sin márgenes a los lados.
 * Devuelve { width, height, data } listo para pasar a PNG.
 */
export function recortarFranjaTransparente(img, franja, fondo) {
  let minX = img.width
  let maxX = -1
  for (let y = franja.y0; y <= franja.y1; y++) {
    for (let x = 0; x < img.width; x++) {
      if (distancia(pixel(img, x, y), fondo) > UMBRAL_CONTENIDO) {
        if (x < minX) minX = x
        if (x > maxX) maxX = x
      }
    }
  }
  if (maxX < 0) return null
  minX = Math.max(0, minX - 2)
  maxX = Math.min(img.width - 1, maxX + 2)
  const width = maxX - minX + 1
  const height = franja.y1 - franja.y0 + 1
  const data = new Uint8ClampedArray(width * height * 4)
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const c = pixel(img, minX + x, franja.y0 + y)
      const o = (y * width + x) * 4
      data[o] = c[0]
      data[o + 1] = c[1]
      data[o + 2] = c[2]
      data[o + 3] = alfaPorDistancia(distancia(c, fondo))
    }
  }
  return { width, height, data }
}

/** Toda la imagen con fondo transparente (para una capa suelta subida aparte). */
export function quitarFondo(img, fondo = muestrearFondo(img)) {
  return recortarFranjaTransparente(img, { y0: 0, y1: img.height - 1 }, fondo)
}

/**
 * Foto suelta para una tarjeta (vista esparcida): recorta al contenido con un
 * margen, ignora bandas negras y pasa el fondo casi blanco a BLANCO PURO, para
 * que se funda con la tarjeta blanca de la carta.
 */
export function recortarSobreBlanco(img, fondo = muestrearFondo(img)) {
  let minX = img.width
  let maxX = -1
  let minY = img.height
  let maxY = -1
  for (let y = 0; y < img.height; y++) {
    for (let x = 0; x < img.width; x++) {
      const c = pixel(img, x, y)
      if (!esNegro(c) && distancia(c, fondo) > UMBRAL_CONTENIDO) {
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
      }
    }
  }
  if (maxX < 0) return { ...img }
  const margen = Math.round(Math.max(maxX - minX, maxY - minY) / 14)
  minX = Math.max(0, minX - margen)
  minY = Math.max(0, minY - margen)
  maxX = Math.min(img.width - 1, maxX + margen)
  maxY = Math.min(img.height - 1, maxY + margen)
  const width = maxX - minX + 1
  const height = maxY - minY + 1
  const data = new Uint8ClampedArray(width * height * 4)
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const c = pixel(img, minX + x, minY + y)
      const d = esNegro(c) ? 0 : distancia(c, fondo)
      const t = d <= BORDE_SUAVE_DESDE ? 1 : d >= UMBRAL_CONTENIDO ? 0 : (UMBRAL_CONTENIDO - d) / (UMBRAL_CONTENIDO - BORDE_SUAVE_DESDE)
      const o = (y * width + x) * 4
      for (let k = 0; k < 3; k++) data[o + k] = Math.round(c[k] * (1 - t) + 255 * t)
      data[o + 3] = 255
    }
  }
  return { width, height, data }
}
