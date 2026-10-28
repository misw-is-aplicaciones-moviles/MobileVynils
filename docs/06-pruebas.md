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
| Pruebas de API | Contratos de BackVynils que usa cada historia: rutas, deserialización y errores HTTP | JUnit 4 + Retrofit + MockWebServer (`MockWebServerRule`) con respuestas reales en `src/test/resources/api/`. Antes, exploración con `curl` o Postman contra el backend local con la semilla | `src/test` | Cada PR (CI) y local |
| E2E | Flujos completos de cada historia en la app | Espresso (core y contrib), `ActivityScenarioRule`, Robots | `src/androidTest` | Antes de pedir revisión y en la matriz antes del release |
| Exploratorias | Escenarios del inventario exploratorio del sprint | Manual, con tester cruzado | Wiki | Al cerrar las historias |
| Aleatorias y sistemáticas | Robustez ante entradas no previstas | Monkey (`adb`), RIP | Scripts en `tests/` (se crean en el Sprint 3) | Sprint 3 |
| Accesibilidad | Reglas de accesibilidad en vistas reales | Accessibility Scanner, TalkBack, `AccessibilityChecks` | Manual + `androidTest` | Sprint 3 |
| Desempeño | CPU, memoria, red | Android Profiler, StrictMode | Manual + informe | Sprint 2 y 3 (`07-desempeno.md`) |

## 3. Pruebas unitarias

- Nombre: `` `acción cuando condición entonces resultado` `` en inglés: `` fun `refresh shows error when backend fails`() ``.
- Estructura Given/When/Then separada por líneas en blanco.
- ViewModel: `InstantTaskExecutorRule` (de `androidx.arch.core:core-testing`) para que `LiveData` publique al instante, `MainDispatcherRule` para `viewModelScope`, `FakeAlbumRepository` y lectura del estado con `getOrAwaitValue()`. `MainDispatcherRule` y `getOrAwaitValue` no vienen de una librería: están en el paquete `testing` de `src/test` (sección 9).

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
- Espera de red: `OkHttpIdlingResourceRule(client)` registra en `IdlingRegistry` un `IdlingResource` sobre el `Dispatcher` del `OkHttpClient` de la app y lo retira al terminar; nunca `Thread.sleep`. Vive en `src/androidTest`, así que la app no necesita `espresso-idling-resource`. El cliente lo expone el `AppContainer` (TR-03).
- Selección por id de vista (`withId`) y por texto de recurso (`withText(R.string.x)`); listas con `RecyclerViewActions` de `espresso-contrib`.
- Page Object (Robot) por pantalla: `AlbumListRobot`, `AlbumDetailRobot`; la prueba se lee como el caso del inventario.
- Una clase por historia: `HU01AlbumCatalogTest`, un método por caso `CP-HU01-nn`.
- Animaciones del sistema desactivadas antes de correr: `testOptions { animationsDisabled = true }` en Gradle para `connectedDebugAndroidTest` y, con `am instrument`, `settings put global window_animation_scale 0` (y lo mismo para `transition_animation_scale` y `animator_duration_scale`).
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

## 9. Cómo agregar una prueba

El entorno ya está configurado (TR-05): dependencias en `app/build.gradle.kts` y utilidades en el paquete `co.edu.uniandes.vinilos.testing`. `testing/EnvironmentSmokeTest.kt` es el ejemplo mínimo de cada utilidad.

| Utilidad | Dónde | Para qué |
|---|---|---|
| `MainDispatcherRule` | `src/test` | Cambia `Dispatchers.Main` por un `TestDispatcher`; `advanceUntilIdle()` avanza `viewModelScope` |
| `getOrAwaitValue()` | `src/test` (`LiveDataExtensions.kt`) | Lee el primer valor de un `LiveData`; requiere `InstantTaskExecutorRule` |
| `MockWebServerRule` | `src/test` | Servidor HTTP local: `baseUrl` para Retrofit y `enqueueJson("api/x.json", code)` |
| `readResource(path)` | `src/test` (`TestResources.kt`) | Lee un archivo de `src/test/resources` |
| `OkHttpIdlingResourceRule` | `src/androidTest` | Espresso espera las llamadas de red de la app |
| `robots/` | `src/androidTest` | Page Objects por pantalla (`MainRobot` es el ejemplo) |

En las unitarias, las clases de `android.*` no lanzan error sino que devuelven valores por defecto (`unitTests.isReturnDefaultValues = true`, necesario porque OkHttp 5 llama a `android.util.Log`). Por eso la lógica que se prueba en `src/test` no usa clases de Android: `TextUtils.isEmpty` devolvería `false` en silencio y la prueba pasaría por error. En su lugar se usa Kotlin puro (`isBlank()`), como ya exige la arquitectura para ViewModels y modelos.

**ViewModel o Repository** (`src/test/java/.../viewmodels/AlbumListViewModelTest.kt`, mismo paquete que la clase probada): reglas `InstantTaskExecutorRule` y `MainDispatcherRule`, un `FakeAlbumRepository` en `src/test`, cuerpo en `runTest`. Ejemplo en la sección 3.

**Service Adapter / API** (`src/test/java/.../network/AlbumServiceAdapterTest.kt`):

```kotlin
@get:Rule val mockWebServerRule = MockWebServerRule()

@Test
fun `getAlbums maps albums when server returns seed`() = runTest {
    mockWebServerRule.enqueueJson("api/albums.json")
    // El convertidor JSON lo elige TR-02; se agrega aquí con addConverterFactory(...).
    val service = Retrofit.Builder().baseUrl(mockWebServerRule.baseUrl).build().create<VinilosApiService>()
    val adapter = AlbumServiceAdapter(service)

    val albums = adapter.getAlbums()

    assertEquals(4, albums.size)
    assertEquals("/albums", mockWebServerRule.server.takeRequest().path)
}
```

La respuesta se captura del backend real (`src/test/resources/api/README.md`). Los casos de error encolan un `MockResponse().setResponseCode(500)` o un JSON de error.

**E2E** (`src/androidTest/java/co/edu/uniandes/vinilos/HU01AlbumCatalogTest.kt`): `@RunWith(AndroidJUnit4::class)`, `ActivityScenarioRule(MainActivity::class.java)`, `OkHttpIdlingResourceRule` cuando la pantalla llama al backend, y un Robot por pantalla en `robots/`. Un método por caso del inventario (`CP-HU01-nn`).

## 10. Comandos

```bash
./gradlew testDebugUnitTest                 # unitarias
./gradlew spotlessCheck detekt lintDebug    # calidad estática
./gradlew connectedDebugAndroidTest         # E2E en el emulador conectado
adb shell am instrument -w co.edu.uniandes.vinilos.test/androidx.test.runner.AndroidJUnitRunner   # E2E sin Gradle
```

En API 21, `connectedDebugAndroidTest` puede tardar varios minutos en cerrar tras terminar las pruebas; `am instrument` devuelve el resultado en cuanto terminan.

## 11. Bitácora de decisiones

| Fecha | Decisión |
|---|---|
| 2026-10-07 | Pirámide de niveles, Page Objects, inventario `CP-HUxx-nn`, defectos como issues, Monkey y RIP en Sprint 3. |
| 2026-10-26 | Reinicio con Vistas: E2E con Espresso y `ActivityScenarioRule`, unitarias de `LiveData` con `InstantTaskExecutorRule`, fakes sin librería de mocks, matriz API 21/30/37, sin capturas seguidas en API 21. Konsist y pruebas de Compose retiradas. |
| 2026-10-27 | TR-05: entorno de pruebas listo. Utilidades en el paquete `testing` (`MainDispatcherRule`, `getOrAwaitValue`, `MockWebServerRule`, `OkHttpIdlingResourceRule`), Robots en `robots/`, `animationsDisabled`. Las pruebas de API pasan a JUnit + Retrofit + MockWebServer en `src/test`, con respuestas reales de BackVynils. |
