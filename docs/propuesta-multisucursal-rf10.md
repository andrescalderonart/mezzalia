# Propuesta técnica: RF-10 (Multi-sucursal)

**Para:** Andrés Calderón / Juan Sebastián Moreno
**Origen:** auditoría de arquitectura sobre `main` (2026-09-04), a partir de un gap flagueado en el handoff de la sesión anterior (2026-09-02).
**Estado:** propuesta para discutir — nada de esto está implementado ni decidido.

## 1. El problema, en una frase

El modelo de cobro cerrado en `documentacion-tecnica.md` §1.2 es "suscripción mensual fija **por sucursal**", pero el sistema hoy no tiene ninguna manera de representar que un tenant tiene más de un local. Cada tenant es, de hecho, una sola sucursal implícita. Si hoy llega un prospecto con 3 locales, no hay dónde meterlo.

## 2. Verificación del estado actual (no asumido, revisado en código)

- No existe `sucursal_id` en ningún lado: ni `Mesa`, ni `Pedido`, ni `ItemMenu`, ni `CategoriaMenu`. Los comentarios en `Mesa.java` y `Pedido.java` documentan la omisión como decisión deliberada, no como olvido.
- RF-03 ("el sistema genera un pedido asociado a `tenant + sucursal + mesa`") y RF-10 (§3) están sin implementar.
- El propio diagrama ER de §7 — el diseño objetivo que dejaron dibujado para cuando esto se construya — **tampoco** le pone `sucursal_id` a `USUARIO_INTERNO`. Es decir: tal como está pensado en el papel hoy, ni siquiera el diseño futuro resuelve si un mesero queda atado a un local o ve todos los de la cadena.
- Consecuencia de ese vacío: cuando esto se construya como está dibujado, un `MESERO` de una cadena de 3 locales va a seguir viendo en `/api/admin/pedidos` los pedidos en vivo de los 3 locales mezclados — la misma clase de fuga que el PR #7 cerró entre tenants distintos, reaparecida un nivel más abajo, entre locales del mismo tenant.

## 3. Decisión de negocio que hay que tomar ANTES de tocar código

**¿Un `MESERO` queda atado a una sola sucursal, o puede operar en cualquiera del tenant?**

Esto no es un detalle técnico, es una decisión de producto con consecuencias técnicas grandes (implica agregar `sucursal_id` a `UsuarioInterno`, decidir el flujo de login/asignación de un empleado a un local, y decidir qué pasa con un empleado que rota entre locales).

Recomendación: atarlo. El argumento de venta del producto (§1.1) es "reducir tiempos de toma de pedido y errores de mesero" — un mesero filtrando pedidos de un local donde no está físicamente presente va en contra de ese argumento, no a favor. Un `ADMIN_RESTAURANTE` sí necesita ver todos los locales de su cadena (para eso paga por varias sucursales bajo un mismo tenant), así que el límite tiene que ser configurable por rol, no una regla única.

## 4. Enfoque técnico propuesto para el aislamiento

**No extender el `@Filter` de Hibernate de tenant para que también filtre por sucursal.**

El límite de tenant es absoluto: nadie nunca necesita cruzarlo, por eso un filtro de sesión incondicional (`tenantFilter`) tiene sentido ahí. El límite de sucursal es distinto: un `ADMIN_RESTAURANTE` necesita cruzarlo a propósito. Un filtro global que hay que desactivar según el rol es exactamente el patrón que ya generó un bug real en este proyecto (PR #7, el leak de `SUPER_ADMIN` sobre `listarCategorias`/`listarMesas`/`listarPedidos`, porque el filtro de tenant no aplica para ese rol y nada lo compensaba a nivel de código).

Propuesta: mismo patrón que ya usaron como parche ahí — un método explícito tipo `sucursalActual()` en cada servicio, que devuelve `null` cuando el rol autenticado puede ver todas las sucursales del tenant, o un id concreto cuando debe quedar acotado a una. Cada query nueva se ve obligada a decidir explícitamente qué hacer con ese valor, en vez de heredar un comportamiento implícito de un filtro que no aplica igual para todos.

## 5. Plan de migración de datos (sin big bang)

Cada tenant existente hoy es, de hecho, una sucursal. La migración tiene que preservar eso en cada paso intermedio, para que el sistema siga siendo deployable y funcional entre un PR y el siguiente (coherente con la propia regla del equipo en `CONTRIBUTING.md` de PRs chicos y frecuentes).

**PR 1 — Entidad `Sucursal` + backfill silencioso.**
Crear la tabla `Sucursal` (`id`, `tenant_id` FK, `nombre`, `direccion`, como ya está en el diagrama de §7). En la misma migración, generar automáticamente una sucursal default por cada `Tenant` existente. Sin cambios de comportamiento visibles todavía.

**PR 2 — `sucursal_id` en `Mesa`, `CategoriaMenu`, `ItemMenu`, `Pedido`.**
Columna nullable primero, backfill apuntando a la sucursal default de cada fila, después `NOT NULL`. El sistema sigue comportándose exactamente igual que hoy (cada tenant sigue teniendo una sola sucursal), solo que ahora explícita en vez de implícita.

**PR 3 — CRUD de sucursales + límite de plan.**
Recién acá se expone la posibilidad de que un tenant cree una segunda sucursal. `TenantService`/`SucursalService` necesita chequear el límite de plan (Starter=1, Pro=5, Enterprise=ilimitado, tabla de §1.2) **antes** de insertar — mismo patrón de dos capas que ya usan para `Tenant.nombre` (chequeo de aplicación + constraint de BD como backstop). Si esto no se aplica en el momento de crear, la tabla de precios de §1.2 deja de tener efecto real: un tenant Starter podría crear sucursales ilimitadas gratis.

**PR 4 — `sucursal_id` en `UsuarioInterno` + scoping real + frontend.**
Recién acá se resuelve la decisión de la sección 3: asignar cada `MESERO` (y opcionalmente cada `ADMIN_RESTAURANTE`) a una o más sucursales, aplicar `sucursalActual()` en `PedidoService`/`MesaService`/`MenuService`, y dar la UI para elegir/cambiar de sucursal en el panel.

## 6. Una trampa a evitar explícitamente

No modelar esto como "cada sucursal es casi un tenant aparte, agrupado por una cadena". Eso invierte el diseño actual y rompe lo que RF-10 pide textualmente: "comparte el tenant y la facturación". El super-admin sigue suspendiendo y facturando por tenant (RF-11), no por sucursal individual — sucursal es una subdivisión operativa *adentro* del tenant, no una entidad de facturación propia.

## 7. Testing que esto necesita

Un test de aislamiento explícito, mismo espíritu que el que ya prueba tenant-a-tenant (§6): *"un MESERO de la sucursal A no puede leer ni escribir pedidos/mesas de la sucursal B del mismo tenant"*, corrido en cada build — no verificado a mano una vez y dado por sentado después.

## 8. Qué falta decidir para poder arrancar

1. La pregunta de la sección 3 (mesero atado a una sucursal, sí o no — y qué pasa con un empleado que rota entre locales).
2. Si el equipo comercial ya tiene algún prospecto real con más de un local esperando, lo cual cambiaría la urgencia de esto frente a otras cosas pendientes (RF-14, el aviso de privacidad de §15.2, etc.).
3. Quién de los dos toma PR 1 y PR 2 (son los más mecánicos y menos riesgosos) versus PR 3/4 (tocan reglas de negocio y requieren la decisión de la sección 3 ya resuelta).
