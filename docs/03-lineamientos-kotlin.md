# 03. Lineamientos de código Kotlin

Base: [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html) y [Android Kotlin style guide](https://developer.android.com/kotlin/style-guide), aplicados por ktlint (`ktlint_official`) y detekt. Lo que sigue son las reglas del proyecto que van más allá del formateo o que conviene tener presentes. Las infracciones las reporta el hook del espacio de trabajo y la CI.

## 1. Formato (lo aplica ktlint)

- Indentación de 4 espacios, líneas de máximo 120 caracteres, `LF`, archivo termina en salto de línea.
- Coma final (trailing comma) en declaraciones y llamadas multilínea.
- Sin imports con comodín. Imports ordenados lexicográficamente, sin líneas en blanco entre grupos.
- Un archivo contiene una clase pública principal y se llama como ella. Funciones de extensión relacionadas pueden ir en el mismo archivo. Archivos con solo funciones de nivel superior se nombran por su tema (`Mappers.kt`, `ViewExtensions.kt`).
- Orden dentro de una clase: propiedades, bloque `init`, constructores secundarios, funciones públicas, funciones privadas, `companion object`.

## 2. Nombres

| Elemento | Convención | Ejemplo |
|---|---|---|
| Paquetes | minúsculas, sin guiones bajos | `ui.albums`, `viewmodels` |
| Clases, interfaces, objetos | `UpperCamelCase`, sustantivos | `AlbumRepositoryImpl` |
| Funciones y propiedades | `lowerCamelCase`, verbos para funciones | `refreshAlbums()`, `isLoading` |
| Fragments, adaptadores y vistas | `UpperCamelCase` con sufijo de tipo | `AlbumListFragment`, `AlbumAdapter`, `TrackCreateBottomSheet` |
| Layouts e ids de vistas | `snake_case` con prefijo de tipo; ids en `camelCase` para ViewBinding | `fragment_album_list.xml`, `item_album.xml`, `@+id/albumList` |
| Constantes (`const val`, `val` de nivel superior inmutable) | `SCREAMING_SNAKE_CASE` | `MAX_TRACKS_PER_ALBUM` |
| Backing field de `LiveData` y del binding | prefijo `_` | `_uiState`, `_binding` |
| Pruebas | descripción con backticks | `` fun `refresh emits error when network fails`() `` |
| Interfaces y su implementación | `Xxx` / `XxxImpl` solo cuando hay una implementación real; `FakeXxx` en pruebas | `AlbumRepository`, `FakeAlbumRepository` |
| DTOs | sufijo por capa | `AlbumDto`, `NewAlbumRequest` |
| Booleanos | prefijo `is`, `has`, `can`, `should` | `hasTracks` |

No se usan prefijos húngaros ni abreviaturas no obvias. Se evitan nombres genéricos (`Manager`, `Helper`, `Util`); si no hay un nombre mejor, es señal de que la clase hace demasiado.

## 3. Nulabilidad y tipos

- Los tipos nulos se evitan en el dominio: un `Album` siempre tiene `name`; lo opcional se modela con valores por defecto o con tipos sellados.
- Prohibido `!!`. Se usa `?.`, `?:`, `requireNotNull` con mensaje o `checkNotNull` cuando la ausencia es un error de programación.
- `lateinit` solo para el contenedor en `VinilosApplication` y para pruebas; nunca en ViewModels ni datos. El binding de un Fragment usa la referencia nula `_binding` (`02-arquitectura.md`, sección 3).
- Los DTOs de red declaran nulos lo que el backend pueda omitir y el mapper decide el valor por defecto.
- Preferir `val` sobre `var`, colecciones de solo lectura (`List`, `Map`) sobre mutables, y `data class` con `copy` para cambios de estado.
- `sealed interface` para estados y errores cerrados; `enum class` para conjuntos fijos sin datos asociados.
- `value class` para identificadores cuando ayudan a no mezclar ids (`AlbumId`, `PerformerId`) si el equipo lo adopta; se decide en el Sprint 1.

## 4. Funciones

- Cortas: máximo 60 líneas (detekt `LongMethod`). El dibujo de un `UiState` se divide en funciones `render...` por sección.
- Máximo 8 parámetros; más de eso es un `data class` de parámetros.
- Expresiones `when` exhaustivas sobre tipos sellados, sin rama `else` que oculte casos nuevos.
- Retornos tempranos para guardas; máximo 3 `return` por función.
- Funciones de extensión para transformar tipos (`AlbumDto.toModel()`) y para utilidades de un tipo ajeno; no para esconder lógica de negocio.
- Argumentos con nombre cuando hay más de dos del mismo tipo o booleanos.
- Sin números mágicos: constantes con nombre en `companion object` o nivel superior privado.

## 5. Clases y visibilidad

- Visibilidad mínima: `private` por defecto, `internal` para lo que cruza paquetes dentro del módulo, `public` solo en interfaces y modelos.
- Composición antes que herencia. Clases abiertas solo con justificación. Sin clases abstractas con una sola implementación.
- `object` para utilidades sin estado; `companion object` solo para constantes, claves de argumentos y la `Factory` del ViewModel.
- Las interfaces se definen desde el principio para Repository y Service Adapter aunque tengan una sola implementación: es lo que permite los fakes en pruebas.

## 6. Corrutinas y LiveData

- Funciones que hacen E/S son `suspend` y main-safe. Retrofit ya ejecuta las llamadas `suspend` fuera del hilo principal; el cálculo pesado va a `withContext(defaultDispatcher)` con el dispatcher recibido por constructor.
- `viewModelScope.launch` en el ViewModel; la Vista no lanza corrutinas de negocio.
- Nunca `GlobalScope`, nunca `runBlocking` en producción, nunca `Thread` ni `AsyncTask`.
- `try/catch` en la capa de datos captura excepciones concretas (`IOException`, `HttpException`, errores del convertidor JSON). `CancellationException` siempre se relanza. Nunca `catch (e: Exception)` que trague todo.
- Estado con `MutableLiveData` privado y `LiveData` público. Eventos de una sola vez con `LiveData<Event<T>>`. No se mezclan `LiveData` y `Flow` en la misma pantalla.
- `value` en el hilo principal (dentro de `viewModelScope`); `postValue` solo desde hilos de fondo.
- La Vista observa con `viewLifecycleOwner` en Fragments.

## 7. Manejo de errores

- Errores esperados (red, HTTP, validación) viajan como valores: `DataResult<T, DataError>`.
- Excepciones solo para errores de programación (`IllegalArgumentException`, `IllegalStateException`) y se dejan propagar.
- Mensajes al usuario desde `strings.xml`: el Fragment traduce el `DataError` a un recurso de texto. Los detalles técnicos van a `Log` solo en `debug`.
- Prohibido `TODO()` como implementación en código integrado; `TODO` en comentario solo con el número de issue: `// TODO(#42): ...`. Prohibidos `FIXME` y `STOPSHIP` (se registran como issues).

## 8. Comentarios y documentación

- El código se explica solo; los comentarios dicen por qué, no qué.
- KDoc en interfaces públicas (Repository, Service Adapter, modelos) con una frase por función: qué devuelve y qué error puede producir.
- Sin código comentado. Sin comentarios de autor ni de fecha; para eso está git.
- Los enlaces a la historia de usuario van en el PR y en el issue, no en el código.

## 9. Recursos y Android

- Textos visibles solo en `res/values/strings.xml` (español) con nombres `pantalla_elemento` (`album_list_title`, `action_retry`). Nunca `android:text="..."` literal ni `setText("...")`: Lint `HardcodedText` falla el build.
- Colores en `colors.xml`, dimensiones en `dimens.xml` (escala de 4 dp), estilos de texto en `themes.xml`/`styles.xml`. Sin `#RRGGBB` ni `dp` sueltos en los layouts.
- `ConstraintLayout` para pantallas con varios elementos; sin anidar `LinearLayout` con pesos para lo mismo.
- Sin `Context` en ViewModels. Si hace falta un recurso, se resuelve en el Fragment.
- APIs posteriores a 21 solo detrás de `Build.VERSION.SDK_INT` (Lint `NewApi` falla el build) o con su versión de AndroidX (`ContextCompat`, `ViewCompat`).
- `@Suppress` solo con motivo en la misma línea y revisado en el PR.

## 10. Ejemplo de referencia

```kotlin
class AlbumDetailViewModel(
    private val albumId: Int,
    private val albumRepository: AlbumRepository,
) : ViewModel() {
    private val _uiState = MutableLiveData(AlbumDetailUiState(isLoading = true))
    val uiState: LiveData<AlbumDetailUiState> = _uiState

    private val _events = MutableLiveData<Event<AlbumDetailEvent>>()
    val events: LiveData<Event<AlbumDetailEvent>> = _events

    init {
        load()
    }

    fun load() {
        _uiState.value = state().copy(isLoading = true, error = null)
        viewModelScope.launch {
            _uiState.value = when (val result = albumRepository.getAlbum(albumId)) {
                is DataResult.Success -> state().copy(isLoading = false, album = result.value)
                is DataResult.Failure -> state().copy(isLoading = false, error = result.error)
            }
        }
    }

    fun onTrackAdded() {
        _events.value = Event(AlbumDetailEvent.ShowMessage(R.string.snack_track_added))
        load()
    }

    private fun state() = checkNotNull(_uiState.value)
}
```

## 11. Bitácora de decisiones

| Fecha | Decisión |
|---|---|
| 2026-10-07 | Estilo `ktlint_official`, 120 columnas, coma final, backing fields clásicos, sin `!!`, errores como valores. |
| 2026-10-26 | Reglas de corrutinas y recursos adaptadas a Fragments y `LiveData`; nombres para Fragments, adaptadores, layouts e ids; `HardcodedText` y `NewApi` como errores de Lint. |
