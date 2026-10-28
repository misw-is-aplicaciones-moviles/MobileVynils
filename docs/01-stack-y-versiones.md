# 01. Stack y versiones

Decisiones de base técnica de Vinilos. El stack se reinició el 2026-10-26 con Vistas XML y `minSdk 21` (ver la bitácora). Las versiones se actualizan solo por decisión de equipo y en un pull request dedicado.

## 1. Base

| Tema | Decisión | Motivo |
|---|---|---|
| Lenguaje | Kotlin 2.2.10 (el integrado en AGP 9.4.1) | Requisito del curso; sin plugin de Kotlin aparte |
| Build | Android Gradle Plugin 9.4.1, Gradle 9.x, JDK 17 o superior (el JBR 25 de Android Studio) | Versiones estables vigentes |
| IDE | Android Studio 2026.2 (Quail) o superior | Instalado en el equipo |
| SDK | `compileSdk 37`, `targetSdk 36`, `minSdk 21` | Ver sección 2 |
| UI | Vistas XML + una `Activity` + Fragments + ViewBinding, Material Components 1.12 | Implementación de la Vista que explica el curso; funciona en API 21 sin trucos |
| Arquitectura | MVVM con Repository y Service Adapter, un solo módulo | Requisito del curso (rúbrica: patrones MVVM, Repository y Service Adapter) |
| Estado | `ViewModel` + `LiveData` (lifecycle 2.9.4) | Mecanismo de observación que enseña el curso para MVVM |
| Inyección | Manual: `AppContainer` en `VinilosApplication` | Menor complejidad para un proyecto académico pequeño |
| Red | Retrofit 3.0.0 + OkHttp 5.1.0 + converter-gson 3.0.0 | Service Adapter del curso sobre REST; Gson con JSON estricto |
| Navegación | Navigation Component 2.9.6 (`navigation-fragment-ktx`, `navigation-ui-ktx`) | Grafo XML, `BottomNavigationView` con `setupWithNavController` |
| Listas | RecyclerView 1.4.0 con `ListAdapter` y `DiffUtil` | Listas eficientes con Vistas |
| Imágenes | Glide 4.16.0 | Carátulas de álbumes e imágenes de artistas con Vistas |
| Asincronía | kotlinx.coroutines 1.11.0 (`viewModelScope`, funciones `suspend`) | Llamadas de red fuera del hilo principal |
| Fechas | `java.time` con desugaring (`desugar_jdk_libs` 2.1.5) | `LocalDate` en API 21 a 25 |
| Persistencia | Room 2.7.2 con KSP 2.3.12: `AlbumEntity` y `TrackEntity` en Sprint 1 (TR-03) | Room 2.8 exige API 23; `kapt` no funciona con el Kotlin integrado de AGP 9 |
| Pruebas unitarias | JUnit 4, `core-testing` 2.2.0 (`InstantTaskExecutorRule`), kotlinx-coroutines-test, MockWebServer | Ver `06-pruebas.md` |
| Pruebas instrumentadas y E2E | Espresso 3.7.0 (core y contrib), AndroidX Test 1.7.0, ext-junit 1.3.0 | La rúbrica exige Espresso |
| Calidad estática | ktlint 1.8.0 (vía Spotless 8.10), detekt 1.23.8, Android Lint | Ver `09-calidad-estatica.md` |

## 2. Niveles de SDK

| Valor | Nivel | Justificación |
|---|---|---|
| `minSdk` | 21 (Android 5.0 Lollipop) | La rúbrica evalúa desde Lollipop: "La aplicación no genera errores al ser ejecutada en dispositivos Android, iniciando en la versión L" |
| `targetSdk` | 36 (Android 16) | Cumple el mínimo de Google Play (35+) sin adoptar aún los cambios de comportamiento de 37 |
| `compileSdk` | 37 | Plataforma instalada en el equipo |

Consecuencias de `minSdk 21`:

- AndroidX queda fijado en las últimas versiones que admiten 21 (sección 3). Activity 1.12, Lifecycle 2.10, Room 2.8 y Navigation 2.10 ya exigen 23 o 24.
- Íconos vectoriales con `vectorDrawables.useSupportLibrary = true` y `app:srcCompat`.
- El ícono adaptativo vive en `mipmap-anydpi-v26`; `mipmap/ic_launcher.xml` es el respaldo para API 21 a 25.
- Lint trata `NewApi` como error: ninguna API posterior a 21 sin comprobar `Build.VERSION.SDK_INT`.

Matriz mínima de pruebas (requisito del curso: probar en distintas versiones antes de pasar a releases): emulador API 21 (`Vinilos_API_21`, Android 5.0), emulador API 30, emulador API 37 (Medium Phone) y al menos un dispositivo físico del equipo. Ver `06-pruebas.md`.

## 3. Catálogo de versiones

`gradle/libs.versions.toml` es la fuente de verdad. Cada versión es la última que se verificó compatible con `minSdk 21`:

| Clave | Versión | Corte conocido |
|---|---|---|
| `agp` | 9.4.1 | |
| `coreKtx` | 1.17.0 | |
| `appcompat` | 1.7.1 | |
| `material` | 1.12.0 | |
| `constraintlayout` | 2.2.1 | |
| `activity` | 1.11.0 | 1.12 exige 23 |
| `fragment` | 1.8.9 | |
| `lifecycle` | 2.9.4 | 2.10 exige 23 |
| `navigation` | 2.9.6 | 2.10 exige 24 |
| `recyclerview` | 1.4.0 | |
| `room` | 2.7.2 | 2.8 exige 23. Sin `room-ktx`: desde 2.7 vive en `room-runtime` |
| `ksp` | 2.3.12 | 2.3.10+ resuelve la clase R con el Kotlin integrado de AGP 9 |
| `retrofit` / `okhttp` | 3.0.0 / 5.1.0 | |
| `coroutines` | 1.11.0 | |
| `glide` | 4.16.0 | |
| `desugarJdkLibs` | 2.1.5 | |
| `junit` / `archCoreTesting` | 4.13.2 / 2.2.0 | |
| `androidxTestExt` / `androidxTest` / `espresso` | 1.3.0 / 1.7.0 / 3.7.0 | |
| `spotless` / `ktlint` / `detekt` | 8.10.3 / 1.8.0 / 1.23.8 | |

### Cómo verificar una versión nueva

1. Agregar la dependencia en `app/build.gradle.kts` (o subir la versión en el catálogo).
2. Ejecutar `./gradlew :app:processDebugMainManifest :app:processDebugAndroidTestManifest`.
3. Si alguna librería declara un `minSdk` mayor, la fusión falla con `uses-sdk:minSdkVersion 21 cannot be smaller than version N declared in library [...]`. Se baja la versión hasta que pase. Nunca se usa `tools:overrideLibrary`.
4. Instalar en el emulador API 21 y abrir la pantalla afectada.

## 4. Reglas sobre dependencias

1. Toda dependencia entra por el catálogo, con versión estable fija. Nada de `+`, `latest` ni versiones alfa, beta o rc.
2. Toda versión nueva pasa la verificación de la sección 3. Lint (`GradleDependency`) avisará de versiones más nuevas: se ignoran mientras exijan más de API 21.
3. Una librería nueva se propone en un issue con el problema que resuelve y la alternativa sin librería, y la aprueba el equipo.
4. Actualizar versiones es un pull request propio, sin cambios funcionales, con el build y las pruebas en verde.
5. No se duplican capacidades: un solo cargador de imágenes (Glide), un solo convertidor JSON, un solo mecanismo de observación (`LiveData`).

## 5. Build

- `gradle.properties`: `org.gradle.jvmargs=-Xmx4g ... -Djava.net.preferIPv4Stack=true`, `org.gradle.caching=true`, `org.gradle.configuration-cache=true`, `android.useAndroidX=true`, `android.nonTransitiveRClass=true`.
- `buildFeatures { viewBinding = true }`; `compileOptions` con Java 17 e `isCoreLibraryDesugaringEnabled = true`.
- `lint { error += setOf("NewApi", "HardcodedText"); abortOnError = true }`.
- `buildTypes`: `debug` y `release`. El APK entregable de cada sprint es el de `release`, firmado con la keystore de depuración del proyecto (`keystore/debug.keystore`; credenciales en el entorno o en `local.properties`, ver `keystore/README.md`); ver `08-flujo-de-trabajo-git.md`.
- URL del backend: `buildFeatures { buildConfig = true }` (AGP no genera `BuildConfig` por defecto) y `buildConfigField` `BASE_URL` con `http://10.0.2.2:3000/` para el emulador en ambas variantes. El dispositivo físico usa `vinilos.baseUrl` en `local.properties` (no versionado); requiere recompilar. Gradle valida HTTP(S), host y `/` final, sin credenciales, query ni fragmento.

## 6. Backend de desarrollo

`BackVynils` (NestJS) corre local con Docker Compose en el puerto 3000 y responde por `http://`. La app apunta a `10.0.2.2:3000` desde el emulador y a la IP de la red local desde un dispositivo físico.

Texto plano: como el entregable es el APK de `release` y el backend del curso es `http://`, el permiso de texto plano está en el manifiesto de `main` (`android:usesCleartextTraffic="true"` en `<application>`), junto con el permiso `INTERNET`. Con `targetSdk 36` el texto plano está bloqueado por defecto desde API 28; en API 21 a 27 el atributo no cambia nada porque ya está permitido. Si se agrega un `network_security_config.xml`, debe permitir también la IP de la red local, no solo `10.0.2.2`.

`NetworkClient` configura caché HTTP de 10 MB, conexión de 10 s y lectura de 30 s. Desactiva `retryOnConnectionFailure` para evitar repetir escrituras al perder una respuesta. La caché HTTP respeta las cabeceras del servidor; la caché en memoria de cada Repository sigue siendo independiente. `networkRequest` convierte errores HTTP, de JSON y de E/S en `DataError`; deja propagar la cancelación y los errores de programación. TR-03 compartirá el cliente desde `AppContainer`; los contratos de API y DTO de producción corresponden a cada historia.

## 7. Bitácora de decisiones

| Fecha | Decisión |
|---|---|
| 2026-10-07 | Stack inicial propuesto: Compose + M3, Navigation Compose, Hilt, Retrofit + kotlinx.serialization, Room, Coil; `minSdk 26`. |
| 2026-10-21 | `minSdk 23` con Compose para acercarse a Lollipop (PR #82, cerrado). |
| 2026-10-26 | Reinicio del stack con la propuesta de Daniela: Vistas XML + Fragments + ViewBinding, `LiveData`, Retrofit e inyección manual. `minSdk 21` por la rúbrica (Daniela proponía 26). Con Compose, llegar a API 21 exigía fijar AndroidX en versiones de 2025 y las pruebas de píxeles congelaban el emulador de Lollipop. PR #80, #81 y #82 cerrados sin integrar. Kotlin pasa a la versión integrada en AGP (2.2.10). Glide en lugar de Coil; Room fuera del camino crítico del Sprint 1. |
| 2026-10-26 | Firma del release restaurada sobre el proyecto reiniciado: `keystore/debug.keystore` (alias `vinilos`) y `signingConfigs.release` con credenciales del entorno o de `local.properties`; sin ellas `assembleRelease` falla con un mensaje claro. |
| 2026-10-27 | TR-03: Room 2.7.2 con KSP 2.3.12 (no `kapt`, incompatible con el Kotlin integrado). Esquema exportado con el argumento `room.schemaLocation` en `app/schemas/`, sin el plugin de Gradle de Room. `buildConfig = true` para `BuildConfig.DEBUG`. |
