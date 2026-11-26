import { describe, expect, it } from 'vitest'
import { idsNuevos, minutosDesde, ordenarParaCocina, urgencia } from './logica.js'

const AHORA = new Date('2026-11-26T20:30:00').getTime()

describe('minutosDesde', () => {
  it('cuenta minutos enteros', () => {
    expect(minutosDesde('2026-11-26T20:17:30', AHORA)).toBe(12)
  })
  it('nunca es negativo (reloj del servidor adelantado)', () => {
    expect(minutosDesde('2026-11-26T20:31:00', AHORA)).toBe(0)
  })
  it('una fecha invalida cuenta como 0 en vez de NaN', () => {
    expect(minutosDesde('no-es-fecha', AHORA)).toBe(0)
  })
})

describe('urgencia', () => {
  it.each([
    [0, 'normal'],
    [9, 'normal'],
    [10, 'atencion'],
    [19, 'atencion'],
    [20, 'urgente'],
    [95, 'urgente'],
  ])('%i min -> %s', (minutos, esperado) => {
    expect(urgencia(minutos)).toBe(esperado)
  })
})

describe('ordenarParaCocina', () => {
  it('lo mas viejo primero, desempatando por id, sin mutar el original', () => {
    const pedidos = [
      { id: 3, creadoEn: '2026-11-26T20:10:00' },
      { id: 1, creadoEn: '2026-11-26T20:05:00' },
      { id: 2, creadoEn: '2026-11-26T20:10:00' },
    ]
    expect(ordenarParaCocina(pedidos).map((p) => p.id)).toEqual([1, 2, 3])
    expect(pedidos.map((p) => p.id)).toEqual([3, 1, 2])
  })
})

describe('idsNuevos', () => {
  it('en la primera carga no reporta nada (no suena al abrir la pantalla)', () => {
    expect(idsNuevos(null, [{ id: 1 }, { id: 2 }])).toEqual([])
  })
  it('reporta solo los que no se conocian', () => {
    expect(idsNuevos(new Set([1, 2]), [{ id: 1 }, { id: 2 }, { id: 7 }])).toEqual([7])
  })
})
