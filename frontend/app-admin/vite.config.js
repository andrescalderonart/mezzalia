import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// El backend (Spring Boot) no tiene CORS configurado -- a proposito, no hace
// falta: en dev, este proxy hace que /api y /uploads salgan del MISMO origen
// (localhost:5173) hacia el navegador, y Vite los reenvia a localhost:8080
// por detras. En produccion, app-admin se sirve desde el mismo dominio que
// el backend (o detras del mismo reverse proxy), asi que tampoco hace falta
// CORS ahi. Si algun dia se separan en dominios distintos de verdad, esto
// hay que revisitarlo (CORS explicito en SecurityConfig).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/uploads': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  // Auditoria 2026-09-08 §2.1 / paso 6: app-admin no tenia ningun test, y esa
  // fue la causa raiz del bug de PR #19 (seleccion de sucursal rota un PR
  // entero sin que nada lo detectara). jsdom para poder montar componentes.
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.js'],
    restoreMocks: true,
  },
})
