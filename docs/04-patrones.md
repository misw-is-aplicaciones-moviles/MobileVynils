# 04. Patrones de diseño

Patrones que usa Vinilos, con el problema que resuelven y cómo se aplican aquí. Al final, los antipatrones que el equipo evita. Los nombres en negrita son los que deben aparecer en el documento de diseño arquitectónico de la wiki.

## 1. Patrones de arquitectura

### Model-View-ViewModel (MVVM)

Problema: separar la lógica de presentación de la vista para poder probarla sin Android. Aplicación: el Fragment (View) solo dibuja el `UiState` y reenvía acciones; el `XViewModel` posee el estado en `LiveData` y la lógica; el Model son los `models` y el Repository. El Fragment nunca llama al Repository.

### Repository

Problema: el ViewModel no debe saber de dónde vienen los datos ni cómo se guardan en caché. Aplicación: interfaz `AlbumRepository` con una implementación que coordina la caché en memoria y el Service Adapter; única frontera de errores.

### Service Adapter

Problema: aislar el protocolo de red (Retrofit, DTOs, códigos HTTP) del resto. Aplicación: `AlbumServiceAdapter` envuelve `VinilosApiService`, recibe DTOs y los mapea a `models`. Cambiar de backend o de librería solo toca esta capa. Es la variante del patrón **Adapter** que pide el curso.

### Observer (`LiveData`)

Problema: actualizar la vista cuando cambian los datos sin consultarlos ni fugar memoria. Aplicación: el Fragment observa `LiveData` con `viewLifecycleOwner`; el ViewModel publica estados nuevos. `LiveData` respeta el ciclo de vida: no entrega valores a vistas destruidas.

### Inyección de dependencias manual (Service Locator acotado)

Problema: acoplamiento a implementaciones concretas y objetos imposibles de reemplazar en pruebas. Aplicación: `AppContainer` crea las dependencias una sola vez (`lazy`) y las entrega a las fábricas de ViewModel; las clases reciben todo por constructor. En pruebas unitarias se construyen con fakes sin tocar el contenedor.

## 2. Patrones de diseño (GoF y afines)

| Patrón | Dónde | Para qué |
|---|---|---|
| **Observer** | `LiveData`, `Observer` en Fragments | La vista reacciona a cambios sin consultar |
| **Adapter** | Service Adapter, mappers `toModel()`, `RecyclerView.Adapter` | Traducir entre DTO y modelo; entre lista de modelos y vistas |
| **Factory** | `ViewModelProvider.Factory` (`viewModelFactory { initializer { } }`), `AppContainer` | Construir ViewModels y objetos con dependencias |
| **Singleton** | Instancias únicas dentro de `AppContainer` (Retrofit, OkHttp, repositorios) | Una instancia por app, sin `object` global con estado |
| **State** | `UiState` con carga, error, vacío y contenido; estados del formulario de HU07 | Estados explícitos, `when` exhaustivo |
| **Command** | `Event<T>` con eventos `sealed interface` (`OpenDetail`, `ShowMessage`) | Eventos de una sola vez como valores, fáciles de probar |
| **Builder** | `OkHttpClient.Builder`, `Retrofit.Builder`, `MaterialAlertDialogBuilder` | Objetos de configuración de terceros |
| **Null Object** | Listas vacías por defecto en el `UiState` | Evitar nulos en el estado |
| **Facade** | Repository sobre caché y Service Adapter; `AppContainer` sobre la construcción | Un punto de entrada simple |
| **ViewHolder** | `RecyclerView.ViewHolder` con ViewBinding del ítem | Reutilizar vistas al hacer scroll |

## 3. Patrones de la Vista

- **ViewBinding con referencia nula** en Fragments (`_binding` se limpia en `onDestroyView`).
- **`ListAdapter` + `DiffUtil.ItemCallback`** en toda lista: `submitList` calcula los cambios fuera del hilo principal.
- **`include` para estados compartidos**: `view_state_loading.xml`, `view_state_error.xml` y `view_state_empty.xml` se incluyen en cada pantalla y se muestran según el `UiState`.
- **Una función `render(state)`** por Fragment que dibuja todo el estado; nunca se cambia la vista desde varios observadores.
- **Navegación por eventos**: el ViewModel emite `Event(OpenDetail(id))` y el Fragment llama a `findNavController().navigate(...)`.

## 4. Patrones de pruebas

- **Fake sobre Mock** para Repository y Service Adapter: `FakeAlbumRepository` con resultados configurables. Sin librería de mocks en el catálogo.
- **InstantTaskExecutorRule** para que `LiveData` publique de forma síncrona en pruebas unitarias.
- **MainDispatcherRule** que instala un `TestDispatcher` en `Dispatchers.Main` para `viewModelScope`. No es de una librería: está en el paquete `testing` de `src/test` (TR-05).
- **`getOrAwaitValue`** (extensión propia en `testing/LiveDataExtensions.kt`, TR-05) para leer un `LiveData` que se publica más tarde; con `InstantTaskExecutorRule` basta `.value` para lo ya publicado.
- **MockWebServer** (`MockWebServerRule`) para probar el Service Adapter con respuestas reales de BackVynils guardadas en `src/test/resources/api/`.
- **IdlingResource sobre OkHttp** (`OkHttpIdlingResourceRule` en `src/androidTest`) para que Espresso espere la red sin `Thread.sleep`.
- **Page Object (Robot)** en pruebas E2E: una clase por pantalla en `robots/` (`AlbumListRobot`) con las acciones y aserciones de Espresso de esa pantalla.

## 5. Antipatrones prohibidos

| Antipatrón | Por qué | Alternativa |
|---|---|---|
| God ViewModel que atiende varias pantallas | Imposible de probar, estado compartido accidental | Un ViewModel por pantalla; lo común va al Repository |
| `Context`, `View` o `Fragment` en el ViewModel | Fugas de memoria, no probable en JVM | Ids de recursos en el estado; el Fragment resuelve los textos |
| Lógica en el Fragment (`if` sobre datos, llamadas al Repository) | Imposible de probar sin dispositivo | Mover al ViewModel |
| Observar con `this` en un Fragment | Observadores duplicados tras volver a la pantalla | `viewLifecycleOwner` |
| Retener el binding después de `onDestroyView` | Fuga de la jerarquía de vistas | `_binding = null` |
| `LiveData` mezclado con `Flow` en la misma pantalla | Dos modelos de reactividad | Solo `LiveData` en la capa de presentación |
| `GlobalScope`, `runBlocking`, `Thread`, `AsyncTask` | Fugas, ANR, cancelación rota | `viewModelScope` y funciones `suspend` |
| `catch (e: Exception)` silencioso | Oculta `CancellationException` y errores reales | Excepciones concretas en la capa de datos |
| `object` con estado global (`object Repository`) | No reemplazable en pruebas | Instancia única dentro de `AppContainer` |
| Pasar objetos completos entre Fragments | Límite de tamaño del `Bundle`, estado duplicado | Pasar ids; la pantalla pide el recurso |
| `!!` | Crash en producción | Tipos no nulos, `?:`, `checkNotNull` |
| Textos, colores y medidas literales | Imposibles de mantener y traducir | `strings.xml`, `colors.xml`, `dimens.xml` |
| `notifyDataSetChanged()` | Redibuja toda la lista | `ListAdapter.submitList` con `DiffUtil` |
| Mapear DTOs en la Vista | Acoplamiento al backend | Mappers en `network/` |
| `@Suppress` sin motivo | Esconde deuda | Motivo en línea y revisión en PR |

## 6. Bitácora de decisiones

| Fecha | Decisión |
|---|---|
| 2026-10-07 | Catálogo inicial de patrones y antipatrones. Fakes antes que mocks. Page Object en E2E. |
| 2026-10-26 | Patrones de la Vista con Fragments y RecyclerView; Observer con `LiveData`; inyección manual con `AppContainer`; pruebas con `InstantTaskExecutorRule` y Robots de Espresso. |
