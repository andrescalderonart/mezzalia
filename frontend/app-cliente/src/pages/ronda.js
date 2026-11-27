// "Otra ronda" (enfoque en bares, 2026-11-26): se recuerda en ESTE celular
// lo ultimo que pidio para esta mesa, para repetirlo con un toque. Va en
// localStorage por token de mesa: el token cambia en cada sesion de mesa
// (QR nuevo = cuenta nueva), asi que una ronda de anoche no aparece hoy.
// Todo acceso a localStorage va en try/catch: en modo privado de Safari o con
// el almacenamiento lleno puede lanzar, y "otra ronda" es una comodidad, no
// algo que deba romper la carta.

import { claveDe, nombresQuitados, textoSin } from './carrito.js'

const PREFIJO = 'mezzalia.ultimaRonda.'

export function guardarUltimaRonda(token, items) {
  try {
    localStorage.setItem(
      PREFIJO + token,
      JSON.stringify(items.map(({ itemMenuId, cantidad, sinIngredientes }) => ({ itemMenuId, cantidad, sinIngredientes: sinIngredientes || [] }))),
    )
  } catch {
    // Sin almacenamiento: simplemente no habra boton de "otra ronda".
  }
}

export function leerUltimaRonda(token) {
  try {
    const crudo = localStorage.getItem(PREFIJO + token)
    const items = crudo ? JSON.parse(crudo) : []
    return Array.isArray(items) ? items.filter((i) => Number.isInteger(i?.itemMenuId) && i.cantidad > 0) : []
  } catch {
    return []
  }
}

/**
 * Convierte la ultima ronda guardada en un carrito ({ itemId: cantidad })
 * usando la carta ACTUAL: se descarta lo que ya no existe o esta agotado
 * (no se puede repetir una cerveza que se acabo). Devuelve tambien que se
 * descarto, para poder avisarle al comensal.
 */
export function rondaComoCarrito(ronda, itemsPorId) {
  const carrito = {}
  const descartados = []
  for (const { itemMenuId, cantidad, sinIngredientes = [] } of ronda) {
    const item = itemsPorId[itemMenuId]
    if (item && item.disponible) {
      // "Sin cebolla" se repite tal cual, salvo ingredientes que ya no
      // existan o ya no se puedan quitar (se ignoran: el plato sale normal).
      const quitablesHoy = new Set((item.ingredientes || []).filter((i) => i.removible).map((i) => i.id))
      const clave = claveDe(itemMenuId, sinIngredientes.filter((id) => quitablesHoy.has(id)))
      carrito[clave] = (carrito[clave] || 0) + cantidad
    } else if (item) {
      descartados.push(item.nombre)
    }
  }
  return { carrito, descartados }
}

/** "2× Cerveza, 1× Papas" para mostrar en el boton. */
export function resumenRonda(ronda, itemsPorId) {
  return ronda
    .filter(({ itemMenuId }) => itemsPorId[itemMenuId])
    .map(({ itemMenuId, cantidad, sinIngredientes = [] }) => {
      const sin = textoSin(nombresQuitados(itemsPorId[itemMenuId], sinIngredientes))
      return `${cantidad}× ${itemsPorId[itemMenuId].nombre}${sin ? ` (${sin})` : ''}`
    })
    .join(', ')
}
