# Calidad y seguridad de Mezzalia

Estado al **2026-11-27**. Este documento dice qué estándares seguimos, cómo se mide cada cosa,
dónde estamos hoy y qué falta. Se actualiza cada vez que se cierra una brecha.

| Estándar | Para qué lo usamos | Nivel objetivo |
|---|---|---|
| **ISO/IEC 25010:2023** (modelo de calidad del producto) | Definir *qué* es calidad para Mezzalia y medirla con números | Metas propias por característica (tabla 1) |
| **OWASP ASVS 5.0** (Application Security Verification Standard) | Lista verificable de requisitos de seguridad para la API y los dos frontends | **Nivel 1 completo** antes del primer restaurante pagando; **Nivel 2** en las partes de login y datos personales |
| **OWASP Top 10 (web)** | Vocabulario común de riesgos; cada requisito ASVS se mapea a uno | — |
| **OWASP Mobile Top 10 (2024)** | Ver la nota de abajo | Solo los puntos que aplican a una web móvil |

> **Sobre el OWASP Mobile Top 10.** Ese estándar es para **apps nativas** (Android/iOS): binarios
> que se pueden descompilar, almacenamiento del teléfono, permisos, etc. Mezzalia hoy es una **web
> que se abre en el celular** (la carta por QR) y un panel web, así que el estándar que manda es
> **ASVS**. Del Mobile Top 10 aplican cuatro puntos y se cubren con requisitos de ASVS:
> M1 (uso indebido de credenciales), M5 (comunicación insegura → HTTPS), M8 (mala configuración de
> seguridad) y M9 (almacenamiento inseguro → tokens en `localStorage`). Si algún día se publica una
> app nativa en las tiendas, entra completo el **OWASP MASVS** y se revisa de nuevo.

---

## 1. ISO/IEC 25010:2023 — características, métricas y estado

Leyenda: ✅ cumple la meta · 🟡 parcial / medido pero sin meta cumplida · 🔴 pendiente

| Característica | Qué significa para Mezzalia | Métrica | Meta | Hoy | Estado |
|---|---|---|---|---|---|
| **Adecuación funcional** | El pedido llega a cocina tal como lo armó el comensal ("sin cebolla") | Tests de punta a punta de los flujos principales (carta → pedido de la mesa → confirmar → cocina → reporte) | Todos los flujos del MVP con test | Cubiertos en `IngredientesEndToEndTest` y afines | ✅ |
| **Eficiencia de desempeño** | La carta abre rápido con datos móviles | Peso JS inicial de la carta (gzip) | ≤ 100 kB | 71 kB (+ 5 kB CSS). El visor 3D (300 kB) se carga aparte | ✅ |
| | | Tiempo de respuesta de `GET /carta` (p95) | ≤ 300 ms en producción | 2 ms en local con H2: **no representa producción** | 🟡 falta prueba de carga en el servidor real |
| **Compatibilidad** | Funciona en los celulares de los comensales | Navegadores probados | Safari iOS y Chrome Android recientes | Probado en iPhone (Safari) y Chrome escritorio en modo móvil | 🟡 falta Android real |
| **Capacidad de interacción** (usabilidad + accesibilidad) | Pedir sin ayuda del mesero; legible para todos | WCAG 2.2 AA en la carta | Sin errores graves de axe | Sin medir. Hay `aria-label` en botones clave | 🔴 |
| **Fiabilidad** | Ningún pedido se pierde ni se duplica | Idempotencia del pedido, límite por mesa, tests de estados (36 combinaciones) | Cubierto con tests | Cubierto | ✅ |
| | | Copias de seguridad de la BD y de `/uploads` | Diarias, restauración probada | No hay producción todavía | 🔴 |
| **Seguridad** | Ver la sección 2 (ASVS) | Requisitos ASVS N1 cumplidos | 100 % | Ver la tabla 2 | 🟡 |
| **Mantenibilidad** | Cambiar algo sin romper otra cosa | Cobertura de líneas backend (JaCoCo) | ≥ 78 % (umbral en CI) | **81,1 % líneas / 72,0 % ramas** | ✅ |
| | | Tests del frontend | Panel y carta con tests | Panel: 64 tests. Carta: **0** | 🟡 |
| | | Linter en los frontends | ESLint en CI | No hay | 🔴 |
| | | Dependencias con soporte | Framework con parches de seguridad | Spring Boot **3.5.16** (subido desde 3.3.5, que ya no recibía parches) | 🟡 planear 4.x (3.5 dejó de tener soporte gratuito en 2026-06) |
| **Flexibilidad** (portabilidad) | Desplegar en un VPS nuevo sin sorpresas | Despliegue reproducible | Dockerfile + compose de producción | `backend/Dockerfile` + `deploy/` (compose, Caddy, respaldos); migraciones probadas contra MySQL 8.4 en CI | 🟡 sin ejecutar aún en un servidor |
| **Seguridad de uso** (*safety*, nueva en 2023) | Un error no daña al comensal | Alérgenos marcados en el plato | Avisar alérgenos y trazas | Solo "quitar ingrediente"; no hay alérgenos | 🔴 riesgo real con comida |

Cómo medir de nuevo:
- Cobertura: `mvn verify` → `backend/target/site/jacoco/index.html` (el CI la publica como artefacto `cobertura-backend`).
- Peso de la carta: `npm run build` en `frontend/app-cliente` (Vite imprime el tamaño gzip).
- Dependencias: `npm audit --omit=dev` en cada frontend (el CI falla si hay vulnerabilidades altas o críticas).

---

## 2. OWASP ASVS 5.0 — Nivel 1 (y lo relevante de Nivel 2)

| Capítulo ASVS | Requisito (resumen) | Estado | Evidencia / qué falta |
|---|---|---|---|
| **V1 Codificación y sanitización** | Sin inyección SQL; salida escapada | ✅ | JPA con parámetros; React escapa todo y no hay `dangerouslySetInnerHTML` |
| **V2 Validación y lógica de negocio** | Validar entradas del lado servidor; límites anti-abuso | ✅ | Bean Validation en DTO (400 con mensaje desde 2026-11-27); límite de pedidos por mesa (20/5 min) y de llamados al mesero; estados del pedido con tabla de transiciones |
| **V3 Seguridad del frontend web** | CSP, `nosniff`, anti-clickjacking, Referrer-Policy | ✅ en la API · 🟡 en producción | CSP, `X-Frame-Options: DENY`, `nosniff`, **`Referrer-Policy: no-referrer`** y **`Permissions-Policy`** (nuevos; test `CabecerasSeguridadTest`). Las SPA tienen `<meta name="referrer">`. **Falta:** que el servidor que sirva las SPA en producción mande las mismas cabeceras |
| **V4 API y servicios web** | Métodos HTTP correctos, sin datos de más en respuestas | ✅ | DTO explícitos; el pedido de la mesa no expone datos del comensal (test) |
| **V5 Manejo de archivos** | Validar tipo real, tamaño y nombre | ✅ | Tamaño máx. 5 MB; nombre aleatorio (UUID); **firma real JPEG/PNG/WEBP** verificada (nuevo; un HTML disfrazado de PNG da 400, con test) |
| **V6 Autenticación** | Contraseñas ≥ 8, permitir ≥ 64, sin reglas de composición, rechazar comunes; anti fuerza bruta | ✅ N1 · 🔴 MFA (N2) | **`PoliticaPassword`** (nuevo): mínimo 8, máximo 72 bytes (límite real de BCrypt), rechaza comunes y las que contienen el usuario. Bloqueo de 15 min tras 5 fallos; respuesta igual exista o no el usuario (sin enumeración, con hash ficticio contra *timing*). **Falta:** lista de comunes más grande; bloqueo también por IP; MFA para el administrador (N2) |
| **V7 Gestión de sesión** | Tokens con expiración, rotación, revocación | 🟡 | Access JWT de 15 min; refresh opaco, hasheado en BD, rotado en cada uso; si se reusa uno viejo se revocan todas las sesiones. **Falta:** los tokens viven en `localStorage` (riesgo si hubiera XSS). Plan: refresh en cookie `HttpOnly; Secure; SameSite=Strict` |
| **V8 Autorización** | Mínimo privilegio, aislamiento entre restaurantes | ✅ | Roles SUPER_ADMIN / ADMIN_RESTAURANTE / MESERO; filtro de Hibernate por `tenant_id`; tests de aislamiento de tenant y de sucursal (un mesero de otra sucursal recibe 404) |
| **V9 Tokens autocontenidos** | Firma fuerte, secreto fuera del código | ✅ | `JWT_SECRET` obligatorio por variable de entorno (sin valor por defecto) |
| **V11 Criptografía** | Algoritmos actuales | ✅ | BCrypt para contraseñas; AES-GCM para el teléfono del comensal; SHA-256 para refresh tokens |
| **V12 Comunicaciones seguras** | TLS en todo | 🔴 | HSTS listo (1 año, se envía solo por HTTPS; test). **Falta:** TLS en el servidor de producción; quitar `useSSL=false` de la conexión a MySQL fuera de local |
| **V13 Configuración** | Sin secretos en el repo, sin endpoints de depuración | ✅ | `.env` ignorado; solo `/actuator/health` expuesto; `test.html` solo con perfil dev |
| **V14 Protección de datos** | Mínimos datos personales, cifrados, con consentimiento | 🟡 | Teléfono cifrado y opcional con casilla de consentimiento; apodo opcional. **Falta:** política de retención y borrado (Ley 1581 de 2012, *habeas data*) |
| **V15 Código y dependencias seguras** | Sin componentes vulnerables; actualizaciones | ✅ | Spring Boot 3.3.5 → **3.5.16**; react-router 6 → **7.18** (2 CVE moderados cerrados); `npm audit` en CI; **Dependabot** semanal. Quedan vulnerabilidades solo en herramientas de desarrollo (vitest 2 / vite 5), que no llegan al usuario |
| **V16 Registro de seguridad** | Registrar eventos de seguridad sin datos sensibles y sin inyección de logs | 🟡 | **`EventosSeguridad`** (nuevo, logger `SEGURIDAD`): login correcto y fallido, cuenta bloqueada, cuenta inactiva, refresh inválido, acceso denegado (403), archivo rechazado, cambio de contraseña. Sin contraseñas ni tokens; quita saltos de línea (test). **Falta:** guardar esos logs fuera del servidor y alertar |

---

## 3. Brechas priorizadas (siguiente trabajo)

**Antes del primer restaurante real (bloqueantes):**
1. **HTTPS en producción** + cabeceras de seguridad en el servidor que sirva las SPA (V12, V3). *Preparado el 2026-11-27*: `deploy/` (Caddy con HTTPS automático y las mismas cabeceras para las SPA) y [docs/despliegue.md](../despliegue.md); falta ejecutarlo en un servidor real.
2. **Refresh token en cookie `HttpOnly`** en vez de `localStorage` (V7).
3. **Copias de seguridad** de MySQL y de las fotos, con una restauración probada (ISO: fiabilidad). *Preparado*: `deploy/respaldo.sh` diario con 14 días de retención y pasos de restauración; falta programarlo y probar una restauración en el servidor.
4. **Política de datos personales**: aviso de privacidad, retención y borrado del teléfono del comensal (V14 / Ley 1581).

**Pronto:**
5. **Alérgenos y trazas** en el plato (ISO: seguridad de uso). Es un riesgo real en comida y además ayuda a vender.
6. **Tests de la carta** (app-cliente tiene 0) y **ESLint** en ambos frontends, en CI.
7. **Accesibilidad**: revisión WCAG 2.2 AA de la carta con axe.
8. Bloqueo de login **también por IP**, y lista de contraseñas comunes más grande.
9. Logs de `SEGURIDAD` guardados fuera del servidor, con alerta ante muchos `login_fallido` o `acceso_denegado`.

**Planeado:**
10. Migración a **Spring Boot 4.x** (3.5 ya no tiene soporte gratuito).
11. Subir vitest / vite (solo herramientas de desarrollo).
12. MFA para el administrador del restaurante (ASVS N2).
13. Prueba de carga de `GET /carta` y `POST /pedidos` en el servidor real (meta: p95 ≤ 300 ms).

---

## 4. Controles automáticos (CI)

Cada PR a `main` corre:
- Backend: `mvn verify`, que incluye **todos los tests** y el **umbral de cobertura** de JaCoCo (78 % líneas, 68 % ramas). El reporte se publica como artefacto.
- app-cliente y app-admin: `npm ci`, **`npm audit --omit=dev --audit-level=high`**, tests (panel) y build.
- **Dependabot** (`.github/dependabot.yml`): PR semanales de actualizaciones de Maven, npm y GitHub Actions; los parches de seguridad llegan apenas salen.
