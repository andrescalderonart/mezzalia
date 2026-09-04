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

## Reglas al trabajar los dos en backend en paralelo

- **Una rama por módulo/tarea, nunca los dos en la misma rama al mismo tiempo.** Ej.: uno en `feature/auth-jwt`, el otro en `feature/menu-crud`.
- **`pom.xml` es zona compartida.** Si ambos necesitan agregar una dependencia nueva, avísense antes de tocarlo — ahí es donde más fácil se generan conflictos de merge.
- **`application.yml` también es compartido** — mismo cuidado que con `pom.xml`.
- **Pull Requests pequeños y frecuentes**, en vez de guardarse cambios grandes por días. Así los conflictos que sí aparezcan son chiquitos y fáciles de resolver.
- **Si los dos necesitan tocar el mismo archivo** (ej. `SecurityConfig.java`, porque ambos módulos agregan reglas de seguridad), avísense por chat antes de empezar, para que uno termine y suba antes de que el otro arranque sobre ese archivo.

## Antes de abrir el PR

- Que compile y pase lo que haya de tests/lint (una vez exista CI, esto lo va a validar GitHub Actions automáticamente).
- Que el PR toque una sola cosa — si te das cuenta de que estás resolviendo dos tareas a la vez, sepáralas en dos PRs.
