# app-admin

Panel del restaurante (login + gestión de carta: categorías, platos, precios, fotos).
React + Vite + Tailwind, sin librerías de estado/routing pesadas -- solo
`react-router-dom` para las rutas y Context API para la sesión.

## Requisitos

- Node.js 18+ (probado con Node 22).
- El backend (`../../backend`) corriendo en `http://localhost:8080` (perfil `dev,h2` para desarrollo local, ver `backend/README` / conversación del equipo sobre como levantarlo).

## Correr en desarrollo

```bash
npm install
npm run dev
```

Abre `http://localhost:5173`. En dev, Vite hace de proxy de `/api` y `/uploads`
hacia `http://localhost:8080` (ver `vite.config.js`) -- así el navegador ve
todo como si fuera el mismo origen y no hace falta configurar CORS en el
backend.

## Usuarios de prueba (perfil `dev` del backend)

- `admin.demo` / `Demo1234!` -- rol ADMIN_RESTAURANTE, puede editar la carta.
- `mesero.demo` / `Demo1234!` -- rol MESERO, solo puede ver la carta (los
  botones de crear/editar/borrar no aparecen).

## Qué falta (a propósito, no es un olvido)

- El logo real de Mezzalia: hoy `src/components/LogoMark.jsx` es un
  monograma inline inspirado en la paleta del logo (terracota/dorado), no el
  ilustrado real -- ese solo existe como foto de un letrero, no como archivo
  vectorial/PNG limpio. En cuanto exista ese archivo, se reemplaza ahí.
- Página de "pedidos" / vista del comensal: no es parte de app-admin, vive
  en `frontend/app-cliente` (todavía sin empezar).
- Gestión de variantes de producto (tamaño, extras): deferido a propósito,
  ver la conversación de diseño del módulo `menu` en el backend.

## Build de producción

```bash
npm run build
```

Genera `dist/` (ignorado por git). Cómo se sirve en producción (¿el mismo
Spring Boot como recursos estáticos? ¿un servidor separado?) es una decisión
de despliegue que todavía no se ha tomado -- no asuman una sin discutirla.
