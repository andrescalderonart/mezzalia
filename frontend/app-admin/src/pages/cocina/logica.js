// Logica pura de la vista de cocina, separada del componente para poder
// probarla sin montar React (ver logica.test.js).

// Umbrales de "este pedido ya lleva mucho esperando". Son un punto de
// partida razonable para un restaurante casual, NO un numero validado con
// un cliente real: ajustarlos cuando el piloto diga que se siente mal.
export const MINUTOS_ATENCION = 10
export const MINUTOS_URGENTE = 20

/** Minutos enteros transcurridos desde `fechaIso` hasta `ahora` (nunca negativo). */
export function minutosDesde(fechaIso, ahora = Date.now()) {
  const inicio = new Date(fechaIso).getTime()
  if (Number.isNaN(inicio)) return 0
  return Math.max(0, Math.floor((ahora - inicio) / 60000))
}

/** 'normal' | 'atencion' | 'urgente' segun cuanto lleva esperando. */
export function urgencia(minutos) {
  if (minutos >= MINUTOS_URGENTE) return 'urgente'
  if (minutos >= MINUTOS_ATENCION) return 'atencion'
  return 'normal'
}

/**
 * En cocina el orden es FIFO: lo que lleva mas tiempo esperando va primero
 * (al reves que PedidosPage, donde lo nuevo va arriba para que el mesero lo
 * vea). Desempata por id para que el orden no "baile" entre refrescos.
 */
export function ordenarParaCocina(pedidos) {
  return [...pedidos].sort((a, b) => new Date(a.creadoEn) - new Date(b.creadoEn) || a.id - b.id)
}

/**
 * Ids de pedidos que no estaban en `idsConocidos`. Con `idsConocidos` null
 * (primera carga) devuelve [] a proposito: al abrir la pantalla no tiene
 * que sonar la alarma por todos los pedidos que ya estaban.
 */
export function idsNuevos(idsConocidos, pedidos) {
  if (idsConocidos == null) return []
  return pedidos.map((p) => p.id).filter((id) => !idsConocidos.has(id))
}
