import { Navigate, Route, Routes } from 'react-router-dom'
import CartaPage from './pages/CartaPage.jsx'

export default function App() {
  return (
    <Routes>
      <Route path="/carta/:token" element={<CartaPage />} />
      {/* Cualquier ruta desconocida (o la raiz, sin token) redirige a un
          token que se sabe invalido -- CartaPage hace el fetch real, el
          backend responde 404 con el mismo mensaje ya pensado para "el
          enlace ya no es valido", y listo: una sola pantalla de error
          real en vez de mantener una pagina 404 aparte y desactualizada. */}
      <Route path="*" element={<Navigate to="/carta/invalido" replace />} />
    </Routes>
  )
}
