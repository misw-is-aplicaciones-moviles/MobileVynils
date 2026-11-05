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
- Validaciones (HU07, HU08): pruebas de tabla con los valores límite del inventario. HU07: `AlbumFormValidatorTest` cubre cada campo vacío o con solo espacios, sin fecha, sin género, sin sello, `Genre.UNKNOWN` y el caso válido. HU08: `TrackCreateViewModelTest` cubre el nombre vacío o con solo espacios, la duración en `00:00`, `00:60` y `60:00`, y los extremos válidos `00:01` y `59:59`.
- Pruebas de API de `POST /albums` (HU07-06): en `AlbumServiceAdapterTest`, con `MockWebServerRule`, se verifica el método, la ruta, el cuerpo JSON con exactamente los seis campos y la fecha en medianoche UTC; y, a través del repositorio, que una respuesta 400 (campo faltante o valor fuera de las listas) da `DataError.Http(400)` y una conexión cortada da `DataError.Network`, sin escribir en Room.
- Sin `Thread.sleep` ni `runBlocking`: `runTest` y `advanceUntilIdle`.

## 4. Pruebas E2E con Espresso

- Runner `co.edu.uniandes.vinilos.testing.VinilosTestRunner`, basado en `AndroidJUnitRunner`; regla `ActivityScenarioRule(MainActivity::class.java)`. Instala el selector de proxy de pruebas antes de construir el cliente HTTP y lo restaura al finalizar.
- Backend: BackVynils local con la semilla (`docker compose up`), alcanzable desde el emulador en `10.0.2.2:3000`. Las pruebas que crean datos (HU07, HU08) usan nombres únicos para ser repetibles.
- La semilla de BackVynils inserta IDs explícitos sin avanzar las secuencias de PostgreSQL. `scripts/run_e2e.sh` prepara automáticamente las secuencias de álbumes, canciones, artistas, premios y asignaciones a partir de los datos presentes y luego ejecuta Gradle. Usa `pg_get_serial_sequence` y el máximo ID real: no contiene IDs esperados, posiciones ni contadores propios de un equipo, no reduce secuencias y no borra registros. Las pruebas no reintentan ni ignoran HTTP 500 de preparación. Esta preparación debe repetirse después de cargar nuevamente la semilla.
- El catálogo admite álbumes adicionales a la semilla: `AlbumListRobot` busca los títulos sin posiciones fijas, comprueba los datos y la numeración de la fila según su posición actual y verifica el orden del catálogo completo con la regla de SQLite `COLLATE NOCASE`. CP-HU01-01 crea además un álbum único anterior a la semilla para cubrir esta regresión. Las pruebas de canciones desplazan el detalle hasta el título y el contador antes de verificarlos, porque la cabecera puede superar el alto visible.
- `AlbumListRobot.pullToRefresh()` vuelve al inicio real de la lista antes de deslizar hacia abajo: si el catálogo está desplazado, el primer gesto puede desplazar filas sin activar el refresco. CP-HU01-10 empieza desplazado hasta "Poeta del pueblo" y verifica que el álbum creado después de abrir el catálogo aparece tras el refresco.
- En CP-HU08-03, `BackendFirewall` redirige temporalmente las conexiones del backend a un socket local que cierra sin respuesta HTTP, y descarta las conexiones ociosas del cliente para evitar su reutilización. `restore()` devuelve las rutas originales y cierra el socket. Funciona con `adb reverse` sin root, modo avión, cambios de wifi/datos, esperas fijas ni procesos externos. Es un fallo de transporte controlado; no simula la pérdida física de conectividad del dispositivo.
- El caso de contenido del detalle HU02 contrasta el contador de canciones con una lectura previa de `GET /albums/:id` sin caché HTTP, en vez de exigir las dos canciones originales. Conserva las comprobaciones del título, artista, sello, año y la canción de la semilla.

Validación de estos ajustes en tablet DBY-W09, Android 12 / API 31 (2026-10-30): `spotlessCheck`, `detekt`, `lintDebug` y `testDebugUnitTest` pasan (132 unitarias, 0 fallos, 0 omitidas). La suite completa pasa sus 48 pruebas instrumentadas, sin fallos ni omisiones: incluye las 5 del catálogo, las 4 del detalle y las 7 de canciones, incluido CP-HU08-03 con fallo de transporte controlado y reintento. Estos ajustes no se han ejecutado en API 21 ni API 37; los resultados anteriores de esas versiones corresponden al código previo. Reporte local: `app/build/reports/androidTests/connected/debug/index.html`.
- Espera de red: `OkHttpIdlingResourceRule(client)` registra en `IdlingRegistry` un `IdlingResource` sobre el `Dispatcher` del `OkHttpClient` de la app y lo retira al terminar; nunca `Thread.sleep`. Vive en `src/androidTest`, así que la app no necesita `espresso-idling-resource`. El cliente lo expone el `AppContainer` (TR-03).
- Selección por id de vista (`withId`) y por texto de recurso (`withText(R.string.x)`); listas con `RecyclerViewActions` de `espresso-contrib`.
- Page Object (Robot) por pantalla: `AlbumListRobot`, `AlbumDetailRobot`; la prueba se lee como el caso del inventario.
- Una clase por pantalla (`AlbumListScreenTest`, `AlbumCreateScreenTest`, `TrackCreateScreenTest`), un método por caso `CP-HUxx-nn`. El inventario relaciona cada caso con su método; el código no nombra historias.
- Animaciones del sistema desactivadas antes de ejecutar: `testOptions { animationsDisabled = true }` en Gradle para `connectedDebugAndroidTest` y, con `am instrument`, `settings put global window_animation_scale 0` (y lo mismo para `transition_animation_scale` y `animator_duration_scale`).
- **API 21:** no se escriben pruebas que hagan capturas de pantalla seguidas (`UiAutomation.takeScreenshot` cuadro a cuadro): congelan el emulador de Lollipop. Lo visual en API 21 se revisa a mano.
- Matriz antes de release: emulador API 21 (`Vinilos_API_21`, arrancado con `-gpu host`), API 30, API 37 y un dispositivo físico. Resultado por versión en la wiki.

## 5. Inventario de casos de prueba

Por sprint en la wiki, una fila por caso:

| Id | HU | Nombre | Precondición | Pasos | Resultado esperado | Tipo | Automatizado (clase.método) | Estado |
|---|---|---|---|---|---|---|---|---|
| CP-HU01-01 | HU01 | Listar álbumes como visitante | Backend local con la semilla (A Day at the Races, A Night at the Opera, Buscando América, Poeta del pueblo); la prueba crea un álbum único anterior a la semilla | Elegir "Visitante" y recorrer las filas | La semilla y el álbum adicional aparecen en un catálogo ordenado alfabéticamente; la fila de "A Day at the Races" conserva "Queen", "Rock · 1976" y el número de su posición actual; sin botón "+" | E2E | `AlbumListScreenTest.visitor_sees_the_seed_albums_in_alphabetical_order_without_the_create_button` | Pasa en API 21 (2026-10-29); API 37 pendiente |
| CP-HU01-02 | HU01 | Listar álbumes como coleccionista | Backend local con la semilla | Elegir "Coleccionista" y tocar "+" | Las cuatro filas de la semilla en orden; el botón "+" se ve y abre el formulario de nuevo álbum | E2E | `AlbumListScreenTest.collector_sees_the_create_button_and_it_opens_the_new_album_form` | Pasa en API 21 (2026-10-29); API 37 pendiente |
| CP-HU01-03 | HU01 | Álbum sin intérpretes | La prueba crea en el backend `Sin artista <número único>` con `POST /albums` (el backend no le asocia intérpretes) | Elegir "Visitante" y desplazarse hasta ese álbum | Su fila muestra "sin artista" | E2E | `AlbumListScreenTest.album_without_performers_shows_no_artist`; en API, `AlbumServiceAdapterTest.album without performers has no performer name` | Pasa en API 21 (2026-10-29); API 37 pendiente |
| CP-HU01-04 | HU01 | Carga sin caché | Repositorio falso sin caché que responde después | Abrir el catálogo | `isLoading` mientras llega la respuesta y después la lista | Unitaria | `AlbumListViewModelTest.without cache it shows loading and then the list` | Pasa (2026-10-29) |
| CP-HU01-05 | HU01 | Catálogo vacío | Backend que responde `[]` | Abrir el catálogo | Estado vacío ("Aún no hay álbumes en el catálogo") | Unitaria y API | `AlbumListViewModelTest.an empty catalog is the empty state`; `AlbumServiceAdapterTest.empty backend list is an empty catalog` | Pasa (2026-10-29) |
| CP-HU01-06 | HU01 | Error del backend sin caché | Backend que responde 500 y Room vacío | Abrir el catálogo | `DataError.Http(500)` y estado de error con "Reintentar" | Unitaria y API | `AlbumListViewModelTest.failure without a list sets the error`; `AlbumServiceAdapterTest.server error becomes an HTTP data error`; `AlbumRepositoryImplTest.HTTP error returns an HTTP data error` | Pasa (2026-10-29) |
| CP-HU01-07 | HU01 | Reintentar tras un error | Primera respuesta con error y segunda con la lista | Tocar "Reintentar" | Se muestra la lista y desaparece el error | Unitaria | `AlbumListViewModelTest.refresh after an error loads the list` | Pasa (2026-10-29) |
| CP-HU01-08 | HU01 | Sin red con caché | Catálogo abierto una vez con conexión | Activar el modo avión (o detener el contenedor de BackVynils) y abrir de nuevo la app como visitante | Las filas guardadas en Room y el aviso "Sin conexión. Mostrando los datos guardados" | Unitaria y E2E manual | `AlbumListViewModelTest.failed revalidation with a list keeps it and emits the network notice`; `AlbumRepositoryImplTest.IO failure returns a network error and leaves Room intact`; manual en la wiki | Unitaria pasa (2026-10-29); manual pendiente |
| CP-HU01-09 | HU01 | Sin red y sin caché | App recién instalada sin conexión | Abrir el catálogo como visitante | "No se pudo cargar el catálogo" con "Reintentar" | API y E2E manual | `AlbumServiceAdapterTest.catalog connection cut becomes a network data error`; `AlbumRepositoryImplTest.network failure with empty Room leaves Room empty`; manual en la wiki | API pasa (2026-10-29); manual pendiente |
| CP-HU01-10 | HU01 | Deslizar para refrescar | Catálogo abierto; la prueba crea `Refresco <número único>` en el backend después de abrirlo | Deslizar la lista hacia abajo | El álbum nuevo aparece en el catálogo | E2E | `AlbumListScreenTest.pull_to_refresh_shows_an_album_added_on_the_backend` | Pasa en API 21 (2026-10-29); API 37 pendiente |
| CP-HU01-11 | HU01 | Tocar una tarjeta abre el detalle | Backend local con la semilla | Tocar "Buscando América" | Se abre el detalle de ese álbum | E2E | `AlbumDetailScreenTest.catalog_card_opens_its_detail_and_returns_without_collector_actions` | Pasa en API 21 (2026-10-29); API 37 pendiente |
| CP-HU01-12 | HU01 | Conservar el catálogo al recrear la pantalla | Catálogo desplazado hasta "Poeta del pueblo" | Recrear la actividad (`ActivityScenario.recreate()`, el mismo cambio de configuración que una rotación) | Sigue "Poeta del pueblo" en pantalla y el rol se conserva | E2E | `AlbumListScreenTest.recreating_the_screen_keeps_the_catalog_and_its_position` | Pasa en API 21 (2026-10-29); API 37 pendiente |
| CP-HU01-13 | HU01 | Contrato de `GET /albums` | MockWebServer con `api/albums.json` (respuesta real de la semilla) | `getAlbums()` | `GET /albums`; cuatro álbumes con nombre, primer intérprete, género y año | API | `AlbumServiceAdapterTest.deserializes the backend albums` | Pasa (2026-10-29) |
| CP-HU01-14 | HU01 | Respuesta con forma inválida | MockWebServer con JSON malformado o un álbum sin nombre | `getAlbums()` | `DataError.Serialization` | API | `AlbumServiceAdapterTest.malformed JSON becomes a serialization error`; `AlbumServiceAdapterTest.album without a name becomes a serialization error` | Pasa (2026-10-29) |
| CP-HU07-01 | HU07 | Crear un álbum con datos válidos (PE-07) | Rol Coleccionista; backend local; nombre `The Game <número único>` por ejecución | Catálogo, tocar "+", llenar los seis campos (fecha: aceptar la que propone el calendario, Rock, EMI), tocar "Crear álbum" | Vuelve al catálogo, aparece "Álbum creado" y el álbum nuevo está en la lista | E2E | `AlbumCreateScreenTest.createsAlbumWithValidData` | Pasa en API 37 (2026-10-29); API 21 pendiente |
| CP-HU07-02 | HU07 | Enviar el formulario vacío (PE-08) | Rol Coleccionista; formulario abierto | Tocar "Crear álbum" sin llenar nada | "Campo obligatorio" bajo nombre, URL, fecha y descripción; "Selecciona un género" y "Selecciona un sello"; sigue en el formulario | E2E | `AlbumCreateScreenTest.showsRequiredErrorsWhenEmpty` | Pasa en API 37 (2026-10-29); API 21 pendiente |
| CP-HU07-03 | HU07 | Nombre con solo espacios y sin fecha (PE-08) | Rol Coleccionista; todo lleno, con `"   "` en el nombre y sin fecha | Tocar "Crear álbum" | Error solo en el nombre y en la fecha; los demás campos sin error y con lo escrito | E2E | `AlbumCreateScreenTest.showsErrorsForBlankNameAndMissingDate` | Pasa en API 37 (2026-10-29); API 21 pendiente |
| CP-HU07-04 | HU07 | El error desaparece al corregir | Rol Coleccionista; formulario enviado vacío | Escribir un nombre y elegir un género | Desaparecen esos dos errores; los otros cuatro siguen | E2E | `AlbumCreateScreenTest.clearsFieldErrorWhenFieldIsFilled` | Pasa en API 37 (2026-10-29); API 21 pendiente |
| CP-HU07-05 | HU07 | El visitante no puede crear álbumes | Rol Visitante | Abrir el catálogo | No aparece el botón "+" | E2E | `AlbumCreateScreenTest.hidesCreateActionForVisitor` | Pasa en API 37 (2026-10-29); API 21 pendiente |
| CP-HU07-06 | HU07 | Cancelar la creación | Rol Coleccionista; formulario con un nombre escrito | Tocar "Cancelar" y repetir con la "X" | Vuelve al catálogo sin "Álbum creado" y sin álbum nuevo | E2E | `AlbumCreateScreenTest.cancelsWithoutCreating` | Pasa en API 37 (2026-10-29); API 21 pendiente |
| CP-HU07-07 | HU07 | Sin conexión y reintento (PE-09) | Rol Coleccionista; formulario válido | Detener el contenedor de BackVynils y tocar "Crear álbum"; levantarlo y tocar de nuevo | Aparece "No se pudo crear el álbum…" y se conservan los datos; al reintentar se crea **un** álbum (catálogo y `GET /albums`) | E2E manual | — (pasos y captura en la wiki) | Pendiente |
| CP-HU07-08 | HU07 | Formulario válido | Campos con espacios al inicio y al final | `AlbumFormValidator.validate` | `Valid` con los valores sin esos espacios | Unitaria | `AlbumFormValidatorTest.valid form returns the album with surrounding spaces removed` | Pasa (2026-10-29) |
| CP-HU07-09 | HU07 | Particiones inválidas (tabla) | Formulario válido con un solo campo roto: vacío o solo espacios en nombre, URL y descripción, sin fecha, sin género, `Genre.UNKNOWN`, sin sello | `validate` por cada fila | `Invalid` con el error de solo ese campo | Unitaria | `AlbumFormValidatorTest.each invalid partition flags only its own field` | Pasa (2026-10-29) |
| CP-HU07-10 | HU07 | Envío inválido no llama al backend | Formulario vacío | `onSubmit` | Los seis errores en `uiState` y el repositorio no recibe nada | Unitaria | `AlbumCreateViewModelTest.invalid submit shows every error and does not call the repository` | Pasa (2026-10-29) |
| CP-HU07-11 | HU07 | Fallo y reintento | Repositorio que responde `DataError.Network` y luego éxito | `onSubmit` dos veces | Primero `Failed`, `isSubmitting = false` y el formulario intacto; después `Created` | Unitaria | `AlbumCreateViewModelTest.failed submit keeps the form and a retry creates the album` | Pasa (2026-10-29) |
| CP-HU07-12 | HU07 | Doble toque | Formulario válido | `onSubmit` dos veces seguidas | Una sola llamada al repositorio | Unitaria | `AlbumCreateViewModelTest.second tap while submitting sends a single request` | Pasa (2026-10-29) |
| CP-HU07-13 | HU07 | Room falla tras un POST exitoso | `FakeAlbumDao` que lanza `SQLiteException` en `upsert` | `createAlbum` | `Success` con el álbum, sin relanzar | Unitaria | `AlbumRepositoryImplTest.album created on the backend is reported even if Room fails to store it` | Pasa (2026-10-29) |
| CP-HU07-14 | HU07 | Carrera con la actualización | Un `getAlbums(forceRefresh = true)` retenido con la lista sin el álbum nuevo | `createAlbum` y luego liberar la actualización | El álbum nuevo sigue en Room (comprueba el `mutex`; sin él, la prueba falla) | Unitaria | `AlbumRepositoryImplTest.refresh that fetched the list before the POST does not delete the new album` | Pasa (2026-10-29) |
| CP-HU07-15 | HU07 | Cuerpo del POST con datos válidos | MockWebServer con `album_created.json` | `createAlbum` con un `NewAlbum` válido | `POST /albums` con exactamente los seis campos y la fecha en medianoche UTC; la respuesta 200 se mapea a `Album` | API | `AlbumServiceAdapterTest.create album posts exactly the six backend fields and maps the saved album` | Pasa (2026-10-29) |
| CP-HU07-16 | HU07 | Campo faltante | MockWebServer con 400 real (`album_create_missing_field.json`) | `createAlbum` desde el repositorio | `DataError.Http(400)` y nada en Room | API | `AlbumServiceAdapterTest.create album with a missing field becomes HTTP 400 and stores nothing` | Pasa (2026-10-29) |
| CP-HU07-17 | HU07 | Valor fuera de las listas | MockWebServer con 400 real (`album_create_invalid_genre.json`) | `createAlbum` desde el repositorio | `DataError.Http(400)` y nada en Room | API | `AlbumServiceAdapterTest.create album with a value outside the backend lists becomes HTTP 400 and stores nothing` | Pasa (2026-10-29) |
| CP-HU07-18 | HU07 | Conexión cortada | MockWebServer que corta la conexión tras recibir el POST | `createAlbum` desde el repositorio | `DataError.Network` y nada en Room | API | `AlbumServiceAdapterTest.create album with the connection cut becomes a network error and stores nothing` | Pasa (2026-10-29) |
| CP-HU07-19 | HU07 | Valores de las listas | — | Recorrer `Genre` (sin `UNKNOWN`) y `RecordLabel` | Coinciden con las listas del esquema Joi de BackVynils | API | `NewAlbumRequestMappersTest.genres and record labels offered by the form are exactly the ones the backend accepts` | Pasa (2026-10-29) |

Casos automatizados de HU02 (clase existente `AlbumDetailScreenTest`):

| Id | HU | Nombre | Precondición | Pasos | Resultado esperado | Tipo | Automatizado (clase.método) | Estado |
|---|---|---|---|---|---|---|---|---|
| CP-HU02-01 | HU02 | Detalle y regreso con cabecera | Backend con semilla; rol Coleccionista | Tocar Buscando América, revisar datos, canciones y comentarios; pulsar Atrás | Contenido correcto; acción de crear solo en catálogo | E2E | `AlbumDetailScreenTest.catalog_card_opens_its_detail_and_returns_without_collector_actions` | Correcto · tablet API 31 |
| CP-HU02-02 | HU02 | Visitante y Atrás del sistema | Backend con semilla | Entrar como Visitante, abrir álbum, pulsar Atrás del sistema | Vuelve al catálogo con Álbumes seleccionado y sin acción de crear | E2E | `AlbumDetailScreenTest.visitor_returns_to_catalog_with_system_back` | Correcto · tablet API 31 |
| CP-HU02-03 | HU02 | Rotación y pestaña Álbumes | Backend con semilla | Abrir álbum, cambiar orientación y tocar de nuevo Álbumes | Conserva el detalle; la pestaña vuelve al catálogo | E2E | `AlbumDetailScreenTest.rotation_preserves_detail_and_reselecting_albums_returns_to_catalog` | Correcto · tablet API 31 |
| CP-HU02-04 | HU02 | Canciones y comentarios vacíos | Poeta del pueblo sin canciones ni comentarios | Abrir el álbum y recorrer ambas secciones | Mensajes vacíos por sección, conservando el álbum | E2E | `AlbumDetailScreenTest.empty_tracks_and_comments_keep_the_album_visible` | Correcto · tablet API 31 |

### Ejecutar los casos de HU02 en la tablet

Con BackVynils disponible en el puerto 3000 y la tablet conectada por USB:

```bash
/home/kausaustralis/Android/Sdk/platform-tools/adb -d reverse tcp:3000 tcp:3000
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=co.edu.uniandes.vinilos.ui.AlbumDetailScreenTest
```

La clase contiene cuatro recorridos con datos reales del backend. Los estados
404, error de servidor y desconexión siguen cubiertos por las pruebas unitarias
del ViewModel, Repository y Service Adapter. No se requieren proxy ni argumentos
especiales para la ejecución instrumentada general.

### Casos propuestos de HU14

Diseño de referencia: [HU14-01](12-hu14-diseno-tecnico.md). La tabla registra el avance por nivel; los métodos y evidencias se detallan en las secciones de HU14-02 a HU14-05 que siguen. La correspondencia con los escenarios PE de la Wiki se asignará por comportamiento cuando su inventario se complete; no reutilizar números PE de otras historias.

| ID | Escenario y precondición | Acción y resultado esperado | Nivel previsto | Estado |
|---|---|---|---|---|
| CP-HU14-01 | Coleccionista, artista válido y catálogo con premios | Abrir Elegir premio; mostrar nombre/organización y seleccionar un premio elegible por ID; pasar de Selecciona un premio a Continuar con «nombre», sin posiciones fijas | E2E, ViewModel y API GET | JVM/API y E2E API 31 aprobados; API 21/37 pendientes |
| CP-HU14-02 | GET /prizes devuelve [] | Mostrar vacío y acceso a HU13; no permitir continuar sin premio | ViewModel, API y UI si hay datos controlables | JVM/API aprobadas; UI vacía e integración HU13 pendientes |
| CP-HU14-03 | Consulta falla y luego responde correctamente | Mostrar error; reintentar la consulta y recuperar la lista sin duplicarla | ViewModel y API | JVM/API aprobadas; error y reintento del catálogo en UI pendientes |
| CP-HU14-04 | Selector abierto y HU13 integrada | Crear premio en HU13; recibirlo seleccionado con el aviso una vez; al cancelar HU13 conservar la selección anterior | E2E y ViewModel | Contrato ViewModel aprobado; integración real HU13 pendiente |
| CP-HU14-05 | Premio elegido y fecha todavía vacía | Mostrar FECHA (OBLIGATORIA), sin Omitir; Asignar premio deshabilitado. Solicitar envío sin fecha no llama al Repository; elegir día habilita el envío sin sustituirlo por hoy | ViewModel, mapper y E2E | JVM/API y E2E API 31 aprobados; API 21/37 pendientes |
| CP-HU14-06 | Selección o fecha ya introducidas | X/Atrás de la hoja la cierra y conserva el selector; X del selector vuelve al detalle sin POST. No existe acción Omitir | E2E y ViewModel | X y conservación de entradas aprobados en API 31; Atrás de la hoja pendiente |
| CP-HU14-07 | Músico y premio existentes de pruebas | Elegir premio y fecha en la hoja; POST a musicians con premiationDate; conservar día UTC/GMT-5, actualizar contador y fila y mostrar Premio asignado al artista una vez | E2E, Repository, mapper y API | JVM/API y E2E API 31 aprobados; API 21/37 pendientes |
| CP-HU14-08 | Banda y premio existentes de pruebas | Mismo recorrido; POST a bands, sin alterar otro artista ni confundir IDs de premio y asignación | E2E, Repository y API | JVM/API y E2E API 31 aprobados; API 21/37 pendientes |
| CP-HU14-09 | Rol Visitante o cambio de rol durante el flujo | Sin acceso de asignación; rechazar envío si dejó de ser Coleccionista; no crear escritura | E2E y ViewModel | ViewModel y acceso Visitante en HU04 aprobados; cambio de rol en UI del selector pendiente |
| CP-HU14-10 | Artista, tipo o premio inválido en argumentos | Salida segura o error de contrato; no invocar la red con IDs no positivos ni adivinar el tipo | ViewModel y Repository | ViewModel y Repository aprobados; salida con argumentos inválidos en UI pendiente |
| CP-HU14-11 | 400 por fecha inválida o 404 por recurso inexistente | Clasificar error y conservar entradas; no afirmar que la asignación fue exitosa; no asumir 400 para fecha omitida | ViewModel, Repository y API | ViewModel y API aprobados; HTTP 400/404 de escritura en UI pendientes |
| CP-HU14-12 | Timeout, desconexión, 500 o 2xx no interpretable al asignar | Conservar entradas; no repetir POST automáticamente; revalidar el resultado antes de habilitar otra escritura, informar si sigue incierto | ViewModel, Repository y API controlada | JVM/API de errores y timeout, y E2E de red API 31 aprobados; API 21/37 pendientes |
| CP-HU14-13 | Pantalla con artista, selección y fecha | Recrear/restaurar; mantener valores sin reenviar POST ni repetir resultados/snackbars consumidos | ViewModel y E2E | ViewModel y recreación E2E API 31 aprobados; API 21/37 pendientes |
| CP-HU14-14 | Asignaciones cuyos IDs difieren de prize.id y un mismo premio con distintas fechas | Resolver el nombre por ID de asignación mediante el catálogo; mantener fechas, sin N peticiones ni falsa sección vacía si falla la unión | Mapper, Repository y ViewModel | Repository/API aprobados; resolución visual API 31 comprobada con asignaciones propias |
| CP-HU14-15 | Guardado suspendido y dos pulsaciones | Una petición en curso; tras éxito volver al detalle una sola vez y conservarlo aunque falle el refresco posterior | ViewModel y E2E cuando sea controlable | Doble envío en ViewModel y retorno E2E API 31 aprobados |
| CP-HU14-16 | Catálogo cargado y búsqueda Buscar premio | Filtrar localmente por nombre sin nuevos GET; mostrar sin coincidencias y permitir limpiar; recibir premio de HU13 visible y seleccionado aunque no coincida con la búsqueda anterior | ViewModel y E2E | Búsqueda E2E API 31 y contrato HU13 en ViewModel aprobados; retorno real HU13 pendiente |
| CP-HU14-17 | Premio ya asignado al artista y otro asignado solo a un tercero | Mostrar YA ASIGNADO sin selección activa solo para el del artista actual; rechazar asignación repetida desde la UI, sin bloquear por asignaciones ajenas ni eliminar duplicados históricos | ViewModel, mapper y E2E | ViewModel y E2E API 31 aprobados; API 21/37 pendientes |

Datos: preparar artista y premio propios cuando la prueba escriba; usar IDs devueltos por el backend, nunca números fijos de la semilla. No alterar datos ajenos. Reutilizar JUnit, `MockWebServerRule`, fakes, utilidades de LiveData/corrutinas y Robots existentes. Las fallas de transporte/servidor se controlan principalmente en JVM/API; no requieren detener contenedores ni instalar un proxy externo. No presentar estas propuestas como resultados reales de la matriz Android.

**Cobertura inicial HU14-02 (03/11/2026):** `PrizeRepositoryImplTest` ejecutó 16 pruebas JVM de Repository y contrato HTTP con Retrofit/OkHttp y MockWebServer local, sin fallos ni omisiones. Cubren catálogo, catálogo vacío, revalidación, ambos endpoints de asignación, fecha UTC obligatoria, IDs inválidos, HTTP 400/404/500, respuesta perdida sin reintento automático, respuestas nulas o incoherentes y resolución por ID de asignación con una sola consulta. Complementan CP-HU14-01/02/07/08/10/11/12/14, pero no acreditan sus recorridos de interfaz ni completan HU14-04/05/06. Las respuestas JSON de `api/prizes.json` y `api/prize_assignment.json` son sintéticas, con estructura contrastada con BackVynils; sus IDs difieren deliberadamente entre premio y asignación.

Validación de esta tarea: `./gradlew spotlessCheck detekt lintDebug testDebugUnitTest --offline --console=plain`, BUILD SUCCESSFUL; 148 pruebas JVM en total, 0 fallidas, 0 errores y 0 omitidas. No se ejecutaron E2E ni se hicieron escrituras en BackVynils para esta validación de datos.

**Cobertura inicial HU14-03 (03/11/2026):** `PrizePickerViewModelTest` ejecutó 10 pruebas JVM aprobadas: búsqueda local, bloqueo de premios asignados, fecha obligatoria, tipo y fecha enviados, doble envío, errores definitivos/inciertos, retorno del premio creado, argumentos/rol inválidos y restauración de selección/fecha o escritura incierta. Complementan la lógica de CP-HU14-01/04/05/09/10/12/13/15/16/17; no acreditan los recorridos E2E ni la integración real con HU04/HU13.

Controles de HU14-03: `./gradlew spotlessCheck detekt lint testDebugUnitTest assembleDebug assembleDebugAndroidTest --offline --console=plain`, BUILD SUCCESSFUL, 158 pruebas JVM sin fallos ni omisiones. El APK de la app y el de pruebas instrumentadas compilan. En esa ejecución inicial ADB no detectó dispositivos; la validación posterior se detalla a continuación. El enlace desde el detalle de artista y su actualización quedan pendientes de HU04; HU13 solo es necesario para el recorrido de creación de un premio nuevo.

Validación posterior de HU14-03 (04/11/2026): cuatro comprobaciones instrumentadas aisladas en Huawei DBY-W09, Android 12/API 31, contra BackVynils local; 4 aprobadas en claro y los mismos 4 casos aprobados en oscuro, sin fallos ni omisiones. Cubren búsqueda/sin coincidencias y premio ya asignado; fecha obligatoria, recreación y cancelación sin escritura; POST a músico; POST a banda. Se contrastaron la fecha UTC, IDs y tipo devueltos con GET del artista, y se revisaron capturas reales. Aportan cobertura parcial de CP-HU14-01/04/05/07/08/13/15/16/17. La entrada se invocó desde el código temporal de prueba porque falta HU04: no valida contador, fila ni snackbar del detalle del artista, ni el retorno de HU13. El código de estas comprobaciones se archivó con sus evidencias para reutilizarlo en HU14-05. Los casos del inventario siguen propuestos hasta completar sus recorridos y resultados esperados.

Los fixtures de esa validación eran exclusivos y se limpiaron por IDs y nombres verificados en SQL. El DELETE de asociación de BackVynils eliminó una asociación incorrecta durante la primera limpieza; se restauró exactamente el registro original y se verificaron las asociaciones previas. Ese endpoint no debe usarse para limpiar fixtures. HU14-05 debe preparar las secuencias de prize, performer y performer_prize y resolver una limpieza segura en el backend local de pruebas; no se considera cerrado por estas comprobaciones.

**Integración HU14-03 con HU04 · 04/11/2026:** se amplía la cobertura inicial, sin dar por completados los escenarios mixtos del inventario:

| Casos asociados | Prueba ampliada o nueva | Resultado y límite |
|---|---|---|
| CP-HU14-01/09/14/17 | `PerformerDetailViewModelTest.prizes are visible to visitors but only collectors can open assignment` | Aprobada en JVM: premios consultables en ambos roles y apertura solo de Coleccionista con referencias de asociación |
| CP-HU14-07/08/13/15 | `PerformerDetailViewModelTest.confirmed assignment updates detail once and rejects another artist result` | Aprobada en JVM: resultado del mismo artista/tipo, contador, referencia, resaltado y aviso sin duplicación; no acredita POST desde la interfaz |
| CP-HU14-03/12 | `PerformerDetailViewModelTest.prize failure preserves biography and blocks assignment until forced revalidation succeeds` | Aprobada en JVM: error de premios conserva el detalle; reintento fuerza detalle y catálogo antes de otra asignación |
| CP-HU14-14 | Contratos de músico/banda e `invalid prize references fail instead of silently showing an empty section` en `PerformerDetailServiceAdapterTest` | Repository/API aprobados; resolución visual API 31 comprobada con asignaciones propias |
| CP-HU14-06/09/16 | Recorridos de banda y selector de rol de `PerformerDetailScreenTest` | Aprobados en tablet API 31: entrada real a HU14 desde ambos tipos, búsqueda sin coincidencias, cancelación y acción oculta a Visitante; no guardado de premios |

Controles finales: 254 pruebas JVM aprobadas, sin fallos, errores u omisiones; Spotless, detekt y Lint debug correctos. Primera ejecución instrumentada integrada: 6 recorridos aprobados de `PerformerDetailScreenTest`, 0 fallos y omisiones, con BackVynils local y tablet Huawei DBY-W09. Se reutilizan pruebas de HU04; no son seis pruebas nuevas de asignación. El POST completo y retorno visual desde la entrada real, errores controlados de escritura, HU13, API 21/37 y evidencias visuales nuevas siguen pendientes de HU14-04/05/06. Ver [integración HU14-03](12-hu14-diseno-tecnico.md#12-integración-de-hu14-03-con-hu04--04112026).

**HU14-04 · 04/11/2026:** `PrizePickerViewModelTest` pasa de 10 a 13 pruebas. Se amplían las existentes y se agregan tres, reutilizando los mismos fakes y reglas. Esta tabla actualiza únicamente la cobertura JVM; no acredita los recorridos E2E pendientes.

| Casos | Método de `PrizePickerViewModelTest` | Resultado comprobado |
|---|---|---|
| CP-HU14-02/03 | `empty catalog and failed loads recover without duplicating or losing valid entries` | Vacío sin selección; carga concurrente única; error y recuperación; refresco fallido conserva lista, selección y fecha |
| CP-HU14-05/13 | `changing the selected prize clears its date and rejects an unknown prize` | Otro premio exige otra fecha; ID ajeno no cambia selección; restauración conserva el nuevo premio sin fecha; no POST inválido |
| CP-HU14-09 | `changing role before saving returns to the selector and never writes` | ViewModel y acceso Visitante en HU04 aprobados; cambio de rol en UI del selector pendiente |
| CP-HU14-07/08/15 | `success sends the correct type and date only once while saving` | Ampliada a músico y banda; un envío en curso, fecha correcta y resultado consumido una vez |
| CP-HU14-11 | `definite validation failure preserves the date and permits retry` | ViewModel y API aprobados; HTTP 400/404 de escritura en UI pendientes |
| CP-HU14-12 | `uncertain write preserves entries and cannot repeat the POST` | JVM/API de errores y timeout, y E2E de red API 31 aprobados; API 21/37 pendientes |
| CP-HU14-10/17 | `visitor and invalid artist do not load prizes`; `search is local and already assigned prizes cannot be selected` | Argumentos ausentes/ID/tipo/nombre inválidos sin consultas ni envíos; asociación de otro artista no bloquea el premio actual |

Ejecución dirigida: **41 pruebas JVM aprobadas**, sin fallos, errores u omisiones: 13 del selector, 12 de `PerformerDetailViewModelTest` y 16 de `PrizeRepositoryImplTest` (incluye contrato HTTP). No son 41 pruebas nuevas de HU14. Spotless y detekt correctos. No hubo cambios productivos ni ejecuciones en Android en esta tarea. Reporte: `app/build/reports/tests/testDebugUnitTest/index.html`.

**HU14-05 · E2E integradas con HU04:** `PrizePickerScreenTest` reutiliza los recorridos temporales de HU14-03, ahora entrando por Artistas → detalle → Agregar premio. Los IDs y nombres son propios de cada ejecución; no se usan posiciones ni conteos globales.

| Casos asociados | Método | Comprobación |
|---|---|---|
| CP-HU14-01/07/15 | `assigns_prize_to_musician_and_updates_its_detail` | Selección y fecha obligatoria; POST real; retorno con contador, nombre, organización, fecha y resaltado; aviso de éxito; recrear no duplica la asociación |
| CP-HU14-01/08/15 | `assigns_prize_to_band_and_updates_its_detail` | Mismo recorrido para banda; fecha y única asociación contrastadas con GET del artista |
| CP-HU14-16/17 | `search_blocks_own_assignments_but_allows_a_prize_assigned_to_another_artist` | Busca por nombre, muestra sin coincidencias y YA ASIGNADO; bloquea el premio propio y permite seleccionar otro asignado solo a un tercero; cancelar no escribe |
| CP-HU14-05/06/13 | `required_date_and_selection_survive_recreation_and_cancel_does_not_write` | Guardar deshabilitado sin fecha; recreación conserva premio/fecha; cerrar y reabrir la hoja conserva entradas; cancelar vuelve al detalle sin POST |
| CP-HU14-12 (red) | `network_failure_requires_review_before_a_new_assignment_and_then_recovers` | BackendFirewall interrumpe el envío; estado incierto sin otro POST; revisar vuelve al detalle revalidado; nueva asignación explícita recupera el flujo y guarda una sola asociación |

Validación del 04/11/2026: **5 E2E aprobadas en claro y las mismas 5 repetidas en oscuro**, sin fallos, errores ni omisiones, en Huawei DBY-W09, Android 12/API 31 y BackVynils local. Regresión: 6 recorridos de HU04 aprobados. Controles: Spotless, detekt, lintDebug y 257 pruebas JVM en verde. Los XML se conservaron por ejecución en `/tmp/HU14-05-evidencias`, junto con capturas reales.

Las capturas del aviso de éxito y del detalle estable se guardan por separado. Las pruebas esperan contenido visible y opacidad completa de la fila; no añaden pausas fijas. `PerformerDetailRobot` comparte con HU04 el desplazamiento por contenido, incluida una fila o cabecera mayor que la pantalla. HU13, errores HTTP de escritura controlados desde UI, API 21/37 y accesibilidad completa permanecen pendientes; los casos mixtos no se consideran totalmente aprobados solo por estas E2E.

**HU14-06 · API · 04/11/2026:** `PrizeRepositoryImplTest` conserva la cobertura previa y pasa de 16 a **20 pruebas aprobadas**: cuatro nuevas y ampliaciones de las existentes. Se prueban Repository, Adapter, Retrofit y OkHttp reales con MockWebServer; no se consulta ni modifica BackVynils.

| Casos asociados | Método | Resultado verificado |
|---|---|---|
| CP-HU14-01/03 | `forced catalog refresh revalidates HTTP` | Caché HTTP fresca con max-age; revalidación no-cache devuelve el nombre actualizado, con dos GET |
| CP-HU14-03/12 | `catalog HTTP and transport failures recover only after an explicit refresh` | 404/500, desconexión y timeout se clasifican; una consulta explícita recupera el catálogo; cliente nuevo por escenario para no reutilizar conexiones al simular DISCONNECT_AT_START |
| CP-HU14-07/08/12/15 | `assignment timeout is a network error and never resends the POST` | Timeout controlado en ambas rutas; una única petición POST por envío y DataError.Network |
| CP-HU14-07/08/14 | `assignment and catalog preserve the UTC day under Bogota timezone` | Fecha obligatoria de solicitud, respuesta y catálogo conserva el día UTC bajo America/Bogota; se restaura la zona original |
| CP-HU14-14 | `a repeated association ID across different prizes is rejected as ambiguous` | No se devuelve un premio arbitrario cuando dos premios contienen el mismo ID de asociación |
| CP-HU14-10/11/12 | Casos ampliados de IDs, errores HTTP y respuestas inválidas de la misma clase | IDs no positivos sin inicializar HTTP; 400/404/500 conservados en ambos endpoints; cuerpo vacío/nulo/JSON inválido/204 o identidad/fecha ausente no confirman la escritura |

Controles `spotlessCheck detekt lintDebug testDebugUnitTest` con JDK 17: **261 pruebas JVM aprobadas**, sin fallos, errores ni omisiones, verificadas en XML. No se añadieron dependencias, utilidades, fixtures ni código productivo. Las E2E no se repitieron en esta tarea; sus resultados y pendientes corresponden a HU14-05. Reporte local: `app/build/reports/tests/testDebugUnitTest/index.html`.

### Casos de HU08

Cada caso crea su propio álbum con `POST /albums` y un nombre único. No usa la semilla ni un conteo fijo de canciones. La duración se ve con minutos de dos dígitos en la hoja (`04:15`) y sin el cero a la izquierda en la fila (`4:15`).

| Id | HU | Nombre | Precondición | Pasos | Resultado esperado | Tipo | Automatizado (clase.método) | Estado |
|---|---|---|---|---|---|---|---|---|
| CP-HU08-01 | HU08 | Asociar una canción a un álbum nuevo (PE-10) | Coleccionista; álbum propio sin canciones | Abrir la hoja, escribir "Todos vuelven", marcar 04:15 y guardar | Subtítulo "· canción 01"; "Canción agregada al álbum"; ficha "1"; fila "01 Todos vuelven 4:15" | E2E | `TrackCreateScreenTest.createsTrackOnANewAlbum` | Pasa en API 37 (2026-10-30); API 21 pendiente |
| CP-HU08-02 | HU08 | Valores límite de la duración (PE-11) | Coleccionista; álbum propio; nombre válido | Guardar en 00:00; marcar 00:01, limpiar, 59:59, limpiar, 05:60 y 60:00 | Error de formato en 00:00, 05:60 y 60:00; desaparece en 00:01 y 59:59; no se guarda ninguna canción | E2E | `TrackCreateScreenTest.rejectsDurationBoundariesWithoutSaving` | Pasa en API 37 (2026-10-30); API 21 pendiente |
| CP-HU08-03 | HU08 | Perder la conexión y reintentar (PE-12) | Coleccionista; álbum propio; hoja con nombre y duración | Cortar la red del dispositivo, guardar, restaurarla y guardar de nuevo | "No se pudo conectar. Revisa tu conexión e inténtalo de nuevo."; la hoja conserva los datos; al reintentar queda una sola canción y la ficha pasa a "1" | E2E | `TrackCreateScreenTest.retriesTrackSaveAfterTheNetworkReturns` | Pasa en API 37 (2026-10-30); API 21 pendiente |
| CP-HU08-04 | HU08 | Nombre vacío o en blanco (PE-13) | Coleccionista; álbum propio; duración 03:20 | Guardar sin nombre y luego con solo espacios | "Campo obligatorio"; la hoja sigue abierta; la ficha no cambia | E2E | `TrackCreateScreenTest.rejectsAMissingOrBlankTrackName` | Pasa en API 37 (2026-10-30); API 21 pendiente |
| CP-HU08-05 | HU08 | Cancelar y cerrar (PE-14) | Coleccionista; álbum propio | Escribir "Canción descartada", tocar "Cancelar" y repetir con "Cerrar" | La hoja se cierra; no aparece "Canción agregada al álbum" ni la canción; la ficha sigue en "0" | E2E | `TrackCreateScreenTest.cancelAndCloseDoNotCreateATrack` | Pasa en API 37 (2026-10-30); API 21 pendiente |
| CP-HU08-06 | HU08 | La canción queda solo en su álbum (PE-15) | Coleccionista; dos álbumes propios | En A escribir "  Campesino  " y 02:10, salir y volver; abrir B | A muestra "01 Campesino 2:10" al volver a entrar; B sigue en "0" y sin "Campesino" | E2E | `TrackCreateScreenTest.createdTrackStaysOnlyOnItsAlbum` | Pasa en API 37 (2026-10-30); API 21 pendiente |
| CP-HU08-07 | HU08 | Teclado de derecha a izquierda (PE-16) | Coleccionista; álbum propio; nombre válido | Marcar 1 2 3 4 5, borrar, limpiar y guardar | "23:45", luego "02:34", luego "00:00"; al guardar, error de formato y la ficha no cambia | E2E | `TrackCreateScreenTest.keypadEntersFromTheRightAndClears` | Pasa en API 37 (2026-10-30); API 21 pendiente |
| CP-HU08-08 | HU08 | Particiones inválidas (tabla) | Nombre vacío o solo espacios; duración `00:00`, `00:60` o `60:00`; extremos `00:01` y `59:59` | `onSave` y luego cada valor | Error solo en el campo roto; `00:01` y `59:59` quedan válidos | Unitaria | `TrackCreateViewModelTest.a blank name is required and a written name clears the error`; `TrackCreateViewModelTest.zero sixty minutes and sixty seconds fail and the ends of the range pass` | Pasa (2026-10-30) |
| CP-HU08-09 | HU08 | Envío inválido no llama al backend | Hoja sin nombre y en `00:00` | `onSave` | Los dos errores en `uiState` y el repositorio no recibe nada | Unitaria | `TrackCreateViewModelTest.errors stay hidden until save` | Pasa (2026-10-30) |
| CP-HU08-10 | HU08 | Borrar una duración válida | Guardado válido y luego borrar los dígitos | `onDeleteDigit` hasta `00:00` | Vuelve el error de duración | Unitaria | `TrackCreateViewModelTest.deleting a valid duration brings the error back` | Pasa (2026-10-30) |
| CP-HU08-11 | HU08 | Dígito fuera del teclado | Hoja en `00:00` | `onDigit` con `-1` y con `10` | La duración no cambia | Unitaria | `TrackCreateViewModelTest.a digit outside the keypad is ignored` | Pasa (2026-10-30) |
| CP-HU08-12 | HU08 | Guardado válido recorta el nombre | Nombre con espacios alrededor y duración `05:55` | `onSave` | Una sola petición al álbum actual, con el nombre recortado y la duración `5:55`; emite la canción creada | Unitaria | `TrackCreateViewModelTest.valid save sends a trimmed draft to the current album and emits the created track` | Pasa (2026-10-30) |
| CP-HU08-13 | HU08 | Fallo de red conserva el formulario | Repositorio que responde `DataError.Network` | `onSave` | `isSubmitting = false`, el formulario intacto y el error de red | Unitaria | `TrackCreateViewModelTest.failed save preserves the form and exposes a retryable error` | Pasa (2026-10-30) |
| CP-HU08-14 | HU08 | Doble toque | Formulario válido | `onSave` dos veces seguidas | Una sola llamada al repositorio | Unitaria | `TrackCreateViewModelTest.two save taps while submitting make one repository request` | Pasa (2026-10-30) |
| CP-HU08-15 | HU08 | La canción se agrega sin otra consulta | Detalle ya cargado | `onTrackCreated` | La fila se suma al álbum en pantalla y no hay un segundo `getAlbum` | Unitaria | `AlbumDetailViewModelTest.created track is appended to the currently displayed album without another request` | Pasa (2026-10-30) |
| CP-HU08-16 | HU08 | Room falla tras un POST exitoso | `FakeTrackDao` que lanza `SQLiteException` al escribir | `addTrack` | `Success` con la canción, sin relanzar | Unitaria | `AlbumRepositoryImplTest.track remains successful when Room cannot cache a backend success` | Pasa (2026-10-30) |
| CP-HU08-17 | HU08 | Conexión cortada no escribe en Room | Adaptador que lanza `IOException` | `addTrack` | `DataError.Network` y Room sin escrituras | Unitaria | `AlbumRepositoryImplTest.track network failure is returned and leaves Room untouched` | Pasa (2026-10-30) |
| CP-HU08-18 | HU08 | Id de álbum inválido | Id `0` | `addTrack` | `IllegalArgumentException` y el adaptador no recibe la petición | Unitaria | `AlbumRepositoryImplTest.invalid track album id does not reach the adapter` | Pasa (2026-10-30) |
| CP-HU08-19 | HU08 | La caché queda en el álbum pedido | Adaptador que devuelve la canción creada | `addTrack(100, …)` | Room guarda la fila con `albumId` 100 y el álbum 101 sigue vacío | Unitaria | `AlbumRepositoryImplTest.created track is cached with the requested album id` | Pasa (2026-10-30) |
| CP-HU08-20 | HU08 | Cuerpo del POST con datos válidos | MockWebServer con `track_100_created.json` | `addTrack` | `POST /albums/100/tracks` con exactamente `name` y `duration`; la respuesta 201 se mapea a `Track` | API | `AlbumServiceAdapterTest.add track posts only name and duration and maps the response` | Pasa (2026-10-30) |
| CP-HU08-21 | HU08 | HTTP 400 | MockWebServer con 400 | `addTrack` desde la capa de red | `DataError.Http(400)` | API | `AlbumServiceAdapterTest.add track maps HTTP failures through network request` | Pasa (2026-10-30) |
| CP-HU08-22 | HU08 | Respuesta de otro álbum | MockWebServer con una canción de otro álbum | `addTrack` | `DataError.Serialization` | API | `AlbumServiceAdapterTest.add track rejects a response for a different album` | Pasa (2026-10-30) |
| CP-HU08-23 | HU08 | Respuesta sin duración | MockWebServer con JSON sin `duration` | `addTrack` | `DataError.Serialization` | API | `AlbumServiceAdapterTest.add track response without duration is a serialization error` | Pasa (2026-10-30) |

PE-12 usa el fallo de transporte controlado de `BackendFirewall`, descrito en la sección 4. La aplicación sigue usando el Repository, Room y el backend real; solo el primer intento de guardar falla por conexión. Se comprueba el mensaje de red, la conservación del nombre y duración, y el guardado tras restaurar la ruta. No se detiene el contenedor: ese corte sigue siendo el caso manual CP-HU07-07. La UI espera con `waitForDisplayed`; la regla ya no usa `Thread.sleep` ni modifica ajustes de red. `NetworkClient.httpClient` tiene visibilidad `internal` para descartar conexiones ociosas durante esta prueba; no cambia la configuración normal del cliente.

El 2026-10-30 `TrackCreateScreenTest` pasó en el emulador Pixel 9 Pro (API 37, Android 17): 7 pruebas, 0 fallos, 49 s. API 21 queda pendiente, igual que el resto de la matriz E2E. Las unitarias y de API de la tabla pasaron el mismo día (`testDebugUnitTest`, 0 fallos).

### Casos de HU03

Los casos E2E crean sus músicos, bandas y álbumes con `POST` (`testing/BackendPerformers.kt`), con nombres únicos, y los borran al terminar; no dependen de cuántos artistas haya además de los de la semilla (Rubén Blades Bellido de Luna y Queen). `scripts/preparar_backend_e2e.sql` también adelanta la secuencia de `performer`, que la semilla deja en 1 con los ids 100 y 101 ya usados.

| Id | HU | Nombre | Precondición | Pasos | Resultado esperado | Tipo | Automatizado (clase.método) | Estado |
|---|---|---|---|---|---|---|---|---|
| CP-HU03-01 | HU03 | Listar artistas como visitante | Backend local con la semilla; la prueba crea una banda con un integrante y dos álbumes, y un músico solo con un álbum | Elegir "Visitante" y abrir la pestaña Artistas | Músicos y bandas por nombre, con "Todos" elegido; sobrelíneas "Banda · desde 1991" y "Músico · 1960", la fila de Rubén Blades con "Músico · 1948"; sin botón "+" | E2E | `PerformerListScreenTest.visitor_sees_musicians_and_bands_by_name_without_the_create_button` | Pasa en API 21 y API 37 (2026-11-03) |
| CP-HU03-02 | HU03 | Listar artistas como coleccionista | Igual que CP-HU03-01 | Elegir "Coleccionista" y abrir la pestaña Artistas | La misma lista; sin botón "+" | E2E | `PerformerListScreenTest.collector_sees_the_same_list_and_no_create_button` | Pasa en API 21 y API 37 (2026-11-03) |
| CP-HU03-03 | HU03 | Tercera línea de cada fila | La prueba crea una banda de dos integrantes y dos álbumes, otra de un integrante y un álbum, y un músico con dos álbumes y sin banda | Abrir la pestaña Artistas | "2 álbumes · 2 integrantes", "1 álbum · 1 integrante", "Integrante de <banda>" y "2 álbumes" (singular y plural correctos) | E2E | `PerformerListScreenTest.third_line_shows_counts_for_bands_the_band_for_members_and_albums_for_the_rest` | Pasa en API 21 y API 37 (2026-11-03) |
| CP-HU03-04 | HU03 | Filtro Músicos | La prueba crea una banda con un integrante y un músico solo | Tocar "Músicos" | Solo músicos, ninguna banda (ni Queen), por nombre, con "Músicos" elegido | E2E | `PerformerListScreenTest.musicians_filter_shows_only_musicians` | Pasa en API 21 y API 37 (2026-11-03) |
| CP-HU03-05 | HU03 | Filtro Bandas | Igual que CP-HU03-04 | Tocar "Bandas" | Solo bandas (la creada y Queen), ningún músico, por nombre | E2E | `PerformerListScreenTest.bands_filter_shows_only_bands` | Pasa en API 21 y API 37 (2026-11-03) |
| CP-HU03-06 | HU03 | Volver a Todos | Filtro "Bandas" elegido | Tocar "Todos" | Vuelven los músicos y las bandas | E2E | `PerformerListScreenTest.todos_filter_brings_back_musicians_and_bands` | Pasa en API 21 y API 37 (2026-11-03) |
| CP-HU03-07 | HU03 | Conservar el filtro al recrear la pantalla | Filtro "Músicos" elegido | Recrear la actividad (`ActivityScenario.recreate()`, el mismo cambio de configuración que una rotación) | "Músicos" sigue elegido y la lista solo tiene músicos | E2E | `PerformerListScreenTest.recreating_the_screen_keeps_the_selected_filter` | Pasa en API 21 y API 37 (2026-11-03) |
| CP-HU03-08 | HU03 | Error sin artistas guardados y reintento | Room sin artistas (la prueba lo vacía); la prueba corta el transporte del backend con `BackendFirewall` | Abrir la pestaña Artistas, restaurar el transporte y tocar "Reintentar" | "No se pudieron cargar los artistas" con "Reintentar"; después aparece la lista | E2E | `PerformerListScreenTest.failing_load_without_saved_artists_shows_the_error_and_retry_loads_the_list` | Pasa en API 21 y API 37 (2026-11-03) |
| CP-HU03-09 | HU03 | Sin conexión con la lista guardada | Lista abierta con conexión; la prueba corta el transporte | Deslizar para actualizar | Las filas siguen y aparece "Sin conexión. Mostrando los datos guardados" | E2E y unitaria | `PerformerListScreenTest.refreshing_without_connection_keeps_the_list_and_shows_the_offline_notice`; `PerformerListViewModelTest.failed revalidation with a list keeps it and emits the network notice` | Pasa en API 21 y API 37 (2026-11-03) |
| CP-HU03-10 | HU03 | Deslizar para refrescar | Lista abierta; la prueba crea un músico en el backend después de abrirla | Deslizar la lista hacia abajo | El músico nuevo aparece con "Músico · 1985" | E2E | `PerformerListScreenTest.pull_to_refresh_shows_a_performer_added_on_the_backend` | Pasa en API 21 y API 37 (2026-11-03) |
| CP-HU03-11 | HU03 | Tocar una fila abre el detalle | La prueba crea una banda | Tocar su fila | Se abre `performerDetailFragment` (hoy la pantalla provisional) con el id de la banda y el tipo `BAND` | E2E | `PerformerListScreenTest.tapping_a_row_opens_the_detail_with_its_id_and_type` | Pasa en API 21 y API 37 (2026-11-03) |
| CP-HU03-12 | HU03 | Carga sin caché | Repositorio falso sin caché que responde después | Abrir la lista | `isLoading` mientras llega la respuesta y después la lista | Unitaria | `PerformerListViewModelTest.without cache it shows loading and then the list` | Pasa (2026-11-03) |
| CP-HU03-13 | HU03 | Lista vacía | Backend con dos listas vacías | Abrir la lista | Estado vacío ("Aún no hay artistas") | Unitaria y API | `PerformerListViewModelTest.an empty list is the empty state`; `PerformerServiceAdapterTest.two empty lists are an empty result`; `PerformerRepositoryImplTest.an empty backend answer empties Room` | Pasa (2026-11-03) |
| CP-HU03-14 | HU03 | Error del backend sin caché | Backend que responde 500 y Room vacío | Abrir la lista | `DataError.Http(500)` y estado de error con "Reintentar" | Unitaria | `PerformerListViewModelTest.failure without a list sets the error and it is not the empty state`; `PerformerRepositoryImplTest.HTTP and serialization failures become data errors and do not touch Room` | Pasa (2026-11-03) |
| CP-HU03-15 | HU03 | Reintentar tras un error | Primera respuesta con error y segunda con la lista | Tocar "Reintentar" | Se muestra la lista y desaparece el error | Unitaria | `PerformerListViewModelTest.refresh after an error loads the list` | Pasa (2026-11-03) |
| CP-HU03-16 | HU03 | Mostrar lo guardado y revalidar | Room con artistas | Abrir la lista | La lista sin indicador de carga y una sola revalidación | Unitaria | `PerformerListViewModelTest.with cache it shows the list without loading and revalidates once`; `PerformerRepositoryImplTest.filled Room without force refresh does not call the adapter` | Pasa (2026-11-03) |
| CP-HU03-17 | HU03 | Fallo parcial: una de las dos llamadas falla | `GET /musicians` responde 200 y `GET /bands` 500 (o al revés, o se corta la conexión) | `getPerformers()` | Toda la carga falla con el código HTTP o `DataError.Network`; Room no se toca y no se guarda ni se muestra una lista a medias | API y unitaria | `PerformerServiceAdapterTest.a failing bands call fails the whole load with its HTTP code`; `PerformerServiceAdapterTest.a failing musicians call fails the whole load with its HTTP code`; `PerformerServiceAdapterTest.a connection cut on one call is a network error for the whole load`; `PerformerServiceAdapterTest.a partial failure through the repository never touches Room`; `PerformerRepositoryImplTest.a failed load with a cache returns the failure and leaves Room intact` | Pasa (2026-11-03) |
| CP-HU03-18 | HU03 | Unión de las dos listas y pertenencia a banda | Respuestas reales de `GET /musicians` y `GET /bands` con Queen y cuatro integrantes | `getPerformers()` | Cinco músicos y una banda con tipo; los cuatro integrantes con `bandId` 101 y "Queen", Rubén sin banda; `GET /musicians` no trae la banda aunque el músico esté asociado | API y unitaria | `PerformerServiceAdapterTest.members of a band are joined from the band list, not from the musician list`; `PerformerMappersTest.musicians listed by a band belong to it and the others do not`; `PerformerMappersTest.a musician listed by two bands keeps the first band received` | Pasa (2026-11-03) |
| CP-HU03-19 | HU03 | El filtro no hace nuevas llamadas | Lista cargada con músicos y bandas | Elegir Músicos, Bandas y Todos | Cada filtro muestra solo su tipo y el repositorio no recibe llamadas nuevas; elegir el mismo filtro no publica estado; el filtro sobrevive a un refresco y a un observador nuevo | Unitaria | `PerformerListViewModelTest.Musicos shows only musicians and Bandas only bands, without new repository calls`; `PerformerListViewModelTest.the filter survives a refresh and a new observer, as after a rotation`; `PerformerListViewModelTest.selecting the filter already selected does not publish a new state`; `PerformerListViewModelTest.a filter without artists is empty but the loaded list is still there` | Pasa (2026-11-03) |
| CP-HU03-20 | HU03 | Orden por nombre | Artistas con mayúsculas y nombres iguales | `getPerformers()` | Por nombre sin distinguir mayúsculas ASCII y, a igual nombre, por id; en el repositorio y en Room | Unitaria e instrumentada | `PerformerRepositoryImplTest.the order ignores case and puts equal names in id order`; `PerformerDaoTest.get_all_returns_performers_by_name_ignoring_case_with_ties_by_id` | Unitaria pasa (2026-11-03); instrumentada pasa en API 21 y API 37 (2026-11-03) |
| CP-HU03-21 | HU03 | Año en UTC | Fechas `1970-01-01T05:00:00.000Z` y `1948-01-01T00:00:00.000Z` con la zona America/Bogota | Mapear | Los años son 1970 y 1948 | Unitaria | `PerformerMappersTest.year is taken in UTC regardless of the device time zone` | Pasa (2026-11-03) |
| CP-HU03-22 | HU03 | Tercera línea como dato | Banda, integrante y músico sin banda | Cargar la lista | `BandCounts`, `MemberOf` y `AlbumCount`; el adaptador escribe los singulares (CP-HU03-03) | Unitaria | `PerformerListViewModelTest.a band shows its albums and members, a member its band, a musician without band its albums`; `PerformerListViewModelTest.a band with one album and one member keeps the singular counts for the adapter` | Pasa (2026-11-03) |
| CP-HU03-23 | HU03 | Respuestas con forma inválida | JSON malformado; músico o banda sin nombre, id o fecha; fecha inválida; id repetido entre las dos listas | `getPerformers()` | `DataError.Serialization` | API y unitaria | `PerformerServiceAdapterTest.malformed JSON in one call is a serialization error`; `PerformerServiceAdapterTest.a musician without a name is a serialization error`; `PerformerMappersTest.a musician without a required field is a serialization error`; `PerformerMappersTest.a band without a required field is a serialization error`; `PerformerMappersTest.an id shared by a musician and a band is rejected instead of overwriting one of them` | Pasa (2026-11-03) |
| CP-HU03-24 | HU03 | Contrato de `GET /musicians` y de `GET /bands` | MockWebServer con las respuestas reales de la semilla | `getMusicians()`, `getBands()` y `getPerformers()` | `GET /musicians` y `GET /bands`; `id`, `name`, `image`, `birthDate` o `creationDate`, cantidad de `albums` y `musicians[].id` | API | `PerformerServiceAdapterTest.GET musicians deserializes the seed musician`; `PerformerServiceAdapterTest.GET bands deserializes the seed band with its members`; `PerformerServiceAdapterTest.both endpoints are called with GET and the seed becomes a musician and a band` | Pasa (2026-11-03) |
| CP-HU03-25 | HU03 | Migración de la base 2 → 3 | Base versión 2 con un álbum y una canción | Migrar con `MIGRATION_2_3` y abrir con Room sin migración destructiva | El álbum y la canción siguen; la tabla `performers` existe y está vacía | Instrumentada | `VinilosMigrationTest.migrating_from_2_to_3_keeps_albums_and_tracks_and_creates_an_empty_performers_table`; `VinilosMigrationTest.room_opens_the_migrated_file_without_recreating_it_and_the_new_dao_works` | Pasa en API 21 y API 37 (2026-11-03) |

### Casos de HU05

Las E2E corren contra BackVynils local (`vinilos.baseUrl=http://10.0.2.2:3000/`). Solo crean coleccionistas sin álbumes ni favoritos con `POST /collectors` (`testing/BackendCollectors.kt`), con nombres únicos, y los borran con `DELETE /collectors/:id`. No crean vínculos de álbumes porque `DELETE /collectors/:id/albums/:albumId` borra el vínculo de id más bajo, que puede ser de la semilla (`10-diseno-de-la-aplicacion.md`, hallazgo 17). Las E2E no se basan en datos concretos: los demás coleccionistas se contrastan con lo que responde `GET /collectors` en ese momento, sin fijar nombres, correos, ids ni conteos, y los textos de singular y plural se comprueban con los recursos reales en `CollectorAdapterTest`, sin backend. `scripts/preparar_backend_e2e.sql` no adelanta la secuencia de `collector`: en una base recién cargada, `POST /collectors` chocará con los ids 100 y 101 de la semilla tras unas 14 corridas completas (cada una crea 7 coleccionistas), y luego sigue en el 102.

#### Pruebas unitarias (HU05-04)

| Id | HU | Nombre | Precondición | Pasos | Resultado esperado | Tipo | Automatizado (clase.método) | Estado |
|---|---|---|---|---|---|---|---|---|
| CP-HU05-01 | HU05 | Carga sin caché | Repositorio falso sin caché | Abrir la lista | `isLoading` y luego la lista, sin ir a la red dos veces | Unitaria | `CollectorListViewModelTest.without cache it shows loading and then the list` | Pasa (2026-11-04) |
| CP-HU05-02 | HU05 | Mostrar lo guardado y revalidar | Room con coleccionistas | Abrir la lista | La lista sin indicador de carga y una sola revalidación | Unitaria | `CollectorListViewModelTest.with cache it shows the list without loading and revalidates once` | Pasa (2026-11-04) |
| CP-HU05-03 | HU05 | Lista vacía | Repositorio con lista vacía | Abrir la lista | Estado vacío, sin error | Unitaria | `CollectorListViewModelTest.an empty list is the empty state` | Pasa (2026-11-04) |
| CP-HU05-04 | HU05 | Error sin lista | Repositorio con `DataError.Http(500)` | Abrir la lista | Estado de error; no es el estado vacío | Unitaria | `CollectorListViewModelTest.failure without a list sets the error and it is not the empty state` | Pasa (2026-11-04) |
| CP-HU05-05 | HU05 | Reintentar tras un error | Primera respuesta con error y segunda con la lista | Reintentar | Se muestra la lista y desaparece el error | Unitaria | `CollectorListViewModelTest.refresh after an error loads the list` | Pasa (2026-11-04) |
| CP-HU05-06 | HU05 | Falla al refrescar con lista | Caché con datos y revalidación sin red | Abrir la lista | Conserva la lista y emite el aviso sin conexión | Unitaria | `CollectorListViewModelTest.failed revalidation with a list keeps it and emits the network notice` | Pasa (2026-11-04) |
| CP-HU05-07 | HU05 | Refrescar durante otra carga | Una revalidación en curso | Refrescar tres veces | Una sola llamada al backend | Unitaria | `CollectorListViewModelTest.refresh is ignored while a load is in progress` | Pasa (2026-11-04) |
| CP-HU05-08 | HU05 | Posición de cada fila | Diez coleccionistas | Cargar la lista | De "01" a "10", según el orden de la lista | Unitaria | `CollectorListViewModelTest.positions follow the order of the list with two digits` | Pasa (2026-11-04) |
| CP-HU05-09 | HU05 | Tercera línea como dato | Con álbumes, sin álbumes y solo con favoritos | Cargar la lista | `Counts` si tiene álbumes; `NoAlbums` si no, aunque tenga favoritos | Unitaria | `CollectorListViewModelTest.the third line shows the counts, or no albums even if the collector has favorites` | Pasa (2026-11-04) |
| CP-HU05-10 | HU05 | Iniciales | Dos palabras, tres, una, minúsculas, acentos, espacios de más y en blanco | `collectorInitials` | "MB", "AP", "C", "CO", "ÁN", "JM" y vacío | Unitaria | `CollectorInitialsTest.initials use the first letter of the first and of the last word` | Pasa (2026-11-04) |
| CP-HU05-11 | HU05 | DTO a modelo | DTO con listas anidadas y sin ellas | `toModel()` | Conteos correctos, sin cruzarse; listas ausentes cuentan 0 | Unitaria | `CollectorMappersTest.the counts come from the nested lists and do not get crossed`; `CollectorMappersTest.missing lists count as zero` | Pasa (2026-11-04) |
| CP-HU05-12 | HU05 | Campos obligatorios | DTO sin `id`, `name` o `email` | `toModel()` | `JsonParseException`, que el Repository convierte en `DataError.Serialization` | Unitaria | `CollectorMappersTest.a collector without a required field is a serialization error` | Pasa (2026-11-04) |
| CP-HU05-13 | HU05 | Entidad y modelo | Coleccionista con 3 álbumes y 2 favoritos | Ida y vuelta por la entidad | Sin pérdida y sin cruzar los conteos | Unitaria | `CollectorEntityMappersTest.a round trip through the entity keeps every field and does not cross the counts` | Pasa (2026-11-04) |
| CP-HU05-14 | HU05 | Room con datos sin forzar | Room con un coleccionista | `getCollectors()` | No llama al adaptador | Unitaria | `CollectorRepositoryImplTest.filled Room without force refresh does not call the adapter` | Pasa (2026-11-04) |
| CP-HU05-15 | HU05 | Forzar actualización | Room con datos | `getCollectors(forceRefresh = true)` con lista y con respuesta vacía | Reemplaza lo guardado; la respuesta vacía vacía Room | Unitaria | `CollectorRepositoryImplTest.force refresh goes to the network and replaces what Room had`; `CollectorRepositoryImplTest.an empty backend answer empties Room` | Pasa (2026-11-04) |
| CP-HU05-16 | HU05 | Fallo de la carga | Adaptador que lanza `IOException` | `getCollectors(forceRefresh = true)` | `DataError.Network` y Room intacto | Unitaria | `CollectorRepositoryImplTest.a failed load returns the data error and leaves Room intact` | Pasa (2026-11-04) |
| CP-HU05-17 | HU05 | La API se crea fuera del hilo principal | Dispatcher de IO propio | Construir el adaptador y cargar | No se invoca al construir y corre en el dispatcher de IO (evidencia contra ANR) | Unitaria | `CollectorServiceAdapterTest.constructing the adapter does not invoke the provider`; `CollectorServiceAdapterTest.api provider runs on the IO dispatcher` | Pasa (2026-11-04) |

#### Pruebas de API (HU05-06)

| Id | HU | Nombre | Precondición | Pasos | Resultado esperado | Tipo | Automatizado (clase.método) | Estado |
|---|---|---|---|---|---|---|---|---|
| CP-HU05-18 | HU05 | Contrato de `GET /collectors` | MockWebServer con la respuesta real de la semilla (`collectors.json`) | `getCollectors()` | `GET /collectors`; ids 100 y 101; nombre, correo y conteos | API | `CollectorServiceAdapterTest.GET collectors deserializes the seed and counts its nested lists` | Pasa (2026-11-04) |
| CP-HU05-19 | HU05 | Error del backend | MockWebServer con HTTP 500 | `networkRequest { getCollectors() }` | `DataError.Http(500)` | API | `CollectorServiceAdapterTest.a failing call is the HTTP error of the response` | Pasa (2026-11-04) |

La conexión cortada, el JSON malformado y la lista vacía no se repiten aquí: son de la red compartida y los cubren `PerformerServiceAdapterTest` y `AlbumServiceAdapterTest`.

#### Pruebas E2E con Espresso (HU05-05)

| Id | HU | Nombre | Precondición | Pasos | Resultado esperado | Tipo | Automatizado (clase.método) | Estado |
|---|---|---|---|---|---|---|---|---|
| CP-HU05-20 | HU05 | Listar coleccionistas como visitante | Backend local; la prueba crea un coleccionista | Elegir "Visitante" y abrir la pestaña Coleccionistas | Número, iniciales, nombre, correo y tercera línea del creado; cada coleccionista de `GET /collectors` con su correo y tercera línea; por nombre; sin botón "+" | E2E | `CollectorListScreenTest.visitor_sees_collectors_by_name_without_the_create_button` | Pasa en API 21 y API 37 (2026-11-04) |
| CP-HU05-21 | HU05 | Listar coleccionistas como coleccionista | Igual que CP-HU05-20 | Elegir "Coleccionista" y abrir la pestaña desde el catálogo | La misma lista; el "+" del catálogo ya no se ve | E2E | `CollectorListScreenTest.collector_sees_the_same_list_and_no_create_button` | Pasa en API 21 y API 37 (2026-11-04) |
| CP-HU05-22 | HU05 | Error sin datos guardados y reintento | Room sin coleccionistas; la prueba corta el transporte con `BackendFirewall` | Abrir la pestaña, restaurar el transporte y tocar "Reintentar" | "No se pudieron cargar los coleccionistas" con "Reintentar"; después aparece la lista | E2E | `CollectorListScreenTest.failing_load_without_saved_collectors_shows_the_error_and_retry_loads_the_list` | Pasa en API 21 y API 37 (2026-11-04) |
| CP-HU05-23 | HU05 | Sin conexión con la lista guardada | Lista abierta; la prueba corta el transporte | Deslizar para actualizar | Las filas siguen y aparece "Sin conexión. Mostrando los datos guardados" | E2E | `CollectorListScreenTest.refreshing_without_connection_keeps_the_list_and_shows_the_offline_notice` | Pasa en API 21 y API 37 (2026-11-04) |
| CP-HU05-24 | HU05 | Deslizar para refrescar | Lista abierta; la prueba crea un coleccionista después de abrirla | Deslizar la lista hacia abajo | Aparece el coleccionista nuevo | E2E | `CollectorListScreenTest.pull_to_refresh_shows_a_collector_added_on_the_backend` | Pasa en API 21 y API 37 (2026-11-04) |
| CP-HU05-25 | HU05 | Tocar una fila abre el detalle | La prueba crea un coleccionista | Tocar su fila | Se abre `collectorDetailFragment` (hoy la pantalla provisional) con su `collectorId` | E2E | `CollectorListScreenTest.tapping_a_row_opens_the_detail_with_its_id` | Pasa en API 21 y API 37 (2026-11-04) |

El estado vacío no se prueba en E2E porque las pruebas no vacían el backend; lo cubren CP-HU05-03 y CP-HU05-15.

#### Pruebas instrumentadas sin backend

| Id | HU | Nombre | Precondición | Pasos | Resultado esperado | Tipo | Automatizado (clase.método) | Estado |
|---|---|---|---|---|---|---|---|---|
| CP-HU05-26 | HU05 | Textos de la tercera línea | Recursos reales de la app | Enlazar filas con distintos conteos | "1 álbum · 1 favorito", "2 álbumes · 3 favoritos", "1 álbum · 0 favoritos" y "Sin álbumes aún" | Instrumentada | `CollectorAdapterTest.third_line_writes_singular_plural_and_no_albums` | Pasa en API 21 y API 37 (2026-11-04) |
| CP-HU05-27 | HU05 | Orden en SQL | Room en memoria | `getAll()` | Por nombre sin distinguir mayúsculas y, a igual nombre, por id | Instrumentada | `CollectorDaoTest.get_all_returns_collectors_by_name_ignoring_case_with_ties_by_id` | Pasa en API 21 y API 37 (2026-11-04) |
| CP-HU05-28 | HU05 | Reemplazar lo guardado | Room en memoria con filas | `replaceAll()` con filas y con lista vacía | Queda exactamente lo recibido | Instrumentada | `CollectorDaoTest.replace_all_leaves_exactly_what_it_receives_and_updates_existing_rows` | Pasa en API 21 y API 37 (2026-11-04) |
| CP-HU05-29 | HU05 | Migración de la base 3 → 4 | Base versión 3 con un álbum, una canción y un artista | Migrar con `MIGRATION_3_4` y validar el esquema | Los datos siguen; la tabla `collectors` existe y está vacía | Instrumentada | `VinilosMigrationTest.migrating_from_3_to_4_keeps_albums_tracks_and_performers_and_creates_an_empty_collectors_table` | Pasa en API 21 y API 37 (2026-11-04) |

Ids: `CP-HUxx-nn`. Cada historia cubre al menos camino feliz, error de backend, estado vacío y, en formularios, las validaciones por campo.

El inventario de pruebas exploratorias usa la plantilla del curso (identificador, fecha, tester, funcionalidad, requerimiento, tipo de escenario positivo, negativo o mixto, nombre del escenario).

### HU04 — Detalle de artista

**Avance HU04-04 · 04/11/2026:** la siguiente correspondencia actualiza la cobertura unitaria pendiente de la tabla inicial. `PerformerDetailViewModelTest` tiene 9 pruebas aprobadas, reutiliza los fakes y reglas existentes y no necesita backend ni dispositivo. Esta ejecución no acredita pruebas E2E nuevas ni rotación real.

| Casos | Métodos de `PerformerDetailViewModelTest` | Resultado unitario |
|---|---|---|
| CP-HU04-01/02 | `initial load preserves the requested type content and role` | Aprobado: contenido, tipo solicitado, rol y fin de carga |
| CP-HU04-04/05 | `only visible album and member identities navigate and double taps are ignored`; `loading and unknown positive identities cannot open unrelated content` | Aprobado: destinos del detalle, bloqueo durante carga y toques duplicados |
| CP-HU04-06/09 | `role changes update the header without reloading and the chip clears the session` | Aprobado: sesión, cambio de rol y evento del selector; regreso/pila se verifica en E2E |
| CP-HU04-07/16 | `retries while loading and new observers do not duplicate the request`; `clearing the ViewModel cancels a pending load without publishing its result` | Aprobado: concurrencia, observadores y cancelación; posición/orientación quedan en interfaz |
| CP-HU04-08 | `empty sections preserve musician and band content without navigation` | Aprobado para ambos tipos con relaciones vacías |
| CP-HU04-10 | `invalid identity shows a safe state without querying or retrying` | Aprobado: ID no positivo o tipo ausente sin consulta |
| CP-HU04-11/12/13/14 | `errors finish loading and explicit retry revalidates the same artist` | Aprobado: 404, Network, 500 y Serialization; recuperación con revalidación |

Regresión ejecutada: `AlbumDetailViewModelTest` (12 pruebas, incluye entrada tipada desde álbum de CP-HU04-03) y `PerformerRepositoryImplTest` (15 pruebas, incluye caché, identidad y cancelación del detalle de CP-HU04-10/14/16). **36 pruebas aprobadas, 0 fallos, errores u omisiones**. Incluye pruebas existentes de HU02/HU03; no son 36 casos nuevos de HU04. Reporte local: `app/build/reports/tests/testDebugUnitTest/index.html`.

**Avance HU04-05 · 04/11/2026:** 6 recorridos de `PerformerDetailScreenTest` aprobados en tablet Huawei DBY-W09, Android 12/API 31, con BackVynils local; 0 fallos, errores u omisiones. Esta correspondencia actualiza los pendientes de interfaz del inventario inicial:

| Casos | Método E2E | Comprobación ejecutada |
|---|---|---|
| CP-HU04-01/04/06/07 | `musician_album_return_recreation_and_artists_tab_keep_the_expected_content` | Músico con 8 álbumes propios; contenido, contador, apertura del último álbum y regreso; recreación conserva fila visible, posición y rol; pestaña Artistas vuelve al listado |
| CP-HU04-02/05/06 | `band_opens_its_member_and_returns_as_collector` | Banda con 6 integrantes propios; desplazamiento al último por nombre, apertura y regreso; fecha, contador y rol; Agregar premio visible y deshabilitado |
| CP-HU04-08 | `empty_sections_keep_the_musician_and_band_visible` | Mensajes específicos de álbumes/integrantes vacíos sin ocultar al artista |
| CP-HU04-03 | `album_artist_opens_typed_detail_and_back_returns_to_the_same_album` | Entrada tipada de músico desde HU02 y regreso al mismo álbum; sin artista no hay enlace activo. Variante de banda desde HU02 pendiente en E2E |
| CP-HU04-09 | `role_selector_clears_detail_and_reopening_preserves_the_new_role` | Chip abre selector sin barra inferior; al entrar como Visitante se conserva el nuevo rol y no aparece Agregar premio |
| CP-HU04-11 | `removed_musician_shows_404_allows_retry_and_returns_to_artists` | 404 real de un músico propio eliminado; reintento conserva error y salida segura. Recuperación tras restablecer un artista no se simula aquí |
| CP-HU04-17 (parcial) | Recorridos de músico y banda anteriores | Listas extensas, búsqueda por identidad y desplazamiento en horizontal; fuentes grandes, lector de pantalla y verificación visual completa pendientes |

Se amplían los cinco recorridos previos y se agrega uno del selector; no son seis pruebas nuevas. Las filas siguientes conservan el inventario inicial como referencia histórica; los avances HU04-04/05 anteriores actualizan su cobertura. API 21/37, rotación real durante un recorrido y navegación con argumentos inválidos siguen pendientes; la recreación no equivale a rotación real. Reporte: `app/build/reports/androidTests/connected/debug/index.html`.

**Avance HU04-06 · 04/11/2026:** `PerformerDetailServiceAdapterTest` pasa de 11 a **12 pruebas de API aprobadas** con Repository, Retrofit y MockWebServer reales; sin BackVynils ni dispositivo. Mantiene los contratos completos, errores de transporte, respuestas inválidas, identidad y revalidación existentes. Se completan estas verificaciones:

| Casos | Método | Ampliación ejecutada |
|---|---|---|
| CP-HU04-08 | `absent relationships become empty sections and a missing image is allowed` | Músico y banda: relaciones ausentes, null o listas vacías; contadores cero e imagen opcional |
| CP-HU04-11/13 | `404 and 500 retain their HTTP status for both endpoints` | Tras cada error, reintento explícito recupera el mismo ID/tipo; GET al endpoint correspondiente y cabecera no-cache |
| CP-HU04-12 | `read timeout is a network error` | Tras agotar lectura, consulta explícita recupera el detalle de la banda, sin reintento automático |
| CP-HU04-15 | `artist and album dates retain the UTC day under Bogota timezone` | Fecha del artista y de sus álbumes conserva el día UTC con zona local America/Bogota, en ambos tipos; la zona original se restaura |

Controles finales `spotlessCheck detekt lintDebug testDebugUnitTest`: **224 pruebas JVM aprobadas, 0 fallos, errores u omisiones**, incluyendo las 12 de API de HU04 y regresiones de las demás historias. No son 224 pruebas nuevas ni E2E. Reporte: `app/build/reports/tests/testDebugUnitTest/index.html`. Los pendientes de dispositivo, accesibilidad e integración HU14 indicados en HU04-05 siguen vigentes.

Inventario inicial de HU04-01 (#37), 04/11/2026. Base inicial develop; contratos compartidos de HU03 revisados en el PR #121 e incorporados después como base local de HU04, con aceptación y correcciones pendientes. Estos IDs son propios de HU04 y no sustituyen los CP-HU03, CP-HU02 ni CP-HU14. El inventario se creó sin ejecuciones en HU04-01. HU04-02 añade la cobertura inicial de datos descrita más abajo; los avances HU04-04/05/06 anteriores actualizan su estado histórico.

Diseño y alcance: [13-hu04-diseno-tecnico.md](13-hu04-diseno-tecnico.md). Las clases y métodos de automatización se asociarán en HU04-02 a HU04-06 según se implementen; las pruebas de datos no acreditan por sí solas recorridos de interfaz.

| ID | HU | Caso | Precondición | Pasos | Resultado esperado | Tipo | Automatización | Estado |
|---|---|---|---|---|---|---|---|---|
| CP-HU04-01 | HU04 | Consultar músico | Músico de prueba con descripción, fecha y álbumes identificables; Visitante | Abrir desde su fila en HU03; revisar contenido y desplazar a álbumes | Nombre, imagen/marcador, descripción, nacimiento UTC, filas y contador coinciden con el dato preparado; sin acciones de escritura | E2E, unitaria y API | `PerformerDetailServiceAdapterTest.musician detail includes biography date and full albums with one GET` (API); UI/VM pendientes | Parcial: API aprobada 04/11/2026; UI/VM pendientes |
| CP-HU04-02 | HU04 | Consultar banda | Banda de prueba con fecha, álbumes e integrantes conocidos | Abrir desde HU03 y revisar todas las secciones | Fecha de creación y contenido correctos; miembros y álbumes recibidos con contadores coherentes, sin asumir los del mockup | E2E, unitaria y API | `PerformerDetailServiceAdapterTest.band detail includes typed members and albums without extra requests` (API); UI/VM pendientes | Parcial: API aprobada 04/11/2026; UI/VM pendientes |
| CP-HU04-03 | HU04 | Entrada desde álbum | Álbum con artista músico o banda, tipado por la respuesta; incluir variante sin artista | Abrir HU02 y tocar el artista visible | Abre el detalle con ID/tipo correctos; sin artista o con tipo indeterminado no navega a una ruta adivinada; no altera el resto de HU02 | E2E, unitaria y API | `PerformerSummaryMappersTest` (3 casos del tipo en el mapper); navegación pendiente | Parcial: mapper aprobado 04/11/2026; UI pendiente |
| CP-HU04-04 | HU04 | Abrir álbum del artista | Detalle con álbum propio de prueba | Desplazar a la fila, abrirla y regresar | Abre HU02 con el albumId de esa fila; Atrás devuelve el mismo artista | E2E y unitaria | Por implementar | Propuesto; no ejecutado |
| CP-HU04-05 | HU04 | Abrir integrante | Banda con al menos un músico de prueba | Abrir un integrante y regresar | Navega al detalle de ese ID con MUSICIAN; vuelve a la banda original sin duplicar solicitudes por toque doble | E2E y unitaria | Por implementar | Propuesto; no ejecutado |
| CP-HU04-06 | HU04 | Regreso y rol | Entradas desde HU03 y HU02; repetir con Visitante/Coleccionista | Usar Atrás de cabecera y sistema; abrir de nuevo | Regresa al origen y conserva el rol; no aparecen acciones de HU12/HU13/HU15 sin destino implementado | E2E y unitaria | Por implementar | Propuesto; no ejecutado |
| CP-HU04-07 | HU04 | Recreación y posición | Detalle cargado, desplazado a una fila identificable | Recrear Activity; comprobar orientación en la validación de dispositivo disponible | Conserva ID/tipo, rol, contenido y posición; recrear vista no repite una carga completada; resultado real de orientación se registra por separado | E2E y unitaria | Por implementar | Propuesto; no ejecutado |
| CP-HU04-08 | HU04 | Sin álbumes o integrantes | Músico sin álbumes; banda sin álbumes y/o sin integrantes | Abrir cada variante | Artista sigue visible; contadores 0 y mensajes específicos; una sección vacía no elimina la otra | E2E, unitaria y API | `PerformerDetailServiceAdapterTest.absent relationships become empty sections and a missing image is allowed`; resto pendiente | Parcial: API aprobada 04/11/2026; UI/VM pendientes |
| CP-HU04-09 | HU04 | Rol y selector | Detalle abierto bajo cada rol | Tocar chip de rol y elegir de nuevo | Limpia sesión/pila como el resto de la app; consulta disponible en ambos roles; sin escrituras por cambiar rol | E2E y unitaria | Por implementar | Propuesto; no ejecutado |
| CP-HU04-10 | HU04 | ID/tipo inválidos | Argumentos ausentes, ID no positivo o tipo desconocido | Crear ViewModel con los argumentos de prueba | No llama al repositorio ni a un endpoint incorrecto; salida segura, sin cierre inesperado | Unitaria; comprobación instrumentada de navegación | `PerformerRepositoryImplTest.non positive detail ids are rejected before invoking the adapter`; argumentos de navegación pendientes | Parcial: Repository aprobado 04/11/2026; VM/UI pendientes |
| CP-HU04-11 | HU04 | Artista inexistente | Endpoint por tipo responde HTTP 404 | Consultar detalle; revisar estado y usar reintento/salida | DataError.Http(404), artista inexistente y navegación segura; ninguna carga infinita ni cierre | API y unitaria; interfaz por comprobar | `PerformerDetailServiceAdapterTest.404 and 500 retain their HTTP status for both endpoints`; estado visible pendiente | Parcial: API aprobada 04/11/2026; VM/UI pendientes |
| CP-HU04-12 | HU04 | Desconexión y timeout | Transporte interrumpido o lectura agotada, controlados en MockWebServer | Consultar y repetir tras preparar una respuesta válida | DataError.Network; finaliza carga, permite reintento y recupera el contenido del mismo artista | API y unitaria | `PerformerDetailServiceAdapterTest` (desconexión, recuperación explícita y timeout); VM pendiente | Parcial: API aprobada 04/11/2026; VM pendiente |
| CP-HU04-13 | HU04 | HTTP 500 y reintento | Error de servidor seguido de respuesta válida | Consultar y reintentar | Error clasificado sin alterar la caché del listado; reintento recupera el detalle y revalida HTTP | API y unitaria | `PerformerDetailServiceAdapterTest` (HTTP 500 y revalidación); estado/reintento del VM pendientes | Parcial: API aprobada 04/11/2026; VM pendiente |
| CP-HU04-14 | HU04 | Respuesta inválida | JSON inválido, cuerpo nulo, fecha inválida, campos requeridos ausentes, elementos nulos o ID diferente | Consultar mediante Repository y adaptador reales | Serialization; no NullPointerException, contenido de otro artista ni cambios de caché; cancelación se propaga | API y unitaria | `PerformerDetailServiceAdapterTest` (4 casos de respuestas inválidas) y `PerformerRepositoryImplTest` (ID/tipo y cancelación) | Parcial: datos aprobados 04/11/2026; ampliación pendiente |
| CP-HU04-15 | HU04 | Contrato y fechas | Fixtures completos de músico/banda; zona America/Bogota | Consultar cada tipo y reintentar con revalidación | GET correcto según tipo/ID; fechas conservan día UTC; no llamadas por álbum/integrante ni consultas a ambas listas por un detalle | API y unitaria | `PerformerDetailServiceAdapterTest` (GET por tipo, fecha y revalidación); ampliación HU04-06 pendiente | Parcial: API aprobada 04/11/2026; ampliación pendiente |
| CP-HU04-16 | HU04 | Caché y concurrencia | Room de HU03 con datos guardados; detalle cargando o completado | Cargar/reintentar rápidamente; cancelar; volver a observar el estado | Sin peticiones concurrentes duplicadas; detalle no reemplaza ni borra lista de artistas, álbumes o tracks; no nueva migración de Room | Unitaria y regresión instrumentada existente | `PerformerRepositoryImplTest` (detalle sin tocar Room); concurrencia/ciclo de vida del VM pendientes | Parcial: Repository aprobado 04/11/2026; VM pendiente |
| CP-HU04-17 | HU04 | Imagen, texto y accesibilidad | Imagen inválida, nombre largo, listas extensas y ambos modos visuales | Recorrer detalle, desplazarse y revisar foco/lectura | Marcador visible; todas las secciones accesibles por scroll; controles de 48 dp, textos y descripciones en recursos; datos reales sin posiciones fijas | E2E y manual | Por implementar | Propuesto; no ejecutado |

La entrada desde HU03 se comprobará con la copia incorporada a nuestra base local y se verificará de nuevo al reconciliar su versión aceptada. La sección de premios y el retorno de su selector se validarán con CP-HU14, reutilizando estos casos de consulta y navegación; no se duplican sus escenarios ni se presentan como completos antes de integrar HU14. API 21/37 quedan pendientes hasta contar con ejecución y evidencia. HU04-01 no requiere correr Gradle al cambiar solo documentación.

**Cobertura inicial HU04-02 (04/11/2026):** 18 pruebas nuevas aprobadas (11 en `PerformerDetailServiceAdapterTest`, 4 añadidas a `PerformerRepositoryImplTest` y 3 en `PerformerSummaryMappersTest`). Se reutilizan los JSON, fakes y reglas de HU03. La tabla distingue esa cobertura de datos de los estados y recorridos de UI pendientes; ningún escenario mixto se marca como totalmente aprobado por una prueba de API. Controles: `spotlessCheck detekt lintDebug testDebugUnitTest assembleDebugAndroidTest`, BUILD SUCCESSFUL con JDK 17, 212 pruebas JVM sin fallos, errores ni omisiones. Compilar las instrumentadas no equivale a ejecutarlas. No se ejecutaron E2E de HU04 ni se implementó todavía su pantalla. Ver métodos y contratos en [el avance HU04-02](13-hu04-diseno-tecnico.md#9-implementación-hu04-02--04112026).


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
| `OkHttpIdlingResourceRule` | `src/androidTest` | Recurso de sincronización disponible; las pantallas actuales usan `waitForDisplayed` |
| `BackendFirewall` | `src/androidTest` | Provoca y restaura un fallo de transporte de BackVynils en CP-HU08-03 |
| `BackendPerformers` | `src/androidTest` | Crea músicos, bandas y álbumes en BackVynils local para los casos CP-HU03 y los borra al terminar |
| `BackendCollectors` | `src/androidTest` | Crea y borra coleccionistas sin álbumes para los casos CP-HU05 y lee los que tiene el backend en ese momento |
| `VinilosTestRunner` | `src/androidTest` | Instala y restaura el selector de proxy requerido por `BackendFirewall` |
| `robots/` | `src/androidTest` | Page Objects por pantalla (`MainRobot`, `AlbumListRobot`, `AlbumCreateRobot`, `TrackCreateRobot`, `PerformerListRobot`, `CollectorListRobot`) |

En las unitarias, las clases de `android.*` no lanzan error sino que devuelven valores por defecto (`unitTests.isReturnDefaultValues = true`, necesario porque OkHttp 5 llama a `android.util.Log`). Por eso la lógica que se prueba en `src/test` no usa clases de Android: `TextUtils.isEmpty` devolvería `false` en silencio y la prueba pasaría por error. En su lugar se usa Kotlin puro (`isBlank()`), como ya exige la arquitectura para ViewModels y modelos.

**ViewModel o Repository** (`src/test/java/.../viewmodels/AlbumListViewModelTest.kt`, mismo paquete que la clase probada): reglas `InstantTaskExecutorRule` y `MainDispatcherRule`, un `FakeAlbumRepository` en `src/test`, cuerpo en `runTest`. Ejemplo en la sección 3.

**Service Adapter / API** (`src/test/java/.../network/AlbumServiceAdapterTest.kt`):

```kotlin
@get:Rule val mockWebServerRule = MockWebServerRule()

@Test
fun `getAlbums maps albums when server returns seed`() = runTest {
    mockWebServerRule.enqueueJson("api/albums.json")
    val service = Retrofit.Builder()
        .baseUrl(mockWebServerRule.baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create<VinilosApiService>()
    val adapter = AlbumServiceAdapter(service)

    val albums = adapter.getAlbums()

    assertEquals(4, albums.size)
    assertEquals("/albums", mockWebServerRule.server.takeRequest().path)
}
```

La respuesta se captura del backend real (`src/test/resources/api/README.md`). Los casos de error encolan un `MockResponse().setResponseCode(500)` o un JSON de error.

**E2E** (`src/androidTest/java/co/edu/uniandes/vinilos/ui/AlbumListScreenTest.kt`): `@RunWith(AndroidJUnit4::class)`, `ActivityScenarioRule(MainActivity::class.java)`, `OkHttpIdlingResourceRule` cuando la pantalla llama al backend, y un Robot por pantalla en `robots/`. Un método por caso del inventario (`CP-HU01-nn`).

## 10. Comandos

```bash
./gradlew testDebugUnitTest                 # unitarias
./gradlew spotlessCheck detekt lintDebug    # calidad estática
bash scripts/run_e2e.sh                    # prepara el backend Docker, ejecuta instrumentadas y limpia sus fixtures HU14
./gradlew connectedDebugAndroidTest         # instrumentadas; para incluir HU14 usar el runner compartido anterior
adb shell am instrument -w co.edu.uniandes.vinilos.test/co.edu.uniandes.vinilos.testing.VinilosTestRunner   # E2E sin Gradle
```

HU14 requiere `bash scripts/run_e2e.sh`: el script añade `hu14FixtureRun` a los argumentos de instrumentación y prepara también las secuencias de `prize` y `performer_prize`. Al finalizar Gradle, incluso si falla, `limpiar_premios_e2e.sql` elimina solo los premios, artistas y asociaciones marcados por esa ejecución; si detecta relaciones ajenas, aborta la transacción. Un fallo de limpieza hace fallar el comando. `BackendPrizes` rechaza la preparación sin esa marca antes del primer POST. No usa el DELETE defectuoso del backend ni se debe pasar una marca manual para omitir la limpieza. Las clases ajenas a HU14 pueden seguir ejecutándose directamente con Gradle.

En API 21, `connectedDebugAndroidTest` puede tardar varios minutos en cerrar tras terminar las pruebas; `am instrument` devuelve el resultado en cuanto terminan.

En API 21, si `connectedDebugAndroidTest` termina con `tests="0"` en el XML de resultados (el registro solo muestra «Failed to pull /storage/emulated/usage…» y no se determinó la causa), no cuenta como ejecución: se instalan los dos APK con `adb install -r` y se ejecuta con `adb shell am instrument -w -r co.edu.uniandes.vinilos.test/co.edu.uniandes.vinilos.testing.VinilosTestRunner`, que informa `OK (N tests)`. Validación de HU03 (2026-11-03): 68 pruebas instrumentadas, `OK (68 tests)`, en API 21; la misma suite pasó con `scripts/run_e2e.sh` en API 37. El filtro `-Pandroid.testInstrumentationRunnerArguments.class=` con varias clases separadas por coma solo ejecutó la primera en la ejecución de HU03; para el paquete de Room se usó `...arguments.package=co.edu.uniandes.vinilos.database`. Si en un emulador recién arrancado fallan casi todas las pruebas de interfaz con `RootViewWithoutFocusException`, hay un diálogo "System UI isn't responding" (o del lanzador) encima de la app: se descarta con "Esperar" y se repite la ejecución; no es un fallo de la app.

Para reproducir las E2E en otro equipo se necesita BackVynils con su semilla, Docker accesible, Bash 4+, el SDK y un dispositivo conectado, y `vinilos.baseUrl` apuntando a ese backend según la configuración local de la app. En una tablet por USB, redirigir antes el puerto con `adb -s SERIAL reverse tcp:3000 tcp:3000`. El script identifica el proyecto Compose por el backend publicado en el puerto 3000 y su servicio `db`; admite `E2E_BACKEND_PORT` o `E2E_DB_CONTAINER` para otra instalación, sin rutas personales ni credenciales en el repositorio. Los argumentos adicionales se pasan a Gradle, por ejemplo el filtro de una clase. No reinicia contenedores ni limpia los datos de trabajo. Las pruebas del catálogo todavía requieren los álbumes conocidos de la semilla; admiten álbumes adicionales. Las de canciones crean su propio álbum con nombre único.

Validación del comando compartido (2026-10-30): `bash scripts/run_e2e.sh --offline --console=plain` preparó el backend y ejecutó las 48 pruebas instrumentadas en la tablet API 31, sin fallos ni omisiones. El SQL se comprobó también en una base temporal con IDs distintos de la semilla, con secuencias adelantadas y ejecutando la preparación repetidamente: las inserciones generaron IDs nuevos y los contadores adelantados se conservaron. La base temporal se eliminó al finalizar. Esto verifica datos diferentes en el mismo equipo; la ejecución en otros equipos y API 21/37 sigue pendiente.

### Capa de red (TR-02)

`NetworkClientTest` usa MockWebServer y `src/test/resources/api/albums_single.json` para verificar rutas, deserialización, campos opcionales, catálogo vacío, códigos HTTP, JSON inválido, timeout, caché HTTP y cancelación. Este ejemplo sintético contiene un solo álbum y es independiente de `api/albums.json`, que conserva los cuatro álbumes de la semilla para TR-05 y HU01. También comprueba que un POST cuya respuesta se pierde no se repite. `NetworkRequestTest` verifica errores DNS y la propagación de cancelación y errores de programación.

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

## 11. Bitácora de decisiones

| Fecha | Decisión |
|---|---|
| 2026-10-07 | Pirámide de niveles, Page Objects, inventario `CP-HUxx-nn`, defectos como issues, Monkey y RIP en Sprint 3. |
| 2026-10-26 | Reinicio con Vistas: E2E con Espresso y `ActivityScenarioRule`, unitarias de `LiveData` con `InstantTaskExecutorRule`, fakes sin librería de mocks, matriz API 21/30/37, sin capturas seguidas en API 21. Konsist y pruebas de Compose retiradas. |
| 2026-10-27 | TR-05: entorno de pruebas listo. Utilidades en el paquete `testing` (`MainDispatcherRule`, `getOrAwaitValue`, `MockWebServerRule`, `OkHttpIdlingResourceRule`), Robots en `robots/`, `animationsDisabled`. Las pruebas de API pasan a JUnit + Retrofit + MockWebServer en `src/test`, con respuestas reales de BackVynils. |
| 2026-10-28 | HU07-01: inventario CP-HU07-01 a CP-HU07-10 (E2E con `HU07AlbumCreateTest`, error del backend en el ViewModel y pruebas de API de `POST /albums` con MockWebServer), validaciones por tabla. |
| 2026-10-29 | HU07-04 a 06: inventario CP-HU07-01 a 19 (6 E2E automatizadas + 1 manual, 7 unitarias, 5 de API). Las unitarias solo cubren lo que E2E y API no alcanzan (llamadas que no deben ocurrir, doble toque, fallo de Room, carrera con el `mutex`). La clase E2E se llama por pantalla, `AlbumCreateScreenTest`, como `AlbumListScreenTest`: los ids de historia viven solo en la documentación. Las respuestas 400 se capturaron del backend real; la 200 es sintética para no crear datos al capturarla. |
| 2026-10-29 | HU01-04 a 06: inventario CP-HU01-01 a 14 (7 E2E, 2 manuales, unitarias y de API). Las E2E usan `AlbumListRobot`; los casos que necesitan un álbum sin intérpretes o un cambio en el backend lo crean con `POST /albums` (`testing/BackendAlbums.kt`) y un nombre único posterior a "Poeta" en orden alfabético, para no mover las filas de la semilla. La rotación (CP-HU01-12) se prueba con `ActivityScenario.recreate()`: rotar el emulador de API 21 lo congela. |
| 2026-10-30 | HU08: inventario CP-HU08-01 a 23 (7 E2E en `TrackCreateScreenTest`, unitarias de la hoja, del detalle y del repositorio, y API de `POST /albums/:id/tracks`). Las E2E crean el álbum con `POST /albums` y un nombre único; no usan la semilla. Pasaron en API 37; API 21 queda pendiente. El corte de red (CP-HU08-03) no detiene el contenedor: ese caso sigue manual en CP-HU07-07. En este emulador iptables exige root, así que `BackendFirewall` usa modo avión y vuelve a encender wifi y datos. El `Thread.sleep` de esa regla solo espera la sonda y los 4 s del pool de OkHttp; la UI espera con `waitForDisplayed`. `OkHttpIdlingResourceRule` no se registra porque `AppContainer` no expone el `OkHttpClient`. Los ids de historia viven solo en este documento. |
| 2026-11-03 | HU03: inventario CP-HU03-01 a 25 (11 E2E en `PerformerListScreenTest`, unitarias del mapper, del repositorio y del ViewModel, API de `GET /musicians` y `GET /bands`, DAO y migración 2 → 3 instrumentados). Las E2E crean y borran sus artistas con `BackendPerformers`; `scripts/preparar_backend_e2e.sql` adelanta también la secuencia de `performer`. `SharedComponentsTest` deja de esperar la pantalla provisional en Artistas (ya es la lista de HU03) y la comprueba en Coleccionistas. En API 21 un `connectedDebugAndroidTest` con 0 pruebas no se da por pasado: se ejecuta con `am instrument`. |
| 2026-11-04 | HU05: inventario CP-HU05-01 a 29 (6 E2E en `CollectorListScreenTest`, 17 unitarias, 2 de API y 4 instrumentadas sin backend). Las E2E no se basan en datos concretos, como se acordó en la retrospectiva, y solo crean coleccionistas sin álbumes porque la ruta para quitar un álbum borra otro vínculo (`10-diseno-de-la-aplicacion.md`, hallazgo 17). Con `vinilos.baseUrl` apuntando a Heroku, CP-HU05-22 no es válido: `BackendFirewall` no alcanzó a cortar la conexión HTTPS. `SharedComponentsTest` deja de esperar la pantalla provisional en Coleccionistas. |
