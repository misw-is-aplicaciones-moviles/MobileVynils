# 04. Patrones de diseño

Patrones que usa Vinilos, con el problema que resuelven y cómo se aplican aquí. Al final, los antipatrones que el equipo evita. Los nombres en negrita son los que deben aparecer en el documento de diseño arquitectónico de la wiki.

## 1. Patrones de arquitectura

### Model-View-ViewModel (MVVM)

Problema: separar la lógica de presentación de la vista para poder probarla sin Android. Aplicación: `XScreen` (View) solo dibuja `XUiState`; `XViewModel` posee el estado y la lógica; el Model son los modelos de dominio y el Repository. La vista nunca llama al Repository.

### Flujo de datos unidireccional (UDF)

Problema: estado inconsistente cuando varias vistas lo mutan. Aplicación: el estado baja (`StateFlow<UiState>`), los eventos suben (`onAction`). Un solo lugar (`_uiState.update { }`) cambia el estado.

### Repository

Problema: la UI no debe saber de dónde vienen los datos ni cómo se combinan. Aplicación: interfaz `AlbumRepository` con una implementación que coordina Room y el Service Adapter; única frontera de errores.

### Service Adapter

Problema: aislar el protocolo de red (Retrofit, DTOs, códigos HTTP) del resto. Aplicación: `AlbumServiceAdapter` envuelve `VinilosApi`, deserializa y mapea a dominio. Cambiar de backend o de librería solo toca esta clase. Es la variante del patrón **Adapter** que pide el curso.

### Fuente única de verdad (Single Source of Truth)

Problema: dos copias de los datos (red y caché) que divergen. Aplicación: la UI siempre observa Room; la red solo escribe en Room. Patrón **Offline-first** en su forma mínima: la lista se ve aunque no haya red, con un aviso.

### Inyección de dependencias (Hilt)

Problema: acoplamiento a implementaciones concretas y objetos imposibles de reemplazar en pruebas. Aplicación: constructores `@Inject`, `@Binds` para interfaces, `@Provides` para terceros, qualifiers para dispatchers. En pruebas, `@TestInstallIn` reemplaza módulos por fakes.

## 2. Patrones de diseño (GoF y afines)

| Patrón | Dónde | Para qué |
|---|---|---|
| **Observer** | `Flow`, `StateFlow`, DAOs de Room, `collectAsStateWithLifecycle` | La UI reacciona a cambios sin consultar |
| **Adapter** | Service Adapter, mappers `toDomain()`/`toEntity()`/`toUi()` | Traducir entre DTO, entidad, dominio y UI |
| **Factory** | `@Provides` de Retrofit, Room, `Json`, `ImageLoader` | Construcción centralizada de objetos complejos |
| **Singleton** | `@Singleton` en Hilt para Retrofit, Room, repositorios | Una instancia, sin `object` global con estado |
| **Strategy** | `SharingStarted`, `DataError.toUiText()` por tipo | Variar comportamiento por tipo sin `if` encadenados |
| **State** | `sealed interface` para estados de carga de detalle o del formulario | Estados explícitos, `when` exhaustivo |
| **Command** | `sealed interface XAction` | Eventos de UI como valores, fáciles de probar y registrar |
| **Builder** | `OkHttpClient.Builder`, `Retrofit.Builder`, `ImageRequest.Builder` | Objetos de configuración de terceros |
| **Null Object** | Listas vacías, `UiText.Empty` | Evitar nulos en el estado |
| **Facade** | `VinilosDatabase` sobre los DAOs; `VinilosNavHost` sobre las rutas | Un punto de entrada simple |
| **Template Method** (vía slots) | Componentes Compose con `content: @Composable () -> Unit` | Reutilizar estructura y variar contenido |

## 3. Patrones de Compose

- **State hoisting**: el estado vive en el nivel más bajo que lo necesita y se sube solo cuando lo comparten varios hijos. Composables sin estado reciben `value` y `onValueChange`.
- **Slot API**: componentes compartidos (`VinilosTopBar`, `VinilosCard`) reciben contenido como lambdas, no banderas booleanas.
- **Modifier como primer parámetro opcional** aplicado al nodo raíz del componente.
- **Previews por estado**: cada `Screen` tiene `@Preview` de carga, contenido, vacío y error, con datos de `core/ui/preview/PreviewData.kt`.
- **Claves estables en listas**: `LazyColumn { items(albums, key = { it.id }) }`.
- **Efectos mínimos**: `LaunchedEffect` con clave correcta para colectar efectos; `DisposableEffect` solo para recursos del sistema.

## 4. Patrones de pruebas

- **Fake sobre Mock** para Repository y Service Adapter: `FakeAlbumRepository` con `MutableStateFlow` controlable. MockK para colaboradores que no vale la pena fingir.
- **MainDispatcherRule**: regla JUnit que instala `UnconfinedTestDispatcher` o `StandardTestDispatcher` en `Dispatchers.Main`.
- **Turbine** para afirmar emisiones de `Flow`.
- **MockWebServer** para probar el Service Adapter con respuestas reales del backend grabadas en `src/test/resources/`.
- **Page Object** en pruebas E2E: una clase por pantalla (`AlbumListRobot`) con las acciones y aserciones de esa pantalla.

## 5. Antipatrones prohibidos

| Antipatrón | Por qué | Alternativa |
|---|---|---|
| God ViewModel que conoce varias pantallas | Imposible de probar, estado compartido accidental | Un ViewModel por pantalla; lo común va al Repository |
| `Context` o `Activity` en ViewModel | Fugas de memoria, no probable en JVM | `UiText`, `Application` solo vía Hilt si es imprescindible |
| Lógica en el Composable (`if (repository...)`) | Recomposiciones costosas, no probable | Mover al ViewModel |
| `LiveData` mezclado con `Flow` | Dos modelos de reactividad | Solo `Flow`/`StateFlow` |
| `GlobalScope`, `runBlocking`, `Thread` | Fugas, ANR, cancelación rota | `viewModelScope`, dispatchers inyectados |
| `catch (e: Exception)` silencioso | Oculta `CancellationException` y errores reales | Excepciones concretas en la capa de datos |
| Singletons manuales (`object Repo`) | Estado global, no reemplazable en pruebas | `@Singleton` en Hilt |
| Pasar objetos completos por navegación | Límite de tamaño, estado duplicado | Pasar ids; la pantalla observa Room |
| `!!` | Crash en producción | Tipos no nulos, `?:`, `requireNotNull` |
| Números y textos mágicos | Imposibles de mantener y traducir | Constantes y `strings.xml` |
| `var` en `UiState` o `MutableList` expuesta | Estado mutable compartido | `data class` con `val` y `copy` |
| Mapear DTOs en la UI | Acoplamiento al backend | Mappers en `data/` |
| Lógica de negocio en mappers | Difícil de encontrar y probar | Mappers solo traducen |
| `@Suppress` sin motivo | Esconde deuda | Motivo en línea y revisión en PR |

## 6. Bitácora de decisiones

| Fecha | Decisión |
|---|---|
| 2026-10-07 | Catálogo inicial de patrones y antipatrones. Fakes antes que mocks. Page Object en E2E. |
