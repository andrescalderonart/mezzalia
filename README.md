# Carta Digital SaaS — Carta + Comanda para Restaurantes

Plataforma SaaS multi-tenant de carta digital y comanda desde la mesa, vía QR. Ver la decisión completa de producto y arquitectura en [`docs/documentacion-tecnica.md`](docs/documentacion-tecnica.md) antes de tocar código — ese documento es la fuente de verdad de por qué el sistema está estructurado así, no una formalidad para archivar.

## Estado actual (actualizado 2026-09-10)

Ya no es Fase 0. Hay lógica real y con tests en `tenant`, `identidad`, `menu`, `mesa`, `pedido`, `sucursal` y `turno`. `app-admin` (panel del restaurante) está construido: login, gestión de carta, mesas, pedidos en vivo, usuarios internos, sucursales, asignación mesero↔sucursal, y control de turnos/horas trabajadas. `app-cliente` (la SPA pública del comensal) está implementada: carta con navegación por categorías (scroll-spy vía `IntersectionObserver`), carrito con selector de cantidad, checkout con captura de datos del comensal gateada por consentimiento explícito (RF-13 — sin consentimiento, `comensal` va como `null`, nunca como objeto vacío), idempotency key en la creación del pedido y pantalla de confirmación.

## Estructura

```
backend/
  pom.xml            Módulo Maven único (NO multi-módulo: todo compila junto)
  src/main/java/com/mezzalia/
    common/            Config compartida (seguridad, manejo de errores, panel de pruebas de dev)
    tenant/            Tenants, branding, alta de admin inicial, EntidadConTenant (filtro de aislamiento)
    identidad/         Usuarios internos, JWT, login/refresh/logout, asignación mesero↔sucursal
    menu/              Categorías, ítems, variantes
    mesa/              Mesas, códigos QR, unicidad de código
    sucursal/          Sucursales, selección obligatoria para MESERO (RF-10 PR4)
    pedido/            Comanda, estados, datos del comensal (Habeas Data)
    turno/             Marcar entrada/salida, horas trabajadas por día
  src/main/resources/
    application.yml       Perfil por defecto (MySQL + Flyway)
    application-h2.yml    Perfil `h2`: levanta todo sin Docker/MySQL, para dev local (ver más abajo)
    db/migration/          Migraciones Flyway, versionadas desde el commit inicial
frontend/
  app-cliente/       SPA pública: carta + carrito + checkout, sin login (React + Vite) — implementada
  app-admin/         SPA panel restaurante + super-admin (React + Vite) — implementada
docs/
  documentacion-tecnica.md          Requisitos, arquitectura, modelo de datos, API, roadmap
  auditoria-tecnica-2026-09-08.md   Auditoría de seguridad/funcionalidad, con seguimiento de qué se resolvió
```

`notificaciones` (mencionado en la documentación técnica) todavía no tiene ni carpeta ni código — se crea cuando alguien empiece a construir esa lógica.

## Por qué está organizado así

- **Monolito modular, no microservicios**: con un equipo de 2 desarrolladores, la sobrecarga operativa de microservicios (service discovery, N pipelines, observabilidad distribuida) cuesta más tiempo del que ahorra. Ver §5.1 de la documentación.
- **Multi-tenancy por `tenant_id` en schema compartido**, no base de datos por cliente — ver §6. El riesgo #1 de este diseño es una query sin filtro de tenant filtrando datos entre restaurantes; cualquier repositorio nuevo debe pasar por la capa de enforcement de tenant, no aplicar el filtro a mano.
- **Dos frontends separados** (`app-cliente` / `app-admin`) porque tienen audiencias, auth y ciclos de release distintos.

## Cómo levantar el backend en desarrollo local (Windows, sin Docker)

Requiere Java 21 y Maven. Si tenés más de un JDK instalado, confirmá cuál usa Maven con `mvn -version` — si no coincide con el que resuelve `java` en tu `PATH`, invocá el jar con la ruta completa al JDK 21 en vez de depender del `PATH` (pasó en este equipo: `spring-boot:run` y `java -jar` a secas resolvían un JDK 17 y tiraban `ClassNotFoundException`/`UnsupportedClassVersionError` en vez de un error claro).

```powershell
cd backend

# JWT_SECRET y COMENSAL_ENCRYPTION_KEY son obligatorias -- la app no arranca sin ellas
# (a propósito, ver .env.example). Un archivo .env en disco NO se carga solo, no hay
# dotenv configurado -- tienen que ser variables de entorno reales de esta sesión de shell.
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)

$bytes2 = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes2)
$env:COMENSAL_ENCRYPTION_KEY = [Convert]::ToBase64String($bytes2)

$env:DEV_SUPERADMIN_USERNAME = "superadmin"
$env:DEV_SUPERADMIN_PASSWORD = "CambiameLocal123!"

mvn clean package -DskipTests
java -jar target\mezzalia-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev,h2
```

El perfil `dev,h2` levanta la app completa sin Docker ni MySQL, con una base H2 persistida en `backend/data/` (ver `application-h2.yml` para el detalle y las limitaciones conocidas). **No valida las migraciones de Flyway reales** (deshabilitado en este perfil) — para eso hace falta MySQL de verdad:

```powershell
docker compose up -d mysql   # el servicio `backend` del compose está comentado a propósito, no lo levanta a él
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

(El contenedor `redis` del `docker-compose.yml` existe para cuando haga falta cache/sesiones de mesa, pero hoy ningún código lo usa — no hace falta levantarlo para nada.)

Si `backend/data/` viene de antes de un pull que agregó una columna `NOT NULL` sin default (pasa cuando se mergea una migración nueva), Hibernate puede tirar `CommandAcceptanceException` al migrar filas viejas. Solución: parar la app, borrar `backend/data/`, volver a arrancar — arranca de cero, sin ese conflicto.

Usuarios demo del perfil `dev`: `admin.demo` / `Demo1234!` (ADMIN_RESTAURANTE) y `mesero.demo` / `Demo1234!` (MESERO).

## Cómo levantar el frontend (app-admin y app-cliente)

```powershell
cd frontend/app-admin
npm install
npm run dev
```

Abre `http://localhost:5173`. Vite hace de proxy de `/api` y `/uploads` hacia `http://localhost:8080` (ver `vite.config.js`).

Para levantar `app-cliente`:

```powershell
cd frontend/app-cliente
npm install
npm run dev
```

Vite le asigna otro puerto si `5173` ya está ocupado por `app-admin` (en este equipo cae en `5174`). La carta de una mesa se accede vía `/carta/{token}` (ej. `http://localhost:5174/carta/{token}`), token que genera `app-admin` al crear una sesión de mesa (RF-08, QR con validez de 4 horas); un token inválido o vencido muestra una pantalla de error ("No pudimos abrir la carta") en vez de una página en blanco. Para probar desde un teléfono real en la misma red Wi-Fi, `app-admin` necesita apuntar a la IP LAN de `app-cliente` en vez de `localhost` (que en el celular resuelve al celular mismo, no a tu máquina) — configurar `VITE_APP_CLIENTE_URL` en `frontend/app-admin/.env.local`.

## Próximos pasos

Ver el backlog activo y las decisiones abiertas en `docs/documentacion-tecnica.md §15.2` y en la memoria de proyecto (`project_mezzalia_repo.md`, backlog al 2026-09-10).
