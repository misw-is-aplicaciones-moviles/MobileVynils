# 02. Arquitectura

Vinilos aplica los patrones que exige el curso: **Model-View-ViewModel** (MVVM), **Repository** y **Service Adapter**. La Vista son Fragments con layouts XML y ViewBinding; el ViewModel expone `LiveData`; el Repository es la puerta única a los datos; el Service Adapter aísla la API REST de BackVynils.

## 1. Vista general

```
┌──────────────────────────────────── Vista ─────────────────────────────────────┐
│  MainActivity (NavHostFragment + BottomNavigationView)                          │
│  XFragment (layout XML + ViewBinding) ──observa LiveData──▶ dibuja UiState      │
│         │ acciones del usuario (funciones del ViewModel)                        │
└─────────┼───────────────────────────────────────────────────────────────────────┘
          ▼
┌──────────────────────────────── ViewModel ─────────────────────────────────────┐
│  XViewModel: MutableLiveData<XUiState> privado, LiveData<XUiState> público,     │
│  LiveData<Event<XEvent>> para eventos de una sola vez, viewModelScope           │
└─────────┼───────────────────────────────────────────────────────────────────────┘
          ▼ suspend
┌──────────────────────────────── Modelo ────────────────────────────────────────┐
│  XRepository (interfaz) ── XRepositoryImpl: caché + frontera de errores         │
│        └── XServiceAdapter: Retrofit (VinilosApiService) → API REST BackVynils  │
│  models/: Album, Track, Comment, Performer, Collector                           │
└─────────────────────────────────────────────────────────────────────────────────┘
```

Reglas de dependencia: el Fragment conoce a su ViewModel; el ViewModel conoce la interfaz del Repository; el Repository conoce el Service Adapter. Nada apunta hacia arriba. Ni `Context`, ni `View`, ni `Fragment` entran en ViewModels, repositorios ni adaptadores.

## 2. Estructura de paquetes

Un solo módulo `:app`, paquete raíz `co.edu.uniandes.vinilos`, organizado por capa como en el ejemplo del curso:

```
co.edu.uniandes.vinilos
├── VinilosApplication.kt           # crea el AppContainer
├── MainActivity.kt                 # NavHostFragment + BottomNavigationView
├── di/
│   └── AppContainer.kt             # Retrofit, OkHttp, service adapters, repositorios, sesión
├── models/                         # Album, Track, Comment, Performer, Collector, Genre, RecordLabel, UserRole,
│                                   # DataError y DataResult (errores de datos)
├── network/
│   ├── VinilosApiService.kt        # interfaz Retrofit con los endpoints
│   ├── dto/                        # AlbumDto, TrackDto, ... y mappers toModel()
│   └── AlbumServiceAdapter.kt      # un adaptador por entidad
├── repositories/                   # AlbumRepository + AlbumRepositoryImpl, SessionRepository
├── viewmodels/                     # AlbumListViewModel, AlbumDetailViewModel, ... sus UiState y Event
└── ui/
    ├── common/                     # extensiones de vistas, PickerAdapter, utilidades de la Vista
    ├── role/                       # RoleSelectFragment (TR-06)
    ├── albums/                     # AlbumListFragment, AlbumAdapter, AlbumDetailFragment, TrackAdapter,
    │                               # AlbumCreateFragment, TrackCreateBottomSheet
    ├── artists/
    └── collectors/
```

Recursos: `res/layout/` (`activity_main.xml`, `fragment_album_list.xml`, `item_album.xml`, `view_state_error.xml`), `res/navigation/nav_graph.xml`, `res/menu/bottom_nav.xml`, `res/values/` (`strings.xml`, `colors.xml`, `themes.xml`, `dimens.xml`), `res/font/`.

Reglas:

1. `ui/*` solo importa `viewmodels`, `models` y `ui/common`. Nunca `network` ni `repositories`.
2. `viewmodels/*` importa `repositories` (interfaces) y `models`. Nunca `android.view`, `android.content.Context` ni clases de `ui`.
3. `repositories/*` importa `network` y `models`. Nunca `ui` ni `viewmodels`.
4. `network/*` importa `models` y Retrofit. Los DTO no salen de `network`: se mapean a `models` en el Service Adapter.
5. `models/*` es Kotlin puro: no importa Android ni nada del proyecto.

## 3. Vista (Fragments)

- Una `Activity` (`MainActivity`) con `NavHostFragment` y `BottomNavigationView`. Cada pantalla es un Fragment con su layout.
- ViewBinding con el patrón de referencia nula para no retener vistas tras `onDestroyView`:

```kotlin
class AlbumListFragment : Fragment(R.layout.fragment_album_list) {
    private var _binding: FragmentAlbumListBinding? = null
    private val binding get() = checkNotNull(_binding)
    private val viewModel: AlbumListViewModel by viewModels { AlbumListViewModel.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentAlbumListBinding.bind(view)
        val adapter = AlbumAdapter(onAlbumClick = viewModel::onAlbumClicked)
        binding.albumList.adapter = adapter
        binding.errorState.retryButton.setOnClickListener { viewModel.refresh() }
        viewModel.uiState.observe(viewLifecycleOwner) { state -> render(state, adapter) }
        viewModel.events.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { handle(it) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

- Se observa siempre con `viewLifecycleOwner`, nunca con `this`.
- El Fragment no decide nada: dibuja el `UiState` y reenvía las acciones al ViewModel.
- La navegación la ejecuta el Fragment con `findNavController()` cuando recibe un evento del ViewModel. Los argumentos son ids en un `Bundle` (`bundleOf(ARG_ALBUM_ID to id)`), nunca objetos completos.

## 4. ViewModel

- Un ViewModel por pantalla, en `viewmodels/`. Estado privado mutable y público de solo lectura:

```kotlin
class AlbumListViewModel(private val albumRepository: AlbumRepository) : ViewModel() {
    private val _uiState = MutableLiveData(AlbumListUiState(isLoading = true))
    val uiState: LiveData<AlbumListUiState> = _uiState

    private val _events = MutableLiveData<Event<AlbumListEvent>>()
    val events: LiveData<Event<AlbumListEvent>> = _events

    init { refresh() }

    fun refresh() {
        _uiState.value = currentState().copy(isLoading = true, error = null)
        viewModelScope.launch {
            _uiState.value = when (val result = albumRepository.getAlbums()) {
                is DataResult.Success -> currentState().copy(isLoading = false, albums = result.value)
                is DataResult.Failure -> currentState().copy(isLoading = false, error = result.error)
            }
        }
    }

    fun onAlbumClicked(albumId: Int) {
        _events.value = Event(AlbumListEvent.OpenDetail(albumId))
    }

    private fun currentState() = checkNotNull(_uiState.value)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VinilosApplication
                AlbumListViewModel(app.container.albumRepository)
            }
        }
    }
}
```

- **UiState**: `data class` inmutable con todo lo que la pantalla necesita, con carga, error, vacío y contenido explícitos.

```kotlin
data class AlbumListUiState(
    val albums: List<Album> = emptyList(),
    val isLoading: Boolean = false,
    val error: DataError? = null,
    val canEdit: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && error == null && albums.isEmpty()
}
```

- **Eventos de una sola vez** (navegar, mostrar un `Snackbar`): `LiveData<Event<T>>`, donde `Event` entrega su contenido una sola vez (`viewmodels/Event.kt`). Así no se repiten al rotar la pantalla.
- `postValue` solo desde hilos de fondo; en `viewModelScope` (hilo principal) se usa `value`.
- Un ViewModel no llama a `observe` (no tiene ciclo de vida). Para combinar el rol de `SessionRepository.role` con el estado de la pantalla (`canEdit`), el `uiState` público es un `MediatorLiveData` con `addSource(sessionRepository.role)`, o se deriva con `map`/`switchMap` de `lifecycle-livedata-ktx`.
- El estado que debe sobrevivir a la muerte del proceso (id seleccionado) llega por los argumentos del Fragment o `SavedStateHandle`.

## 5. Modelo: Repository y Service Adapter

### Repository

Interfaz en `repositories/` y una implementación. Es la puerta única de los ViewModels a los datos y la **frontera de errores**: convierte las excepciones en `DataResult.Failure(DataError)`.

```kotlin
interface AlbumRepository {
    suspend fun getAlbums(forceRefresh: Boolean = false): DataResult<List<Album>, DataError>
    suspend fun getAlbum(id: Int, forceRefresh: Boolean = false): DataResult<Album, DataError>
    suspend fun createAlbum(draft: NewAlbum): DataResult<Album, DataError>          // HU07
    suspend fun addTrack(albumId: Int, draft: NewTrack): DataResult<Track, DataError> // HU08
}
```

En el Sprint 1 el repositorio guarda en memoria la última lista recibida (caché, como explica el curso) y la invalida tras una escritura. Si el equipo incorpora Room, la caché pasa a los DAO sin cambiar la interfaz.

### Service Adapter

Nombre del curso para la fuente remota. Uno por entidad (`AlbumServiceAdapter`, `PerformerServiceAdapter`, `CollectorServiceAdapter`); envuelve `VinilosApiService` (Retrofit), llama al endpoint, recibe DTOs y los mapea a `models`. No sabe de caché ni de UI. Sus funciones son `suspend` y Retrofit ya las ejecuta fuera del hilo principal.

### Errores

```kotlin
sealed interface DataError {
    data object Network : DataError            // sin conexión, timeout
    data class Http(val code: Int) : DataError // 4xx, 5xx
    data object Serialization : DataError
    data class Unknown(val cause: Throwable) : DataError
}

sealed interface DataResult<out T, out E> {
    data class Success<T>(val value: T) : DataResult<T, Nothing>
    data class Failure<E>(val error: E) : DataResult<Nothing, E>
}
```

El Fragment traduce `DataError` a un texto de `strings.xml`; nunca muestra mensajes técnicos. `CancellationException` jamás se captura.

## 6. Inyección de dependencias manual

`VinilosApplication` crea un único `AppContainer` y los ViewModels lo obtienen en su `Factory`:

```kotlin
class VinilosApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}

class AppContainer {
    private val retrofit: Retrofit by lazy { /* OkHttp, BASE_URL, convertidor */ }
    private val apiService: VinilosApiService by lazy { retrofit.create(VinilosApiService::class.java) }
    val albumRepository: AlbumRepository by lazy { AlbumRepositoryImpl(AlbumServiceAdapter(apiService)) }
    val sessionRepository: SessionRepository by lazy { SessionRepositoryImpl() }
}
```

- Todo lo pesado se crea con `lazy` al primer uso.
- Las clases reciben sus dependencias por constructor; nadie hace `AppContainer()` fuera de la `Application`.
- En pruebas, los ViewModels se crean directamente con repositorios falsos; las pruebas E2E reemplazan el contenedor o apuntan a un backend de pruebas (`06-pruebas.md`).

## 7. Concurrencia

- Corrutinas: `viewModelScope` en los ViewModels y funciones `suspend` en repositorios y adaptadores. Sin `GlobalScope`, `runBlocking` ni `Thread`.
- El hilo principal solo dibuja. Los mapeos pesados van a `Dispatchers.Default`, recibido por constructor para poder reemplazarlo en pruebas.
- La Vista no lanza corrutinas de negocio; solo observa `LiveData`.

## 8. Glosario

| Término (código, inglés) | Significado en el proyecto |
|---|---|
| `Album`, `Track`, `Comment` | Álbum, canción y comentario del catálogo |
| `Performer` (`Musician`, `Band`) | Artista; el backend distingue músico y banda. En código, destinos y layouts se usa siempre `Performer` (`performerListFragment`, `item_performer.xml`); "Artista" solo aparece en textos visibles y en el paquete `ui/artists` |
| `Collector`, `CollectorAlbum` | Coleccionista y álbum de su colección |
| `ServiceAdapter` | Fuente de datos remota sobre Retrofit |
| `Repository` | Puerta única a los datos y frontera de errores |
| `UiState`, `Event` | Estado de una pantalla y evento de una sola vez |
| `UserRole` | Visitante o coleccionista (TR-06) |

## 9. Lista de aceptación de un cambio

- [ ] La clase está en el paquete de su capa (sección 2) y respeta las reglas de dependencia.
- [ ] El `UiState` es inmutable y cubre carga, error, vacío y contenido.
- [ ] Ningún error técnico llega a la Vista; el Repository lo convierte en `DataError`.
- [ ] Ninguna E/S corre en el hilo principal.
- [ ] Textos en `strings.xml` (Lint `HardcodedText` como error).
- [ ] Probado en el emulador API 21 y API 37.
- [ ] El diagrama de clases, paquetes y componentes de la historia en la wiki refleja el cambio (la rúbrica evalúa que el código sea coherente con el diseño).
- [ ] Prueba unitaria del ViewModel o del Repository para la lógica nueva y prueba E2E con Espresso si la historia lo exige.
- [ ] ktlint, detekt y Android Lint en verde.

## 10. Bitácora de decisiones

| Fecha | Decisión |
|---|---|
| 2026-10-07 | MVVM con flujo unidireccional, Compose, Hilt y Room como fuente única de verdad (propuesta inicial). |
| 2026-10-26 | Reinicio: Fragments + ViewBinding, `LiveData` con `Event` para eventos de una sola vez, inyección manual con `AppContainer`, paquetes por capa como en el ejemplo del curso, caché en memoria en el Repository para el Sprint 1. |
