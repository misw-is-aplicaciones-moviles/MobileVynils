# 06. Estrategia de pruebas

Define qué se prueba, con qué herramienta, en qué nivel y cómo se documenta. La estrategia de cada sprint (objetivos, plan, inventario, reportes) se publica en la wiki y se apoya en este documento.

## 1. Objetivos

1. Verificar que cada historia de usuario cumple sus criterios de aceptación de extremo a extremo en Android, con pruebas E2E en Espresso (requisito de la rúbrica).
2. Proteger la lógica de presentación y de datos con pruebas rápidas que corren en cada pull request.
3. Evaluar robustez (exploratorias, aleatorias), desempeño y accesibilidad en los sprints que lo piden.
4. Ejecutar en varias versiones de Android, desde Lollipop (API 21), antes de cada release.

## 2. Pirámide y niveles

| Nivel | Qué prueba | Herramientas | Dónde | Cuándo corre |
|---|---|---|---|---|
| Unitarias | ViewModels, repositorios, service adapters, mappers, validaciones | JUnit 4, `InstantTaskExecutorRule`, kotlinx-coroutines-test, MockWebServer, fakes | `src/test` | Cada PR (CI) y local |
| Pruebas de API | Contratos de BackVynils que usa cada historia | Colección de Postman o `curl` contra el backend local con la semilla | Wiki del sprint | Antes de implementar cada historia |
| E2E | Flujos completos de cada historia en la app | Espresso (core y contrib), `ActivityScenarioRule`, Robots | `src/androidTest` | Antes de pedir revisión y en la matriz antes del release |
| Exploratorias | Escenarios del inventario exploratorio del sprint | Manual, con tester cruzado | Wiki | Al cerrar las historias |
| Aleatorias y sistemáticas | Robustez ante entradas no previstas | Monkey (`adb`), RIP | Scripts en `tests/` (se crean en el Sprint 3) | Sprint 3 |
| Accesibilidad | Reglas de accesibilidad en vistas reales | Accessibility Scanner, TalkBack, `AccessibilityChecks` | Manual + `androidTest` | Sprint 3 |
| Desempeño | CPU, memoria, red | Android Profiler, StrictMode | Manual + informe | Sprint 2 y 3 (`07-desempeno.md`) |

## 3. Pruebas unitarias

- Nombre: `` `acción cuando condición entonces resultado` `` en inglés: `` fun `refresh shows error when backend fails`() ``.
- Estructura Given/When/Then separada por líneas en blanco.
- ViewModel: `InstantTaskExecutorRule` (de `androidx.arch.core:core-testing`) para que `LiveData` publique al instante, `MainDispatcherRule` para `viewModelScope`, `FakeAlbumRepository` y lectura del estado con `getOrAwaitValue()`. `MainDispatcherRule` y `getOrAwaitValue` no vienen de una librería: los escribe TR-05 en `src/test`.

```kotlin
class AlbumListViewModelTest {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `refresh shows albums when repository succeeds`() = runTest {
        val repository = FakeAlbumRepository(albums = listOf(buscandoAmerica))

        val viewModel = AlbumListViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.getOrAwaitValue()
        assertEquals(listOf(buscandoAmerica), state.albums)
        assertFalse(state.isLoading)
    }
}
```

- Repository: fake del Service Adapter; verificar la caché y que los errores se convierten en `DataError`.
- Service Adapter: MockWebServer con respuestas JSON reales de BackVynils en `src/test/resources/api/`; verificar deserialización, mapeo y errores HTTP.
- Validaciones (HU07, HU08): pruebas de tabla con los valores límite del inventario.
- Sin `Thread.sleep` ni `runBlocking`: `runTest` y `advanceUntilIdle`.

## 4. Pruebas E2E con Espresso

- Runner `androidx.test.runner.AndroidJUnitRunner`; regla `ActivityScenarioRule(MainActivity::class.java)`.
- Backend: BackVynils local con la semilla (`docker compose up`), alcanzable desde el emulador en `10.0.2.2:3000`. Las pruebas que crean datos (HU07, HU08) usan nombres únicos para ser repetibles.
- Espera de red: `IdlingResource` registrado en `IdlingRegistry` para las llamadas de Retrofit (lo define TR-05); nunca `Thread.sleep`. Un `CountingIdlingResource` usado desde el código de la app necesita `androidx.test.espresso:espresso-idling-resource` como dependencia `implementation`, que hay que agregar al catálogo y verificar con `minSdk 21`.
- Selección por id de vista (`withId`) y por texto de recurso (`withText(R.string.x)`); listas con `RecyclerViewActions` de `espresso-contrib`.
- Page Object (Robot) por pantalla: `AlbumListRobot`, `AlbumDetailRobot`; la prueba se lee como el caso del inventario.
- Una clase por historia: `HU01AlbumCatalogTest`, un método por caso `CP-HU01-nn`.
- Animaciones del sistema desactivadas antes de correr: `testOptions { animationsDisabled = true }` en Gradle (lo agrega TR-05) para `connectedDebugAndroidTest` y, con `am instrument`, `settings put global window_animation_scale 0` (y lo mismo para `transition_animation_scale` y `animator_duration_scale`).
- **API 21:** no se escriben pruebas que hagan capturas de pantalla seguidas (`UiAutomation.takeScreenshot` cuadro a cuadro): congelan el emulador de Lollipop. Lo visual en API 21 se revisa a mano.
- Matriz antes de release: emulador API 21 (`Vinilos_API_21`, arrancado con `-gpu host`), API 30, API 37 y un dispositivo físico. Resultado por versión en la wiki.

## 5. Inventario de casos de prueba

Por sprint en la wiki, una fila por caso:

| Id | HU | Nombre | Precondición | Pasos | Resultado esperado | Tipo | Automatizado (clase.método) | Estado |
|---|---|---|---|---|---|---|---|---|
| CP-HU01-01 | HU01 | Listar álbumes con conexión | Backend con la semilla (4 álbumes: Buscando América, Poeta del pueblo, A Night at the Opera, A Day at the Races) | Abrir la app, pestaña Álbumes | Se muestran 4 tarjetas con nombre, artista y "Género · Año" | E2E | `HU01AlbumCatalogTest.listsAlbums` | Pendiente |

Ids: `CP-HUxx-nn`. Cada historia cubre al menos camino feliz, error de backend, estado vacío y, en formularios, las validaciones por campo.

El inventario de pruebas exploratorias usa la plantilla del curso (identificador, fecha, tester, funcionalidad, requerimiento, tipo de escenario positivo, negativo o mixto, nombre del escenario).

## 6. Reportes de defectos

Los defectos son issues de GitHub con la etiqueta `bug`:

```
**Historia / caso:** HU01 / CP-HU01-02
**Dispositivo y versión:** emulador Vinilos_API_21, Android 5.0
**Build:** commit abc1234, rama develop
**Pasos:** 1. ... 2. ...
**Resultado actual:** ...
**Resultado esperado:** ...
**Evidencia:** captura, video o logcat adjunto
**Severidad:** bloqueante / alta / media / baja
```

Se enlazan desde el inventario y se cierran con el PR que los corrige, que incluye una prueba de regresión.

## 7. Pruebas aleatorias y de exploración sistemática (Sprint 3)

- **Monkey**: `adb shell monkey -p co.edu.uniandes.vinilos --throttle 200 -s 42 -v 2000`, con tres semillas en API 21 y API 37; cualquier crash o ANR es un defecto.
- **RIP** (TheSoftwareDesignLab): exploración sistemática sobre el APK de release; se adjuntan el grafo de estados y la cobertura de pantallas.

## 8. Pruebas de accesibilidad (Sprint 3)

- `AccessibilityChecks.enable().setRunChecksFromRootView(true)` en las pruebas Espresso, con `androidx.test.espresso:espresso-accessibility` agregado al catálogo (no está todavía).
- Accessibility Scanner en cada pantalla, con hallazgos y correcciones en la wiki.
- Recorrido con TalkBack del flujo principal grabado como evidencia.

## 9. Comandos

```bash
./gradlew testDebugUnitTest                 # unitarias
./gradlew spotlessCheck detekt lintDebug    # calidad estática
./gradlew connectedDebugAndroidTest         # E2E en el emulador conectado
adb shell am instrument -w co.edu.uniandes.vinilos.test/androidx.test.runner.AndroidJUnitRunner   # E2E sin Gradle
```

En API 21, `connectedDebugAndroidTest` puede tardar varios minutos en cerrar tras terminar las pruebas; `am instrument` devuelve el resultado en cuanto terminan.

### Capa de red (TR-02)

`NetworkClientTest` usa MockWebServer y `src/test/resources/api/albums.json` para verificar rutas, deserialización, campos opcionales, catálogo vacío, códigos HTTP, JSON inválido, timeout, caché HTTP y cancelación. También comprueba que un POST cuya respuesta se pierde no se repite. `NetworkRequestTest` verifica errores DNS y la propagación de cancelación y errores de programación.

`BackendConnectionTest` es una prueba de integración optativa, independiente de Espresso: usa el cliente compartido del `AppContainer` real de `VinilosApplication`, comprueba que se reutiliza la misma instancia y consulta `/albums`. Requiere BackVynils con la semilla y un emulador o dispositivo con acceso a `BuildConfig.BASE_URL`. Se activa explícitamente:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=co.edu.uniandes.vinilos.network.BackendConnectionTest \
  -Pandroid.testInstrumentationRunnerArguments.backendSmoke=true
```

Sin `backendSmoke=true`, se omite. Los contratos `ProbeApi` y `ProbeAlbumDto` pertenecen solo a las pruebas; cada historia añadirá sus contratos de producción.

#### Inventario automatizado de TR-02

Todos los casos unitarios usan MockWebServer o excepciones controladas, sin depender del backend real. El último caso requiere el backend con datos y `backendSmoke=true`.

| Id | Caso | Resultado esperado | Prueba |
|---|---|---|---|
| CP-TR02-01 | Ruta base y respuesta de álbumes | Conserva `/api/albums` y deserializa ID y nombre | `NetworkClientTest.request preserves base path and decodes backend shaped JSON` |
| CP-TR02-02 | Campos opcionales ausentes | Conserva los campos como nulos para el Service Adapter | `NetworkClientTest.missing optional DTO fields remain nullable for the service adapter` |
| CP-TR02-03 | Catálogo vacío | Devuelve una lista vacía exitosa | `NetworkClientTest.empty catalog is a successful empty list` |
| CP-TR02-04 | Respuestas HTTP 400, 404 y 500 | Devuelve `DataError.Http` con el código original | `NetworkClientTest.HTTP errors preserve status codes` |
| CP-TR02-05 | JSON inválido, truncado o incompatible | Devuelve `DataError.Serialization` | `NetworkClientTest.invalid truncated and incompatible JSON produce serialization errors` |
| CP-TR02-06 | JSON no estándar | Rechaza la respuesta con `DataError.Serialization` | `NetworkClientTest.non standard JSON is rejected by the strict converter` |
| CP-TR02-07 | Timeout de lectura | Devuelve `DataError.Network` | `NetworkClientTest.read timeout produces a network error` |
| CP-TR02-08 | Pérdida de respuesta de un POST | No repite la escritura | `NetworkClientTest.lost POST response does not repeat the write` |
| CP-TR02-09 | Respuesta con `max-age` vigente | Reutiliza la caché sin una segunda petición | `NetworkClientTest.fresh response is reused from HTTP cache` |
| CP-TR02-10 | Respuesta con `no-store` | Vuelve a consultar al servidor | `NetworkClientTest.no store response is requested again` |
| CP-TR02-11 | Cancelación durante la petición | Cancela la coroutine y la petición | `NetworkClientTest.cancellation propagates during an HTTP request` |
| CP-TR02-12 | Error DNS | Devuelve `DataError.Network` | `NetworkRequestTest.DNS failure produces a network error` |
| CP-TR02-13 | Cancelación explícita | Propaga `CancellationException` | `NetworkRequestTest.cancellation is never converted to a data error` |
| CP-TR02-14 | Error de programación | Propaga el error sin convertirlo en fallo de red | `NetworkRequestTest.programming errors are never converted to network failures` |
| CP-TR02-15 | Conexión real desde Android | Reutiliza el cliente del contenedor y recibe álbumes con ID y nombre | `BackendConnectionTest.backend_returns_albums` |

## 10. Bitácora de decisiones

| Fecha | Decisión |
|---|---|
| 2026-10-07 | Pirámide de niveles, Page Objects, inventario `CP-HUxx-nn`, defectos como issues, Monkey y RIP en Sprint 3. |
| 2026-10-26 | Reinicio con Vistas: E2E con Espresso y `ActivityScenarioRule`, unitarias de `LiveData` con `InstantTaskExecutorRule`, fakes sin librería de mocks, matriz API 21/30/37, sin capturas seguidas en API 21. Konsist y pruebas de Compose retiradas. |
