# Guía de contribución — Carta Digital SaaS

Reglas de trabajo acordadas por el equipo (Juan Sebastián Moreno / Andrés Calderón) para trabajar los dos en el mismo monolito sin pisarse. Si algo de esto deja de aplicar, actualícenlo aquí — no lo dejen solo en el chat del equipo.

Antes de tocar código, lean `docs/documentacion-tecnica.md` — es la fuente de verdad de por qué el sistema está estructurado así.

## Flujo estándar para empezar una tarea nueva

```
git checkout main
git pull
git checkout -b <tipo>/<nombre-de-la-tarea>
```

- `git checkout main` → te asegura de estar parado sobre la rama principal.
- `git pull` → trae cualquier cambio reciente que haya subido el otro.
- `git checkout -b <tipo>/<nombre>` → crea la rama nueva y te cambia a ella.

## Convención de nombres de rama

| Prefijo | Cuándo usarlo | Ejemplo |
|---|---|---|
| `feature/` | Una funcionalidad nueva | `feature/menu-crud`, `feature/auth-jwt` |
| `fix/` | Corrección de un error | `fix/bug-precio` |
| `test/` | Agregar o mejorar cobertura de tests sin cambiar comportamiento | `test/mesa-service-cobertura` |
| `docs/` | Documentación o propuestas técnicas para discutir en equipo | `docs/propuesta-multisucursal-rf10` |
| `chore/` | Tareas de mantenimiento (dependencias, config) | `chore/actualizar-dependencias` |

## Antes de arrancar una feature de tamaño mediano o grande: avísense QUÉ van a construir, no solo QUÉ ARCHIVO van a tocar

Esto es más que la regla de abajo sobre archivos compartidos. El 2026-09-10 los dos terminaron implementando el mismo requisito (RF-10 PR4: selección obligatoria de sucursal para el MESERO) en paralelo, sin saberlo — mismo endpoint, mismo código de error, con nombres de archivo y de columna de `localStorage` distintos (`SeleccionarSucursal.jsx` vs `SeleccionarSucursalPage.jsx`, por ejemplo). Los archivos no coincidían, así que la regla de "si van a tocar el mismo archivo, avísense" no lo hubiera prevenido — el problema no era el archivo, era la feature. Se resolvió descartando una de las dos implementaciones completas al mergear, sin poder rescatar nada de la que se tiró.

Regla: antes de arrancar cualquier `feature/` que no sea trivial, un mensaje de una línea en el chat del equipo con qué RF o qué pantalla van a construir — no hace falta detalle, alcanza con "arranco con la selección de sucursal del mesero" para que el otro diga "yo ya la tengo a medio hacer" a tiempo.

## Reglas al trabajar los dos en backend en paralelo

- **Una rama por módulo/tarea, nunca los dos en la misma rama al mismo tiempo.** Ej.: uno en `feature/auth-jwt`, el otro en `feature/menu-crud`.
- **`pom.xml` es zona compartida.** Si ambos necesitan agregar una dependencia nueva, avísense antes de tocarlo — ahí es donde más fácil se generan conflictos de merge.
- **`application.yml` también es compartido** — mismo cuidado que con `pom.xml`.
- **Pull Requests pequeños y frecuentes**, en vez de guardarse cambios grandes por días. Así los conflictos que sí aparezcan son chiquitos y fáciles de resolver.
- **Si los dos necesitan tocar el mismo archivo** (ej. `SecurityConfig.java`, porque ambos módulos agregan reglas de seguridad), avísense por chat antes de empezar, para que uno termine y suba antes de que el otro arranque sobre ese archivo.

## Antes de abrir el PR

- **El CI ya existe y ya bloquea de verdad** (GitHub Actions, `mvn verify` en cada PR) — no es una promesa a futuro, ya encontró bugs reales de autorización antes de mergear. Que tu rama esté verde antes de pedir review.
- Que el PR toque una sola cosa — si te das cuenta de que estás resolviendo dos tareas a la vez, sepáralas en dos PRs.
- Si tu PR agrega o cambia un requisito funcional real (no un detalle de implementación), actualizá `docs/documentacion-tecnica.md §3` (tabla de RFs) en el mismo PR — no lo dejes solo como comentario de código. La feature de turnos y la de selección de sucursal para MESERO se construyeron completas sin que ninguna llegara a la tabla de requisitos, y eso es parte de por qué pasó el problema descrito arriba: no había un lugar único donde mirar "qué se está construyendo" antes de arrancar.
