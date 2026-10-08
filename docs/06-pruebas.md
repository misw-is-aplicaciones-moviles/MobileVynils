# 06. Estrategia de pruebas

Define qué se prueba, con qué herramienta, en qué nivel y cómo se documenta. Es el documento que se entrega en cada sprint como "estrategia de pruebas"; los artefactos (inventario, scripts, reportes de defectos, análisis) se enlazan desde la wiki del sprint.

## 1. Objetivos

1. Verificar que cada historia de usuario cumple sus criterios de aceptación de extremo a extremo en Android.
2. Proteger la lógica de presentación y de datos con pruebas rápidas que corren en cada pull request.
3. Evaluar atributos de calidad del curso: robustez (pruebas aleatorias y exploración sistemática), desempeño y accesibilidad.
4. Ejecutar en varias versiones de Android antes de cada release.

## 2. Pirámide y niveles

| Nivel | Qué prueba | Herramientas | Dónde | Cuándo corre |
|---|---|---|---|---|
| Unitarias | ViewModels, Repositories, Service Adapters, mappers, validaciones | JUnit 4, MockK, Turbine, kotlinx-coroutines-test, Truth, MockWebServer | `src/test` | Cada PR (CI) y local |
| Arquitectura | Reglas de capas y nombres | Konsist (JUnit) | `src/test` | Cada PR |
| Instrumentadas de componente | DAOs de Room contra SQLite real, pantallas Compose aisladas | AndroidX Test, Room testing, Compose UI test | `src/androidTest` | Antes de merge a `develop` |
| E2E (end-to-end) | Flujos completos de cada historia contra el backend de pruebas | Compose UI test + Espresso, Hilt testing, Page Objects | `src/androidTest` | Antes de release y en la matriz de dispositivos |
| Exploratorias y aleatorias | Robustez ante entradas no previstas | Monkey (`adb`), RIP (exploración sistemática) | Scripts en `tests/` | Sprint 3 y antes de release |
| Accesibilidad | Reglas de accesibilidad en vistas reales | Accessibility Scanner, TalkBack, `AccessibilityChecks` en E2E | Manual + `androidTest` | Cada pantalla nueva; Sprint 3 |
| Desempeño | CPU, memoria, red, fluidez | Android Profiler, LeakCanary, StrictMode, Macrobenchmark opcional | Manual + informe | Sprint 2 y 3 (`07-desempeno.md`) |

Objetivo de cobertura: 80 % de líneas en `feature/*/ *ViewModel` y `data/*`; no se exige cobertura en Compose ni en módulos Hilt. Se mide con el reporte de cobertura de Android Studio o Jacoco en CI.

## 3. Pruebas unitarias

- Nombre: `` `acción cuando condición entonces resultado` `` en inglés: `` fun `refresh emits error when backend fails`() ``.
- Estructura Given/When/Then separada por líneas en blanco.
- ViewModel: `MainDispatcherRule`, `FakeAlbumRepository`, Turbine sobre `uiState` y `effects`.
- Repository: fakes de DAO y Service Adapter; verificar que la red escribe en Room y que los errores se convierten en `DataError`.
- Service Adapter: MockWebServer con respuestas JSON reales de `BackVynils` guardadas en `src/test/resources/api/`; verificar deserialización, mapeo y errores HTTP.
- Mappers y validaciones: pruebas de tabla con parámetros.
- Sin `Thread.sleep`, sin `runBlocking`: `runTest` y `advanceUntilIdle`.

## 4. Pruebas E2E con Espresso y Compose

- Runner: `HiltTestRunner` (`AndroidJUnitRunner` con `HiltTestApplication`).
- Regla: `createAndroidComposeRule<MainActivity>()` con `@HiltAndroidTest` y `@BindValue` o `@TestInstallIn` para apuntar al backend de pruebas (MockWebServer dentro del test o `BackVynils` con datos semilla).
- Selección por semántica: `onNodeWithText`, `onNodeWithContentDescription`, `onNodeWithTag` solo cuando no hay texto ni descripción. Los `testTag` se centralizan en `core/ui/TestTags.kt`.
- Espresso para lo que Compose no cubre: Intents, teclado del sistema, vistas del sistema. `Espresso.pressBack()` para navegación hacia atrás.
- Page Object (`AlbumListRobot`, `AlbumDetailRobot`) con funciones de acción y de aserción; la prueba se lee como el caso de prueba del inventario.
- Una clase de prueba por historia de usuario: `HU01AlbumCatalogTest`, con un método por caso del inventario.
- Idempotentes: cada prueba limpia Room (`clearAllTables()`) en `@Before`.
- Matriz de ejecución antes de release: emulador API 26, API 33, API 37 y un dispositivo físico. Resultado por versión en la wiki.

## 5. Inventario de casos de prueba

Archivo por sprint en la wiki (`Sprint N / Pruebas / Inventario`), una fila por caso:

| Id | HU | Nombre | Precondición | Pasos | Resultado esperado | Tipo | Automatizado (clase.método) | Estado |
|---|---|---|---|---|---|---|---|---|
| CP-HU01-01 | HU01 | Listar álbumes con conexión | Backend con 3 álbumes | Abrir app, pestaña Álbumes | Se muestran 3 tarjetas con nombre e imagen | E2E | `HU01AlbumCatalogTest.listsAlbums` | Pasa |
| CP-HU01-02 | HU01 | Listar sin conexión con caché | Álbumes cargados antes; modo avión | Abrir app | Se muestran los álbumes en caché y aviso de sin conexión | E2E | ... | Pendiente |
| CP-HU01-03 | HU01 | Error de backend sin caché | Backend caído; sin caché | Abrir app | Pantalla de error con reintentar | E2E | ... | Pendiente |

Ids: `CP-HUxx-nn`. Cada historia tiene al menos: camino feliz, sin red, error de backend, estado vacío, y para formularios, validaciones por campo.

## 6. Reportes de defectos

Los defectos son issues de GitHub con la etiqueta `bug` y la plantilla:

```
**Historia / caso:** HU01 / CP-HU01-02
**Dispositivo y versión:** Pixel 8 emulador, API 37
**Build:** commit abc1234, rama develop
**Pasos:** 1. ... 2. ...
**Resultado actual:** ...
**Resultado esperado:** ...
**Evidencia:** captura, video o logcat adjunto
**Severidad:** bloqueante / alta / media / baja
```

Se enlazan desde el inventario y se cierran con el PR que los corrige, que incluye una prueba de regresión. El resumen de defectos por sprint (abiertos, cerrados, por severidad) va en la wiki.

## 7. Pruebas de arquitectura con Konsist

`src/test/.../architecture/ArchitectureTest.kt`:

- Clases con sufijo `ViewModel` extienden `ViewModel`, están en `feature..`, tienen `@HiltViewModel` y no importan `android.content.Context`.
- `feature..` no importa `core.network..`, `core.database..` ni `..RepositoryImpl`.
- `data..` no importa `androidx.compose..` ni `feature..`.
- `domain.model..` no importa `android..` ni `androidx..`.
- Interfaces `*Repository` tienen exactamente una implementación `*RepositoryImpl` enlazada en `di`.
- Composables públicos tienen `modifier: Modifier = Modifier` como primer parámetro opcional.

## 8. Pruebas aleatorias y de exploración sistemática (Sprint 3)

- **Monkey** (aleatorias): `adb shell monkey -p co.edu.uniandes.vinilos.debug --throttle 200 -s 42 -v 2000`. Se corre con tres semillas distintas en API 26 y 37; cualquier crash o ANR es un defecto. Script en `tests/monkey.sh` y resultado en la wiki.
- **RIP** (exploración sistemática, TheSoftwareDesignLab): recorre la app por su árbol de vistas y genera un grafo de estados. Se ejecuta sobre el APK de release contra el backend de pruebas y se adjuntan el grafo y las capturas. Se registra cobertura de pantallas (todas las del prototipo deben aparecer).

## 9. Pruebas de accesibilidad (Sprint 3)

- `AccessibilityChecks.enable().setRunChecksFromRootView(true)` en las pruebas E2E con Espresso.
- Accessibility Scanner en cada pantalla con informe de hallazgos y corrección en la wiki.
- Recorrido con TalkBack del flujo principal (listar, detalle, crear álbum) grabado como evidencia.
- Pruebas Compose: `onNodeWithContentDescription` para iconos de acción y `assertHasClickAction`; `assertIsDisplayed` con fuente al 200 % mediante `@Config`/ajuste del emulador.

## 10. Comandos

```bash
./gradlew testDebugUnitTest                 # unitarias y Konsist
./gradlew connectedDebugAndroidTest         # instrumentadas y E2E en el emulador conectado
./gradlew spotlessCheck detekt lint           # calidad estática
./gradlew koverHtmlReport                   # cobertura, si se adopta Kover
```

## 11. Bitácora de decisiones

| Fecha | Decisión |
|---|---|
| 2026-10-07 | Pirámide de cinco niveles, Page Objects, inventario `CP-HUxx-nn`, defectos como issues, Konsist para capas, Monkey y RIP en Sprint 3. |
