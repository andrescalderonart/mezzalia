import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Puerto 5174 a propósito: app-admin ya usa 5173 (ver frontend/app-admin/
// vite.config.js) y ambos frontends corren en dev al mismo tiempo contra el
// mismo backend. Mismo proxy de /api (y /uploads, por si algún día la carta
// pública referencia imágenes con ruta relativa) hacia localhost:8080.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/uploads': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
