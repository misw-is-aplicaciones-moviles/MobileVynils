# 02. Arquitectura

Vinilos sigue la guía de arquitectura de Android (capa de UI, capa de datos y capa de dominio opcional) con el patrón **Model-View-ViewModel** y **flujo de datos unidireccional** (UDF), tal como exige el curso: MVVM con Room, Repository y Service Adapter.

## 1. Vista general

```
┌─────────────────────────────── UI ───────────────────────────────┐
│  Screen (Compose)  ──acciones──▶  ViewModel  ──UiState──▶ Screen  │
└──────────────────────────────────┬───────────────────────────────┘
                                   │ Flow<T> / suspend
┌──────────────────────────── Datos ┴──────────────────────────────┐
│  Repository (interfaz)                                            │
│    ├── Service Adapter (remoto): Retrofit → API REST BackVynils   │
│    └── Room (local): fuente única de verdad, DAOs                 │
└───────────────────────────────────────────────────────────────────┘
```

Reglas de dependencia: la UI conoce al ViewModel; el ViewModel conoce la interfaz del Repository; el Repository conoce el Service Adapter y los DAOs. Nada apunta hacia arriba. Las clases de Android (`Context`, `Activity`, recursos) no entran en ViewModels ni en la capa de datos, salvo lo que Hilt provea por constructor.

## 2. Capa de UI

- **Screen**: función `@Composable` pura, `XScreen(uiState: XUiState, onAction: (XAction) -> Unit, modifier: Modifier = Modifier)`. No recibe el ViewModel, no lanza corrutinas de negocio, es previsualizable con `@Preview`.
- **Route**: función `@Composable` `XRoute(onNavigateTo...)` que obtiene el ViewModel con `hiltViewModel()`, colecciona `uiState` con `collectAsStateWithLifecycle()`, colecciona efectos con `LaunchedEffect` y llama a `XScreen`.
- **ViewModel**: una clase `@HiltViewModel` por pantalla. Expone `val uiState: StateFlow<XUiState>`, `val effects: Flow<XEffect>` y funciones de acción. Usa `viewModelScope`. Sobrevive a cambios de configuración; el estado que debe sobrevivir a la muerte del proceso (por ejemplo el id seleccionado) viaja en `SavedStateHandle`.
- **UiState**: `data class` inmutable con todo lo que la pantalla necesita para dibujarse. Modela carga, error, vacío y contenido explícitamente:

```kotlin
data class AlbumListUiState(
    val albums: List<AlbumUi> = emptyList(),
    val isLoading: Boolean = false,
    val error: UiText? = null,
) {
    val isEmpty: Boolean get() = !isLoading && error == null && albums.isEmpty()
}
```

- **Acciones**: `sealed interface XAction` cuando la pantalla tiene más de tres eventos; funciones sueltas del ViewModel cuando son pocas. Los nombres describen lo que hizo el usuario (`AlbumClicked`, `RetryClicked`), no lo que debe pasar.
- **Efectos**: eventos de una sola vez (navegar, mostrar snackbar) por `Channel<XEffect>(Channel.BUFFERED)` expuesto con `receiveAsFlow()`. Todo lo demás es estado.
- **Navegación**: `NavHost` único en `MainActivity`, rutas como `@Serializable data class`/`data object`, argumentos mínimos (ids), nunca objetos completos. La pantalla recibe callbacks de navegación (`onAlbumClick: (Int) -> Unit`), no el `NavController`.

## 3. Estructura de paquetes

Un solo módulo `:app`. Paquete raíz `co.edu.uniandes.vinilos` (se confirma al crear el proyecto). Organización por capa y, dentro de cada capa, por entidad o funcionalidad.

```
co.edu.uniandes.vinilos
├── VinilosApplication.kt          # @HiltAndroidApp, Timber, Coil ImageLoader
├── MainActivity.kt                # enableEdgeToEdge, VinilosTheme, VinilosNavHost
├── di/                            # módulos Hilt: Network, Database, Dispatchers, Repositories
├── core/
│   ├── common/                    # Result/DataError, qualifiers de dispatchers, UiText, extensiones
│   ├── network/                   # VinilosApi (Retrofit), DTOs, interceptores, NetworkModule
│   ├── database/                  # VinilosDatabase, entidades, DAOs, convertidores
│   └── ui/                        # tema M3, componentes compartidos, navegación (Routes, NavHost)
├── domain/
│   └── model/                     # Album, Artist, Collector, Track, Comment, Performer
├── data/
│   ├── album/                     # AlbumRepository, AlbumRepositoryImpl, AlbumServiceAdapter, mappers
│   ├── artist/
│   └── collector/
└── feature/
    ├── albums/
    │   ├── list/                  # AlbumListRoute, AlbumListScreen, AlbumListViewModel, AlbumListUiState
    │   ├── detail/
    │   └── create/                # HU07 y HU08
    ├── artists/
    └── collectors/
```

Reglas:

1. `feature/*` solo importa `domain/model`, `core/common`, `core/ui` y las interfaces `data/*/*Repository`. Nunca `core/network`, `core/database` ni `*RepositoryImpl`.
2. `data/*` solo importa `domain/model`, `core/common`, `core/network` y `core/database`. Nunca `feature/*` ni nada de Compose.
3. `domain/model` no importa nada del proyecto ni de Android. Son `data class` de Kotlin puro.
4. `core/network` y `core/database` no se conocen entre sí. El Repository es quien los combina.
5. Un `feature/*` no importa otro `feature/*`. Lo compartido sube a `core/ui`.

Estas reglas se verifican con pruebas de Konsist (`06-pruebas.md`, sección 7).

## 4. Capa de datos

### Repository

Interfaz en `data/<entidad>/`, implementación `*RepositoryImpl` enlazada con `@Binds` en un módulo Hilt. Es la única puerta de la UI a los datos y la **frontera de errores**: captura excepciones de red y base de datos y las convierte en `Result.Failure(DataError)`.

```kotlin
interface AlbumRepository {
    fun observeAlbums(): Flow<List<Album>>                 // desde Room, fuente única de verdad
    suspend fun refreshAlbums(): Result<Unit, DataError>   // remoto → Room
    fun observeAlbum(id: Int): Flow<Album?>
    suspend fun createAlbum(draft: NewAlbum): Result<Album, DataError>
    suspend fun addTrack(albumId: Int, draft: NewTrack): Result<Track, DataError>
}
```

Lecturas: la UI observa Room; el ViewModel pide `refresh` al entrar y al reintentar. Escrituras (HU07, HU08): se envían al backend y, si responde bien, se guarda la respuesta en Room; la UI se actualiza por el `Flow`.

### Service Adapter

Nombre del curso para la fuente remota. Hay uno por entidad (`AlbumServiceAdapter`, `ArtistServiceAdapter`, `CollectorServiceAdapter`) y envuelve la interfaz Retrofit `VinilosApi`. Su trabajo: llamar al endpoint, deserializar DTOs y mapearlos a modelos de dominio. No sabe de Room ni de UI. Es `main-safe`: Retrofit ya ejecuta en su propio pool y los mapeos pesados van a `Dispatchers.Default` inyectado.

### Room

`VinilosDatabase` con entidades espejo del dominio (`AlbumEntity`, etc.) y DAOs que devuelven `Flow` para lecturas y `suspend` para escrituras. Room es la **fuente única de verdad** de lo que la UI muestra. Los mapeadores `toEntity()` / `toDomain()` viven en `data/<entidad>/Mappers.kt`.

### Errores

```kotlin
sealed interface DataError {
    data object Network : DataError            // sin conexión, timeout
    data class Http(val code: Int) : DataError // 4xx, 5xx
    data object Serialization : DataError
    data object Database : DataError
    data class Unknown(val cause: Throwable) : DataError
}
sealed interface Result<out T, out E> {
    data class Success<T>(val value: T) : Result<T, Nothing>
    data class Failure<E>(val error: E) : Result<Nothing, E>
}
```

El ViewModel traduce `DataError` a un `UiText` (recurso de cadena) y nunca muestra mensajes técnicos. `CancellationException` jamás se captura.

## 5. Capa de dominio

Modelos puros en `domain/model`. No hay casos de uso en el MVP: la lógica vive en el ViewModel o en el Repository. Se crea un `UseCase` solo cuando dos ViewModels necesitan la misma orquestación, y entonces es una clase con una única función `operator fun invoke`.

## 6. Inyección de dependencias

Hilt en toda la app. Constructores con `@Inject`; interfaces enlazadas con `@Binds`; objetos de terceros (Retrofit, OkHttp, Room, `Json`, `ImageLoader`) con `@Provides` en `@InstallIn(SingletonComponent::class)`. Dispatchers inyectados por qualifiers `@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher` para poder reemplazarlos en pruebas. Nada se obtiene por `Context` global ni por singletons manuales.

## 7. Concurrencia

- Una sola fuente de asincronía: corrutinas y `Flow`.
- El hilo principal solo dibuja. Toda E/S y todo mapeo pesado corren en dispatchers inyectados.
- `viewModelScope` para trabajo ligado a la pantalla; sin `GlobalScope`.
- Los `Flow` de Room se exponen con `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`.
- Colección en UI con `collectAsStateWithLifecycle()`.

## 8. Glosario

| Término (código, inglés) | Significado en el proyecto |
|---|---|
| `Album`, `Track`, `Comment` | Álbum, pista y comentario del catálogo |
| `Artist` (`Musician`, `Band`) | Artista; el backend distingue músico y banda |
| `Collector`, `CollectorAlbum` | Coleccionista y álbum de su colección |
| `Performer` | Intérprete asociado a un álbum (músico o banda) |
| `Prize` | Premio de un artista |
| `ServiceAdapter` | Fuente de datos remota sobre Retrofit |
| `Repository` | Puerta única a datos locales y remotos |
| `UiState`, `Action`, `Effect` | Estado, evento del usuario y efecto de una sola vez de una pantalla |
| `Route` / `Screen` | Composable conectado al ViewModel / composable puro |

## 9. Lista de aceptación de un cambio

- [ ] La clase está en la capa y el paquete que le corresponden (sección 3) y Konsist pasa.
- [ ] El `UiState` es inmutable y cubre carga, error y vacío.
- [ ] Ningún error técnico llega a la UI; el Repository lo convierte en `DataError`.
- [ ] Ninguna E/S ni mapeo pesado corre en el hilo principal.
- [ ] Textos en `strings.xml`; sin cadenas literales visibles en Kotlin.
- [ ] Elementos interactivos con `contentDescription` y tamaño táctil de 48 dp.
- [ ] Hay prueba unitaria del ViewModel o del Repository para la lógica nueva, y prueba E2E si la historia lo exige.
- [ ] ktlint, detekt y Android Lint en verde (hook y CI).

## 10. Bitácora de decisiones

| Fecha | Decisión |
|---|---|
| 2026-10-07 | MVVM + UDF, un módulo, paquetes por capa y entidad. Sin casos de uso en el MVP. Room como fuente única de verdad. Propuesta pendiente de ratificación en el Inception. |
