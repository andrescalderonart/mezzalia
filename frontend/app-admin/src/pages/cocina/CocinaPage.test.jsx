import { beforeEach, describe, expect, it, vi } from 'vitest'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

const apiJson = vi.fn()
vi.mock('../../context/AuthContext.jsx', () => ({ useApiJson: () => apiJson }))
vi.mock('./sonido.js', () => ({ prepararAudio: vi.fn(() => true), sonarPedidoNuevo: vi.fn() }))
import { sonarPedidoNuevo } from './sonido.js'
import CocinaPage from './CocinaPage.jsx'

const hace = (min) => new Date(Date.now() - min * 60000).toISOString()

function pedido(id, estado, extra = {}) {
  return {
    id,
    mesaId: 1,
    estado,
    total: 45000,
    notas: null,
    items: [{ cantidad: 2, nombreItem: 'Bandeja paisa', subtotal: 45000 }],
    comensal: { id: 9, nombre: 'Laura', telefono: '300***4567' },
    creadoEn: hace(1),
    ...extra,
  }
}

// Respuestas del "backend" por URL; se pueden cambiar entre refrescos.
let datos
function responder() {
  apiJson.mockImplementation(async (url, opciones) => {
    if (url === '/api/admin/mesas') return [{ id: 1, codigo: 'Terraza 4' }]
    if (url === '/api/admin/pedidos?estado=RECIBIDO') return datos.RECIBIDO
    if (url === '/api/admin/pedidos?estado=EN_PREPARACION') return datos.EN_PREPARACION
    if (opciones?.method === 'PATCH') return {}
    throw new Error(`URL inesperada ${url}`)
  })
}

const montar = () =>
  render(
    <MemoryRouter>
      <CocinaPage />
    </MemoryRouter>,
  )

beforeEach(() => {
  apiJson.mockReset()
  sonarPedidoNuevo.mockClear()
  datos = { RECIBIDO: [], EN_PREPARACION: [] }
  responder()
})

describe('CocinaPage', () => {
  it('pide al backend solo los estados de cocina, nunca la lista completa', async () => {
    montar()
    await waitFor(() => expect(apiJson).toHaveBeenCalledWith('/api/admin/pedidos?estado=RECIBIDO'))
    expect(apiJson).toHaveBeenCalledWith('/api/admin/pedidos?estado=EN_PREPARACION')
    expect(apiJson).not.toHaveBeenCalledWith('/api/admin/pedidos')
  })

  it('muestra los pedidos en su columna, lo mas viejo primero, con mesa, items y notas', async () => {
    datos.RECIBIDO = [
      pedido(2, 'RECIBIDO', { creadoEn: hace(3) }),
      pedido(1, 'RECIBIDO', { creadoEn: hace(12), notas: 'sin cebolla' }),
    ]
    datos.EN_PREPARACION = [pedido(3, 'EN_PREPARACION')]
    montar()

    const nuevos = await screen.findByRole('region', { name: 'Nuevos' })
    const tarjetas = await within(nuevos).findAllByRole('article')
    expect(tarjetas.map((t) => within(t).getByText(/^#/).textContent)).toEqual(['#1', '#2'])
    expect(within(tarjetas[0]).getByText('“sin cebolla”')).toBeInTheDocument()
    expect(within(tarjetas[0]).getByText('hace 12 min')).toBeInTheDocument()
    expect(tarjetas[0].dataset.urgencia).toBe('atencion')
    expect(within(tarjetas[0]).getByText('Terraza 4')).toBeInTheDocument()

    const enPrep = screen.getByRole('region', { name: 'En preparación' })
    expect(within(enPrep).getAllByRole('article')).toHaveLength(1)
  })

  it('no muestra precios ni datos del comensal (Habeas Data)', async () => {
    datos.RECIBIDO = [pedido(1, 'RECIBIDO')]
    montar()
    await screen.findAllByRole('article')
    expect(screen.queryByText(/Laura/)).not.toBeInTheDocument()
    expect(screen.queryByText(/300/)).not.toBeInTheDocument()
    expect(screen.queryByText(/\$/)).not.toBeInTheDocument()
  })

  it('"Empezar" pasa el pedido a EN_PREPARACION y "¡Listo!" a LISTO', async () => {
    datos.RECIBIDO = [pedido(1, 'RECIBIDO')]
    datos.EN_PREPARACION = [pedido(2, 'EN_PREPARACION')]
    montar()

    fireEvent.click(await screen.findByRole('button', { name: 'Empezar' }))
    await waitFor(() =>
      expect(apiJson).toHaveBeenCalledWith('/api/admin/pedidos/1/estado', { method: 'PATCH', body: { estado: 'EN_PREPARACION' } }),
    )
    fireEvent.click(screen.getByRole('button', { name: '¡Listo!' }))
    await waitFor(() =>
      expect(apiJson).toHaveBeenCalledWith('/api/admin/pedidos/2/estado', { method: 'PATCH', body: { estado: 'LISTO' } }),
    )
  })

  it('si el backend rechaza el cambio (otro dispositivo ya lo cancelo), muestra el error', async () => {
    datos.RECIBIDO = [pedido(1, 'RECIBIDO')]
    montar()
    apiJson.mockImplementationOnce(async () => {
      throw new Error('Transición de estado inválida')
    })
    fireEvent.click(await screen.findByRole('button', { name: 'Empezar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Transición de estado inválida')
  })

  it('suena solo con pedidos NUEVOS y solo si el sonido esta activado', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      datos.RECIBIDO = [pedido(1, 'RECIBIDO')]
      montar()
      await screen.findAllByRole('article')
      fireEvent.click(screen.getByRole('button', { name: /Activar sonido/ }))
      expect(sonarPedidoNuevo).toHaveBeenCalledTimes(1) // el pitido de prueba al activar
      sonarPedidoNuevo.mockClear()

      // Refresco sin cambios: no suena.
      await act(() => vi.advanceTimersByTimeAsync(6000))
      expect(sonarPedidoNuevo).not.toHaveBeenCalled()

      // Llega un pedido nuevo: suena una vez.
      datos.RECIBIDO = [pedido(1, 'RECIBIDO'), pedido(5, 'RECIBIDO')]
      await act(() => vi.advanceTimersByTimeAsync(6000))
      expect(sonarPedidoNuevo).toHaveBeenCalledTimes(1)
    } finally {
      vi.useRealTimers()
    }
  })

  it('muestra en grande lo que el comensal quito ("SIN CEBOLLA")', async () => {
    datos.RECIBIDO = [
      pedido(1, 'RECIBIDO', {
        items: [{ cantidad: 1, nombreItem: 'Hamburguesa', subtotal: 32000, sinIngredientes: ['Cebolla', 'Tomate'] }],
      }),
    ]
    montar()
    expect(await screen.findByText('Sin cebolla, tomate')).toBeInTheDocument()
  })

  it('columnas vacias muestran un mensaje en vez de nada', async () => {
    montar()
    expect(await screen.findByText('Sin pedidos nuevos.')).toBeInTheDocument()
    expect(screen.getByText('Nada en preparación.')).toBeInTheDocument()
  })
})
