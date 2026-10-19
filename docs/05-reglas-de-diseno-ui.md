# 05. Reglas de diseño de UI

Reglas para construir las pantallas de Vinilos con Jetpack Compose y Material 3, coherentes con el prototipo navegable del Inception. Cubren tema, componentes, navegación, recursos, accesibilidad y usabilidad.

## 1. Sistema de diseño

- **Material 3** con esquema de color dinámico en Android 12+ y paleta propia de Vinilos como respaldo (`core/ui/theme/Color.kt`). Tema claro y oscuro obligatorios.
- `VinilosTheme` envuelve toda la app en `MainActivity`. Ningún composable usa colores, tipografías ni formas literales: siempre `MaterialTheme.colorScheme`, `MaterialTheme.typography`, `MaterialTheme.shapes`.
- Espaciado en una escala de 4 dp (`Dimens.kt`: 4, 8, 12, 16, 24, 32). Margen de pantalla 16 dp.
- Iconos de `material-icons-extended`; sin PNG para iconos. Imágenes remotas con Coil y placeholder de carga y error.
- Edge-to-edge activado (`enableEdgeToEdge()`); los insets se aplican con `Scaffold` y `contentWindowInsets`, nunca con paddings fijos para la barra de estado.

## 2. Estructura de pantalla

Toda pantalla sigue esta plantilla:

```kotlin
@Composable
fun AlbumListScreen(
    uiState: AlbumListUiState,
    onAction: (AlbumListAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { VinilosTopBar(title = stringResource(R.string.album_list_title)) },
    ) { innerPadding ->
        when {
            uiState.isLoading && uiState.albums.isEmpty() -> LoadingContent(Modifier.padding(innerPadding))
            uiState.error != null && uiState.albums.isEmpty() -> ErrorContent(uiState.error, onRetry = { onAction(RetryClicked) })
            uiState.isEmpty -> EmptyContent(...)
            else -> AlbumListContent(uiState.albums, onAlbumClick = { onAction(AlbumClicked(it)) })
        }
    }
}
```

- Cuatro estados visibles siempre resueltos: **cargando**, **error**, **vacío**, **contenido**. Los componentes `LoadingContent`, `ErrorContent` y `EmptyContent` son compartidos en `core/ui/components`.
- Error con datos ya cargados: se muestra el contenido y un `Snackbar` con reintento, no una pantalla de error.
- Las listas son `LazyColumn`/`LazyVerticalGrid` con `key` y `contentType`; nunca `Column` con `forEach` para más de 10 elementos.
- Los formularios (HU07 crear álbum, HU08 agregar track) validan en el ViewModel y muestran el error bajo el campo con `supportingText` e `isError`. El botón de guardar se deshabilita mientras se envía.
- Pull-to-refresh con `PullToRefreshBox` en los listados.

## 3. Navegación

- Un `NavHost` en `core/ui/navigation/VinilosNavHost.kt`. Rutas en `Routes.kt`, las mismas de `10-diseno-de-la-aplicacion.md` (sección 3):

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

- Navegación principal con `NavigationBar` de tres destinos (Álbumes, Artistas, Coleccionistas), estado guardado por pestaña (`saveState`/`restoreState`, `launchSingleTop`). `RoleSelect` no muestra la barra.
- Las pantallas de detalle se apilan sobre la pestaña y vuelven con la flecha y con el gesto de retroceso predictivo. `CreateAlbum` y los tres selectores abren a pantalla completa con icono "X".
- Las hojas "Nueva canción" (HU08) y "Comentar álbum" (HU09) son `ModalBottomSheet` controladas por `AlbumDetailUiState.activeSheet`; no son rutas.
- El rol (visitante o coleccionista) se elige en `RoleSelect` y vive en `SessionRepository`. Cada `UiState` expone `canEdit`; con él se ocultan el FAB "+", el FAB "Canción", el botón "Comentar" y los botones "Agregar álbum / integrante / favorito". El `RoleChip` de la barra superior vuelve a `RoleSelect`.
- Transiciones por defecto de Navigation Compose; sin animaciones personalizadas en el MVP.

## 4. Componentes compartidos (`core/ui/components`)

| Componente | Uso |
|---|---|
| `VinilosTopBar` | Título, acción de volver o cerrar ("X") y `RoleChip` a la derecha |
| `RoleChip` | `AssistChip` con el rol actual; al tocarlo vuelve a `RoleSelect` |
| `AlbumCard`, `ArtistCard`, `CollectorCard` | Elementos de listado: imagen o avatar, título, subtítulo, tercera línea opcional, chevron |
| `InitialsAvatar` | Avatar con iniciales para coleccionistas (el backend no tiene imagen) |
| `InfoTile` | Ficha de dato con etiqueta y valor (Sello, Lanzamiento, Canciones, Teléfono, Correo) |
| `LoadingContent`, `ErrorContent`, `EmptyContent` | Estados de pantalla; `ErrorContent` con icono, título, cuerpo y "Reintentar" |
| `VinilosTextField` | Campo con etiqueta, error en `supportingText` y teclado adecuado |
| `ChoiceChipGroup` | Fila de `FilterChip` de una sola selección (filtros, género, sello, coleccionista, valoración) |
| `SectionTitle` | Título de sección con conteo ("Álbumes · 3") y acción opcional a la derecha |
| `PickerScreen` | Lista en modo selección: búsqueda local, `RadioButton`, chip de estado por ítem, botón inferior con el nombre elegido |
| `StatusChip` | `SuggestionChip` deshabilitado: "Ya agregado", "Ya integrante", "Ya es favorito", "Disponible" / "No disponible" |

La correspondencia completa entre el kit del prototipo y Material 3 está en `10-diseno-de-la-aplicacion.md` (sección 7).

Cada componente: `modifier` como primer parámetro opcional, sin acceder a ViewModels, con `@Preview` claro y oscuro.

## 5. Recursos y textos

- Todo texto en `res/values/strings.xml` (español). Claves en `snake_case` minúsculas con prefijo de pantalla o de tipo (`album_list_title`, `error_required`, `action_cancel`, `snack_album_created`), tomadas del catálogo de `10-diseno-de-la-aplicacion.md` (sección 8). Plurales con `<plurals>`. Formatos con argumentos posicionales (`%1$s`). El hook del espacio de trabajo rechaza `Text("...")` literal en composables y claves mal formadas o repetidas.
- Sin `strings` por defecto en inglés en el MVP; la estructura permite añadir `values-en` después.
- Fechas con `java.time` y formato de la configuración regional del dispositivo.
- Imágenes: `AsyncImage` con `contentDescription` descriptivo o `null` si es decorativa, `ContentScale.Crop` y tamaño fijo en listas para evitar saltos.

## 6. Accesibilidad (mínimos obligatorios, Sprint 3 los amplía)

- Todo elemento interactivo tiene área táctil de al menos 48 x 48 dp (Material lo garantiza; no se reduce con `Modifier.size` menor).
- Todo icono con acción tiene `contentDescription`; los decorativos usan `null`.
- Contraste mínimo 4.5:1 en texto normal (el esquema M3 lo cumple si no se sobreescriben colores).
- Los encabezados de sección se marcan con `Modifier.semantics { heading() }`.
- Los textos escalan con la preferencia de tamaño de fuente del usuario: nunca `fontSize` fijo en `sp` fuera del tema; se prueba con fuente al 200 %.
- Los estados de carga y error se anuncian: `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` en el contenedor de mensajes.
- Orden de foco lógico: el layout sigue el orden de lectura; no se reordena con `zIndex` sin ajustar `traversalIndex`.
- Verificación: Accessibility Scanner en cada pantalla nueva, TalkBack en el flujo principal y `AccessibilityChecks.enable()` en las pruebas E2E (ver `06-pruebas.md`).

## 7. Usabilidad

- Feedback inmediato: todo toque produce ripple y toda acción larga muestra progreso en menos de 300 ms.
- Nunca bloquear la pantalla con un diálogo de carga; usar indicadores en línea.
- Mensajes de error en lenguaje del usuario, con acción de recuperación ("Reintentar", "Revisar conexión").
- Formularios: teclado adecuado por campo (`KeyboardType.Number` para duración, año), acción `ImeAction.Next`/`Done`, el foco pasa al siguiente campo.
- Estado preservado ante rotación y vuelta desde segundo plano (lo garantiza el ViewModel; se prueba).
- Compatibilidad con ventanas pequeñas: el contenido hace scroll; nada depende de una altura fija.

## 8. Lista de verificación por pantalla

- [ ] Cuatro estados resueltos y previsualizados.
- [ ] Sin colores, dimensiones ni textos literales.
- [ ] `contentDescription` en iconos de acción e imágenes significativas.
- [ ] 48 dp en todo elemento tocable.
- [ ] Probada en claro y oscuro, fuente al 200 % y rotación.
- [ ] Accessibility Scanner sin errores.
- [ ] Coincide con su especificación en `10-diseno-de-la-aplicacion.md` (sección 6: campos, validaciones, mensajes, acciones por rol) o la diferencia está justificada en el PR.
- [ ] Probada en los dos roles: el visitante no ve ninguna acción de escritura.

## 9. Bitácora de decisiones

| Fecha | Decisión |
|---|---|
| 2026-10-07 | Material 3 con color dinámico, edge-to-edge, `NavigationBar` de tres destinos, plantilla de pantalla con cuatro estados, mínimos de accesibilidad desde el Sprint 1. |
| 2026-10-14 | Rutas, componentes y claves de texto alineados con el prototipo de Figma y `10-diseno-de-la-aplicacion.md`: `RoleSelect` inicial con `RoleChip`, hojas inferiores para canción y comentario, `PickerScreen` común para HU15, HU12 y HU10. La paleta gris e Inter del prototipo no se replican: se usa el esquema M3. |
