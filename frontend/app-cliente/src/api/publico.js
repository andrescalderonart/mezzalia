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
