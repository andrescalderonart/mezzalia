# Auditoría técnica Mezzalia — 2026-09-08

Revisor: Claude (rol de auditor/crítico de arquitectura configurado en este proyecto). Alcance: revisión estática del código en `main` (commit `defdd79`, incluye PR #20) — backend Spring Boot + frontend `app-admin`/`app-cliente`. **No incluye**: correr la suite de tests real (sin Maven/JDK en este entorno), `npm audit` de dependencias, ni un pentest dinámico contra una instancia corriendo. Todo lo que sigue es lo que el código dice que hace, no lo que se observó corriendo.

---

## 0. Veredicto general, sin rodeos

El código está mejor que la mayoría de MVPs de dos personas que voy a auditar. El aislamiento multi-tenant (el riesgo #1 que la propia `documentacion-tecnica.md` §6 identifica) está tratado en serio: filtro de Hibernate + defensa explícita por servicio + un test dedicado (`AislamientoTenantTest`) + comentarios que documentan bugs reales ya encontrados y cerrados. El cifrado de datos del comensal tiene rotación de clave de verdad, no un "TODO". Eso no es lo normal en un proyecto de este tamaño, y lo digo habiendo ido a buscar huecos activamente.

Dicho eso, encontré problemas reales — algunos de seguridad, uno de observabilidad que me parece más grave de lo que su severidad técnica sugiere — y la disciplina de tests tiene un punto ciego específico y peligroso: todo el control de acceso por rol depende de anotaciones (`@PreAuthorize`) que **ningún test verifica**. Ya vivieron una vez lo que pasa cuando algo crítico no tiene test (el frontend de selección de sucursal, completamente roto durante un PR entero sin que nadie lo notara). Esto es la misma clase de riesgo aplicada a autorización, que es peor que un botón roto.

---

## 1. Hallazgos de seguridad

### 1.1 — MEDIO-ALTO: el control de acceso por rol no tiene ni un test automatizado

`SecurityConfig` solo exige `authenticated()` para todo `/api/admin/**`; la restricción real por rol (`ADMIN_RESTAURANTE` vs `MESERO`) vive **exclusivamente** en anotaciones `@PreAuthorize` repartidas por cada método de cada controlador (`UsuarioInternoAdminController`, `MenuAdminController`, `MesaAdminController`, `SucursalAdminController`, `TenantAdminController`...). Revisé todas y hoy están bien puestas — pero no hay una sola prueba (`@WebMvcTest` + `@WithMockUser` con rol MESERO, por ejemplo) que falle si alguien borra o tipea mal una de esas anotaciones en un PR futuro. Con la cantidad de PRs por rama que ya llevan (20 y subiendo), esto es una cuestión de "cuándo", no de "si", un `@PreAuthorize` desaparece silenciosamente en un refactor y un MESERO termina pudiendo, por ejemplo, crear/desactivar sucursales o usuarios.

**Por qué me preocupa más que su severidad técnica actual**: el propio historial de este repo ya demostró exactamente este patrón — un PR (#19) se mergeó con el flujo de selección de sucursal completamente roto para su único usuario real, sin que nada lo detectara, porque cero tests cubrían ese camino. Un `@PreAuthorize` faltante es el mismo problema, pero en autorización en vez de en UX: mucho más caro de descubrir después del hecho.

**Recomendación**: una clase de test por controlador admin, mínimo un caso "MESERO recibe 403 en cada endpoint restringido a ADMIN_RESTAURANTE" y "SUPER_ADMIN recibe 403 fuera de `/api/admin/tenants/**`". No hace falta cobertura exhaustiva, hace falta que el CI reviente si la anotación desaparece.

### 1.2 — MEDIO: el catch-all de excepciones no deja rastro en logs

`GlobalExceptionHandler.handleGenerico(Exception ex)` devuelve correctamente un 500 genérico sin filtrar detalles internos al cliente (bien) — pero **no llama a ningún logger**. Cualquier excepción no prevista (NullPointerException, timeout de conexión a MySQL, lo que sea) se traduce en un JSON `"Error interno del servidor"` para el cliente y **desaparece sin dejar traza en el servidor**. La propia `documentacion-tecnica.md` §11 dice explícitamente "logging estructurado desde el primer commit... para poder rastrear un incidente de un cliente específico" — y de hecho el patrón de log JSON con `tenantId`/`requestId` ya existe en `application.yml`. Este handler es la única pieza que no lo usa, justo donde más importa (es el que atrapa lo que nadie previó).

**Recomendación**: una línea, `log.error("Error no manejado en {}: {}", request.getRequestURI(), ex.getMessage(), ex)` (con MDC de tenantId ya poblado por el logging pattern existente). Cambio de bajo riesgo, alto valor — arreglenlo antes de tener el primer incidente real en producción sin poder diagnosticarlo.

### 1.3 — MEDIO: `test.html` se empaqueta y se sirve públicamente en TODOS los builds, incluida producción

Vive en `backend/src/main/resources/static/test.html` — Spring Boot empaqueta automáticamente todo lo que hay en `static/` en el JAR, sin excepción por perfil (los recursos estáticos no tienen `@Profile`). `SecurityConfig` además lo permite explícitamente sin autenticación (`permitAll` sobre `/test.html`). El comentario en el propio código dice "no debe existir en producción", pero no hay ningún mecanismo — ni de build, ni de deploy, ni de código — que lo garantice; depende 100% de que nadie lo olvide.

No es una puerta trasera por sí sola (no otorga ningún permiso que la API real no otorgue ya), pero: (a) es superficie de reconocimiento gratis para cualquiera que la encuentre — muestra la forma exacta de la API de auth, incluye un botón para "probar 6 logins fallidos seguidos"; (b) si alguna vez el perfil `dev` se activa por error en producción (un `SPRING_PROFILES_ACTIVE` mal puesto en una variable de entorno, típico error humano de deploy), esta página tiene las credenciales demo (`admin.demo`/`Demo1234!`) precargadas en los inputs — convierte un error de configuración en una explotación de un clic.

**Recomendación**: excluirlo del build de producción (un profile de Maven con `maven-resources-plugin` que excluya `static/test.html`, o simplemente moverlo fuera de `src/main/resources` a una carpeta que solo se copie en local/CI de dev).

### 1.4 — MEDIO, ya documentado pero vale precisarlo: revocar el acceso de un MESERO a una sucursal no es instantáneo

`MesaService.sucursalActual()`/`PedidoService.sucursalActual()` confían directamente en el claim `sucursalId` del JWT vigente — no re-consultan la base en cada request. `AuthService.seleccionarSucursal()` sí valida contra la BD (sucursal activa + asignación vigente), pero esa validación solo se repite cuando el access token expira y el frontend reintenta la reselección automática (hasta cada 15 minutos, `expiracion-acceso-ms`). Entre que un ADMIN_RESTAURANTE desactiva una sucursal o le quita la asignación a un MESERO, y que el token de ese MESERO expira, ese MESERO sigue operando con normalidad sobre mesas/pedidos de esa sucursal. Es un trade-off razonable de un diseño stateless (y está parcialmente documentado en el código), pero no está documentado como una decisión de seguridad aceptada explícitamente — y una ventana de hasta 15 minutos es mucho si el escenario real es "le acabo de quitar acceso a alguien porque hubo un problema".

**Recomendación**: si esto importa para el negocio (ej. offboarding de un mesero problemático a mitad de turno), la solución barata es bajar `expiracion-acceso-ms` bastante (a 2-3 min) sin tocar arquitectura, o agregar una revalidación liviana (un `EXISTS` contra `AsignacionSucursal`+`Sucursal.activa`) en el punto de entrada de `MesaService`/`PedidoService` en vez de confiar ciegamente en el claim. Como mínimo, decídanlo conscientemente y anótenlo en `documentacion-tecnica.md` §10 en vez de dejarlo como comportamiento implícito.

### 1.5 — BAJO: sin Content-Security-Policy, con JWT (access + refresh) en `localStorage`

Ambos tokens viven en `localStorage` (`client.js`), no en cookies `httpOnly`. Es una decisión razonable dado que no hay CSRF que mitigar (CSRF está deshabilitado a propósito, correctamente, porque no hay auth por cookie) — pero significa que el único perímetro real contra robo de sesión es "que nunca haya XSS". Hoy no encontré `dangerouslySetInnerHTML` ni `innerHTML` en ningún lado de los dos frontends (React escapa por defecto), así que el riesgo actual es bajo. Pero no hay ningún `Content-Security-Policy` (ni meta tag ni header) que actúe como segunda capa si algún día una dependencia nueva o un descuido futuro introduce un vector de XSS. Con refresh tokens de 7 días de vida, el costo de un robo exitoso es alto.

**Recomendación**: agregar una CSP razonable (`default-src 'self'`, permitiendo lo que haga falta para fuentes/imágenes) como header desde el reverse proxy o `WebConfig`. Es barato y es la clase de control que uno agrega ANTES de necesitarlo, no después.

### 1.6 — Confirmado, no es un hueco (para que quede escrito y no se re-audite después)

- **CORS ausente**: es intencional y correcto — mismo origen en dev (proxy de Vite) y en producción (mismo dominio/reverse proxy), documentado explícitamente en `vite.config.js`. Si algún día separan `app-admin` en un dominio propio, ahí sí hace falta configurarlo — no antes.
- **Cierre de turnos vencidos (12h)**: verifiqué los 4 métodos de `TurnoService` (`marcarEntrada`, `marcarSalida`, `estadoActual`, `horasPorDiaDe`) — los cuatro llaman a `cerrarSiVencido` antes de leer. La preocupación que planteé en la sesión anterior (un reporte futuro que lea `Turno` directo sin pasar por ahí) sigue siendo válida como regla a seguir, pero hoy no hay ningún camino que la viole.
- **Nombres de archivo de imágenes subidas**: `AlmacenamientoImagenService` usa `UUID.randomUUID()`, no el nombre original — no hay path traversal ni enumeración práctica.
- **Secretos**: `JWT_SECRET` y `COMENSAL_ENCRYPTION_KEY` no tienen default, la app falla al arrancar si faltan — bien hecho, nada de secretos cómodos en el repo.
- **`HttpMessageNotReadableException`**: la nota de memoria del proyecto decía que esto seguía sin manejar — **ya está resuelto** en el código actual (`GlobalExceptionHandler` lo maneja explícitamente con un 400 claro). Corrijo esto en la memoria del proyecto.

---

## 2. Funcionalidad — estado, deuda y mejoras concretas

### 2.1 Deuda funcional real (no solo seguridad)

- **El gap de sucursales desactivadas sigue abierto** (ya reportado el 2026-09-08 en la sesión anterior): una sucursal desactivada con un mesero todavía asignado desaparece de toda la UI (`GET /api/admin/sucursales` filtra `activa=true`), sin forma de auditarla ni desasignar desde la interfaz. El backend soporta verla (`obtenerSucursalDelTenantActualSinImportarEstado`) pero nada en el frontend lo expone. Sigue pendiente de que Sebas decida prioridad.
- **`frontend/app-admin` no tiene ningún test ni test runner** (`package.json` sin Vitest/Jest). Es la causa raíz documentada del bug de PR #19. Mientras esto siga así, cualquier regresión de UI (no solo de sucursales) puede volver a pasar desapercibida hasta que un usuario real la encuentre.
- **RF-13/14 (datos del comensal) sigue con el mismo hueco legal que `documentacion-tecnica.md` §15.2 marca como pendiente desde agosto**: el mecanismo técnico de cifrado y borrado ya está — lo que falta es el texto real de aviso de privacidad, el flujo de consentimiento revisado legalmente, y una política de retención. Esto no es deuda técnica, es exposición legal activa (Habeas Data, Ley 1581) desde el primer comensal real que capturaron.
- **Backend sin Maven/JDK verificable en este entorno de trabajo remoto** (ya documentado): toda la rama de turno/sucursal (4 commits) sigue sin correr contra Maven real ni MySQL real. Antes de mergear a producción, correr la suite completa y probar el flujo end-to-end a mano.
- **Cobertura de tests con puntos ciegos específicos**: nada cubre `JwtService`, `AuthService`, `LoginAttemptService`, ni `TurnoService` (el feature más nuevo). El resto del dominio (pedido, mesa, tenant, sucursal, aislamiento de tenant) tiene cobertura sólida y hasta tests de concurrencia — la asimetría llama la atención.

### 2.2 Mejoras que no son "features nuevas" sino terminar lo empezado

- Loggear el catch-all (§1.2).
- Excluir `test.html` de producción (§1.3).
- Tests de autorización por rol (§1.1).
- Cerrar el gap de sucursales inactivas en la UI (decisión de Sebas, pero técnicamente barato: reusar `obtenerSucursalDelTenantActualSinImportarEstado` para una vista de "sucursales inactivas con asignaciones" en `SucursalesPage.jsx`).
- Health check (`/actuator/health`) — mencionado en el roadmap propio (§11) y no implementado todavía; es prácticamente gratis con `spring-boot-starter-actuator` y cualquier hosting lo va a pedir para reiniciar instancias caídas.
- Backups de MySQL automatizados y probados — sigue sin mención en el repo (no es código, es infraestructura, pero es exactamente el tipo de cosa que "nadie prioriza hasta que ya es tarde", como dice su propio documento).

---

## 3. Diferenciador — qué construiría primero, y qué NO construiría todavía

Su propio documento técnico ya es explícito sobre el diferenciador elegido ("comanda digital: reducir tiempos de toma de pedido y errores de mesero") y sobre qué queda fuera de alcance a propósito (pagos, reservas, KDS físico, fidelización, POS, facturación electrónica — todo Fase 4). Estoy de acuerdo con no tocar esa lista todavía: un equipo de 2 personas que se distrae con pagos online antes de tener retención de clientes reales está apostando mal. Pero hay tres cosas que se pueden construir CASI gratis con lo que ya existe, sin salirse del alcance que su propio roadmap define, y que sí mueven la aguja del diferenciador real:

**3.1 — Vista de cocina en vivo ("KDS-lite"), sin hardware dedicado.**
No es el KDS físico de Fase 4 (pantallas dedicadas, integración con impresoras de comanda). Es una URL más: una pantalla de solo-lectura, letra grande, que filtra `PedidoAdminController` a estados `RECIBIDO`/`EN_PREPARACION`, pensada para dejar abierta en una tablet o monitor viejo en la cocina. Reutiliza el mismo polling que ya existe (`PedidosPage.jsx`), el mismo endpoint, el mismo modelo de datos — es prácticamente una página nueva de frontend y cero backend nuevo. El argumento de venta ("reducir errores de mesero") se vuelve literal: el mesero deja de ser el mensajero entre la mesa y la cocina.

**3.2 — Analítica operativa a partir de datos que YA están registrando y hoy nadie mira.**
`PedidoEstadoHistorial` ya guarda cada cambio de estado con timestamp. Eso ya alcanza para calcular, sin capturar un solo dato nuevo: tiempo promedio de preparación por plato, hora pico real por sucursal, ítems más pedidos. Menuly y competidores "de carta bonita" (mencionados en su propio doc) no tienen esto — es información operativa real, no cosmética, y ya la están pagando en almacenamiento sin usarla. Es una pantalla de reportes + un par de queries agregadas, no un feature de meses.

**3.3 — Aviso de "pedido listo" al comensal por WhatsApp.**
Ya capturan el teléfono del comensal (con consentimiento, cifrado). Un enlace `wa.me` generado del lado del servidor cuando el pedido pasa a LISTO/ENTREGADO (sin necesitar la API de WhatsApp Business todavía, solo un link que el mesero o el sistema puede disparar) es una mejora de experiencia perceptible con cero infraestructura nueva. Es más barato que "reservas" o "fidelización" y ataca directamente la percepción de "esto se siente más moderno que llamar al mesero".

De las tres, si tengo que elegir una: **3.1 (vista de cocina)**, porque es la que más directamente cumple la promesa de venta que ya eligieron, la más barata de construir, y la que un cliente piloto puede validar en un sábado real sin pedirles nada nuevo de infraestructura. Constrúyanla ustedes, no la deleguen a "algún día" — es exactamente el tipo de cosa "pequeña pero real" que la Fase 0/1 de su propio roadmap pide priorizar.

No recomendaría tocar pagos, reservas, ni facturación electrónica todavía — ni siquiera "una versión simple". Cada uno de esos trae una superficie de cumplimiento/regulación (facturación electrónica en Colombia, DIAN) o de seguridad (pagos, PCI) que un equipo de 2 personas sin haber cerrado RF-13/14 (que sigue abierto, ver §2.1) no debería abrir todavía.

---

## 4. Plan paso a paso

Ordenado por lo que bloquea o expone más, no por lo más entretenido de construir.

**Paso 1 — Cerrar lo que ya está a medio camino (esta semana).**
Pushear y mergear la rama `fix/frontend-seleccion-sucursal-mesero` (ya tiene los 4 commits listos). Antes de mergear: correr la suite de backend completa con Maven/JDK real (no se pudo verificar desde este entorno) y probar a mano el flujo completo descrito en la sesión anterior (crear sucursal → asignar mesero → loguearse → marcar entrada/salida → reporte de horas → desactivar sucursal y confirmar el gap conocido).

**Paso 2 — Cerrar los hallazgos de seguridad de este informe, en este orden de costo/beneficio:**
1. Loggear el catch-all de excepciones (§1.2) — 1 línea, altísimo valor.
2. Excluir `test.html` de producción (§1.3) — bajo esfuerzo.
3. Decidir y documentar explícitamente el trade-off de revocación de sucursal (§1.4) — o bajar el TTL del access token, o aceptarlo por escrito.
4. Agregar una CSP básica (§1.5).
5. Escribir tests de autorización por rol para los controladores admin (§1.1) — el de mayor esfuerzo de los cinco, pero el que previene la clase de bug más cara.

**Paso 3 — Cerrar el hueco legal de RF-13/14 (no técnico, pero bloqueante).**
Esto no es negociable según su propio documento (§15.2): texto de aviso de privacidad revisado legalmente, consentimiento explícito (no checkbox premarcado — ya verificado que no lo está, bien), y una política de retención de datos del comensal. Cada día que pasa con comensales reales capturados sin esto resuelto es exposición real bajo Ley 1581, no deuda técnica.

**Paso 4 — Cerrar el gap de sucursales inactivas en la UI**, una vez que Sebas decida si lo prioriza (§2.1) — es barato técnicamente, el trabajo real es la decisión de producto.

**Paso 5 — Observabilidad mínima antes de tener el primer cliente real dependiendo del sistema (§11 de su propio doc, todavía no aplicado):**
`/actuator/health`, backups de MySQL automatizados y probados (restaurar uno de prueba, no asumir que el backup sirve). Ninguno de los dos es opcional una vez que haya un restaurante piloto real operando un sábado a la noche.

**Paso 6 — Frontend: agregar un test runner** (Vitest, ya que usan Vite) y al menos cubrir el flujo de selección de sucursal + el gate de rutas protegidas — el camino que ya falló una vez.

**Paso 7 — Recién ahí, un diferenciador (§3.1: vista de cocina)**, como pieza de validación con el cliente piloto que su propio roadmap (Fase 0) pide confirmar: que el mesero realmente usa el panel bajo presión de servicio. Una vista de cocina es la extensión más directa de esa misma pregunta ("¿esto realmente ahorra tiempo en un sábado real?").

**Paso 8 — Solo después de validar con el piloto, seguir con Fase 2 de su propio roadmap** (self-service, aplicación real de límites por plan, precios en COP definidos con costos reales — sigue pendiente según §15.2).

No mencioné pagos/reservas/KDS físico/facturación electrónica en este plan a propósito: siguen correctamente fuera de alcance según su propio documento, y nada de lo que encontré hoy cambia esa conclusión.
