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
})
