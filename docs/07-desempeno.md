# 07. Desempeño

Reglas para prevenir errores de desempeño relacionados con CPU, memoria y red, y método para evaluarlo con análisis estático y dinámico. El informe de análisis de desempeño es entregable del Sprint 2 y del Sprint 3.

## 1. Reglas de construcción

### Hilo principal

- El hilo principal solo compone y dibuja. Nada de E/S, parsing JSON, acceso a Room ni mapeo de listas grandes en él.
- `StrictMode` activo en `debug` (`detectAll().penaltyLog()` para hilo y VM): cualquier violación en logcat es un defecto.
- Tiempo de arranque: nada pesado en `Application.onCreate` salvo Timber y la configuración de Coil; Hilt crea lo demás bajo demanda.

### Memoria

- Sin referencias a `Context`, `Activity` o vistas en ViewModels, singletons ni corrutinas de larga vida.
- Colección de `Flow` siempre ligada al ciclo de vida (`collectAsStateWithLifecycle`, `viewModelScope`).
- Imágenes con Coil: tamaño solicitado acorde al contenedor (`size` en `ImageRequest` o tamaño fijo del composable), caché de memoria y disco con los valores por defecto, sin `Bitmap` manuales.
- Listas con `LazyColumn` y claves estables; sin `Column` para listas.
- LeakCanary en `debug`: toda fuga detectada se registra como defecto y se corrige en el mismo sprint.

### CPU y composición

- Estado estable para Compose: `data class` con `val` e inmutables; listas expuestas como `List` dentro de `data class` marcadas `@Immutable` cuando el compilador no puede inferirlo.
- Lecturas de estado diferidas en lambdas de layout y dibujo (`Modifier.offset { }`, `graphicsLayer { }`) cuando el valor cambia por frame.
- `derivedStateOf` para valores calculados a partir de estado que cambia con frecuencia.
- Sin asignaciones ni cálculos pesados dentro de composables: se calculan en el ViewModel y llegan en el `UiState`.
- Trabajo en segundo plano con `Dispatchers.Default` para cálculo y `Dispatchers.IO` para E/S, inyectados.

### Red

- Una única instancia de `OkHttpClient` con pool de conexiones y caché HTTP de 10 MB.
- Timeouts explícitos (conexión 10 s, lectura 30 s). Reintentos solo en lecturas idempotentes y con límite.
- Peticiones concurrentes cuando son independientes (`async` en `coroutineScope`), nunca en serie sin motivo.
- Sin peticiones repetidas por recomposición o por rotación: la carga la decide el ViewModel una vez.
- Logging HTTP solo en `debug`.

### Build

- `release` con R8 (`isMinifyEnabled`, `isShrinkResources`) y reglas de keep mínimas para kotlinx.serialization y Retrofit.
- Recursos no usados eliminados; vectores en lugar de PNG; sin librerías duplicadas.

## 2. Análisis estático

| Herramienta | Qué detecta | Cuándo |
|---|---|---|
| Android Lint (categoría Performance) | Layouts y recursos ineficientes, asignaciones en bucles, `Handler` con fugas, APIs obsoletas | Cada PR (`./gradlew lint`) |
| detekt (`performance`, `coroutines`) | `GlobalScope`, dispatchers sin inyectar, `ArrayPrimitive`, `SpreadOperator`, `ForEachOnRange` | Cada edición (hook) y cada PR |
| Reporte de estabilidad del compilador de Compose | Clases inestables, composables no skippables | Sprint 2, manual: `./gradlew assembleRelease -PcomposeCompilerReports=true` |
| Inspección de dependencias | Tamaño del APK por librería (APK Analyzer) | Sprint 2 y 3 |

Los hallazgos se corrigen o se justifican en el informe con `@Suppress` motivado.

## 3. Análisis dinámico

| Herramienta | Métrica | Escenario |
|---|---|---|
| Android Studio Profiler (CPU) | Tiempo en hilo principal, frames lentos, métodos calientes | Scroll del catálogo, apertura de detalle |
| Android Studio Profiler (Memory) | Heap tras navegar 10 veces lista → detalle → atrás; objetos retenidos | Flujo principal |
| Android Studio Profiler (Network) | Número y tamaño de peticiones por pantalla, duplicados | Primera carga y rotación |
| LeakCanary | Fugas de Activity, ViewModel, composables | Toda sesión de prueba manual |
| StrictMode | Violaciones de E/S en hilo principal | Pruebas E2E en debug |
| Layout Inspector (Compose) | Conteo de recomposiciones por composable | Scroll y cambios de estado |
| Macrobenchmark (opcional, Sprint 3) | Tiempo de arranque en frío, jank en scroll | Release en dispositivo físico |
| `adb shell dumpsys gfxinfo <pkg>` | Porcentaje de frames con jank | Scroll de 30 s en catálogo |

Procedimiento: medir en build `release` (o `debug` sin LeakCanary para CPU) en el mismo dispositivo y la misma semilla de datos, tres repeticiones, se reporta la mediana.

## 4. Presupuestos

| Métrica | Objetivo |
|---|---|
| Arranque en frío hasta lista visible | < 2 s en emulador API 37, < 3 s en API 26 |
| Frames con jank en scroll de catálogo | < 5 % |
| Heap retenido tras 10 ciclos lista → detalle | Sin crecimiento sostenido (< 5 MB de diferencia) |
| Fugas detectadas por LeakCanary | 0 |
| Peticiones por apertura de detalle | 1 |
| Tamaño del APK release | < 15 MB |

## 5. Plantilla del informe (wiki del sprint)

1. Objetivo y alcance del análisis.
2. Entorno: dispositivos, versión de Android, build, commit, datos semilla.
3. Análisis estático: herramientas, hallazgos, correcciones, justificaciones.
4. Análisis dinámico: por escenario, métrica, valores antes y después (tabla y capturas del Profiler).
5. Presupuestos cumplidos e incumplidos, con causa.
6. Cambios aplicados (PRs) y mejoras pendientes como issues.
7. Conclusiones.

## 6. Bitácora de decisiones

| Fecha | Decisión |
|---|---|
| 2026-10-07 | StrictMode y LeakCanary en debug desde el Sprint 1; presupuestos iniciales; informe con mediana de tres repeticiones. |
