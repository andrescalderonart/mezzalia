// Prepara una foto suelta antes de subirla (2026-11-27), según dónde se va a
// mostrar. Si algo falla (formato raro, navegador viejo) se sube la original:
// aligerar es una mejora, no un requisito.
import { aArchivo, cargarImagen } from './canvas.js'
import { quitarFondo, recortarSobreBlanco } from './procesarImagen.js'

/** Capa de un plato APILADO: fondo transparente, máx. 600 px de ancho. */
export async function prepararCapa(archivo) {
  try {
    const recorte = quitarFondo(await cargarImagen(archivo, 1600))
    if (!recorte) return archivo
    return await aArchivo(recorte, { escala: Math.min(1, 600 / recorte.width), transparente: true, nombre: 'capa' })
  } catch {
    return archivo
  }
}

/** Ingrediente de un plato ESPARCIDO: recortado sobre blanco, máx. 720 px. */
export async function prepararTarjeta(archivo) {
  try {
    const foto = recortarSobreBlanco(await cargarImagen(archivo, 1600))
    const escala = Math.min(1, 720 / Math.max(foto.width, foto.height))
    return await aArchivo(foto, { escala, transparente: false, nombre: 'ingrediente' })
  } catch {
    return archivo
  }
}

/** Foto principal del plato: solo se reduce (máx. 1400 px) si es pesada. */
export async function prepararFotoPlato(archivo) {
  if (archivo.size < 350 * 1024) return archivo
  try {
    const imagen = await cargarImagen(archivo, 1400)
    return await aArchivo(imagen, { transparente: false, nombre: 'plato' })
  } catch {
    return archivo
  }
}
