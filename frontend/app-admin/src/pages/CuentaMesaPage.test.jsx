import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'

const apiJson = vi.fn()
vi.mock('../context/AuthContext.jsx', () => ({ useApiJson: () => apiJson }))
import CuentaMesaPage from './CuentaMesaPage.jsx'

const pedido = (id, apodo, estado, total, items) => ({ id, mesaId: 7, apodo, estado, total, notas: null, items })

function renderCuenta() {
  return render(
    <MemoryRouter initialEntries={['/mesas/7/cuenta']}>
      <Routes>
        <Route path="/mesas/:mesaId/cuenta" element={<CuentaMesaPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

let pedidos
beforeEach(() => {
  apiJson.mockReset()
  pedidos = [
    pedido(1, 'Andrés', 'RECIBIDO', 32000, [
      { cantidad: 1, nombreItem: 'Hamburguesa', subtotal: 32000, sinIngredientes: ['Cebolla'] },
    ]),
    pedido(2, null, 'LISTO', 18000, [{ cantidad: 2, nombreItem: 'Limonada', subtotal: 18000, sinIngredientes: [] }]),
    pedido(3, 'Andrés', 'CANCELADO', 50000, [{ cantidad: 1, nombreItem: 'Bandeja', subtotal: 50000, sinIngredientes: [] }]),
  ]
  apiJson.mockImplementation(async (url) => {
    if (url === '/api/admin/pedidos/mesa/7/cuenta') return pedidos
    if (url === '/api/admin/mesas') return [{ id: 7, codigo: 'Terraza 4' }]
    return []
  })
})

describe('CuentaMesaPage', () => {
  it('agrupa por persona, marca lo quitado y no suma lo cancelado', async () => {
    renderCuenta()
    expect(await screen.findByText('Cuenta de Terraza 4')).toBeInTheDocument()
    expect(screen.getByText('Andrés')).toBeInTheDocument()
    expect(screen.getByText('Sin nombre')).toBeInTheDocument()
    expect(screen.getByText(/sin cebolla/i)).toBeInTheDocument()
    expect(screen.queryByText('Bandeja')).not.toBeInTheDocument()
    // 32.000 + 18.000; la propina sugerida es el 10 % y voluntaria.
    expect(screen.getByText('Total consumido').nextSibling.textContent).toMatch(/50\.000/)
    expect(screen.getByText(/Propina sugerida \(voluntaria, 10 %\)/).nextSibling.textContent).toMatch(/5\.000/)
    expect(screen.getByText(/no es una factura/)).toBeInTheDocument()
  })

  it('avisa si quedan pedidos sin confirmar antes de cobrar', async () => {
    pedidos.push(pedido(4, 'Laura', 'POR_CONFIRMAR', 9000, [{ cantidad: 1, nombreItem: 'Limonada', subtotal: 9000, sinIngredientes: [] }]))
    renderCuenta()
    expect(await screen.findByText(/1 pedido sin confirmar/)).toBeInTheDocument()
  })
})
