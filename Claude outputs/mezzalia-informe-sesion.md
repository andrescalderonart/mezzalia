# Informe de auditoría y trabajo — proyecto Mezzalia

Sesión: trabajo autónomo sobre 4 ramas, sin intervención del usuario, según
`docs/documentacion-tecnica.md` y el estado real de GitHub/`main`.

No voy a dorar la píldora: hice cuatro ramas de tamaño manejable en vez de
una sola rama gigante, precisamente porque una sola rama que tocara todo a
la vez —incluyendo el camino público de creación de pedidos— habría sido
imposible de revisar con algo de rigor en un entorno donde **no hay Maven
ni JDK instalados** y por lo tanto **no se compiló ni se corrió un solo
test en toda la sesión**. Todo lo que sigue fue verificado por lectura
línea por línea contra los patrones ya existentes en el código y por un
chequeo automático de balance de llaves/paréntesis — eso es notablemente
más débil que un `mvn test` real, y lo trato como tal.

---

## 1. Qué se hizo, rama por rama

### `feature/comensal-borrado-habeas-data` (continuación de rama ya existente, sin pushear)
Ya existía en el repo con el derecho de supresión (Habeas Data, RF-13)
implementado pero **con un hueco de seguridad documentado en el propio
código como "pendiente, no arrancado"**: cualquier `MESERO` veía el
teléfono completo del comensal en el listado de pedidos, cuando
`documentacion-tecnica.md` §10 exige enmascararlo por rol.

- `ComensalDto.desde(...)` ahora recibe un flag `puedeVerTelefonoCompleto`
  y enmascara todo menos los últimos 2 dígitos cuando es falso.
- `PedidoAdminController` decide ese flag mirando el rol autenticado
  (`ADMIN_RESTAURANTE` ve completo, `MESERO` no).
- 5 tests unitarios nuevos para el enmascarado (incluye teléfono nulo y
  teléfonos cortos).

Commit local: `85463cf` (1 commit por delante de `origin`, sin pushear).

### `feature/rotacion-clave-cifrado-comensal` (rama nueva)
El cifrador AES-256-GCM del teléfono del comensal tenía, en su propio
comentario, una **"ADVERTENCIA operativa real: no implementada"**: no
había forma de rotar `COMENSAL_ENCRYPTION_KEY` sin dejar ilegibles todos
los registros ya cifrados con la clave vieja.

- Soporte para `COMENSAL_ENCRYPTION_KEY_PREVIOUS`: al descifrar, prueba
  primero la clave actual y cae a la anterior si falla.
- Endpoint de mantenimiento `POST /api/superadmin/comensales/reencriptar`
  (`SUPER_ADMIN` únicamente) que reescribe cada teléfono con la clave
  nueva.
- Detalle técnico importante que si no se hubiera manejado habría hecho
  que la migración fuera un **no-op silencioso**: el dirty-checking de
  Hibernate compara el valor Java ya descifrado, no el texto cifrado, así
  que un `load + save()` normal nunca dispara el `AttributeConverter` de
  nuevo. Se resolvió con un `UPDATE` JPQL explícito por fila.
- `.env.example` documenta el procedimiento de rotación paso a paso.
- Tests nuevos para el cifrador (round-trip, aleatoriedad del IV, fallo
  con clave incorrecta).

**Punto ciego que dejo explícito y no resolví**: el endpoint de
reencriptación no pagina ni tiene límite de tamaño de lote — reencripta
TODOS los comensales de TODOS los tenants en una sola llamada HTTP
síncrona. Para un cliente con una base de datos grande esto es una
petición larga, bloqueando un hilo, sin progreso ni forma de cancelar. Lo
que sí es intencional y mitiga el peor escenario: como el descifrado
prueba clave actual y luego la anterior, una ejecución que se corta a la
mitad deja el sistema en un estado consistente y se puede volver a correr
sin duplicar trabajo raro. Pero "se puede reintentar" no es lo mismo que
"está bien diseñado" — un chunking real (por ejemplo, por tenant o por
lotes de N filas) queda pendiente si el volumen de datos lo justifica.

Commit local: `2527a0d` (rama nueva, sin pushear).

### `feature/usuarios-internos-crud` (rama nueva)
La documentación lista "gestión de usuarios internos con roles" como
parte del alcance del MVP (§2), y no existía ningún CRUD — solo seeds de
desarrollo (`SeedDatosDemoConfig`/`SeedSuperAdminConfig`).

- Backend: `UsuarioInternoAdminService` (crear, listar, activar/
  desactivar, resetear password) con las mismas defensas ya establecidas
  en el resto del proyecto: excepción explícita si el `ADMIN_RESTAURANTE`
  intenta asignar el rol `SUPER_ADMIN`, y si intenta desactivarse a sí
  mismo.
- Endpoint `/api/admin/usuarios`, restringido a `ADMIN_RESTAURANTE`.
- Frontend: `UsuariosPage.jsx` (listar, crear, activar/desactivar,
  resetear password), ruta y link de navegación nuevos.
- 9 tests de servicio.

**Lo que no cubrí y que es una laguna real, no un detalle menor**: no hay
ningún límite de complejidad de password en `ResetearPasswordRequest` más
allá de lo que ya exista en el request DTO (revisar eso antes de exponerlo
a un cliente real), y no hay rate-limiting en el reseteo de password — un
`ADMIN_RESTAURANTE` comprometido podría resetear passwords de todo su
equipo en un loop sin que nada lo frene. Esto no es específico de esta
rama: el proyecto no tiene rate-limiting en ningún endpoint autenticado
todavía, así que es un problema preexistente que decidí no intentar
resolver de forma aislada y mal pensada en esta sesión.

Commit local: `54c36cd` (rama nueva, sin pushear).

### `feature/sucursales-multi-tenant` (rama nueva, terminada al final de esta sesión)
El ER de la documentación (§7) modela una entidad `SUCURSAL` que nunca
existió en el código — ni la tabla, ni el CRUD, ni la asociación con
`Mesa`.

- Módulo `sucursal` completo: entidad (tenant-scoped, soft delete),
  repositorio, excepciones con su propio `@RestControllerAdvice`
  (`@Order(10)`, mismo criterio obligatorio que el resto de módulos —
  este orden explícito es lo que evita el bug real de "500 genérico en
  vez de 401/403" que ya ocurrió antes en este proyecto), DTOs, servicio y
  controlador (`/api/admin/sucursales`).
- `Mesa` ahora exige un `sucursalId` válido y activo del tenant actual.
  `MesaService.crearMesa` valida tenant → sucursal → unicidad de código,
  en ese orden.
- Dos migraciones Flyway: `V15__sucursales.sql` (crea la tabla y siembra
  una "Sucursal Principal" por cada tenant existente) y
  `V16__mesas_sucursal_id.sql` (agrega `sucursal_id` a `mesas`, backfill,
  `NOT NULL`, FK).
- Frontend: `SucursalesPage.jsx` (listar, crear, desactivar), ruta y nav
  link para `ADMIN_RESTAURANTE`. `MesasPage.jsx` ahora carga las
  sucursales activas: si hay una sola se preselecciona sin mostrar combo,
  si hay varias exige elegir una explícitamente, y si el tenant se quedó
  sin ninguna, bloquea el alta con un mensaje que lleva a `/sucursales`.
- Guardia de negocio que **la documentación no pedía explícitamente pero
  que es una consecuencia obvia y peligrosa de no tenerlo**: agregué
  `UltimaSucursalActivaException` (409) para que no se pueda desactivar
  la única sucursal activa de un tenant — sin esto, un `ADMIN_RESTAURANTE`
  se habría podido dejar a sí mismo sin ningún lugar donde crear mesas
  nuevas, sin ninguna forma de salir de ese estado desde la UI.
- Ripple effect real: hacer `sucursalId` obligatorio en `Mesa` rompía la
  firma de `MesaService` y de `crearMesa(...)` en todos los callers.
  Reescribí `MesaServiceTest`, `MesaServiceConcurrenciaTest` y parcheé los
  helpers de `PedidoServiceTest`/`PedidoServiceConcurrenciaTest`.
- `SucursalServiceTest` nuevo: aislamiento de tenant, "no encontrada"
  genérica para sucursal inactiva o de otro tenant, y el guardia de
  última-sucursal-activa.

**Lo que deliberadamente NO toqué en esta rama, y por qué**:
`CategoriaMenu`, `ItemMenu` y `Pedido` siguen siendo *tenant-scoped*
únicamente — no distinguen sucursal. Para un tenant con más de una
sucursal activa, la carta y los pedidos siguen siendo una sola lista
compartida entre todas sus sucursales, no una por sucursal como pide el
RF completo. Extender eso implica tocar el camino público **sin
autenticación** de creación de pedidos (`MenuPublicoController` /
`PedidoService`) — el endpoint de mayor tráfico y mayor sensibilidad de
todo el sistema — y no tenía forma de compilar ni correr un solo test
antes de abrir el PR. Preferí una rama más chica y verificable a una que
tocara ese camino a ciegas. Esto **no está cerrado**, es el siguiente
paso obvio y lo dejo dicho así de claro para que no se confunda con "RF-10
completo".

**Dos huecos reales que encontré mientras escribía esto y que no alcancé
a resolver**:

1. **Carrera de condición (TOCTOU) en el guardia nuevo**: el chequeo
   `countByTenantIdAndActivaTrue(...) <= 1` en `desactivarSucursal` no
   tiene ningún backstop a nivel de base de datos. Si dos requests
   concurrentes desactivan las dos últimas sucursales activas de un
   tenant casi al mismo tiempo, ambas pueden leer "quedan 2 activas"
   antes de que cualquiera de las dos escrituras se confirme, y el tenant
   queda con cero sucursales activas de todos modos. El resto del
   proyecto sí tiene este backstop para invariantes parecidas (la
   unicidad de `Mesa.codigo` tiene una restricción `UNIQUE` en la base de
   datos además del chequeo en la aplicación, exactamente para cubrir
   este mismo tipo de carrera) — aquí no lo agregué porque un `CHECK`
   declarativo de "al menos una fila activa por tenant" no es algo que
   MySQL soporte de forma directa sin un trigger, y no quise improvisar
   un trigger sin poder probarlo. Es una ventana de carrera realista pero
   estrecha (requiere que dos administradores desactiven sucursales al
   mismo tiempo), y la dejo documentada en vez de escondida.
2. **Sin restricción de unicidad en `Sucursal.nombre`**: a diferencia de
   `Mesa.codigo`, dos sucursales del mismo tenant pueden llamarse igual.
   No es un requisito explícito de la documentación, pero es inconsistente
   con el criterio que el propio proyecto ya sigue en otras partes.

Commit local: `1081c80` (rama nueva, sin pushear).

---

## 2. Lo que sigue genuinamente sin implementar (y por qué no es un olvido)

- **Sucursal en Carta/Pedidos** (arriba, ya explicado): siguiente paso
  obvio de RF-10, pospuesto por riesgo de tocar el camino público sin
  poder probarlo.
- **Límites numéricos por plan** (§1.2 de la documentación: cuántas mesas,
  usuarios o ítems de carta permite cada plan): no hay ninguna
  verificación de eso en ningún servicio. No lo implementé porque la
  documentación no fija los números reales por plan — inventar límites
  arbitrarios sin que el negocio los haya definido sería peor que no
  tener nada, generaría un falso sentido de que el control existe.
- **Textos legales de consentimiento Habeas Data, política de privacidad,
  período de retención de datos, rol de soporte/guardia**: la propia
  documentación (§15.2) los lista explícitamente como preguntas de
  negocio genuinamente abiertas, no como deuda técnica. No los resolví
  con código porque no es una decisión que me corresponda inventar.
- **Precios en COP y moneda por tenant**: mismo criterio, es una decisión
  de negocio, no un gap de ingeniería.

## 3. Riesgos estructurales que ya existían y que esta sesión no resuelve

Esto es lo que un auditor externo señalaría y que prefiero decir yo antes
de que lo descubran en producción:

- **Cero tests de integración/controlador en todo el proyecto.** Todo lo
  que se probó en esta sesión (y lo que ya existía) es a nivel de
  servicio con `@DataJpaTest`. Ningún test ejercita realmente
  `@PreAuthorize`, la validación de `@Valid` en los DTOs, ni el mapeo de
  excepción→código HTTP a través de una petición HTTP real. Si algún
  `@RestControllerAdvice` quedara mal ordenado (el mismo bug de `@Order`
  que ya pasó una vez), nada en la suite de tests lo detectaría hoy.
- **Ninguna migración Flyway se ha ejecutado nunca contra un MySQL real
  en CI.** V15 y V16 en particular mezclan DDL y DML (`ALTER TABLE` +
  `UPDATE` + `ALTER TABLE` + índice) en el mismo script. En MySQL/InnoDB
  el DDL no es transaccional statement-a-statement de la misma forma que
  el DML — si el `UPDATE` de backfill fallara a mitad de camino (por
  ejemplo, por una fila huérfana que nadie previó), el esquema queda a
  medio migrar y no hay rollback automático. Antes de correr esto contra
  producción: backup primero, ventana de mantenimiento, y correrlo contra
  una copia real con `docker-compose up`.
- **La disciplina de aislamiento de tenant es 100% convención, no está
  forzada por ningún mecanismo del lenguaje ni por análisis estático.**
  Cada servicio nuevo (incluyendo `SucursalService`, que yo mismo escribí
  esta sesión) tiene que acordarse manualmente de llamar
  `TenantContext.obtenerTenantId()` y comparar contra `tenantId` de la
  entidad, porque el filtro de Hibernate nunca se activa para
  `SUPER_ADMIN`. Ya causó dos fugas de datos reales en el pasado de este
  proyecto. Yo seguí el patrón al pie de la letra en todo lo nuevo, pero
  el patrón en sí sigue siendo frágil: un desarrollador nuevo que no lea
  los comentarios de otros módulos puede repetir exactamente el mismo
  error de nuevo. Vale la pena, a mediano plazo, considerar un aspecto o
  una clase base que fuerce este chequeo en tiempo de compilación en vez
  de confiar en que cada autor se acuerde.
- **Ningún endpoint autenticado tiene rate-limiting.** Reseteo de
  password, login, generación de sesión de mesa: todos aceptan intentos
  ilimitados.
- **Frontend sin ninguna suite de tests** (ni Jest ni Vitest configurados
  en el repo) — las 3 páginas nuevas (`UsuariosPage`, `SucursalesPage`, y
  los cambios de `MesasPage`) no tienen ni un test, consistente con que
  nada en `frontend/app-admin` lo tenía antes tampoco. `npm install` /
  `npm run build` tampoco se corrieron en esta sesión — no hay
  confirmación real de que el frontend compile.

---

## 4. Lo que el usuario tiene que hacer manualmente

`device_bash` en este entorno **no tiene forma de llegar a GitHub**
(SSH no resuelve el DNS de github.com desde esta VM, y HTTPS pide
credenciales que nunca voy a escribir). Todo lo que sigue es 100%
responsabilidad tuya desde tu propio PowerShell/IntelliJ:

1. **Pushear las 4 ramas** (una ya existía remotamente, las otras 3 son
   completamente nuevas):
   - `git push origin feature/comensal-borrado-habeas-data` (tiene 1
     commit nuevo por delante del remoto)
   - `git push -u origin feature/rotacion-clave-cifrado-comensal`
   - `git push -u origin feature/usuarios-internos-crud`
   - `git push -u origin feature/sucursales-multi-tenant`
2. **Abrir 5 PRs** (las 4 de arriba, más `chore/contributing-docs`, que ya
   está pusheada y solo le falta el click de abrir el PR — no tiene
   cambios de código pendientes).
3. **Dejar que la CI real de GitHub Actions corra los tests de verdad.**
   Esta es la primera vez que este código se compila desde que empecé.
   Es muy posible que aparezca algo que la revisión manual no detectó —
   trátalo como esperable, no como una sorpresa alarmante.
4. **Orden de merge sugerido y un conflicto ya esperado**:
   `feature/comensal-borrado-habeas-data` crea
   `ComensalService.java` (con `eliminarDatosComensal`).
   `feature/rotacion-clave-cifrado-comensal` crea el mismo archivo,
   independientemente, con un método distinto
   (`reencriptarTodosLosComensales`). Al mergear ambas a `main`, git va a
   marcar un conflicto real de "add/add" en ese archivo. La resolución es
   directa: combinar ambos métodos en una sola clase. Lo dejé
   documentado en el javadoc del archivo y en el mensaje de commit para
   que no llegue como sorpresa.
5. **Verificar V15/V16 contra MySQL real antes de tocar producción**:
   `docker-compose up`, correr las migraciones, confirmar que el backfill
   de "Sucursal Principal" y de `sucursal_id` en `mesas` da los números
   esperados.
6. **Correr `npm install && npm run build` en `frontend/app-admin`** para
   confirmar que `SucursalesPage.jsx`, `UsuariosPage.jsx` y los cambios de
   `MesasPage.jsx`/`App.jsx`/`Layout.jsx` compilan de verdad — no se
   verificó en esta sesión.
7. Decidir, en algún momento (no ahora, no por mí): los límites por plan,
   el texto legal de Habeas Data, la política de retención de datos y el
   modelo de precios en COP. Son decisiones de negocio genuinamente
   abiertas, no tareas de ingeniería pendientes.
