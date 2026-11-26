/**
 * Sin auth: la carta pública no lleva Bearer ni maneja refresh -- el token
 * de la URL (sesión de mesa) es toda la credencial que existe en este
 * frontend. Deliberadamente más simple que api/client.js de app-admin.
 */
export async function obtenerCarta(token) {
  const resp = await fetch(`/api/publico/mesas/${encodeURIComponent(token)}/carta`)
  const datos = await resp.json().catch(() => null)
  if (!resp.ok) {
    throw new Error(datos?.mensaje || 'No se pudo cargar la carta.')
  }
  return datos
}


/**
 * RF-03/RF-04: crea un pedido para la mesa detras de este token. El
 * `payload` no lleva ningun precio -- ver ItemSolicitadoRequest en el
 * backend: el precio siempre se recalcula del lado del servidor (RF-05).
 * `claveIdempotencia` debe ser la MISMA en un reintento del mismo intento
 * de envio (ver CartaPage.jsx) para que un doble-tap o un timeout de red no
 * cree un pedido duplicado.
 */
export async function crearPedido(token, payload) {
  const resp = await fetch(`/api/publico/mesas/${encodeURIComponent(token)}/pedidos`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  const datos = await resp.json().catch(() => null)
  if (!resp.ok) {
    throw new Error(datos?.mensaje || 'No se pudo enviar el pedido.')
  }
  return datos
}

/**
 * "Llamar al mesero" (tipo MESERO) o "pedir la cuenta" (tipo CUENTA) desde
 * la mesa. Tocarlo varias veces no duplica el aviso: el backend devuelve el
 * mismo llamado mientras siga pendiente (ver LlamadoService.llamar).
 */
export async function llamarDesdeMesa(token, tipo) {
  const resp = await fetch(`/api/publico/mesas/${encodeURIComponent(token)}/llamados`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ tipo }),
  })
  const datos = await resp.json().catch(() => null)
  if (!resp.ok) {
    throw new Error(datos?.mensaje || 'No pudimos avisar al personal. Intenta de nuevo.')
  }
  return datos
}
