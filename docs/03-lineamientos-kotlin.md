# 03. Lineamientos de código Kotlin

Base: [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html) y [Android Kotlin style guide](https://developer.android.com/kotlin/style-guide), aplicados por ktlint (`ktlint_official`) y detekt. Lo que sigue son las reglas del proyecto que van más allá del formateo o que conviene tener presentes. Las infracciones las reporta el hook del espacio de trabajo y la CI.

## 1. Formato (lo aplica ktlint)

- Indentación de 4 espacios, líneas de máximo 120 caracteres, `LF`, archivo termina en salto de línea.
- Coma final (trailing comma) en declaraciones y llamadas multilínea.
- Sin imports con comodín. Imports ordenados lexicográficamente, sin líneas en blanco entre grupos.
- Un archivo contiene una clase pública principal y se llama como ella. Funciones de extensión relacionadas pueden ir en el mismo archivo. Archivos con solo funciones de nivel superior se nombran por su tema (`Mappers.kt`, `Routes.kt`).
- Orden dentro de una clase: propiedades, bloque `init`, constructores secundarios, funciones públicas, funciones privadas, `companion object`.

## 2. Nombres

| Elemento | Convención | Ejemplo |
|---|---|---|
| Paquetes | minúsculas, sin guiones bajos | `feature.albums.detail` |
| Clases, interfaces, objetos | `UpperCamelCase`, sustantivos | `AlbumRepositoryImpl` |
| Funciones y propiedades | `lowerCamelCase`, verbos para funciones | `refreshAlbums()`, `isLoading` |
| Composables | `UpperCamelCase`, sustantivo de lo que dibujan | `AlbumCard`, `AlbumListScreen` |
| Constantes (`const val`, `val` de nivel superior inmutable) | `SCREAMING_SNAKE_CASE` | `MAX_TRACKS_PER_ALBUM` |
| Backing field de `StateFlow` | prefijo `_` | `_uiState` |
| Pruebas | descripción con backticks | `` fun `refresh emits error when network fails`() `` |
| Interfaces y su implementación | `Xxx` / `XxxImpl` solo cuando hay una implementación real; `FakeXxx` en pruebas | `AlbumRepository`, `FakeAlbumRepository` |
| DTOs y entidades | sufijo por capa | `AlbumDto`, `AlbumEntity`, `AlbumUi` |
| Booleanos | prefijo `is`, `has`, `can`, `should` | `hasTracks` |

No se usan prefijos húngaros ni abreviaturas no obvias. Se evitan nombres genéricos (`Manager`, `Helper`, `Util`); si no hay un nombre mejor, es señal de que la clase hace demasiado.

## 3. Nulabilidad y tipos

- Los tipos nulos se evitan en el dominio: un `Album` siempre tiene `name`; lo opcional se modela con valores por defecto o con tipos sellados.
- Prohibido `!!`. Se usa `?.`, `?:`, `requireNotNull` con mensaje o `checkNotNull` cuando la ausencia es un error de programación.
- `lateinit` solo para inyección de campo en pruebas y Activities; nunca en ViewModels ni datos.
- Los DTOs de red declaran nulos lo que el backend pueda omitir y el mapper decide el valor por defecto.
- Preferir `val` sobre `var`, colecciones de solo lectura (`List`, `Map`) sobre mutables, y `data class` con `copy` para cambios de estado.
- `sealed interface` para estados y errores cerrados; `enum class` para conjuntos fijos sin datos asociados.
- `value class` para identificadores cuando ayudan a no mezclar ids (`AlbumId`, `ArtistId`) si el equipo lo adopta; se decide en el Sprint 1.

## 4. Funciones

- Cortas: máximo 60 líneas (detekt `LongMethod`); un composable puede ser más largo si es solo layout.
- Máximo 8 parámetros; más de eso es un `data class` de parámetros.
- Expresiones `when` exhaustivas sobre tipos sellados, sin rama `else` que oculte casos nuevos.
- Retornos tempranos para guardas; máximo 3 `return` por función.
- Funciones de extensión para transformar tipos (`AlbumDto.toDomain()`) y para utilidades de un tipo ajeno; no para esconder lógica de negocio.
- Argumentos con nombre cuando hay más de dos del mismo tipo o booleanos.
- Sin números mágicos: constantes con nombre en `companion object` o nivel superior privado.

## 5. Clases y visibilidad

- Visibilidad mínima: `private` por defecto, `internal` para lo que cruza paquetes dentro del módulo, `public` solo en interfaces y modelos.
- Composición antes que herencia. Clases abiertas solo con justificación. Sin clases abstractas con una sola implementación.
- `object` para módulos Hilt y utilidades sin estado; `companion object` solo para constantes y fábricas.
- Las interfaces se definen desde el principio para Repository y Service Adapter aunque tengan una sola implementación: es lo que permite los fakes en pruebas.

## 6. Corrutinas y Flow

- Funciones que hacen E/S son `suspend` y main-safe: cambian de dispatcher internamente con el dispatcher inyectado.
- `withContext(ioDispatcher)` en la capa de datos, nunca en el ViewModel ni en la UI.
- Nunca `GlobalScope`, nunca `runBlocking` en producción, nunca `Dispatchers.IO` escrito directamente fuera del módulo de Hilt que lo provee.
- `try/catch` en la capa de datos captura excepciones concretas (`IOException`, `HttpException`, `SerializationException`, `SQLiteException`). `CancellationException` siempre se relanza. Nunca `catch (e: Exception)` que trague todo.
- `Flow` frío para datos; `StateFlow` para estado; `Channel` para efectos. No `SharedFlow` sin repetición para eventos de UI (se pierden).
- Operadores de `Flow` puros: sin efectos secundarios dentro de `map`; los efectos van en `onEach`.
- `stateIn` con `WhileSubscribed(5_000)` y valor inicial explícito.

## 7. Manejo de errores

- Errores esperados (red, HTTP, validación) viajan como valores: `Result<T, DataError>`.
- Excepciones solo para errores de programación (`IllegalArgumentException`, `IllegalStateException`) y se dejan propagar.
- Mensajes al usuario desde `strings.xml` mediante `UiText`; los detalles técnicos van a Timber en debug.
- Prohibido `TODO()` como implementación en código integrado; `TODO` en comentario solo con el número de issue: `// TODO(#42): ...`. Prohibidos `FIXME` y `STOPSHIP` (se registran como issues).

## 8. Comentarios y documentación

- El código se explica solo; los comentarios dicen por qué, no qué.
- KDoc en interfaces públicas (Repository, Service Adapter, modelos) con una frase por función: qué devuelve y qué error puede producir.
- Sin código comentado. Sin comentarios de autor ni de fecha; para eso está git.
- Los enlaces a la historia de usuario van en el PR y en el issue, no en el código.

## 9. Recursos y Android

- Textos visibles solo en `res/values/strings.xml` (español) con nombres `pantalla_elemento` (`album_list_title`, `common_retry`). Sin `hardcoded text` en Compose.
- Dimensiones de tema desde `MaterialTheme`; sin `dp` sueltos repetidos (constantes en `core/ui/Dimens.kt`).
- Sin `Log.d`: Timber, y solo se planta el árbol en `debug`.
- Sin `Context` en ViewModels. Si hace falta un recurso, se resuelve en la UI con `UiText`.
- `@Suppress` solo con motivo en la misma línea y revisado en el PR.

## 10. Ejemplo de referencia

```kotlin
@HiltViewModel
class AlbumListViewModel @Inject constructor(
    private val albumRepository: AlbumRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumListUiState(isLoading = true))
    val uiState: StateFlow<AlbumListUiState> = _uiState.asStateFlow()

    private val _effects = Channel<AlbumListEffect>(Channel.BUFFERED)
    val effects: Flow<AlbumListEffect> = _effects.receiveAsFlow()

    init {
        observeAlbums()
        refresh()
    }

    fun onAction(action: AlbumListAction) {
        when (action) {
            is AlbumListAction.AlbumClicked -> viewModelScope.launch {
                _effects.send(AlbumListEffect.NavigateToDetail(action.albumId))
            }
            AlbumListAction.RetryClicked -> refresh()
        }
    }

    private fun observeAlbums() {
        albumRepository.observeAlbums()
            .onEach { albums -> _uiState.update { it.copy(albums = albums.map(Album::toUi)) } }
            .launchIn(viewModelScope)
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = albumRepository.refreshAlbums()
            _uiState.update { state ->
                when (result) {
                    is Result.Success -> state.copy(isLoading = false)
                    is Result.Failure -> state.copy(isLoading = false, error = result.error.toUiText())
                }
            }
        }
    }
}
```

## 11. Bitácora de decisiones

| Fecha | Decisión |
|---|---|
| 2026-10-07 | Estilo `ktlint_official`, 120 columnas, coma final, backing fields clásicos, sin `!!`, errores como valores. |
