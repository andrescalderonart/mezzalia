import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'

const apiJson = vi.fn()
vi.mock('../context/AuthContext.jsx', () => ({ useApiJson: () => apiJson }))
import MesasPorConfirmar from './MesasPorConfirmar.jsx'

const pedido = (id, mesaId, apodo, items, extra = {}) => ({
  id,
  mesaId,
  apodo,
  estado: 'POR_CONFIRMAR',
  total: 32000,
  notas: null,
  items,
  creadoEn: '2026-11-27T20:00:00',
  ...extra,
})

let pendientes
beforeEach(() => {
  apiJson.mockReset()
  pendientes = [
    pedido(1, 7, 'Andrés', [{ cantidad: 1, nombreItem: 'Hamburguesa', sinIngredientes: ['Cebolla'] }]),
    pedido(2, 7, null, [{ cantidad: 2, nombreItem: 'Limonada', sinIngredientes: [] }]),
    pedido(3, 9, 'Laura', [{ cantidad: 1, nombreItem: 'Taco', sinIngredientes: [] }]),
  ]
  apiJson.mockImplementation(async (url) => (url.startsWith('/api/admin/pedidos?estado=POR_CONFIRMAR') ? pendientes : []))
})

const nombreMesa = (id) => ({ 7: 'Terraza 4', 9: 'Barra 1' })[id]

describe('MesasPorConfirmar', () => {
  it('agrupa por mesa, con el apodo de cada quien y lo que quitó', async () => {
    render(<MesasPorConfirmar nombreMesa={nombreMesa} />)
    const terraza = (await screen.findByText('Terraza 4')).closest('article')
    expect(within(terraza).getByText('Andrés')).toBeInTheDocument()
    expect(within(terraza).getByText('Sin nombre')).toBeInTheDocument()
    expect(within(terraza).getByText(/sin cebolla/i)).toBeInTheDocument()
    expect(within(terraza).getByText('2 pedidos', { exact: false })).toBeInTheDocument()
    expect(screen.getByText('Barra 1')).toBeInTheDocument()
  })

  it('confirmar la mesa llama al endpoint de esa mesa y la saca de la lista', async () => {
    const onConfirmada = vi.fn()
    render(<MesasPorConfirmar nombreMesa={nombreMesa} onConfirmada={onConfirmada} />)
    const terraza = (await screen.findByText('Terraza 4')).closest('article')
    fireEvent.click(within(terraza).getByRole('button', { name: /Confirmar mesa/ }))

    await waitFor(() => expect(apiJson).toHaveBeenCalledWith('/api/admin/pedidos/mesa/7/confirmar', { method: 'PATCH' }))
    await waitFor(() => expect(screen.queryByText('Terraza 4')).not.toBeInTheDocument())
    expect(screen.getByText('Barra 1')).toBeInTheDocument()
    expect(onConfirmada).toHaveBeenCalled()
  })

  it('sin pedidos pendientes no ocupa espacio', async () => {
    pendientes = []
    const { container } = render(<MesasPorConfirmar nombreMesa={nombreMesa} />)
    await waitFor(() => expect(apiJson).toHaveBeenCalled())
    expect(container).toBeEmptyDOMElement()
  })
})
