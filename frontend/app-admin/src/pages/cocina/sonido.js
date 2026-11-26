// Aviso sonoro de pedido nuevo con Web Audio (sin archivos que servir ni
// cachear). Los navegadores solo dejan sonar audio despues de un gesto del
// usuario, por eso la pantalla tiene un boton "Activar sonido" que llama a
// `prepararAudio()` dentro del click.
let contexto = null

export function prepararAudio() {
  const Ctx = globalThis.AudioContext || globalThis.webkitAudioContext
  if (!Ctx) return false
  if (!contexto) contexto = new Ctx()
  if (contexto.state === 'suspended') contexto.resume()
  return true
}

/** Dos notas cortas ascendentes: se oye sobre el ruido de una cocina sin ser una alarma. */
export function sonarPedidoNuevo() {
  if (!contexto) return
  const t0 = contexto.currentTime
  ;[880, 1318.5].forEach((frecuencia, i) => {
    const osc = contexto.createOscillator()
    const gan = contexto.createGain()
    osc.type = 'triangle'
    osc.frequency.value = frecuencia
    const inicio = t0 + i * 0.18
    gan.gain.setValueAtTime(0.0001, inicio)
    gan.gain.exponentialRampToValueAtTime(0.4, inicio + 0.02)
    gan.gain.exponentialRampToValueAtTime(0.0001, inicio + 0.35)
    osc.connect(gan).connect(contexto.destination)
    osc.start(inicio)
    osc.stop(inicio + 0.4)
  })
}
