# 07. Desempeño

Reglas para prevenir errores de desempeño relacionados con CPU, memoria y red, y método para evaluarlo con análisis estático y dinámico. El informe de análisis de desempeño es entregable del Sprint 2 y del Sprint 3.

## 1. Reglas de construcción

### Hilo principal

- El hilo principal solo dibuja. Nada de E/S, parsing JSON ni mapeo de listas grandes en él.
- `StrictMode` se activa en `debug` desde `VinilosApplication` (TR-03) (`detectAll().penaltyLog()` para hilo y VM): cualquier violación en logcat es un defecto.
- Tiempo de arranque: `Application.onCreate` solo crea el `AppContainer`; sus dependencias se construyen con `lazy` al primer uso.

### Memoria

- Sin referencias a `Context`, `Activity` o vistas en ViewModels, singletons ni corrutinas de larga vida.
- `LiveData` observado con `viewLifecycleOwner`; el binding del Fragment se libera en `onDestroyView`; corrutinas en `viewModelScope`.
- Imágenes con Glide: tamaño acorde al contenedor (`override` o vista de tamaño fijo), caché de memoria y disco por defecto, sin `Bitmap` manuales.
- Listas con `RecyclerView`, `ListAdapter` y `DiffUtil`; `areItemsTheSame` compara ids y `areContentsTheSame` compara el contenido.
- LeakCanary en `debug` si el equipo lo agrega al catálogo (verificado con `minSdk 21`): toda fuga detectada se registra como defecto.

### CPU y dibujo

- Jerarquías de vistas planas con `ConstraintLayout`; sin anidar layouts con pesos para lo mismo.
- Sin sobredibujado innecesario: un solo fondo por capa (la hoja crema pinta el fondo; los ítems no repiten el mismo color).
- `ViewHolder` con ViewBinding; nada de `findViewById` repetido en `onBindViewHolder`.
- Sin cálculos pesados en `onBindViewHolder` ni en `render(state)`: llegan calculados en el `UiState`.
- Cálculo en `Dispatchers.Default` recibido por constructor; la red ya corre fuera del hilo principal con Retrofit.

### Red

- Una única instancia de `OkHttpClient` con pool de conexiones y caché HTTP de 10 MB.
- Timeouts explícitos (conexión 10 s, lectura 30 s). Reintentos solo en lecturas idempotentes y con límite.
- Peticiones concurrentes cuando son independientes (`async` en `coroutineScope`), nunca en serie sin motivo.
- Sin peticiones repetidas al rotar o al volver a la pantalla: la carga la decide el ViewModel una vez.
- Logging HTTP solo en `debug`.

### Build

- `release` con R8 (`isMinifyEnabled`, `isShrinkResources`) cuando el equipo lo active, con reglas de keep para Retrofit y el convertidor JSON.
- Recursos no usados eliminados; vectores en lugar de PNG; sin librerías duplicadas.

## 2. Análisis estático

| Herramienta | Qué detecta | Cuándo |
|---|---|---|
| Android Lint (categoría Performance) | Layouts y recursos ineficientes, asignaciones en bucles, `Handler` con fugas, APIs obsoletas | Cada PR (`./gradlew lint`) |
| detekt (`performance`, `coroutines`) | `GlobalScope`, dispatchers sin inyectar, `ArrayPrimitive`, `SpreadOperator`, `ForEachOnRange` | Cada edición (hook) y cada PR |
| Inspección de dependencias | Tamaño del APK por librería (APK Analyzer) | Sprint 2 y 3 |

Los hallazgos se corrigen o se justifican en el informe con `@Suppress` motivado.

## 3. Análisis dinámico

| Herramienta | Métrica | Escenario |
|---|---|---|
| Android Studio Profiler (CPU) | Tiempo en hilo principal, frames lentos, métodos calientes | Scroll del catálogo, apertura de detalle |
| Android Studio Profiler (Memory) | Heap tras navegar 10 veces lista → detalle → atrás; objetos retenidos | Flujo principal |
| Android Studio Profiler (Network) | Número y tamaño de peticiones por pantalla, duplicados | Primera carga y rotación |
| LeakCanary (si se agrega) | Fugas de Activity, Fragment, ViewModel y vistas | Toda sesión de prueba manual |
| StrictMode | Violaciones de E/S en hilo principal | Pruebas E2E en debug |
| Layout Inspector y "Debug GPU overdraw" | Profundidad de la jerarquía y sobredibujado | Catálogo y detalle |
| Macrobenchmark (opcional, Sprint 3; módulo aparte con `minSdk 23`) | Tiempo de arranque en frío, jank en scroll | Release en dispositivo físico |
| `adb shell dumpsys gfxinfo <pkg>` | Porcentaje de frames con jank | Scroll de 30 s en catálogo |

Procedimiento: medir en build `release` (o `debug` sin LeakCanary para CPU) en el mismo dispositivo y la misma semilla de datos, tres repeticiones, se reporta la mediana.

## 4. Presupuestos

| Métrica | Objetivo |
|---|---|
| Arranque en frío hasta lista visible | < 2 s en emulador API 37, < 3 s en API 21 |
| Frames con jank en scroll de catálogo | < 5 % |
| Heap retenido tras 10 ciclos lista → detalle | Sin crecimiento sostenido (< 5 MB de diferencia) |
| Fugas detectadas por LeakCanary (si se agrega) | 0 |
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
| 2026-10-26 | Reglas de dibujo y memoria para Vistas y RecyclerView; Glide en lugar de Coil; presupuesto de arranque medido en API 21. |
