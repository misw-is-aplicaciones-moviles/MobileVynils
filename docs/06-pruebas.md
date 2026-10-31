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

- Runner `androidx.test.runner.AndroidJUnitRunner`; regla `ActivityScenarioRule(MainActivity::class.java)`.
- Backend: BackVynils local con la semilla (`docker compose up`), alcanzable desde el emulador en `10.0.2.2:3000`. Las pruebas que crean datos (HU07, HU08) usan nombres únicos para ser repetibles.
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
| CP-HU01-01 | HU01 | Listar álbumes como visitante | Backend local con la semilla (A Day at the Races, A Night at the Opera, Buscando América, Poeta del pueblo) | Elegir "Visitante" | Las cuatro filas de la semilla en orden alfabético; la primera con "01", "A Day at the Races", "Queen" y "Rock · 1976"; sin botón "+" | E2E | `AlbumListScreenTest.visitor_sees_the_seed_albums_in_alphabetical_order_without_the_create_button` | Pasa en API 21 (2026-10-29); API 37 pendiente |
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

PE-12 corta la red del dispositivo con `BackendFirewall`: intenta iptables y, si la shell no es root, activa el modo avión y apaga wifi y datos. Al terminar enciende las radios otra vez, porque en el emulador solo desactivar el modo avión no devuelve la ruta hacia `10.0.2.2`. No se detiene el contenedor: ese corte sigue siendo el caso manual CP-HU07-07. Antes de cada prueba la regla borra un proxy HTTP pegado: OkHttp lo respeta y la sonda hacia `10.0.2.2` no. La UI espera con `waitForDisplayed`. El `Thread.sleep` de la regla solo cubre esa sonda y los 4 s de keep-alive del pool de OkHttp. `OkHttpIdlingResourceRule` no se registra porque `AppContainer` no expone el `OkHttpClient`.

El 2026-10-30 `TrackCreateScreenTest` pasó en el emulador Pixel 9 Pro (API 37, Android 17): 7 pruebas, 0 fallos, 49 s. API 21 queda pendiente, igual que el resto de la matriz E2E. Las unitarias y de API de la tabla pasaron el mismo día (`testDebugUnitTest`, 0 fallos).

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
| `OkHttpIdlingResourceRule` | `src/androidTest` | Espresso espera las llamadas de red de la app. Hoy no se registra: `AppContainer` no expone el `OkHttpClient` |
| `BackendFirewall` | `src/androidTest` | Corta y restaura el acceso a BackVynils en CP-HU08-03 |
| `robots/` | `src/androidTest` | Page Objects por pantalla (`MainRobot`, `AlbumListRobot`, `AlbumCreateRobot`, `TrackCreateRobot`) |

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
./gradlew connectedDebugAndroidTest         # E2E en el emulador conectado
adb shell am instrument -w co.edu.uniandes.vinilos.test/androidx.test.runner.AndroidJUnitRunner   # E2E sin Gradle
```

En API 21, `connectedDebugAndroidTest` puede tardar varios minutos en cerrar tras terminar las pruebas; `am instrument` devuelve el resultado en cuanto terminan.

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
