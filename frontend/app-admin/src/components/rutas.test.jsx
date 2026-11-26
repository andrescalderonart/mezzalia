import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'

vi.mock('../context/AuthContext.jsx', () => ({ useAuth: vi.fn() }))
import { useAuth } from '../context/AuthContext.jsx'
import ProtectedRoute from './ProtectedRoute.jsx'
import RutaConRol from './RutaConRol.jsx'
import RequiereSucursal from './RequiereSucursal.jsx'
import InicioRedirect from './InicioRedirect.jsx'

// Pantalla de destino que muestra en que ruta quedamos (y el state, para
// verificar que RequiereSucursal recuerda de donde venia el usuario).
function Destino({ nombre }) {
  const location = useLocation()
  return <p>{nombre} desde={location.state?.desde ?? '-'}</p>
}

function montar(usuario, rutaInicial, guardia) {
  useAuth.mockReturnValue({ usuario })
  render(
    <MemoryRouter initialEntries={[rutaInicial]}>
      <Routes>
        <Route path="/" element={<Destino nombre="INICIO" />} />
        <Route path="/login" element={<Destino nombre="LOGIN" />} />
        <Route path="/seleccionar-sucursal" element={<Destino nombre="ELEGIR-SUCURSAL" />} />
        <Route element={guardia}>
          <Route path="/mesas" element={<Destino nombre="MESAS" />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('ProtectedRoute', () => {
  it('sin sesion manda a /login', () => {
    montar(null, '/mesas', <ProtectedRoute />)
    expect(screen.getByText(/^LOGIN/)).toBeInTheDocument()
  })

  it('con sesion deja pasar', () => {
    montar({ rol: 'MESERO' }, '/mesas', <ProtectedRoute />)
    expect(screen.getByText(/^MESAS/)).toBeInTheDocument()
  })
})

describe('RutaConRol', () => {
  const guardia = <RutaConRol permitidos={['ADMIN_RESTAURANTE', 'MESERO']} />

  it('deja pasar a un rol permitido', () => {
    montar({ rol: 'ADMIN_RESTAURANTE' }, '/mesas', guardia)
    expect(screen.getByText(/^MESAS/)).toBeInTheDocument()
  })

  it('SUPER_ADMIN (sin tenant) no entra a pantallas del restaurante: vuelve a /', () => {
    montar({ rol: 'SUPER_ADMIN' }, '/mesas', guardia)
    expect(screen.getByText(/^INICIO/)).toBeInTheDocument()
  })

  it('sin usuario tambien redirige a /', () => {
    montar(null, '/mesas', guardia)
    expect(screen.getByText(/^INICIO/)).toBeInTheDocument()
  })
})

describe('RequiereSucursal (RF-10 PR4, el flujo que se rompio en PR #19)', () => {
  it('MESERO sin sucursal elegida va a /seleccionar-sucursal recordando de donde venia', () => {
    montar({ rol: 'MESERO', sucursalId: null }, '/mesas', <RequiereSucursal />)
    expect(screen.getByText('ELEGIR-SUCURSAL desde=/mesas')).toBeInTheDocument()
  })

  it('MESERO con sucursal elegida entra', () => {
    montar({ rol: 'MESERO', sucursalId: 4 }, '/mesas', <RequiereSucursal />)
    expect(screen.getByText(/^MESAS/)).toBeInTheDocument()
  })

  it('ADMIN_RESTAURANTE sin sucursal entra: para el admin significa "todas las sucursales"', () => {
    montar({ rol: 'ADMIN_RESTAURANTE', sucursalId: null }, '/mesas', <RequiereSucursal />)
    expect(screen.getByText(/^MESAS/)).toBeInTheDocument()
  })
})

describe('InicioRedirect', () => {
  function montarInicio(usuario) {
    useAuth.mockReturnValue({ usuario })
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<InicioRedirect />} />
          <Route path="/tenants" element={<p>TENANTS</p>} />
          <Route path="/pedidos" element={<p>PEDIDOS</p>} />
        </Routes>
      </MemoryRouter>,
    )
  }

  it('SUPER_ADMIN aterriza en /tenants', () => {
    montarInicio({ rol: 'SUPER_ADMIN' })
    expect(screen.getByText('TENANTS')).toBeInTheDocument()
  })

  it.each(['ADMIN_RESTAURANTE', 'MESERO'])('%s aterriza en /pedidos', (rol) => {
    montarInicio({ rol })
    expect(screen.getByText('PEDIDOS')).toBeInTheDocument()
  })
})
