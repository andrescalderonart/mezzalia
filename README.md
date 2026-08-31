# Carta Digital SaaS — Carta + Comanda para Restaurantes

Plataforma SaaS multi-tenant de carta digital y comanda desde la mesa, vía QR. Ver la decisión completa de producto y arquitectura en [`docs/documentacion-tecnica.md`](docs/documentacion-tecnica.md) antes de tocar código — ese documento es la fuente de verdad de por qué el sistema está estructurado así, no una formalidad para archivar.

## Estado actual

Fase 0 (validación con restaurante piloto) — monolito modular Spring Boot con un único módulo Maven (`backend/pom.xml`). Hay lógica real en `tenant` (branding) e `identidad` (login JWT + aislamiento de tenant); `menu` y `pedidos` siguen siendo stubs. Los frontends todavía no tienen código.

## Estructura

```
backend/
  pom.xml            Módulo Maven único (NO multi-módulo: todo compila junto)
  src/main/java/com/mezzalia/
    common/            Config compartida (seguridad, manejo de errores)
    tenant/            Tenants, branding, EntidadConTenant (filtro de aislamiento)
    identidad/         Usuarios internos, JWT, login/refresh/logout
    menu/              Categorías, ítems, variantes (stub)
  src/main/resources/
    application.yml
    db/migration/      Migraciones Flyway, versionadas desde el commit inicial
frontend/
  app-cliente/       SPA pública: carta + carrito, sin login (React + TS + Vite) — aún sin código
  app-admin/         SPA panel restaurante + super-admin (React + TS + Vite) — aún sin código
docs/
  documentacion-tecnica.md   Requisitos, arquitectura, modelo de datos, API, roadmap
```

Nota: `pedidos` y `notificaciones` (mencionados en la documentación técnica §12) todavía no tienen ni carpeta ni código — se crean cuando alguien empiece a construir esa lógica, no antes (evitar carpetas vacías que sugieran una estructura multi-módulo que este proyecto no usa).

## Por qué está organizado así

- **Monolito modular, no microservicios**: con un equipo de 2 desarrolladores, la sobrecarga operativa de microservicios (service discovery, N pipelines, observabilidad distribuida) cuesta más tiempo del que ahorra. Ver §5.1 de la documentación.
- **Multi-tenancy por `tenant_id` en schema compartido**, no base de datos por cliente — ver §6. El riesgo #1 de este diseño es una query sin filtro de tenant filtrando datos entre restaurantes; cualquier repositorio nuevo debe pasar por la capa de enforcement de tenant, no aplicar el filtro a mano.
- **Dos frontends separados** (`app-cliente` / `app-admin`) porque tienen audiencias, auth y ciclos de release distintos.

## Cómo levantar el entorno de desarrollo (objetivo, aún no implementado)

```
docker-compose up          # MySQL + Redis + backend
cd frontend/app-cliente && npm install && npm run dev
cd frontend/app-admin && npm install && npm run dev
```

## Próximos pasos (Fase 0)

1. Definir el restaurante piloto real y confirmar alcance mínimo con ese cliente (no expandir alcance sin validación, ver §13 de la documentación).
2. Bootstrap del módulo `common` (config base, manejo de errores, filtro de tenant) y `tenant`/`menu` como los primeros módulos con lógica real.
3. CI mínimo en GitHub Actions: build + test en cada PR, desde el primer commit con código.
