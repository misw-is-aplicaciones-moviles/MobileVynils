# 10. Diseño de la aplicación

Documento de diseño que desarrolla la planeación del Inception: convierte el backlog, la distribución por sprints, los acuerdos de equipo y el prototipo navegable en una especificación concreta de roles, navegación, pantallas, datos, validaciones y textos para construir Vinilos con la arquitectura de `02-arquitectura.md` y las reglas de `05-reglas-de-diseno-ui.md`.

## 1. Fuentes y alcance

| Fuente | Qué aporta | Estado revisado |
|---|---|---|
| Wiki del proyecto (Home, Acuerdos de Equipo, Inception, Backlog de Producto, HU01 a HU15, Sprint 1 a 3, Milestones 1 a 3, Wireframe navegable, actas) | Historias comprometidas, puntos, tareas, responsables, sprints, roles del prototipo | Commit `a7cc5c3` del 2026-10-14 |
| Prototipo de media fidelidad en Figma (64 pantallas, 360 x 800) en seis tableros | Pantallas, flujos, estados, validaciones y mensajes por historia | Revisado el 2026-10-14 en Figma (enlace en la página "Wireframe navegable" de la wiki) |
| Mockups v2 de alta fidelidad en Figma ("Vynils V2 MockUps", 76 pantallas de 390 x 844 y "Kit v2 · Componentes") | Aspecto final: colores, tipografía, cabecera, barra inferior, tarjetas, hojas | Revisados el 2026-10-21; fuente visual de todas las pantallas (`05-reglas-de-diseno-ui.md`) |
| Documento del curso "App Vinilos: orden de las 15 historias y funcionamiento del backend" | Orden de las historias, endpoints, campos y restricciones; mapa de navegación propuesto | Contrastado con el código de `BackVynils` |
| Código de `BackVynils` (entidades, servicios, validaciones Joi) | Reglas reales: listas de género y sello, rating 0 a 5, un músico por banda, estados `Active`/`Inactive` | Clon local |

Alcance: las 12 historias comprometidas (HU01 a HU08 del MVP y HU09, HU15, HU12 y HU10 como extras) y las 6 tareas transversales del Sprint 1. HU13, HU14 y HU11 forman el backlog alterno; se mencionan solo donde el diseño debe dejarles sitio.

Los tableros del prototipo:

| Tablero en Figma | Contenido |
|---|---|
| Inicio y modo Visitante | Selector de rol y las nueve pantallas de consulta (HU01 a HU06) sin acciones |
| Sprint 1 | HU01 (catálogo y error), HU02 (detalle), HU07 (formulario con 7 estados), HU08 (hoja de canción con 4 estados) |
| Sprint 2 | HU03 (lista con tres filtros), HU04 (músico y banda), HU05 (lista), HU06 (detalle) |
| Sprint 3 | HU09, HU15, HU12 y HU10, más las combinaciones HU08 + HU09 y HU12 + HU15 en cualquier orden |
| Kit | Botones, campos, chips, barra inferior, paleta de grises y tipografía Inter |

El tablero "Grupo: Equipo 3" se exportó en blanco (solo contenía texto) y no se incluye.

## 2. Roles y modo de uso

El backend no tiene autenticación. Por indicación del profesor, la app abre con un **selector de rol** (tarea TR-06) y mantiene el rol elegido durante la sesión:

| Rol | Historias | Qué ve | Qué no ve |
|---|---|---|---|
| Visitante | HU01 a HU06 | Listas y detalles de álbumes, artistas y coleccionistas, incluidos comentarios, integrantes, favoritos y álbumes del coleccionista | Ningún botón de acción: sin FAB "+", sin "Canción", sin "Comentar", sin "Agregar álbum / integrante / favorito" |
| Coleccionista | Todas | Lo anterior más HU07, HU08, HU09, HU15, HU12 y HU10 | Nada |

Reglas de diseño derivadas del prototipo:

1. El rol se muestra como chip en la esquina superior derecha de **todas** las pantallas ("⇄ VISITANTE" o "⇄ COLECCIONISTA", como en los mockups v2). Tocarlo vuelve al selector de rol; el texto de la pantalla inicial lo anuncia: "Puedes cambiar de rol desde el catálogo".
2. El rol vive en un `SessionRepository` en memoria, instancia única del `AppContainer`, que expone `LiveData<UserRole?>`. Si el proceso muere, la app vuelve al selector; no se persiste en disco en el MVP. Cada `UiState` incluye `val canEdit: Boolean` derivado del rol, y los Fragments muestran u ocultan acciones con ese valor; nunca consultan el repositorio de sesión directamente.
3. Quién comenta (HU09): como no hay usuario autenticado, la hoja "Comentar álbum" incluye un selector de coleccionista con chips (`¿Quién comenta?`), tal como lo dibuja el prototipo. Se descarta la alternativa de un "coleccionista actual" fijo.

```kotlin
enum class UserRole { VISITOR, COLLECTOR }

interface SessionRepository {
    val role: LiveData<UserRole?>
    fun selectRole(role: UserRole)
    fun clearRole()
}
```

## 3. Mapa de navegación

Barra inferior (`BottomNavigationView`) con tres destinos, idéntica en ambos roles. Las historias 9 a 15 no añaden pestañas: se entra a ellas por botones en los detalles y por listas existentes en "modo selección".

```
roleSelectFragment (inicial, sin barra inferior)                  TR-06
└── MainActivity: NavHostFragment + BottomNavigationView
    ├── ÁLBUMES
    │   ├── albumListFragment                           HU01   "+" de la barra (coleccionista)
    │   │   ├── albumCreateFragment (pantalla completa) HU07
    │   │   └── albumDetailFragment(albumId)            HU02
    │   │       ├── TrackCreateBottomSheet              HU08   "+" (coleccionista)
    │   │       └── CommentCreateBottomSheet            HU09   botón "Comentar"
    ├── ARTISTAS
    │   ├── performerListFragment (Todos/Músicos/Bandas) HU03
    │   └── performerDetailFragment(performerId, type)  HU04
    │       ├── albumPickerFragment(performerId, type)  HU15   botón "Agregar álbum"
    │       └── musicianPickerFragment(bandId)          HU12   botón "Agregar integrante" (solo banda)
    └── COLECCIONISTAS
        ├── collectorListFragment                       HU05
        └── collectorDetailFragment(collectorId)        HU06
            └── performerPickerFragment(collectorId)    HU10   botón "Agregar favorito"
```

Destinos de `res/navigation/nav_graph.xml` y sus argumentos (`Bundle`, claves en el `companion object` del Fragment):

| Destino | Argumentos | Historia |
|---|---|---|
| `roleSelectFragment` | — | TR-06 |
| `albumListFragment` | — | HU01 |
| `albumDetailFragment` | `albumId: Int` | HU02 |
| `albumCreateFragment` | — | HU07 |
| `performerListFragment` | — | HU03 |
| `performerDetailFragment` | `performerId: Int`, `type: String` (`MUSICIAN` o `BAND`) | HU04 |
| `albumPickerFragment` | `performerId: Int`, `type: String` | HU15 |
| `musicianPickerFragment` | `bandId: Int` | HU12 |
| `collectorListFragment` | — | HU05 |
| `collectorDetailFragment` | `collectorId: Int` | HU06 |
| `performerPickerFragment` | `collectorId: Int` | HU10 |

Comportamiento de navegación:

- `roleSelectFragment` es el destino inicial; al elegir rol se navega a `albumListFragment` con `popUpTo` del selector (`inclusive = true`). El chip de rol vuelve al selector limpiando la pila. La barra inferior se oculta en el selector.
- Los detalles se apilan sobre su pestaña y vuelven con la flecha y con el botón atrás. Los selectores abren a pantalla completa con "X" y vuelven al detalle que los abrió. `albumCreateFragment` abre a pantalla completa con "X", sin barra inferior, y vuelve al catálogo; si crea el álbum, deja el resultado en `previousBackStackEntry.savedStateHandle` antes de cerrarse (HU07, sección 6).
- `TrackCreateBottomSheet` y `CommentCreateBottomSheet` son `BottomSheetDialogFragment` abiertos desde el detalle con `show(childFragmentManager, TAG)`. Al guardar, la hoja llama a `parentFragmentManager.setFragmentResult(...)` y el detalle, que escucha con `childFragmentManager.setFragmentResultListener(...)`, vuelve a cargar el álbum. Así "HU08 + HU09 en cualquier orden" se cumple sin apilar destinos.
- Tras una escritura exitosa se vuelve a la pantalla de origen y se muestra un `Snackbar` de confirmación.
- Transición `MaterialFadeThrough` de 300 ms entre destinos.

## 4. Inventario de pantallas

Siete pantallas base, un selector de rol, un formulario, dos hojas y tres selectores. Cada una es un Fragment (o `BottomSheetDialogFragment`) con su layout y su `XViewModel` en `viewmodels/`.

| # | Pantalla | Paquete `ui/` | HU | Estados dibujados en el prototipo |
|---|---|---|---|---|
| 0 | Selector de rol | `role/` | TR-06 | Único |
| 1 | Catálogo de álbumes | `albums/` | HU01 | Contenido (5 álbumes en el mockup; la semilla de BackVynils trae 4), error con "Reintentar"; cargando y vacío se añaden según la plantilla de pantalla |
| 2 | Detalle de álbum | `albums/` | HU02, HU08, HU09 | Contenido con canciones y comentarios; con hoja de canción; con hoja de comentario; con snackbar de confirmación |
| 3 | Nuevo álbum | `albums/` | HU07 | Vacío, errores por campo (obligatorio, género no seleccionado), lleno, selector de fecha, enviado, catálogo con snackbar "Álbum creado" |
| 4 | Lista de artistas | `artists/` | HU03 | Todos, Músicos, Bandas |
| 5 | Detalle de músico | `artists/` | HU04, HU15 | Contenido con álbumes; con snackbar "Álbum agregado al artista" |
| 6 | Detalle de banda | `artists/` (misma pantalla, secciones según tipo) | HU04, HU12, HU15 | Contenido con integrantes y álbumes; con snackbars |
| 7 | Lista de coleccionistas | `collectors/` | HU05 | Contenido (4 coleccionistas) |
| 8 | Detalle de coleccionista | `collectors/` | HU06, HU10 | Contenido con favoritos y álbumes con precio y estado; con snackbar |
| 9 | Hoja "Nueva canción" | `albums/` | HU08 | Vacía, con errores (obligatorio, formato mm:ss), llena |
| 10 | Hoja "Comentar álbum" | `albums/` | HU09 | Vacía, con errores (coleccionista, valoración, texto), llena |
| 11 | Elegir álbum | `artists/` | HU15 | Sin selección (botón deshabilitado), con selección; ítems "Ya agregado" deshabilitados |
| 12 | Elegir músico | `artists/` | HU12 | Sin selección, con selección, con error "pertenece a otra banda"; ítems "Ya integrante" |
| 13 | Elegir artista | `collectors/` | HU10 | Sin selección, con selección; ítems "Ya es favorito" |

Los tres selectores comparten un layout `fragment_picker.xml` y un adaptador `PickerAdapter` en `ui/common` (campo de búsqueda, lista con `RadioButton`, chip de estado por ítem, botón inferior con el nombre elegido entre comillas angulares: "Agregar «The Game»").

## 5. Modelo de datos

### 5.1 Del backend al dominio

| Backend (entidad, campos) | Modelo (`models/`) | Observaciones |
|---|---|---|
| `Album`: `id`, `name`, `cover`, `releaseDate`, `description`, `genre`, `recordLabel`, `tracks[]`, `performers[]`, `comments[]` | `Album(id, name, coverUrl, releaseDate: LocalDate, description, genre: Genre, recordLabel: RecordLabel, tracks: List<Track>, performers: List<PerformerSummary>, comments: List<Comment>)` | `GET /albums` ya trae tracks, performers y comments: la tarjeta muestra el artista sin otra llamada |
| `Track`: `id`, `name`, `duration` (texto) | `Track(id, name, duration: String)` | Duración se guarda como texto `mm:ss`; se valida en la app |
| `Comment`: `id`, `description`, `rating`, `collector` | `Comment(id, description, rating: Int, collectorId: Int?, collectorName: String?)` | Ver hallazgo 1 sobre el nombre del autor |
| `Performer` (tabla única con herencia: `Musician` con `birthDate` y `band`; `Band` con `creationDate` y `musicians[]`), campos `id`, `name`, `image`, `description`, `albums[]` | `Performer(id, type: PerformerType, name, imageUrl, description, date: LocalDate, albums: List<AlbumSummary>, members: List<PerformerSummary>, bandId: Int?)` | Los ids de músicos y bandas no chocan: comparten la secuencia de `performer`. `PerformerType { MUSICIAN, BAND }` |
| `Collector`: `id`, `name`, `telephone`, `email`, `favoritePerformers[]`, `comments[]`, `collectorAlbums[]` | `Collector(id, name, telephone, email, favorites: List<PerformerSummary>, albums: List<CollectorAlbum>)` | Sin imagen: avatar con iniciales |
| `CollectorAlbum`: `id`, `price`, `status` (`Active`/`Inactive`), `album` | `CollectorAlbum(album: AlbumSummary, price: Int, status: AlbumStatus)` | Se obtiene con `GET /collectors/:id/albums`; estado visible como "Disponible" / "No disponible" |
| Enumeraciones Joi: `genre` ∈ Classical, Salsa, Rock, Folk; `recordLabel` ∈ Sony Music, EMI, Discos Fuentes, Elektra, Fania Records; `rating` ∈ 0..5; `status` ∈ Active, Inactive | `Genre`, `RecordLabel`, `AlbumStatus` como `enum class` con `apiName` | Las listas se dibujan como chips en el formulario |

`PerformerSummary(id, type, name)` y `AlbumSummary(id, name, year, coverUrl)` evitan ciclos entre modelos.

### 5.2 Caché

Cada repositorio guarda en Room (2.7.2, la última versión que admite API 21) la última respuesta de sus listas y detalles, y la actualiza tras una escritura sobre ese recurso, sin cambiar las interfaces de los repositorios. TR-03 crea `AlbumEntity` y `TrackEntity` (Sprint 1); las demás entidades llegan con sus historias en el Sprint 2, con una migración por versión del esquema:

| Entidad Room | Clave | Relación |
|---|---|---|
| `AlbumEntity` (Sprint 1) | `id` | — |
| `TrackEntity` (Sprint 1) | `id` | `albumId` → `AlbumEntity`, `CASCADE` al borrar |
| `CommentEntity` | `id` | `albumId` → `AlbumEntity`; `collectorId`, `collectorName` opcionales |
| `PerformerEntity` | `id` | `type`, `bandId` opcional (músico que pertenece a una banda) |
| `AlbumPerformerCrossRef` | (`albumId`, `performerId`) | álbumes de un artista y artistas de un álbum |
| `CollectorEntity` | `id` | — |
| `CollectorFavoriteCrossRef` | (`collectorId`, `performerId`) | favoritos |
| `CollectorAlbumEntity` | (`collectorId`, `albumId`) | `price`, `status` |

### 5.3 Repositorios

```kotlin
interface AlbumRepository {
    suspend fun getAlbums(forceRefresh: Boolean = false): DataResult<List<Album>, DataError>         // HU01
    suspend fun getAlbum(id: Int, forceRefresh: Boolean = false): DataResult<Album, DataError>      // HU02
    suspend fun createAlbum(draft: NewAlbum): DataResult<Album, DataError>                         // HU07
    suspend fun addTrack(albumId: Int, draft: NewTrack): DataResult<Track, DataError>              // HU08
    suspend fun addComment(albumId: Int, draft: NewComment): DataResult<Comment, DataError>        // HU09
}

interface PerformerRepository {
    suspend fun getPerformers(forceRefresh: Boolean = false): DataResult<List<Performer>, DataError> // GET /musicians + GET /bands
    suspend fun getPerformer(id: Int, type: PerformerType): DataResult<Performer, DataError>
    suspend fun addAlbum(performerId: Int, type: PerformerType, albumId: Int): DataResult<Unit, DataError> // HU15
    suspend fun addMember(bandId: Int, musicianId: Int): DataResult<Unit, DataError>                     // HU12
}

interface CollectorRepository {
    suspend fun getCollectors(forceRefresh: Boolean = false): DataResult<List<Collector>, DataError>
    suspend fun getCollector(id: Int): DataResult<Collector, DataError> // GET /collectors/:id + /albums
    suspend fun addFavorite(collectorId: Int, performerId: Int, type: PerformerType): DataResult<Unit, DataError> // HU10
}
```

Tras cada escritura exitosa el repositorio invalida la caché del recurso afectado y el ViewModel de origen vuelve a pedirlo. Los POST de BackVynils responden 200 y sin autenticación.

## 6. Especificación por historia

Convenciones comunes a todas las pantallas: cabecera carbón (`view_header.xml`: título, flecha o X, chip de rol) sobre la hoja crema, cuatro estados resueltos (cargando, error, vacío, contenido) con los `include` de estado, listas con `RecyclerView`, imágenes con Glide y marcador de posición, textos en `strings.xml` (`05-reglas-de-diseno-ui.md`).

### Sprint 1 · Álbumes

**TR-01 a TR-06.** TR-01: tema `Theme.Vinilos` con el kit v2, `MainActivity` con `NavHostFragment` y `BottomNavigationView` de tres pestañas. TR-02: capa de red (Retrofit) con URL base configurable por `BuildConfig`. TR-03: `AppContainer` (inyección manual) y fábricas de ViewModel. TR-04: componentes XML compartidos (cabecera, chip de rol, tarjetas de lista, estados, ficha de dato, campo de texto, grupo de chips). TR-05: entorno de pruebas (JUnit, `InstantTaskExecutorRule`, MockWebServer, Espresso). TR-06: selector de rol y `SessionRepository`.

**Selector de rol (TR-06).** Logotipo, título "Vinilos", pregunta "¿Cómo quieres ingresar?" y dos tarjetas con icono, nombre y descripción: "Visitante: Consulta álbumes, artistas y coleccionistas" y "Coleccionista: Consulta y además crea álbumes, agrega canciones, comenta y administra artistas". Sin barra inferior.

**HU01 · Catálogo.** `RecyclerView` de `item_album_catalog.xml` (fila de 136 dp: número "01", carátula de 96 dp con el disco asomando, nombre en mayúsculas hasta dos líneas, artista principal, "Género · Año"; medidas en el diseño técnico de HU01, sección 3). Orden alfabético por nombre. Artista = primer `performer`; si no hay, "sin artista". Chevron a la derecha. Estado de error de pantalla completa con ilustración de alerta en anillos punteados, "No se pudo cargar el catálogo", "Revisa tu conexión e inténtalo de nuevo." y botón "Reintentar". Si hay datos en caché y falla el refresco, se muestra el contenido con `Snackbar`. Botón "+" (en la joroba de la barra inferior, según los mockups v2) solo para coleccionista; abre `albumCreateFragment` (HU07).

**HU02 · Detalle de álbum.** Cabecera con carátula, "Género · Año", nombre y artista; tres fichas: Sello, Lanzamiento (fecha corta "1 ago 1984") y Canciones (conteo); descripción; sección "Canciones" numerada `01`, `02` con duración a la derecha; sección "Comentarios" con avatar, nombre, estrellas (rating sobre 5) y texto. Para coleccionista: botón "Comentar" junto al título de la sección y botón "+" para agregar una canción (los mockups v2 lo ponen en la joroba de la barra inferior; el prototipo v1 decía "+ Canción", ver hallazgo 14). Álbum inexistente (404): estado de error con mensaje "No encontramos este álbum".

**HU07 · Nuevo álbum.** Pantalla completa con X. Aviso "Todos los campos son obligatorios". Vista previa de la portada cargada desde la URL escrita. Campos: Nombre, URL de la portada, Fecha de lanzamiento (`MaterialDatePicker`, formato `dd / mm / aaaa`), Descripción (multilínea), Género (chips de una sola selección: Classical, Salsa, Rock, Folk), Sello discográfico (chips: Sony Music, EMI, Discos Fuentes, Elektra, Fania Records). Botones inferiores "Cancelar" y "Crear álbum". Validación en el ViewModel al tocar "Crear álbum": "Campo obligatorio" bajo cada campo vacío, "Selecciona un género" y "Selecciona un sello" bajo los grupos de chips. Mientras envía, el formulario se atenúa y el botón se deshabilita. En éxito se vuelve al catálogo con snackbar "Álbum creado" y el nuevo álbum aparece en su posición alfabética, porque el catálogo ordena por nombre.

*Diseño técnico de HU07 (HU07-01).*

Estados de los mockups v2 (`docs/docs_daniela/Vynils temp version wireframe/HU07 · n …`):

| # | Estado | Qué se ve |
|---|---|---|
| 1 | Formulario vacío | Aviso, vista previa con la carátula gris por defecto, campos con su pista, chips sin selección, "Cancelar" y "Crear álbum" |
| 2 | Campos obligatorios | Tras tocar "Crear álbum" vacío: "Campo obligatorio" bajo los cuatro campos, "Selecciona un género" y "Selecciona un sello" bajo los chips |
| 3 | Datos sin fecha | Todo lleno salvo la fecha; la vista previa ya muestra la portada de la URL |
| 4 | Falta la fecha | Tras tocar "Crear álbum": solo la fecha muestra "Campo obligatorio" |
| 5 | Seleccionar fecha | `MaterialDatePicker` con título "Seleccionar fecha" y botones "Cancelar" y "Aceptar" |
| 6 | Listo para crear | Todo lleno, sin errores |
| 7 | Álbum creado | Catálogo con el álbum nuevo y snackbar "Álbum creado" sobre el "+" |

Validación (`AlbumFormValidator`, Kotlin puro en `viewmodels/`):

| Campo | Control | Regla | Error |
|---|---|---|---|
| Nombre | `TextInputEditText`, `inputType="textCapSentences"`, `actionNext` | Obligatorio: no vacío tras `isBlank()` | `error_required` |
| URL de la portada | `TextInputEditText`, `inputType="textUri"`, `actionNext` | Obligatorio | `error_required` |
| Fecha de lanzamiento | `TextInputEditText` de solo lectura que abre el `MaterialDatePicker` | Obligatoria | `error_required` |
| Descripción | `TextInputEditText` multilínea | Obligatoria | `error_required` |
| Género | `ChipGroup` de selección única con `Genre.entries` sin `UNKNOWN` | Uno seleccionado | `error_select_genre` |
| Sello | `ChipGroup` de selección única con `RecordLabel.entries` | Uno seleccionado | `error_select_label` |

- La validación corre al tocar "Crear álbum" y muestra todos los errores a la vez. Desde ese momento, cada cambio de un campo limpia su propio error.
- No se valida el formato de la URL ni que la fecha sea pasada: los mockups solo piden campos obligatorios y el backend no lo exige.
- El ViewModel expone los errores como tipos (`AlbumFormErrors` con un `FieldError?` por campo), nunca como textos; el Fragment los traduce a `strings.xml` (`02-arquitectura.md`, sección 4).
- Mientras `isSubmitting` es verdadero, un segundo toque de "Crear álbum" no hace nada.

Contrato de `POST /albums` (BackVynils, sin autenticación):

```json
{
  "name": "The Game",
  "cover": "https://ejemplo.com/portadas/the-game.jpg",
  "releaseDate": "1980-06-30T00:00:00.000Z",
  "description": "Álbum de estudio de Queen…",
  "genre": "Rock",
  "recordLabel": "EMI"
}
```

- Verificado contra el código de BackVynils (`src/album/album.controller.ts`, `album.service.ts`): responde **200** y devuelve el álbum guardado con `id`, `name`, `cover`, `releaseDate`, `description`, `genre` y `recordLabel`, sin las claves `tracks`, `performers` ni `comments`. Se mapea con el `AlbumDto.toModel()` existente (que ya tolera esas claves ausentes), así que llega "sin artista".
- El esquema Joi del backend exige los seis campos (`name`, `cover`, `releaseDate`, `description`, `genre`, `recordLabel`), cada texto no vacío, y **rechaza campos desconocidos**: el cuerpo no debe llevar `id` ni ningún otro campo. Un campo faltante, vacío, un campo extra o un `genre`/`recordLabel` fuera de las listas responde **400** y llega a la pantalla como `DataError.Http(400)`. El backend no usa 412 en este endpoint.
- La fecha elegida en el `MaterialDatePicker` (milisegundos UTC) se convierte a `LocalDate` en UTC y se envía como medianoche UTC, que es como `parseReleaseDate` de `AlbumMappers.kt` la vuelve a leer: el año no se corre en UTC-5.

Clases (`02-arquitectura.md`, sección 2):

| Capa | Clase | Responsabilidad |
|---|---|---|
| `models/` | `RecordLabel` | `enum class` con `apiName`, como `Genre` |
| `models/` | `NewAlbum(name, coverUrl, releaseDate: LocalDate, description, genre: Genre, recordLabel: RecordLabel)` | Borrador que el formulario entrega al repositorio |
| `network/dto/` | `NewAlbumRequest` y `NewAlbum.toRequest()` | Cuerpo del POST; da formato ISO a la fecha |
| `network/` | `VinilosApiService.createAlbum(body)`, `AlbumServiceAdapter.createAlbum(draft): Album` | Llamada y mapeo de la respuesta |
| `repositories/` | `AlbumRepository.createAlbum(draft): DataResult<Album, DataError>` | `networkRequest` y, si tiene éxito, `AlbumDao.upsert` del álbum recibido |
| `viewmodels/` | `AlbumFormValidator`, `AlbumCreateUiState`, `AlbumCreateEvent`, `AlbumCreateViewModel` | Valores, errores por campo, envío y eventos de una sola vez |
| `ui/albums/` | `AlbumCreateFragment`, `fragment_album_create.xml` | Dibuja el estado, abre el selector de fecha, carga la vista previa con Glide |

`Album.recordLabel` sigue siendo `String`: HU07 no cambia el modelo ni la entidad de HU01.

Flujo:

1. "+" del catálogo → `AlbumListViewModel.onCreateAlbumClicked()` emite `OpenCreateAlbum` → `AlbumListFragment` navega con `action_albumList_to_albumCreate`. Reemplaza al aviso `album_create_pending` de HU01, que se elimina.
2. "X", "Cancelar" y el botón atrás cierran el formulario sin crear nada.
3. Al crear, `AlbumCreateFragment` deja `album_created = true` en `previousBackStackEntry.savedStateHandle` y cierra con `popBackStack()`.
4. `AlbumListFragment` observa esa clave, la borra al consumirla (no se repite al rotar) y llama a `AlbumListViewModel.onAlbumCreated()`, que vuelve a leer Room y emite `ShowAlbumCreated` para el snackbar `snack_album_created` anclado con `PrimaryActionHost.snackbarAnchor`.
5. Si el envío falla, el formulario conserva lo escrito y muestra `create_album_error` en un snackbar. Si el POST sale bien pero Room no logra guardar la respuesta (`SQLiteException`), el repositorio informa éxito igual: el álbum ya existe en el servidor y un error haría que el usuario lo duplicara al reintentar. El álbum entra a la caché al deslizar para actualizar o al abrir de nuevo la app.
6. El chip de rol del formulario hace lo mismo que en el catálogo: limpia el rol y vuelve al selector.

**HU08 · Nueva canción.** `TrackCreateBottomSheet` (`BottomSheetDialogFragment`) sobre el detalle, con sobretítulo "BUSCANDO AMÉRICA · CANCIÓN 03" (nombre del álbum y número que tendrá la pista), campos Nombre y Duración (mm:ss) con teclado numérico, botones "Cancelar" y "Guardar canción". Errores: "Campo obligatorio" e "Ingresa la duración con formato mm:ss" (regex `^\d{1,2}:[0-5]\d$`). En éxito se cierra la hoja, la ficha "Canciones" sube a 3, la pista aparece numerada y se muestra "Canción agregada al álbum".

### Sprint 2 · Artistas y coleccionistas

**HU03 · Lista de artistas.** `ChipGroup` con selección única: "Todos", "Músicos", "Bandas". Tarjeta con imagen, nombre, "Músico · 1947" o "Banda · desde 1970" y tercera línea: "2 álbumes · 4 integrantes" para banda, "Integrante de Queen" o "N álbumes" para músico. La unión de `GET /musicians` y `GET /bands` se hace en `PerformerServiceAdapter`; la pertenencia a banda se deriva de `bands[].musicians`. Orden: por nombre, con el filtro aplicado en el ViewModel sobre la lista cargada.

**HU04 · Detalle de artista.** Título "Músico" o "Banda" según el tipo. Cabecera con imagen, "Músico · nació el 16 jul 1948" o "Banda · desde 1970", nombre y descripción. Banda: sección "Integrantes · N" (lista de músicos) y "Álbumes · N". Músico: solo "Álbumes · N". Para coleccionista, cada sección lleva a la derecha un `MaterialButton` con estilo de contorno: "Agregar integrante" (solo banda) y "Agregar álbum". Los premios (`performerPrizes`) no se muestran en el alcance comprometido.

**HU05 · Lista de coleccionistas.** Tarjeta con avatar de iniciales, nombre, correo y "N álbumes · M favoritos" ("Sin álbumes aún" cuando no tiene), a partir de los arreglos de `GET /collectors`.

**HU06 · Detalle de coleccionista.** Cabecera con avatar, sobretítulo "Coleccionista" y nombre; fichas Teléfono y Correo; sección "Artistas favoritos · N" (imagen, nombre, tipo); sección "Sus álbumes · N" con precio a la derecha ("$ 35") y chip de estado "DISPONIBLE" / "NO DISPONIBLE". Requiere dos llamadas: `GET /collectors/:id` y `GET /collectors/:id/albums`. Para coleccionista, botón "Agregar favorito" en la sección de favoritos.

### Sprint 3 · Funcionalidades adicionales

**HU09 · Comentar álbum.** `CommentCreateBottomSheet` (`BottomSheetDialogFragment`) con sobretítulo del álbum y título "Comentar álbum". Grupo "¿Quién comenta?" con chips de los coleccionistas (una selección). Grupo "Valoración (0 a 5)" con seis chips circulares `0` a `5`; al elegir, la etiqueta cambia a "Valoración · 4 de 5". Campo "Comentario" multilínea con marcador "Escribe tu opinión". Botones "Cancelar" y "Publicar comentario". Errores: "Selecciona un coleccionista", "Selecciona una valoración", "Campo obligatorio". Envío: `POST /albums/:albumId/comments` con `description`, `rating` y `collector: { id }`. En éxito, el comentario aparece al final de la lista con el nombre del coleccionista y se muestra "Comentario publicado". Los coleccionistas de la hoja salen de `CollectorRepository.getCollectors()`.

**HU15 · Agregar un álbum a un artista.** `albumPickerFragment` a pantalla completa con X, título "Elegir álbum", subtítulo "para Rubén Blades Bellido de Luna", campo "Buscar en el catálogo" (filtro local por nombre y artista), lista con `RadioButton`, nombre, "Artista · Año" (o "sin artista"). Los álbumes que ya pertenecen al artista se muestran atenuados, sin radio activo y con chip "Ya agregado". Botón inferior "Selecciona un álbum" deshabilitado hasta elegir; luego "Agregar «Poeta del pueblo»". Envío: `POST /musicians/:id/albums/:albumId` o `POST /bands/:id/albums/:albumId`. En éxito vuelve al detalle con "Álbum agregado al artista" y la sección "Álbumes · N" incrementada.

**HU12 · Agregar músicos a una banda.** `musicianPickerFragment` con título "Elegir músico", subtítulo "para Queen", campo "Buscar músico", lista de músicos con "Músico · Año" y, cuando aplica, "Integrante de The Beatles". Los que ya integran la banda llevan chip "Ya integrante" y quedan deshabilitados. Si se elige un músico con otra banda, la app bloquea el envío y muestra un aviso en la parte superior: "Ringo Starr ya pertenece a otra banda (The Beatles). Un músico solo puede estar en una banda." Esta regla se valida en el ViewModel porque el backend reasigna la banda sin error. Envío: `POST /bands/:bandId/musicians/:musicianId`. En éxito: "Integrante agregado a la banda" e "Integrantes · N" incrementado.

**HU10 · Agregar artistas favoritos.** `performerPickerFragment` con título "Elegir artista", subtítulo "para Manolo Bellon", campo "Buscar artista", lista con músicos y bandas ("Músico · 1948", "Banda · desde 1970"); favoritos actuales con chip "Ya es favorito" y deshabilitados (la app evita duplicados porque el backend no lo verifica). Botón "Agregar «Freddie Mercury» a favoritos". Envío: `POST /collectors/:collectorId/musicians/:musicianId` o `.../bands/:bandId`. En éxito: "Artista favorito agregado" y "Artistas favoritos · N" incrementado.

**Combinaciones.** El prototipo dibuja HU08 + HU09 sobre el mismo álbum y HU12 + HU15 sobre la misma banda en cualquier orden. Se cumple porque las hojas y los selectores no guardan estado propio: cada escritura invalida la caché y el detalle vuelve a cargar el recurso.

## 7. Del kit a Material Components

El prototipo v1 era de media fidelidad en escala de grises con Inter. El aspecto final lo dan los mockups v2 y su "Kit v2 · Componentes": hoja crema `#F2EFE7` sobre cabecera carbón `#232323`, grises `#66645E` y `#8A8780`, fuentes Outfit y Montserrat (tokens en `05-reglas-de-diseno-ui.md`, sección 1). Cada elemento del kit se construye con Material Components 1.12:

| Elemento del kit | Componente (Vistas) | Notas |
|---|---|---|
| Botón primario (relleno carbón) | `MaterialButton` con el estilo del tema | "Crear álbum", "Guardar canción", "Publicar comentario", "Agregar «…»" |
| Botón secundario (borde) | `MaterialButton` con `Widget.Material3.Button.OutlinedButton` | "Cancelar", "Reintentar", "Agregar álbum / integrante / favorito", "Comentar" |
| Botón deshabilitado | `MaterialButton` con `isEnabled = false` | Mientras no hay selección o mientras se envía |
| Campo y campo con error | `TextInputLayout` + `TextInputEditText`, `error` en el `TextInputLayout` | Texto de error bajo el campo |
| Chips de filtro y de selección | `Chip` dentro de `ChipGroup` con `singleSelection` | Filtros de artistas, género, sello, coleccionista y valoración |
| Chip de estado ("Ya agregado", "DISPONIBLE") | `Chip` no clicable con estilo propio | Solo informativo |
| Chip de rol (arriba a la derecha) | `Chip` en `view_header.xml` | "⇄ VISITANTE" / "⇄ COLECCIONISTA"; vuelve al selector de rol |
| Barra inferior con tres destinos, punto bajo la activa y joroba con "+" | `BottomNavigationView` + `FloatingActionButton` centrado (solo coleccionista) | Íconos vectoriales propios: disco, micrófono, personas |
| Tarjeta de lista (número, imagen, dos o tres líneas) | `item_*.xml` con `ConstraintLayout` | `ImageView` 56 dp con Glide; avatar de iniciales para coleccionistas |
| Fichas de datos (Sello, Lanzamiento, Canciones, Teléfono, Correo) | `view_info_tile.xml` | Etiqueta en Montserrat mayúsculas y valor en Outfit |
| Hojas "Nueva canción" y "Comentar álbum" | `BottomSheetDialogFragment` | Con asa superior como en los mockups |
| Selector de fecha | `MaterialDatePicker` | Devuelve milisegundos en UTC: se convierte con `Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()` (con la zona local la fecha se corre un día en Colombia) y se envía en ISO 8601 |
| Barra de búsqueda de los selectores | `TextInputLayout` con ícono de búsqueda | Filtro local, sin llamada al backend |
| Mensaje de confirmación | `Snackbar` | Disparado por un `Event` del ViewModel |
| Aviso de bloqueo (HU12) | `MaterialCardView` con color de error | Encima de la lista, no bloquea la pantalla |
| Imagen y avatar sin datos | `ImageView` con marcador vectorial | Nunca un PNG de relleno |

## 8. Textos visibles

Claves para `res/values/strings.xml` según el prototipo. El código usa los nombres de la columna izquierda; los textos pueden ajustarse en el PR que los introduce.

| Clave | Texto |
|---|---|
| `role_title` / `role_question` | Vinilos / ¿Cómo quieres ingresar? |
| `role_visitor` / `role_visitor_description` | Visitante / Consulta álbumes, artistas y coleccionistas. |
| `role_collector` / `role_collector_description` | Coleccionista / Consulta y además crea álbumes, agrega canciones, comenta y administra artistas. |
| `role_change_hint` | Puedes cambiar de rol desde el catálogo. |
| `nav_albums` / `nav_artists` / `nav_collectors` | Álbumes / Artistas / Coleccionistas |
| `album_list_title` / `album_detail_title` | Catálogo / Álbum |
| `album_list_error_title` / `album_list_error_body` / `action_retry` | No se pudo cargar el catálogo / Revisa tu conexión e inténtalo de nuevo. / Reintentar |
| `album_no_artist` | sin artista |
| `album_list_empty` | Aún no hay álbumes en el catálogo |
| `album_list_offline_notice` | Sin conexión. Mostrando los datos guardados |
| `album_list_refresh_error` | No se pudo actualizar el catálogo |
| `album_genre_year` | %1$s · %2$d |
| `album_label` / `album_release` / `album_tracks` | Sello / Lanzamiento / Canciones |
| `album_section_tracks` / `album_section_comments` | Canciones / Comentarios |
| `album_action_add_track` / `album_action_comment` | Canción / Comentar |
| `create_album_title` / `create_album_all_required` | Nuevo álbum / Todos los campos son obligatorios. |
| `create_album_cover_hint` | Vista previa. La portada se carga desde la URL que pegues abajo. |
| `field_name` / `field_cover_url` / `field_release_date` / `field_description` / `field_genre` / `field_record_label` | Nombre / URL de la portada / Fecha de lanzamiento / Descripción / Género / Sello discográfico |
| `hint_album_name` / `hint_description` | Nombre del álbum / Describe el álbum |
| `hint_cover_url` | https://ejemplo.com/portada.jpg |
| `hint_release_date` | dd / mm / aaaa |
| `create_album_cover_description` / `date_picker_title` / `action_accept` | Vista previa de la portada / Seleccionar fecha / Aceptar |
| `error_required` / `error_select_genre` / `error_select_label` | Campo obligatorio / Selecciona un género / Selecciona un sello |
| `create_album_error` | No se pudo crear el álbum. Inténtalo de nuevo. |
| `action_cancel` / `action_create_album` / `snack_album_created` | Cancelar / Crear álbum / Álbum creado |
| `track_sheet_title` / `track_sheet_subtitle` | Nueva canción / %1$s · Canción %2$02d |
| `field_track_name` / `field_track_duration` / `error_duration_format` | Nombre / Duración (mm:ss) / Ingresa la duración con formato mm:ss |
| `action_save_track` / `snack_track_added` | Guardar canción / Canción agregada al álbum |
| `artist_list_title` / `filter_all` / `filter_musicians` / `filter_bands` | Artistas / Todos / Músicos / Bandas |
| `artist_musician` / `artist_band` / `artist_born` / `artist_since` | Músico / Banda / nació el %1$s / desde %1$d |
| `artist_member_of` / `artist_albums_count` / `artist_members_count` | Integrante de %1$s / %1$d álbumes / %1$d integrantes |
| `artist_section_members` / `artist_section_albums` | Integrantes · %1$d / Álbumes · %1$d |
| `action_add_member` / `action_add_album` | Agregar integrante / Agregar álbum |
| `collector_list_title` / `collector_detail_title` | Coleccionistas / Coleccionista |
| `collector_summary` / `collector_no_albums` | %1$d álbumes · %2$d favoritos / Sin álbumes aún |
| `collector_phone` / `collector_email` | Teléfono / Correo |
| `collector_section_favorites` / `collector_section_albums` | Artistas favoritos · %1$d / Sus álbumes · %1$d |
| `album_status_available` / `album_status_unavailable` | Disponible / No disponible |
| `action_add_favorite` | Agregar favorito |
| `comment_sheet_title` / `comment_who` / `comment_rating` / `comment_rating_value` / `field_comment` / `comment_hint` | Comentar álbum / ¿Quién comenta? / Valoración (0 a 5) / Valoración · %1$d de 5 / Comentario / Escribe tu opinión |
| `error_select_collector` / `error_select_rating` | Selecciona un coleccionista / Selecciona una valoración |
| `action_publish_comment` / `snack_comment_published` | Publicar comentario / Comentario publicado |
| `picker_album_title` / `picker_musician_title` / `picker_artist_title` | Elegir álbum / Elegir músico / Elegir artista |
| `picker_for` | para %1$s |
| `picker_search_catalog` / `picker_search_musician` / `picker_search_artist` | Buscar en el catálogo / Buscar músico / Buscar artista |
| `picker_select_album` / `picker_select_musician` / `picker_select_artist` | Selecciona un álbum / Selecciona un músico / Selecciona un artista |
| `picker_add_named` / `picker_add_to_band` / `picker_add_favorite_named` | Agregar «%1$s» / Agregar a %1$s / Agregar «%1$s» a favoritos |
| `picker_already_added` / `picker_already_member` / `picker_already_favorite` | Ya agregado / Ya integrante / Ya es favorito |
| `picker_member_elsewhere` | %1$s ya pertenece a otra banda (%2$s). Un músico solo puede estar en una banda. |
| `snack_album_added_to_artist` / `snack_member_added` / `snack_favorite_added` | Álbum agregado al artista / Integrante agregado a la banda / Artista favorito agregado |

## 9. Reparto por sprint

| Sprint | Pantallas y componentes | Historias | Tareas transversales |
|---|---|---|---|
| 1 | Selector de rol, catálogo, detalle de álbum, nuevo álbum, hoja de canción; tema, barra inferior, componentes compartidos, red, inyección manual, pruebas | HU01, HU02, HU07, HU08 | TR-01 a TR-06 |
| 2 | Lista y detalle de artistas (músico y banda), lista y detalle de coleccionistas, avatar de iniciales, `view_info_tile.xml` | HU03, HU04, HU05, HU06 | — |
| 3 | Hoja de comentario, `fragment_picker.xml` y sus tres variantes, aviso de bloqueo, botones "Agregar" en detalles | HU09, HU15, HU12, HU10 | — |

El Sprint 1 debe dejar el rol, la barra inferior y la plantilla de pantalla cerrados, porque todas las pantallas posteriores los reutilizan. El selector del Sprint 3 (`fragment_picker.xml` y `PickerAdapter`) se diseña una sola vez (tarea HU15-01) y HU12 y HU10 solo lo parametrizan.

## 10. Hallazgos del análisis y decisiones pendientes

Resultado de contrastar wiki, prototipo, documento del curso y código del backend. Cada punto tiene una propuesta; las marcadas "equipo" se ratifican en la reunión del lunes y se registran en la bitácora.

1. **Nombre del autor de un comentario (HU02, HU09).** Verificado el 2026-10-19 contra el backend local: `GET /albums/:id` devuelve cada comentario solo con `id`, `description` y `rating`, sin coleccionista. El prototipo muestra "Manolo Bellon" junto a cada comentario. Decisión: la app conserva `collectorId` y `collectorName` en la caché al publicar (HU09) y muestra "Coleccionista" en los comentarios cuyo autor se desconoce; `GET /collectors` sí trae los comentarios de cada coleccionista, así que en Sprint 3 puede cruzarse esa lista para resolver el nombre. Dato adicional de la semilla: `/bands` devuelve Queen con `musicians` vacío; la lista de integrantes del prototipo solo aparecerá al agregar músicos (HU12).
2. **Músico en otra banda (HU12).** El backend reasigna la banda sin error; el prototipo bloquea con un aviso. Se valida en el ViewModel con la pertenencia derivada de `bands[].musicians`. Decidido por el prototipo.
3. **Favorito duplicado (HU10) y álbum ya asociado (HU15).** El backend no lo impide; la app deshabilita los ítems con chip "Ya es favorito" / "Ya agregado". Decidido por el prototipo.
4. **Álbumes del coleccionista (HU06).** `GET /collectors/:id` no anida el álbum en `collectorAlbums`; hace falta `GET /collectors/:id/albums`. `getCollector` hace las dos llamadas en paralelo con `coroutineScope { async }`.
5. **Rol en memoria.** No se persiste; reiniciar la app vuelve al selector. Equipo: confirmar o guardarlo en `SharedPreferences` (disponible en API 21 sin librerías).
6. **Tareas transversales.** El backlog define seis (TR-01 a TR-06), pero las páginas Sprint 1 y Milestone 1 de la wiki dicen "TR-01 a TR-05". Corregir en la wiki e incluir TR-06 en las condiciones de cierre del Milestone 1.
7. **Enlaces pendientes en la wiki.** La página Inception sigue con "Repositorio: pendiente", "Tablero: pendiente", "Prototipo: pendiente", fechas de milestones "pendientes de registrar" y las páginas de sprint con "Fecha de inicio: Por definir", aunque los milestones, el tablero y el wireframe ya existen. Completar en la wiki.
8. **Orden de la lista de artistas (HU03).** El prototipo mezcla bandas y músicos sin orden visible. Propuesta: orden alfabético por nombre en "Todos" y en cada filtro. Equipo.
9. **Fechas.** El formulario usa `dd / mm / aaaa` y el detalle "1 ago 1984" y "nació el 16 jul 1948"; el backend recibe y devuelve ISO 8601. Conversión en los mappers con `java.time` y formato de la configuración regional.
10. **Vocabulario.** La UI dice "Canción" y el código `Track`; la UI dice "Integrante" y el backend `musician`. Se añaden al glosario de `02-arquitectura.md` en el PR que cree los modelos.
11. **Paleta y tipografía.** El prototipo v1 es gris con Inter; la app sigue el kit de los mockups v2 (crema, carbón, Outfit y Montserrat), sección 7.
12. **Backlog alterno.** El detalle de artista y el de coleccionista reservan sitio para "Agregar premio" (HU14/HU13) y "Agregar álbum" con precio y estado (HU11) sin diseñar esas pantallas. Si se incorporan, reutilizan el selector común y el formulario corto de HU07.
13. **Tablero "Grupo" del prototipo.** La exportación salió en blanco; si se quiere incluir en la wiki, exportarlo con fondo o reemplazarlo por texto.
14. **Botón para agregar una canción (HU08).** El prototipo v1 dibuja un FAB extendido "+ Canción"; los mockups v2, un "+" en la joroba de la barra inferior. Equipo: decidir uno y alinear el Gherkin de HU08 y los casos CP.
15. **Duración de la canción (HU08).** Los mockups v2 usan un teclado propio "00:00" con "Limpiar" y borrar; la expresión `^\d{1,2}:[0-5]\d$` de la sección 6 acepta `60:00` y `00:00`, que el Gherkin rechaza. Equipo: fijar la regla (mínimo `00:01`, máximo `59:59`) antes de implementar HU08.
16. **Datos de los mockups frente a la semilla.** El mockup del catálogo muestra "Poeta del pueblo · SIN ARTISTA · 1974"; la semilla de BackVynils lo trae con Rubén Blades y 1984. Las pruebas usan los datos de la semilla.

## 11. Bitácora de decisiones

| Fecha | Decisión |
|---|---|
| 2026-10-14 | Documento creado a partir de la wiki (commit `a7cc5c3`), los seis tableros del prototipo y el código de BackVynils. Selector de rol inicial con chip de rol en todas las pantallas; hojas inferiores para canción y comentario; tres selectores sobre un `PickerScreen` común; validaciones del lado de la app para banda única, favoritos y álbumes repetidos. Pendientes de ratificar: persistencia del rol, orden de artistas. |
| 2026-10-26 | Reinicio con Vistas: destinos del grafo de navegación con argumentos en `Bundle`, hojas como `BottomSheetDialogFragment`, `SessionRepository` con `LiveData`, caché en memoria en los repositorios (Room opcional), kit v2 llevado a Material Components; hallazgos 14 a 16 sobre el botón de canción, la regla de duración y los datos de los mockups. |
| 2026-10-27 | TR-03: Room deja de ser opcional; `AlbumEntity` y `TrackEntity` en Sprint 1, el resto de entidades en Sprint 2. |
| 2026-10-28 | HU01-01: el catálogo se guarda en Room (`AlbumEntity` con `performerName`, base de datos versión 2), orden alfabético, fila propia `item_album_catalog.xml` con las medidas del mockup (carátula de 96 dp, ya no 56 dp), estado de error con ilustración, botón "+" en la joroba de la barra para el coleccionista (lo muestra el Fragment según `canEdit` de su `UiState`) y cinco textos nuevos del catálogo (`album_list_empty`, `album_list_offline_notice`, `album_list_refresh_error`, `album_genre_year`, `album_create_pending`). |
| 2026-10-28 | HU07-01: diseño técnico del formulario "Nuevo álbum" (sección 6): siete estados, validación de campos obligatorios en `AlbumFormValidator` con errores como tipos, contrato de `POST /albums` con fecha en medianoche UTC, `RecordLabel` con `apiName` (antes decía `apiValue`), `NewAlbumRequest` como cuerpo del POST, resultado al catálogo por `savedStateHandle`. El álbum nuevo aparece en su posición alfabética (antes decía "al final"); `album_create_pending` se retira y se suman los textos de pista, selector de fecha y error al crear. |
