// Lineas del carrito con modificaciones ("sin cebolla", 2026-11-26).
//
// El carrito es un objeto { clave: cantidad }. Para el plato tal cual la
// clave es su id ("12"), asi que todo el codigo que ya hacia carrito[item.id]
// sigue funcionando. Un plato con ingredientes quitados es OTRA linea:
// "12~3.7" = plato 12 sin los ingredientes 3 y 7 (ids ordenados, para que
// "sin cebolla y tomate" y "sin tomate y cebolla" sean la misma linea).

export function claveDe(itemId, sinIds = []) {
  const ids = [...new Set(sinIds)].sort((a, b) => a - b)
  return ids.length === 0 ? String(itemId) : `${itemId}~${ids.join('.')}`
}

export function parsearClave(clave) {
  const [itemId, sin] = String(clave).split('~')
  return { itemId: Number(itemId), sinIds: sin ? sin.split('.').map(Number) : [] }
}

/** Nombres de los ingredientes quitados, en el orden del plato. */
export function nombresQuitados(item, sinIds) {
  if (!sinIds.length || !item?.ingredientes) return []
  return item.ingredientes.filter((ing) => sinIds.includes(ing.id)).map((ing) => ing.nombre)
}

/** "sin cebolla y tomate" */
export function textoSin(nombres) {
  if (nombres.length === 0) return ''
  const minusc = nombres.map((n) => n.toLowerCase())
  const lista = minusc.length === 1 ? minusc[0] : `${minusc.slice(0, -1).join(', ')} y ${minusc[minusc.length - 1]}`
  return `sin ${lista}`
}
