// Puente entre archivos/canvas y las funciones puras de procesarImagen.js.

/**
 * Lee un archivo de imagen y devuelve { width, height, data } (RGBA),
 * reducido para que su lado mayor no pase de `ladoMax` (procesar una foto de
 * 4000 px en JS puro tardaría demasiado y no aporta calidad al celular).
 */
export async function cargarImagen(archivo, ladoMax = 1600) {
  const bitmap = await createImageBitmap(archivo)
  const escala = Math.min(1, ladoMax / Math.max(bitmap.width, bitmap.height))
  const width = Math.round(bitmap.width * escala)
  const height = Math.round(bitmap.height * escala)
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d', { willReadFrequently: true })
  ctx.drawImage(bitmap, 0, 0, width, height)
  bitmap.close?.()
  const { data } = ctx.getImageData(0, 0, width, height)
  return { width, height, data }
}

function aCanvas(imagen, escala = 1) {
  const base = document.createElement('canvas')
  base.width = imagen.width
  base.height = imagen.height
  base.getContext('2d').putImageData(new ImageData(imagen.data, imagen.width, imagen.height), 0, 0)
  if (escala === 1) return base
  const destino = document.createElement('canvas')
  destino.width = Math.max(1, Math.round(imagen.width * escala))
  destino.height = Math.max(1, Math.round(imagen.height * escala))
  const ctx = destino.getContext('2d')
  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(base, 0, 0, destino.width, destino.height)
  return destino
}

/** Vista previa (data URL) para mostrar en pantalla. */
export function aDataUrl(imagen, escala = 1) {
  return aCanvas(imagen, escala).toDataURL('image/png')
}

/**
 * Exporta para subir. Prefiere WebP (mucho más liviano y con transparencia);
 * si el navegador no sabe codificarlo (Safari viejo), cae a PNG o JPEG.
 * `transparente` decide el respaldo: PNG si hace falta alfa, JPEG si no.
 */
export function aArchivo(imagen, { escala = 1, transparente, nombre = 'foto' } = {}) {
  const canvas = aCanvas(imagen, escala)
  return new Promise((resolver, rechazar) => {
    canvas.toBlob(
      (webp) => {
        if (webp && webp.type === 'image/webp') {
          resolver(new File([webp], `${nombre}.webp`, { type: 'image/webp' }))
          return
        }
        const tipo = transparente ? 'image/png' : 'image/jpeg'
        canvas.toBlob(
          (blob) => (blob ? resolver(new File([blob], `${nombre}.${transparente ? 'png' : 'jpg'}`, { type: tipo })) : rechazar(new Error('No se pudo procesar la foto'))),
          tipo,
          0.85,
        )
      },
      'image/webp',
      0.85,
    )
  })
}
