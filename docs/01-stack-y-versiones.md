# 01. Stack y versiones

Decisiones de base técnica de Vinilos. Las versiones se fijaron el 2026-10-07 a partir de los índices oficiales (Google Maven, GitHub Releases). Se actualizan solo por decisión de equipo y en un pull request dedicado.

## 1. Base

| Tema | Decisión | Motivo |
|---|---|---|
| Lenguaje | Kotlin 2.4.x (el integrado en AGP 9 o KGP 2.4.20) | Requisito del curso; K2 estable |
| Build | Android Gradle Plugin 9.4.1, Gradle 9.x, JDK 17 o superior (el JBR 25 de Android Studio) | Versiones estables vigentes |
| IDE | Android Studio 2026.2 (Quail) o superior | Instalado en el equipo |
| SDK | `compileSdk 37`, `targetSdk 36`, `minSdk 26` | Ver sección 2 |
| UI | Jetpack Compose + Material 3 (BOM 2026.09.00) | Toolkit recomendado por Google; pruebas de UI por semántica |
| Arquitectura | MVVM con flujo unidireccional, un solo módulo | Requisito del curso; equipo de 4 y 8 semanas |
| Inyección | Hilt 2.60.1 con KSP | Estándar en Android; soporte en pruebas |
| Red | Retrofit 3.0.0 + OkHttp 5.x + kotlinx.serialization | Service Adapter del curso sobre REST |
| Persistencia | Room 2.8.5 con KSP | Requisito del curso (caché local, fuente única de verdad) |
| Navegación | Navigation Compose 2.10.2 con rutas `@Serializable` | Estable, documentado, integra Hilt |
| Imágenes | Coil 3.6.x | Carátulas de álbumes e imágenes de artistas |
| Asincronía | kotlinx.coroutines 1.11.0 y Flow | Único mecanismo; sin RxJava ni LiveData |
| Pruebas unitarias | JUnit 4, MockK 1.14.x, Turbine 1.2.x, kotlinx-coroutines-test, Truth 1.4.4, MockWebServer | Ver `06-pruebas.md` |
| Pruebas instrumentadas | Compose UI test (ui-test-junit4), Espresso 3.7.0, AndroidX Test 1.7.0, Hilt testing | E2E exigidas por el curso |
| Pruebas de arquitectura | Konsist 0.17.3 | Reglas de capas verificadas como pruebas unitarias |
| Calidad estática | ktlint 1.8.0 (vía Spotless 8.10), detekt 1.23.8, Android Lint | Ver `09-calidad-estatica.md` |
| Depuración de memoria | LeakCanary 2.14 (solo `debugImplementation`) | Fugas detectadas en desarrollo |
| Logs | Timber 5.0.1 | Sin logs en release |

## 2. Niveles de SDK

| Valor | Nivel | Justificación |
|---|---|---|
| `minSdk` | 26 (Android 8.0) | Cubre la práctica totalidad de dispositivos activos; evita código de compatibilidad para notificaciones, `java.time` y adaptive icons |
| `targetSdk` | 36 (Android 16) | Cumple el mínimo de Google Play (35+) y habilita edge-to-edge obligatorio y permisos modernos sin adoptar aún los cambios de comportamiento de 37 |
| `compileSdk` | 37 | Plataforma instalada en el equipo; da acceso a las API más recientes de AndroidX |

Matriz mínima de pruebas (requisito del curso: probar en distintas versiones y dispositivos antes de pasar a releases): emulador API 26, emulador API 33, emulador API 37 (Medium Phone) y al menos un dispositivo físico del equipo. Ver `06-pruebas.md`.

## 3. Catálogo de versiones

`gradle/libs.versions.toml`. El asistente de Android Studio genera uno; se reemplaza por este y se verifica cada versión en el primer build. Marcado con `# verificar` lo que debe confirmarse en Maven Central al crear el proyecto.

```toml
[versions]
agp = "9.4.1"
kotlin = "2.4.20"                 # solo si se usa el plugin de Kotlin explícito
ksp = "2.4.20-2.0.4"              # verificar: KSP sigue la versión de Kotlin
coreKtx = "1.19.1"
lifecycle = "2.11.0"
activityCompose = "1.13.0"
composeBom = "2026.09.00"
navigation = "2.10.2"
hilt = "2.60.1"
hiltNavigationCompose = "1.4.0"
room = "2.8.5"
retrofit = "3.0.0"
okhttp = "5.1.0"                  # verificar
kotlinxSerialization = "1.9.0"    # verificar
coroutines = "1.11.0"
coil = "3.6.3"
timber = "5.0.1"
leakcanary = "2.14"
junit = "4.13.2"
mockk = "1.14.11"
turbine = "1.2.1"
truth = "1.4.4"
konsist = "0.17.3"
androidxTestExt = "1.3.0"
androidxTestRunner = "1.7.0"
espresso = "3.7.0"
uiautomator = "2.4.0"
spotless = "8.10.3"
detekt = "1.23.8"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-kotlinx-serialization = { group = "com.squareup.retrofit2", name = "converter-kotlinx-serialization", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }
timber = { group = "com.jakewharton.timber", name = "timber", version.ref = "timber" }
leakcanary = { group = "com.squareup.leakcanary", name = "leakcanary-android", version.ref = "leakcanary" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
mockk-android = { group = "io.mockk", name = "mockk-android", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
truth = { group = "com.google.truth", name = "truth", version.ref = "truth" }
konsist = { group = "com.lemonappdev", name = "konsist", version.ref = "konsist" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestExt" }
androidx-test-runner = { group = "androidx.test", name = "runner", version.ref = "androidxTestRunner" }
androidx-test-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso" }
androidx-test-espresso-accessibility = { group = "androidx.test.espresso", name = "espresso-accessibility", version.ref = "espresso" }
androidx-test-uiautomator = { group = "androidx.test.uiautomator", name = "uiautomator", version.ref = "uiautomator" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
hilt-android-testing = { group = "com.google.dagger", name = "hilt-android-testing", version.ref = "hilt" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }   # omitir si AGP 9 integra Kotlin
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
```

## 4. Reglas sobre dependencias

1. Toda dependencia entra por el catálogo, con versión estable fija. Nada de `+`, `latest` ni versiones alfa, beta o rc.
2. Una librería nueva se propone en un issue con el problema que resuelve y la alternativa sin librería. La aprueba el equipo en la reunión de los lunes.
3. `debugImplementation` para herramientas de desarrollo (LeakCanary, tooling de Compose). Nunca llegan al APK de release.
4. Actualizar versiones es un pull request propio, sin cambios funcionales, con el build y las pruebas en verde.
5. No se usan librerías que dupliquen una capacidad ya presente (por ejemplo, Gson o Moshi junto a kotlinx.serialization; Glide junto a Coil).

## 5. Build

- `gradle.properties`: `org.gradle.jvmargs=-Xmx4g`, `org.gradle.caching=true`, `org.gradle.configuration-cache=true`, `android.useAndroidX=true`, `kotlin.code.style=official`.
- `buildTypes`: `debug` (con `applicationIdSuffix ".debug"`, LeakCanary, logging HTTP) y `release` (`isMinifyEnabled = true`, `isShrinkResources = true`, reglas R8 en `proguard-rules.pro`). El APK entregable de cada sprint es el de `release` firmado con la keystore de depuración del proyecto, salvo decisión contraria; ver `08-flujo-de-trabajo-git.md`.
- `BuildConfig.BASE_URL` por `buildConfigField` para el backend; el valor del emulador es `http://10.0.2.2:3000/` y el del dispositivo físico se define por `local.properties` (no se versiona).
- `lint { abortOnError = true; warningsAsErrors = true }` con baseline inicial en `lint-baseline.xml` si hace falta.

## 6. Backend de desarrollo

`BackVynils` (NestJS) corre local con Docker Compose en el puerto 3000. La app apunta a `10.0.2.2:3000` desde el emulador. Para red limpia en texto plano en desarrollo se declara `android:usesCleartextTraffic` solo en el `AndroidManifest.xml` de `debug` mediante `networkSecurityConfig` con el dominio `10.0.2.2`.

## 7. Bitácora de decisiones

| Fecha | Decisión |
|---|---|
| 2026-10-07 | Stack inicial propuesto: Compose + M3, Navigation Compose 2.x, Hilt, Retrofit + kotlinx.serialization, Room, Coil. Pendiente de ratificación por el equipo en el Inception. |
| 2026-10-07 | `minSdk 26`, `targetSdk 36`, `compileSdk 37`. |
