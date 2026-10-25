# 10. Diseño de la aplicación

Documento de diseño que desarrolla la planeación del Inception: convierte el backlog, la distribución por sprints, los acuerdos de equipo y el prototipo navegable en una especificación concreta de roles, navegación, pantallas, datos, validaciones y textos para construir Vinilos con la arquitectura de `02-arquitectura.md` y las reglas de `05-reglas-de-diseno-ui.md`.

## 1. Fuentes y alcance

| Fuente | Qué aporta | Estado revisado |
|---|---|---|
| Wiki del proyecto (Home, Acuerdos de Equipo, Inception, Backlog de Producto, HU01 a HU15, Sprint 1 a 3, Milestones 1 a 3, Wireframe navegable, actas) | Historias comprometidas, puntos, tareas, responsables, sprints, roles del prototipo | Commit `a7cc5c3` del 2026-10-14 |
| Prototipo de media fidelidad en Figma (64 pantallas, 360 x 800) exportado en seis tableros | Pantallas, flujos, estados, validaciones y mensajes por historia; kit de componentes | Revisado el 2026-10-14 en Figma (enlace en la página "Wireframe navegable" de la wiki) |
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

1. El rol se muestra como chip en la esquina superior derecha de **todas** las pantallas (`Visitante` o `Coleccionista`). Tocarlo vuelve al selector de rol; el texto de la pantalla inicial lo anuncia: "Puedes cambiar de rol desde el catálogo".
2. El rol vive en un `SessionRepository` de alcance `@Singleton` con `StateFlow<UserRole?>` en memoria. Si el proceso muere, la app vuelve al selector; no se persiste en disco en el MVP (evita añadir DataStore sin acuerdo del equipo, regla 10 de la skill). Cada `UiState` de pantalla incluye `val canEdit: Boolean` derivado del rol, y los composables muestran u ocultan acciones con ese valor; nunca consultan el repositorio de sesión directamente.
3. Quién comenta (HU09): como no hay usuario autenticado, la hoja "Comentar álbum" incluye un selector de coleccionista con chips (`¿Quién comenta?`), tal como lo dibuja el prototipo. Se descarta la alternativa de un "coleccionista actual" fijo.

```kotlin
enum class UserRole { VISITOR, COLLECTOR }

interface SessionRepository {
    val role: StateFlow<UserRole?>
    fun selectRole(role: UserRole)
    fun clearRole()
}
```

## 3. Mapa de navegación

Barra inferior con tres destinos, idéntica en ambos roles. Las historias 9 a 15 no añaden pestañas: se entra a ellas por botones en los detalles y por listas existentes en "modo selección".

```
RoleSelect (inicial, sin barra inferior)
└── VinilosNavHost con NavigationBar
    ├── ÁLBUMES
    │   ├── AlbumList                                   HU01   FAB "+" (coleccionista)
    │   │   ├── CreateAlbum (pantalla completa, X)      HU07
    │   │   └── AlbumDetail(albumId)                    HU02
    │   │       ├── hoja AddTrack                       HU08   FAB "Canción"
    │   │       ├── hoja Comment                        HU09   botón "Comentar"
    │   │       └── (backlog alterno) sin cambios
    ├── ARTISTAS
    │   ├── ArtistList (filtro Todos / Músicos / Bandas) HU03
    │   └── ArtistDetail(artistId, type)                HU04
    │       ├── AlbumPicker(artistId, type)             HU15   botón "Agregar álbum"
    │       └── MusicianPicker(bandId)                  HU12   botón "Agregar integrante" (solo banda)
    │       └── (backlog alterno) "Agregar premio"      HU14/HU13
    └── COLECCIONISTAS
        ├── CollectorList                               HU05
        └── CollectorDetail(collectorId)                HU06
            ├── ArtistPicker(collectorId)               HU10   botón "Agregar favorito"
            └── (backlog alterno) "Agregar álbum"       HU11
```

Rutas tipadas (`core/ui/navigation/Routes.kt`); sustituyen la lista provisional de `05-reglas-de-diseno-ui.md`:

```kotlin
@Serializable data object RoleSelect
@Serializable data object AlbumList
@Serializable data class AlbumDetail(val albumId: Int)
@Serializable data object CreateAlbum
@Serializable data object ArtistList
@Serializable data class ArtistDetail(val artistId: Int, val type: ArtistType)
@Serializable data class AlbumPicker(val artistId: Int, val type: ArtistType)
@Serializable data class MusicianPicker(val bandId: Int)
@Serializable data object CollectorList
@Serializable data class CollectorDetail(val collectorId: Int)
@Serializable data class ArtistPicker(val collectorId: Int)
```

Comportamiento de navegación:

- `RoleSelect` es el destino inicial del `NavHost`; al elegir rol se navega a `AlbumList` con `popUpTo(RoleSelect) { inclusive = true }`. El chip de rol navega de vuelta a `RoleSelect` limpiando la pila.
- Los detalles se apilan sobre su pestaña y vuelven con la flecha y con el gesto de retroceso. Los selectores (`*Picker`) y `CreateAlbum` abren a pantalla completa con icono "X" y vuelven al detalle que los abrió.
- `AddTrack` y `Comment` no son rutas: son `ModalBottomSheet` controlados por el `UiState` de `AlbumDetail` (`activeSheet: AlbumSheet?`). Así el prototipo "HU08 + HU09 en cualquier orden" se cumple sin apilar destinos.
- Tras una escritura exitosa se vuelve a la pantalla de origen y se muestra un `Snackbar` de confirmación; el contenido se actualiza solo porque la pantalla observa Room.

## 4. Inventario de pantallas

Nueve pantallas base, un selector de rol, un formulario, dos hojas y tres selectores. Cada una es un `XScreen(uiState, onAction)` con su `XRoute()` y su `XViewModel`.

| # | Pantalla | Paquete `feature/` | HU | Estados dibujados en el prototipo |
|---|---|---|---|---|
| 0 | Selector de rol | `session/` | TR-06 | Único |
| 1 | Catálogo de álbumes | `albums/list/` | HU01 | Contenido (5 álbumes), error con "Reintentar"; cargando y vacío se añaden según la plantilla de pantalla |
| 2 | Detalle de álbum | `albums/detail/` | HU02, HU08, HU09 | Contenido con canciones y comentarios; con hoja de canción; con hoja de comentario; con snackbar de confirmación |
| 3 | Nuevo álbum | `albums/create/` | HU07 | Vacío, errores por campo (obligatorio, género no seleccionado), lleno, selector de fecha, enviado, catálogo con snackbar "Álbum creado" |
| 4 | Lista de artistas | `artists/list/` | HU03 | Todos, Músicos, Bandas |
| 5 | Detalle de músico | `artists/detail/` | HU04, HU15 | Contenido con álbumes; con snackbar "Álbum agregado al artista" |
| 6 | Detalle de banda | `artists/detail/` (misma pantalla, secciones según tipo) | HU04, HU12, HU15 | Contenido con integrantes y álbumes; con snackbars |
| 7 | Lista de coleccionistas | `collectors/list/` | HU05 | Contenido (4 coleccionistas) |
| 8 | Detalle de coleccionista | `collectors/detail/` | HU06, HU10 | Contenido con favoritos y álbumes con precio y estado; con snackbar |
| 9 | Hoja "Nueva canción" | `albums/detail/` | HU08 | Vacía, con errores (obligatorio, formato mm:ss), llena |
| 10 | Hoja "Comentar álbum" | `albums/detail/` | HU09 | Vacía, con errores (coleccionista, valoración, texto), llena |
| 11 | Elegir álbum | `artists/picker/` | HU15 | Sin selección (botón deshabilitado), con selección; ítems "Ya agregado" deshabilitados |
| 12 | Elegir músico | `artists/picker/` | HU12 | Sin selección, con selección, con error "pertenece a otra banda"; ítems "Ya integrante" |
| 13 | Elegir artista | `collectors/picker/` | HU10 | Sin selección, con selección; ítems "Ya es favorito" |

Los tres selectores comparten un componente `PickerScreen` en `core/ui/components` (campo de búsqueda, lista con `RadioButton`, chip de estado por ítem, botón inferior con el nombre elegido entre comillas angulares: "Agregar «The Game»").

## 5. Modelo de datos

### 5.1 Del backend al dominio

| Backend (entidad, campos) | Dominio (`domain/model`) | Observaciones |
|---|---|---|
| `Album`: `id`, `name`, `cover`, `releaseDate`, `description`, `genre`, `recordLabel`, `tracks[]`, `performers[]`, `comments[]` | `Album(id, name, coverUrl, releaseDate: LocalDate, description, genre: Genre, recordLabel: RecordLabel, tracks: List<Track>, performers: List<ArtistSummary>, comments: List<Comment>)` | `GET /albums` ya trae tracks, performers y comments: la tarjeta muestra el artista sin otra llamada |
| `Track`: `id`, `name`, `duration` (texto) | `Track(id, name, duration: String)` | Duración se guarda como texto `mm:ss`; se valida en la app |
| `Comment`: `id`, `description`, `rating`, `collector` | `Comment(id, description, rating: Int, collectorId: Int?, collectorName: String?)` | Ver hallazgo 1 sobre el nombre del autor |
| `Performer` (tabla única con herencia: `Musician` con `birthDate` y `band`; `Band` con `creationDate` y `musicians[]`), campos `id`, `name`, `image`, `description`, `albums[]` | `Artist(id, type: ArtistType, name, imageUrl, description, date: LocalDate, albums: List<AlbumSummary>, members: List<ArtistSummary>, bandId: Int?)` | Los ids de músicos y bandas no chocan: comparten la secuencia de `performer`. `ArtistType { MUSICIAN, BAND }` |
| `Collector`: `id`, `name`, `telephone`, `email`, `favoritePerformers[]`, `comments[]`, `collectorAlbums[]` | `Collector(id, name, telephone, email, favorites: List<ArtistSummary>, albums: List<CollectorAlbum>)` | Sin imagen: avatar con iniciales |
| `CollectorAlbum`: `id`, `price`, `status` (`Active`/`Inactive`), `album` | `CollectorAlbum(album: AlbumSummary, price: Int, status: AlbumStatus)` | Se obtiene con `GET /collectors/:id/albums`; estado visible como "Disponible" / "No disponible" |
| Enumeraciones Joi: `genre` ∈ Classical, Salsa, Rock, Folk; `recordLabel` ∈ Sony Music, EMI, Discos Fuentes, Elektra, Fania Records; `rating` ∈ 0..5; `status` ∈ Active, Inactive | `Genre`, `RecordLabel`, `AlbumStatus` como `enum class` con `apiValue` | Las listas se dibujan como chips en el formulario |

`ArtistSummary(id, type, name)` y `AlbumSummary(id, name, year, coverUrl)` evitan ciclos entre modelos.

### 5.2 Room, fuente única de verdad

| Entidad Room | Clave | Relación |
|---|---|---|
| `AlbumEntity` | `id` | — |
| `TrackEntity` | `id` | `albumId` → `AlbumEntity` |
| `CommentEntity` | `id` | `albumId` → `AlbumEntity`; `collectorId`, `collectorName` opcionales |
| `ArtistEntity` | `id` | `type`, `bandId` opcional (músico que pertenece a una banda) |
| `AlbumArtistCrossRef` | (`albumId`, `artistId`) | álbumes de un artista y artistas de un álbum |
| `CollectorEntity` | `id` | — |
| `CollectorFavoriteCrossRef` | (`collectorId`, `artistId`) | favoritos |
| `CollectorAlbumEntity` | (`collectorId`, `albumId`) | `price`, `status` |

Lecturas con `@Transaction` y `@Relation` para `AlbumWithDetails`, `ArtistWithAlbums`, `CollectorWithDetails`. Cada `refresh*` del repositorio reemplaza el conjunto completo de la entidad en una transacción (`deleteAll` + `insertAll`), suficiente para el volumen del curso.

### 5.3 Repositorios

```kotlin
interface AlbumRepository {
    fun observeAlbums(): Flow<List<Album>>
    fun observeAlbum(id: Int): Flow<Album?>
    suspend fun refreshAlbums(): Result<Unit, DataError>
    suspend fun refreshAlbum(id: Int): Result<Unit, DataError>
    suspend fun createAlbum(draft: NewAlbum): Result<Album, DataError>          // HU07
    suspend fun addTrack(albumId: Int, draft: NewTrack): Result<Track, DataError> // HU08
    suspend fun addComment(albumId: Int, draft: NewComment): Result<Comment, DataError> // HU09
}

interface ArtistRepository {
    fun observeArtists(): Flow<List<Artist>>                       // músicos + bandas unidos
    fun observeArtist(id: Int, type: ArtistType): Flow<Artist?>
    suspend fun refreshArtists(): Result<Unit, DataError>          // GET /musicians + GET /bands
    suspend fun refreshArtist(id: Int, type: ArtistType): Result<Unit, DataError>
    suspend fun addAlbum(artistId: Int, type: ArtistType, albumId: Int): Result<Unit, DataError> // HU15
    suspend fun addMember(bandId: Int, musicianId: Int): Result<Unit, DataError>                 // HU12
}

interface CollectorRepository {
    fun observeCollectors(): Flow<List<Collector>>
    fun observeCollector(id: Int): Flow<Collector?>
    suspend fun refreshCollectors(): Result<Unit, DataError>
    suspend fun refreshCollector(id: Int): Result<Unit, DataError> // GET /collectors/:id + /albums
    suspend fun addFavorite(collectorId: Int, artistId: Int, type: ArtistType): Result<Unit, DataError> // HU10
}
```

Tras cada escritura exitosa el repositorio vuelve a pedir el recurso afectado (`refreshAlbum`, `refreshArtist`, `refreshCollector`) y lo guarda en Room; la UI se actualiza por el `Flow`. Los POST de BackVynils responden 200 y sin autenticación.

## 6. Especificación por historia

Convenciones comunes a todas las pantallas: `Scaffold` con `VinilosTopBar` (título, flecha o X, chip de rol), cuatro estados resueltos (cargando, error, vacío, contenido), `PullToRefreshBox` en listas, imágenes con Coil y marcador de posición, textos en `strings.xml`.

### Sprint 1 · Álbumes

**TR-01 a TR-06.** Proyecto con `VinilosTheme`, `NavigationBar` de tres pestañas, capa de red con URL base configurable por `BuildConfig`, Room con Hilt, componentes compartidos (`VinilosTopBar`, `RoleChip`, `ListItemCard`, `LoadingContent`, `ErrorContent`, `EmptyContent`, `VinilosTextField`, `SectionTitle`), entorno de pruebas y pantalla de selección de rol.

**Selector de rol (TR-06).** Logotipo, título "Vinilos", pregunta "¿Cómo quieres ingresar?" y dos tarjetas con icono, nombre y descripción: "Visitante: Consulta álbumes, artistas y coleccionistas" y "Coleccionista: Consulta y además crea álbumes, agrega canciones, comenta y administra artistas". Sin barra inferior.

**HU01 · Catálogo.** `LazyColumn` de `AlbumCard` (carátula 56 dp, nombre, artista principal, "Género · Año"). Artista = primer `performer`; si no hay, "sin artista". Chevron a la derecha. Estado de error de pantalla completa con icono de alerta, "No se pudo cargar el catálogo", "Revisa tu conexión e inténtalo de nuevo" y botón "Reintentar". Si hay datos en caché y falla el refresco, se muestra el contenido con `Snackbar`. FAB "+" solo para coleccionista.

**HU02 · Detalle de álbum.** Cabecera con carátula, "Género · Año", nombre y artista; tres fichas: Sello, Lanzamiento (fecha corta "1 ago 1984") y Canciones (conteo); descripción; sección "Canciones" numerada `01`, `02` con duración a la derecha; sección "Comentarios" con avatar, nombre, estrellas (rating sobre 5) y texto. Para coleccionista: botón "Comentar" junto al título de la sección y FAB extendido "+ Canción". Álbum inexistente (404): estado de error con mensaje "No encontramos este álbum".

**HU07 · Nuevo álbum.** Pantalla completa con X. Aviso "Todos los campos son obligatorios". Vista previa de la portada cargada desde la URL escrita. Campos: Nombre, URL de la portada, Fecha de lanzamiento (`DatePickerDialog`, formato `dd / mm / aaaa`), Descripción (multilínea), Género (chips de una sola selección: Classical, Salsa, Rock, Folk), Sello discográfico (chips: Sony Music, EMI, Discos Fuentes, Elektra, Fania Records). Botones inferiores "Cancelar" y "Crear álbum". Validación en el ViewModel al tocar "Crear álbum": "Campo obligatorio" bajo cada campo vacío, "Selecciona un género" y "Selecciona un sello" bajo los grupos de chips. Mientras envía, el formulario se atenúa y el botón se deshabilita. En éxito se vuelve al catálogo con snackbar "Álbum creado" y el nuevo álbum aparece al final de la lista.

**HU08 · Nueva canción.** `ModalBottomSheet` sobre el detalle, con sobretítulo "BUSCANDO AMÉRICA · CANCIÓN 03" (nombre del álbum y número que tendrá la pista), campos Nombre y Duración (mm:ss) con teclado numérico, botones "Cancelar" y "Guardar canción". Errores: "Campo obligatorio" e "Ingresa la duración con formato mm:ss" (regex `^\d{1,2}:[0-5]\d$`). En éxito se cierra la hoja, la ficha "Canciones" sube a 3, la pista aparece numerada y se muestra "Canción agregada al álbum".

### Sprint 2 · Artistas y coleccionistas

**HU03 · Lista de artistas.** Fila de `FilterChip` "Todos", "Músicos", "Bandas" (una activa). Tarjeta con imagen, nombre, "Músico · 1947" o "Banda · desde 1970" y tercera línea: "2 álbumes · 4 integrantes" para banda, "Integrante de Queen" o "N álbumes" para músico. La unión de `GET /musicians` y `GET /bands` se hace en `ArtistServiceAdapter`; la pertenencia a banda se deriva de `bands[].musicians`. Orden: por nombre, con el filtro aplicado en el ViewModel sobre la lista observada.

**HU04 · Detalle de artista.** Título "Músico" o "Banda" según el tipo. Cabecera con imagen, "Músico · nació el 16 jul 1948" o "Banda · desde 1970", nombre y descripción. Banda: sección "Integrantes · N" (lista de músicos) y "Álbumes · N". Músico: solo "Álbumes · N". Para coleccionista, cada sección lleva a la derecha un `OutlinedButton` pequeño: "Agregar integrante" (solo banda) y "Agregar álbum". Los premios (`performerPrizes`) no se muestran en el alcance comprometido.

**HU05 · Lista de coleccionistas.** Tarjeta con avatar de iniciales, nombre, correo y "N álbumes · M favoritos" ("Sin álbumes aún" cuando no tiene), a partir de los arreglos de `GET /collectors`.

**HU06 · Detalle de coleccionista.** Cabecera con avatar, sobretítulo "Coleccionista" y nombre; fichas Teléfono y Correo; sección "Artistas favoritos · N" (imagen, nombre, tipo); sección "Sus álbumes · N" con precio a la derecha ("$ 35") y chip de estado "DISPONIBLE" / "NO DISPONIBLE". Requiere dos llamadas: `GET /collectors/:id` y `GET /collectors/:id/albums`. Para coleccionista, botón "Agregar favorito" en la sección de favoritos.

### Sprint 3 · Funcionalidades adicionales

**HU09 · Comentar álbum.** `ModalBottomSheet` con sobretítulo del álbum y título "Comentar álbum". Grupo "¿Quién comenta?" con chips de los coleccionistas (una selección). Grupo "Valoración (0 a 5)" con seis chips circulares `0` a `5`; al elegir, la etiqueta cambia a "Valoración · 4 de 5". Campo "Comentario" multilínea con marcador "Escribe tu opinión". Botones "Cancelar" y "Publicar comentario". Errores: "Selecciona un coleccionista", "Selecciona una valoración", "Campo obligatorio". Envío: `POST /albums/:albumId/comments` con `description`, `rating` y `collector: { id }`. En éxito, el comentario aparece al final de la lista con el nombre del coleccionista y se muestra "Comentario publicado". Los coleccionistas de la hoja salen de `CollectorRepository.observeCollectors()`.

**HU15 · Agregar un álbum a un artista.** `AlbumPicker` a pantalla completa con X, título "Elegir álbum", subtítulo "para Rubén Blades Bellido de Luna", campo "Buscar en el catálogo" (filtro local por nombre y artista), lista con `RadioButton`, nombre, "Artista · Año" (o "sin artista"). Los álbumes que ya pertenecen al artista se muestran atenuados, sin radio activo y con chip "Ya agregado". Botón inferior "Selecciona un álbum" deshabilitado hasta elegir; luego "Agregar «Poeta del pueblo»". Envío: `POST /musicians/:id/albums/:albumId` o `POST /bands/:id/albums/:albumId`. En éxito vuelve al detalle con "Álbum agregado al artista" y la sección "Álbumes · N" incrementada.

**HU12 · Agregar músicos a una banda.** `MusicianPicker` con título "Elegir músico", subtítulo "para Queen", campo "Buscar músico", lista de músicos con "Músico · Año" y, cuando aplica, "Integrante de The Beatles". Los que ya integran la banda llevan chip "Ya integrante" y quedan deshabilitados. Si se elige un músico con otra banda, la app bloquea el envío y muestra un aviso en la parte superior: "Ringo Starr ya pertenece a otra banda (The Beatles). Un músico solo puede estar en una banda." Esta regla se valida en el ViewModel porque el backend reasigna la banda sin error. Envío: `POST /bands/:bandId/musicians/:musicianId`. En éxito: "Integrante agregado a la banda" e "Integrantes · N" incrementado.

**HU10 · Agregar artistas favoritos.** `ArtistPicker` con título "Elegir artista", subtítulo "para Manolo Bellon", campo "Buscar artista", lista con músicos y bandas ("Músico · 1948", "Banda · desde 1970"); favoritos actuales con chip "Ya es favorito" y deshabilitados (la app evita duplicados porque el backend no lo verifica). Botón "Agregar «Freddie Mercury» a favoritos". Envío: `POST /collectors/:collectorId/musicians/:musicianId` o `.../bands/:bandId`. En éxito: "Artista favorito agregado" y "Artistas favoritos · N" incrementado.

**Combinaciones.** El prototipo dibuja HU08 + HU09 sobre el mismo álbum y HU12 + HU15 sobre la misma banda en cualquier orden. Se cumple porque las hojas y los selectores no guardan estado propio: el detalle observa Room y cada escritura refresca el recurso.

## 7. Del kit del prototipo a Material 3

El prototipo es de media fidelidad en escala de grises (paleta `#FFFFFF`, `#F6F6F6`, `#ECECEC`, `#DADADA`, `#BDBDBD`, `#757575`, `#424242`, `#1F1F1F`; tipografía Inter). La app usa el esquema de color de Material 3 con color dinámico y la tipografía del tema (`05-reglas-de-diseno-ui.md`); la diferencia es intencional y queda justificada aquí.

| Elemento del kit | Componente Compose | Notas |
|---|---|---|
| Button/Primary (relleno negro) | `Button` | Acción principal: "Crear álbum", "Guardar canción", "Publicar comentario", "Agregar «…»" |
| Button/Secondary (borde) | `OutlinedButton` | "Cancelar", "Reintentar", "Agregar álbum / integrante / favorito", "Comentar" |
| Button/Disabled | `Button(enabled = false)` | "Selecciona un álbum / músico / artista" mientras no hay selección; botón de envío mientras se envía |
| Field/Default y Field/Error | `OutlinedTextField` con `isError` y `supportingText` | El icono de alerta del prototipo se sustituye por el color de error y el texto de apoyo |
| Chip/Off y Chip/On | `FilterChip` | Filtros de artistas, género, sello, coleccionista y valoración |
| Chip de estado ("Ya agregado", "Ya integrante", "Ya es favorito", "DISPONIBLE") | `SuggestionChip` con `enabled = false` | Solo informativo |
| Chip de rol (arriba a la derecha) | `AssistChip` con `onClick` | Navega al selector de rol |
| BottomNav con tres destinos | `NavigationBar` + `NavigationBarItem` | Iconos de `material-icons-extended`: `Album`, `Person`/`Groups`, `Collections` |
| Tarjeta de lista (imagen, dos o tres líneas, chevron) | `ListItem` dentro de `Card` o `Surface` | `leadingContent` con `AsyncImage` 56 dp; avatar de iniciales para coleccionistas |
| Fichas de datos (Sello, Lanzamiento, Canciones, Teléfono, Correo) | `Surface` con `tonalElevation` y dos textos | Componente compartido `InfoTile` |
| Hojas "Nueva canción" y "Comentar álbum" | `ModalBottomSheet` | Con `dragHandle` como en el prototipo |
| Selector de fecha | `DatePickerDialog` + `DatePicker` | Botones "Cancelar" y "Aceptar"; la app convierte a `LocalDate` y envía ISO 8601 |
| Barra de búsqueda de los selectores | `OutlinedTextField` con icono de búsqueda | Filtro local, sin llamada al backend |
| Mensajes de confirmación (barra negra inferior con check) | `Snackbar` | Vía `SnackbarHostState` del `Scaffold`, disparado por un `Effect` |
| Aviso de bloqueo (HU12) | `Card` con `colorScheme.errorContainer` | Encima de la lista, no bloquea la pantalla |
| Imagen y avatar (cuadrado con X) | `AsyncImage` con marcador `Icon` | Nunca un PNG de relleno |

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
| `album_label` / `album_release` / `album_tracks` | Sello / Lanzamiento / Canciones |
| `album_section_tracks` / `album_section_comments` | Canciones / Comentarios |
| `album_action_add_track` / `album_action_comment` | Canción / Comentar |
| `create_album_title` / `create_album_all_required` | Nuevo álbum / Todos los campos son obligatorios. |
| `create_album_cover_hint` | Vista previa. La portada se carga desde la URL que pegues abajo. |
| `field_name` / `field_cover_url` / `field_release_date` / `field_description` / `field_genre` / `field_record_label` | Nombre / URL de la portada / Fecha de lanzamiento / Descripción / Género / Sello discográfico |
| `error_required` / `error_select_genre` / `error_select_label` | Campo obligatorio / Selecciona un género / Selecciona un sello |
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
| 1 | Selector de rol, catálogo, detalle de álbum, nuevo álbum, hoja de canción; tema, barra inferior, componentes compartidos, red, Room, pruebas | HU01, HU02, HU07, HU08 | TR-01 a TR-06 |
| 2 | Lista y detalle de artistas (músico y banda), lista y detalle de coleccionistas, avatar de iniciales, `InfoTile` | HU03, HU04, HU05, HU06 | — |
| 3 | Hoja de comentario, `PickerScreen` y sus tres variantes, aviso de bloqueo, botones "Agregar" en detalles | HU09, HU15, HU12, HU10 | — |

El Sprint 1 debe dejar el rol, la barra inferior y la plantilla de pantalla cerrados, porque todas las pantallas posteriores los reutilizan. El `PickerScreen` del Sprint 3 se diseña una sola vez (tarea HU15-01) y HU12 y HU10 solo lo parametrizan.

## 10. Hallazgos del análisis y decisiones pendientes

Resultado de contrastar wiki, prototipo, documento del curso y código del backend. Cada punto tiene una propuesta; las marcadas "equipo" se ratifican en la reunión del lunes y se registran en la bitácora.

1. **Nombre del autor de un comentario (HU02, HU09).** Verificado el 2026-10-19 contra el backend local: `GET /albums/:id` devuelve cada comentario solo con `id`, `description` y `rating`, sin coleccionista. El prototipo muestra "Manolo Bellon" junto a cada comentario. Decisión: la app guarda `collectorId` y `collectorName` en Room al publicar (HU09) y muestra "Coleccionista" en los comentarios cuyo autor se desconoce; `GET /collectors` sí trae los comentarios de cada coleccionista, así que en Sprint 3 puede cruzarse esa lista para resolver el nombre. Dato adicional de la semilla: `/bands` devuelve Queen con `musicians` vacío; la lista de integrantes del prototipo solo aparecerá al agregar músicos (HU12).
2. **Músico en otra banda (HU12).** El backend reasigna la banda sin error; el prototipo bloquea con un aviso. Se valida en el ViewModel con la pertenencia derivada de `bands[].musicians`. Decidido por el prototipo.
3. **Favorito duplicado (HU10) y álbum ya asociado (HU15).** El backend no lo impide; la app deshabilita los ítems con chip "Ya es favorito" / "Ya agregado". Decidido por el prototipo.
4. **Álbumes del coleccionista (HU06).** `GET /collectors/:id` no anida el álbum en `collectorAlbums`; hace falta `GET /collectors/:id/albums`. `refreshCollector` hace las dos llamadas en paralelo con `coroutineScope { async }`.
5. **Rol en memoria.** No se persiste; reiniciar la app vuelve al selector. Equipo: confirmar o añadir DataStore Preferences al catálogo.
6. **Tareas transversales.** El backlog define seis (TR-01 a TR-06), pero las páginas Sprint 1 y Milestone 1 de la wiki dicen "TR-01 a TR-05". Corregir en la wiki e incluir TR-06 en las condiciones de cierre del Milestone 1.
7. **Enlaces pendientes en la wiki.** La página Inception sigue con "Repositorio: pendiente", "Tablero: pendiente", "Prototipo: pendiente", fechas de milestones "pendientes de registrar" y las páginas de sprint con "Fecha de inicio: Por definir", aunque los milestones, el tablero y el wireframe ya existen. Completar antes de la entrega del 18 de octubre.
8. **Orden de la lista de artistas (HU03).** El prototipo mezcla bandas y músicos sin orden visible. Propuesta: orden alfabético por nombre en "Todos" y en cada filtro. Equipo.
9. **Fechas.** El formulario usa `dd / mm / aaaa` y el detalle "1 ago 1984" y "nació el 16 jul 1948"; el backend recibe y devuelve ISO 8601. Conversión en los mappers con `java.time` y formato de la configuración regional.
10. **Vocabulario.** La UI dice "Canción" y el código `Track`; la UI dice "Integrante" y el backend `musician`. Se añaden al glosario de `02-arquitectura.md` en el PR que cree los modelos.
11. **Paleta y tipografía.** El prototipo es gris con Inter; la app sigue Material 3 con color dinámico. Diferencia justificada en la sección 7; no se replica el gris.
12. **Backlog alterno.** El detalle de artista y el de coleccionista reservan sitio para "Agregar premio" (HU14/HU13) y "Agregar álbum" con precio y estado (HU11) sin diseñar esas pantallas. Si se incorporan, reutilizan `PickerScreen` y el formulario corto de HU07.
13. **Tablero "Grupo" del prototipo.** La exportación salió en blanco; si se quiere incluir en la wiki, exportarlo con fondo o reemplazarlo por texto.

## 11. Bitácora de decisiones

| Fecha | Decisión |
|---|---|
| 2026-10-14 | Documento creado a partir de la wiki (commit `a7cc5c3`), los seis tableros del prototipo y el código de BackVynils. Selector de rol inicial con chip de rol en todas las pantallas; hojas inferiores para canción y comentario; tres selectores sobre un `PickerScreen` común; validaciones del lado de la app para banda única, favoritos y álbumes repetidos. Pendientes de ratificar: persistencia del rol, orden de artistas. |
