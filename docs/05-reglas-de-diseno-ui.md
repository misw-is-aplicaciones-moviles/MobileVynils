# 05. Reglas de diseño de UI

Reglas para construir las pantallas de Vinilos con Vistas XML, Fragments y Material Components, fieles a los mockups v2 de Figma ("Vynils V2 MockUps", 76 pantallas de 390 x 844 nombradas por historia, más "Kit v2 · Componentes"). Cubren tema, estructura de pantalla, navegación, componentes, recursos, accesibilidad y usabilidad.

## 1. Sistema de diseño

Tokens del kit v2, declarados una sola vez en `res/values/colors.xml`, `dimens.xml` y `themes.xml`:

| Token | Valor | Uso |
|---|---|---|
| `vinilos_cream` | `#F2EFE7` | Hoja de contenido (fondo de cada pantalla), con textura sutil de puntos |
| `vinilos_charcoal` | `#232323` | Cabecera, texto principal, botón primario |
| `vinilos_graphite` | `#66645E` | Texto secundario |
| `vinilos_ash` | `#8A8780` | Íconos y etiquetas inactivos de la barra inferior |
| `vinilos_line` | `#D2CFC7` | Divisores y bordes suaves |
| `vinilos_stone` | `#D9D6CE` | Botón deshabilitado y opción elegida |
| `vinilos_paper_dot` | `#0F232323` | Puntos de la textura de papel (carbón al 6 %) |
| Fuente de títulos y cuerpo | Outfit (`res/font/outfit_*.ttf`) | Título de cabecera Outfit 400, 19 sp |
| Fuente de etiquetas | Montserrat 600 y 700, mayúsculas | Etiquetas de la barra inferior, chips, sobretítulos |
| Esquinas de la hoja | 28 dp arriba | Forma de la hoja crema sobre la cabecera carbón |

- Tema `Theme.Vinilos` sobre `Theme.Material3.Light.NoActionBar` (Material Components 1.12). La plantilla actual hereda de `Theme.Material3.DayNight.NoActionBar`; TR-01 cambia el padre a la variante clara. Los colores del kit se asignan a los atributos del tema (`colorPrimary`, `colorSurface`, `colorOnSurface`...) y las vistas los leen con `?attr/`, nunca con valores literales.
- Tema claro en el Sprint 1 (los mockups v2 son claros). El oscuro (`values-night`) se decide en equipo más adelante.
- Fuentes empaquetadas en `res/font` (licencia OFL), **un archivo por peso** (`outfit_regular.ttf`, `montserrat_semibold.ttf`, `montserrat_bold.ttf`): `android:textFontWeight` solo existe desde API 28, así que el peso se elige por recurso, no por atributo. Si se crea una familia XML, declara cada fuente con los atributos `android:` y `app:` para que AppCompat la lea en API 21. Solo se empaquetan los pesos que usa algún estilo: cada TTF de Montserrat pesa unos 450 KB. Si TR-04 necesita Montserrat 800 u Outfit 500, los agrega con el estilo que los usa.
- Espaciado en una escala de 4 dp en `dimens.xml` (`space_xs` 4, `space_s` 8, `space_m` 12, `space_l` 16, `space_xl` 24, `space_xxl` 32). Margen de pantalla 16 dp.
- Íconos vectoriales (`res/drawable/ic_*.xml`) con `app:srcCompat`; nunca PNG para íconos. Con `useSupportLibrary` los vectores no se convierten a PNG: en `android:drawableStart`/`drawableEnd` se usa `app:drawableStartCompat`/`app:drawableEndCompat`. Dentro de selectores o `layer-list` los infla el `VectorDrawable` del sistema (API 21+), que en API 21 a 23 no admite referencias a tema (`?attr/`), `android:fillType` ni degradados. Por eso los selectores de la barra inferior (`ic_nav_*_state.xml`) usan vectores simples con relleno fijo y el color lo pone el tinte de la barra. Un vector con esas funciones va en `app:srcCompat` o se carga con `AppCompatResources.getDrawable`.
- Imágenes remotas con Glide, `placeholder` y `error` propios, tamaño fijo en listas.
- Edge-to-edge: con `targetSdk 36`, Android 15 o superior dibuja la app detrás de las barras del sistema aunque no se pida. `MainActivity` llama a `enableEdgeToEdge()` (seguro desde API 21) y aplica los márgenes con `ViewCompat.setOnApplyWindowInsetsListener`: la cabecera carbón recibe el margen superior y la barra inferior el inferior. Se verifica en el emulador API 37.

## 2. Estructura de pantalla

Cada pantalla tiene cabecera carbón con el título (y la flecha o la X cuando corresponde, más el chip de rol a la derecha) y, debajo, la hoja crema con el contenido:

```xml
<androidx.constraintlayout.widget.ConstraintLayout ...>
    <include android:id="@+id/header" layout="@layout/view_header" ... />
    <co.edu.uniandes.vinilos.ui.common.PaperSheetLayout android:id="@+id/sheet" ...>
        <androidx.recyclerview.widget.RecyclerView android:id="@+id/albumList" ... />
        <include android:id="@+id/loadingState" layout="@layout/view_state_loading" />
        <include android:id="@+id/errorState" layout="@layout/view_state_error" />
        <include android:id="@+id/emptyState" layout="@layout/view_state_empty" />
    </co.edu.uniandes.vinilos.ui.common.PaperSheetLayout>
</androidx.constraintlayout.widget.ConstraintLayout>
```

- La hoja es `PaperSheetLayout` (`ui/common`): un `FrameLayout` cuyo fondo dibuja la crema con las esquinas superiores de 28 dp y la retícula de puntos con un `BitmapShader` (un mosaico de un punto repetido). El fondo no recorta a los hijos: HU01 agrega el recorte (`ViewOutlineProvider` y `clipToOutline`) cuando la lista haga scroll bajo las esquinas.
- Cuatro estados siempre resueltos: **cargando**, **error**, **vacío**, **contenido**. El Fragment muestra uno con `isVisible` según el `UiState` en su función `render(state)`.
- Error con datos ya cargados: se mantiene el contenido y se muestra un `Snackbar` con "Reintentar".
- Listas con `RecyclerView` + `ListAdapter` + `DiffUtil`; nunca `LinearLayout` con vistas añadidas en un bucle.
- Formularios (HU07 crear álbum, HU08 agregar canción): `TextInputLayout` + `TextInputEditText`; el error se muestra con `textInputLayout.error`. La validación vive en el ViewModel. El botón de guardar se deshabilita mientras se envía.

## 3. Navegación

- `MainActivity` con `NavHostFragment` (`res/navigation/nav_graph.xml`) y `BottomNavigationView` (`res/menu/bottom_nav.xml`) enlazados con `setupWithNavController`.
- Tres destinos de primer nivel: Álbumes (`albumListFragment`), Artistas (`performerListFragment`) y Coleccionistas (`collectorListFragment`), con el ícono de disco, micrófono y personas y un punto bajo la pestaña activa, como en los mockups.
- El selector de rol (`roleSelectFragment`, TR-06) es el destino inicial y oculta la barra inferior.
- Detalles y formularios se apilan sobre la pestaña y vuelven con la flecha y con el botón atrás. La hoja "Nueva canción" (HU08) es un `BottomSheetDialogFragment`, no un destino de pantalla completa.
- Argumentos: ids en un `Bundle` con claves constantes (`ARG_ALBUM_ID`). Sin objetos completos.
- Transiciones entre pestañas y pantallas con `MaterialFadeThrough` de 300 ms (`material`), definidas en el Fragment (`enterTransition`/`exitTransition`).
- Las acciones de coleccionista (botón "+" de la barra para crear álbum, "+" para agregar canción) solo se muestran si `uiState.canEdit` es verdadero.

## 4. Componentes compartidos (TR-04)

| Componente | Implementación | Uso |
|---|---|---|
| Cabecera | `view_header.xml` (título, botón atrás o cerrar, chip de rol) | Todas las pantallas |
| Chip de rol | `Chip` con estilo propio ("⇄ VISITANTE" / "⇄ COLECCIONISTA") | Vuelve al selector de rol |
| Tarjetas de lista | `item_album.xml`, `item_performer.xml`, `item_collector.xml` (imagen, título, subtítulo, número) | Listados |
| Estados | `view_state_loading.xml`, `view_state_error.xml` (mensaje y "Reintentar"), `view_state_empty.xml` | Todas las listas y detalles |
| Ficha de dato | `view_info_tile.xml` (etiqueta y valor) | Sello, Lanzamiento, Canciones, Teléfono, Correo |
| Campo de texto | Estilo `Widget.Vinilos.TextInputLayout` | Formularios |
| Grupo de chips | `ChipGroup` con `app:singleSelection="true"` y estilo `Widget.Vinilos.Chip` | Género, sello, filtros |

Cada componente vive en `res/layout` o `res/values/styles.xml` y no conoce ViewModels.

## 5. Recursos y textos

- Todo texto en `res/values/strings.xml` (español). Nombres `pantalla_elemento` (catálogo en `10-diseno-de-la-aplicacion.md`, sección 8). Plurales con `<plurals>`, formatos con argumentos posicionales (`%1$s`).
- Lint `HardcodedText` es error: ningún `android:text` literal en layouts.
- Fechas con `java.time` (desugaring) y formato de la configuración regional del dispositivo.
- Imágenes: `ImageView` con `android:contentDescription` descriptivo o `@null` si es decorativa, `scaleType="centerCrop"` y tamaño fijo en listas.

## 6. Accesibilidad (mínimos obligatorios, Sprint 3 los amplía)

- Todo elemento tocable mide al menos 48 x 48 dp (`minHeight`/`minWidth` o `TouchDelegate`).
- Todo `ImageButton` e ícono con acción tiene `contentDescription`; los decorativos usan `@null` o `importantForAccessibility="no"`.
- Contraste mínimo 4.5:1 en texto normal. El carbón sobre crema y el crema sobre carbón lo cumplen; el gris `#8A8780` solo se usa para íconos inactivos, no para texto.
- Encabezados (título de cabecera y de sección) con `ViewCompat.setAccessibilityHeading(view, true)`, que funciona desde API 21; `android:accessibilityHeading` solo existe desde API 28 y en API 21 a 27 se ignora.
- Textos en `sp`; se prueba con fuente al 200 %.
- Mensajes de carga y error que cambian en pantalla con `android:accessibilityLiveRegion="polite"`; un texto fijo no lo lleva.
- Orden de foco lógico: el layout sigue el orden de lectura.
- Verificación: Accessibility Scanner en cada pantalla nueva, TalkBack en el flujo principal y `AccessibilityChecks.enable()` en las pruebas Espresso (Sprint 3; requiere agregar `espresso-accessibility` al catálogo y verificarlo con `minSdk 21`).

## 7. Usabilidad

- Feedback inmediato: todo toque produce ripple (`?attr/selectableItemBackground`) y toda acción larga muestra progreso en menos de 300 ms.
- Nunca bloquear la pantalla con un diálogo de carga; indicadores en línea (`CircularProgressIndicator`).
- Mensajes de error en lenguaje del usuario, con acción de recuperación ("Reintentar").
- Formularios: `inputType` adecuado (`number` para año y duración), `imeOptions="actionNext"`/`actionDone`.
- Estado preservado ante rotación y vuelta desde segundo plano (lo garantiza el ViewModel; se prueba).
- El contenido hace scroll; nada depende de una altura fija. Se verifica en el emulador API 21 (pantalla 1080 x 2280).

## 8. Lista de verificación por pantalla

- [ ] Cuatro estados resueltos.
- [ ] Sin colores, dimensiones ni textos literales.
- [ ] `contentDescription` en íconos de acción e imágenes significativas.
- [ ] 48 dp en todo elemento tocable.
- [ ] Probada en API 21 y API 37, con fuente al 200 % y rotación.
- [ ] Coincide con el mockup v2 de su historia o la diferencia está justificada en el PR.

## 9. Bitácora de decisiones

| Fecha | Decisión |
|---|---|
| 2026-10-07 | Material 3 con Compose, color dinámico, `NavigationBar` de tres destinos, plantilla de pantalla con cuatro estados. |
| 2026-10-26 | Reinicio con Vistas: Material Components 1.12, tokens del kit v2 (crema, carbón, Outfit y Montserrat) en recursos XML, `BottomNavigationView` con Navigation Component, `MaterialFadeThrough` de 300 ms, estados con `include`, hoja de canción como `BottomSheetDialogFragment`. |
| 2026-10-26 | TR-01 en XML: la barra de Material no aplica sus propios insets (lo hace su contenedor; en API 21 sumaba 48 dp); el punto bajo la pestaña activa se posiciona a 4 dp de la etiqueta visible, calculado en `MainActivity`, porque `BottomNavigationView` no lo ofrece; íconos inactivos con contorno por selector de estado. |
| 2026-10-27 | Revisión del PR de TR-01: la tabla de tokens usa los nombres de `colors.xml`; la hoja es `PaperSheetLayout` con la textura en un `BitmapShader`; se aclara cuándo un vector puede ir en un selector en API 21 a 23; se quitan `montserrat_extrabold.ttf` y `outfit_medium.ttf` (sin uso, unos 530 KB); el título de cabecera se marca como encabezado con `ViewCompat`; el punto de la pestaña arranca invisible y se recalcula cuando cambia la medida de la barra. Pendiente: recorte a las esquinas de la hoja (HU01) y volver a la raíz al tocar de nuevo la pestaña activa (HU02 y HU04). |
