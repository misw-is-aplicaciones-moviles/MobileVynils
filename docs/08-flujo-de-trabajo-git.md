# 08. Flujo de trabajo con Git

GitFlow tal como lo exige el curso: rama de releases, rama de desarrollo e integración, ramas de funcionalidad integradas por pull request aprobado con revisión de código y pruebas locales, en modalidad squash and merge. El paso a la rama de releases requiere las pruebas en distintas versiones de Android.

## 1. Ramas

| Rama | Propósito | Quién escribe | Protección |
|---|---|---|---|
| `main` | Releases. Cada entrega de sprint es un merge desde `release/*` con tag | Solo por PR desde `release/*` o `hotfix/*` | Requiere PR, 1 aprobación, CI en verde, sin push directo |
| `develop` | Integración continua del sprint | Solo por PR desde `feature/*` o `fix/*` | Requiere PR, 1 aprobación, CI en verde, squash and merge |
| `feature/HUxx-descripcion` | Una tarea o historia | Su asignado | Se borra al integrarla |
| `fix/<issue>-descripcion` | Corrección de defecto | Su asignado | Igual que feature |
| `release/sprint-N` | Estabilización y pruebas de la entrega | Equipo | Solo correcciones; se integra a `main` y de vuelta a `develop` |
| `hotfix/<issue>-descripcion` | Corrección urgente sobre `main` | Equipo | Se integra a `main` y a `develop` |

Nombres en minúsculas con guiones: `feature/HU01-album-catalog`, `fix/23-detail-crash-rotation`.

## 2. Commits

Formato [Conventional Commits](https://www.conventionalcommits.org/) en inglés:

```
<tipo>(<ámbito>): <resumen en imperativo, minúsculas, sin punto>

<cuerpo opcional: qué y por qué>

Refs #<issue>
```

Tipos: `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`, `perf`, `style`. Ámbitos: `albums`, `artists`, `collectors`, `core`, `data`, `ui`, `nav`, `di`, `gradle`.

Ejemplos: `feat(albums): show album catalog from local cache`, `test(albums): cover refresh error path`, `build: pin compose bom 2026.09.00`.

Como se hace squash and merge, el título del PR se convierte en el mensaje del commit en `develop`; debe cumplir el formato.

## 3. Pull requests

1. Se abre desde `feature/*` hacia `develop` con la plantilla de `.github/PULL_REQUEST_TEMPLATE.md`: historia e issue, qué cambia, cómo probarlo, capturas si toca UI, lista de verificación.
2. Antes de abrirlo: `./gradlew spotlessCheck detekt lint testDebugUnitTest` en verde local y, si toca UI, `connectedDebugAndroidTest` en el emulador.
3. Tamaño: idealmente menos de 400 líneas de diferencia; un PR por tarea del tablero.
4. Revisión por al menos un compañero distinto del autor en menos de 24 h hábiles. El revisor ejecuta las pruebas locales y comenta con sugerencias concretas. Se aprueba cuando no quedan comentarios bloqueantes.
5. Integración por **squash and merge** por el autor, tras la aprobación y la CI en verde. La rama se borra.
6. El issue se mueve a `Done` en el tablero al integrarse, no antes.

## 4. Integración continua

GitHub Actions (`.github/workflows/ci.yml`):

- En cada PR y push a `develop` y `main`: ktlint, detekt, Android Lint, pruebas unitarias (incluye Konsist), ensamblado de `debug`.
- En push de tag `sprint-N`: ensamblado de `release`, APK adjunto como artefacto y como asset del GitHub Release.
- Las pruebas instrumentadas no corren en CI en el MVP (sin emulador en el runner); se ejecutan localmente antes de cada release y se registra el resultado en la wiki. Si hay tiempo en el Sprint 3, se añade un job con emulador.

## 5. Releases y APK

1. Al cerrar el sprint se crea `release/sprint-N` desde `develop`.
2. Se ejecuta la matriz de pruebas (`06-pruebas.md`, sección 4) y se corrigen defectos en la rama.
3. PR de `release/sprint-N` a `main`, aprobación, merge (merge commit, no squash, para conservar historia) y tag `sprint-N` (`git tag -a sprint-1 -m "Sprint 1: HU01, HU02"`).
4. La CI construye el APK de release; se publica un GitHub Release `Sprint N` con el APK, las historias incluidas y el enlace a la wiki.
5. Merge de `main` de vuelta a `develop`.
6. Firma: keystore de depuración del proyecto versionada en `keystore/debug.keystore` con contraseñas en `local.properties` (no versionado) y en secrets de GitHub. Suficiente para "APK listo para instalar"; nunca una keystore de producción en el repositorio.

## 6. Wiki

Organizada por sprint, como pide el curso:

```
Home
├── Inception: equipo, contrato, franja de reunión, backlog, puntos, diseño UX/UI, actas, retrospectiva
├── Sprint 1: alcance, diseño arquitectónico, estrategia de pruebas, inventario, defectos, APK, actas, retrospectiva
├── Sprint 2: lo anterior + análisis de desempeño
└── Sprint 3: lo anterior + pruebas aleatorias y sistemáticas, revisión de accesibilidad
```

Los documentos de `docs/` se enlazan desde la wiki, no se duplican. La wiki guarda hora de edición: las entregas se cierran antes de las 11:59 p. m. del domingo.

## 7. Tablero e issues

- Columnas: TO-DO, Diseño, Implementación, Integración, Pruebas, Done.
- Etiquetas: `HU01`…`HU08`, `bug`, `tech-debt`, `docs`, `tests`, `performance`, `accessibility`, `sprint-1`, `sprint-2`, `sprint-3`.
- Milestones: `Sprint 1`, `Sprint 2`, `Sprint 3` con fecha de cierre el domingo correspondiente.
- Todo issue tiene asignado, milestone y etiqueta de historia. Las ramas y PRs referencian el número de issue.

## 8. Bitácora de decisiones

| Fecha | Decisión |
|---|---|
| 2026-10-07 | GitFlow con `main`, `develop`, `feature/HUxx-*`, `release/sprint-N`; Conventional Commits en inglés; squash and merge; tags `sprint-N`; APK por CI en GitHub Release. |
