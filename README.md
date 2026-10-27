# Vinilos (Android)

[![CI](https://github.com/misw-is-aplicaciones-moviles/MobileVynils/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/misw-is-aplicaciones-moviles/MobileVynils/actions/workflows/ci.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-minSdk%2021%20%7C%20targetSdk%2036-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Views](https://img.shields.io/badge/UI-XML%20%2B%20Fragments%20%2B%20ViewBinding-3DDC84)](https://developer.android.com/topic/libraries/view-binding)
[![Material Components](https://img.shields.io/badge/Material%20Components-1.12-6750A4?logo=materialdesign&logoColor=white)](https://github.com/material-components/material-components-android)
[![LiveData](https://img.shields.io/badge/ViewModel%20%2B%20LiveData-2.9-4285F4)](https://developer.android.com/topic/libraries/architecture/livedata)
[![Navigation](https://img.shields.io/badge/Navigation-2.9-4285F4)](https://developer.android.com/guide/navigation)
[![Retrofit](https://img.shields.io/badge/Retrofit-3.0-48B983)](https://square.github.io/retrofit/)
[![Coroutines](https://img.shields.io/badge/Coroutines-1.11-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/coroutines-overview.html)
[![Glide](https://img.shields.io/badge/Glide-4.16-4CAF50)](https://bumptech.github.io/glide/)
[![Gradle](https://img.shields.io/badge/Gradle-9%20%7C%20AGP%209.4-02303A?logo=gradle&logoColor=white)](https://gradle.org/)
[![ktlint](https://img.shields.io/badge/code%20style-ktlint-FF4081)](https://pinterest.github.io/ktlint/)
[![detekt](https://img.shields.io/badge/static%20analysis-detekt-1976D2)](https://detekt.dev/)
[![Espresso](https://img.shields.io/badge/tests-JUnit%20%7C%20Espresso-6DB33F)](docs/06-pruebas.md)

Versión móvil de Vinilos: catálogo de álbumes, artistas y coleccionistas de vinilos, construida en Kotlin con Vistas XML, Fragments y el patrón MVVM (LiveData, Repository y Service Adapter con Retrofit) para el curso Ingeniería de software para aplicaciones móviles (MISO, Uniandes).

## Equipo

Nombre del equipo: por definir.

| Integrante | Correo | GitHub |
|---|---|---|
| Yoimar Moreno Bertel | y.morenob2@uniandes.edu.co | @yoimar-uniandes |
| Manfred Ariel Martinez Bastos | ma.martinezb123@uniandes.edu.co | @ManfredAriel91122 |
| Daniela Mesa | d.mesam2@uniandes.edu.co | @daniela-mesa |
| Jhon Jairo Rincon Castro | j.rinconc23@uniandes.edu.co | @jrinconc23 |

## Requisitos

- Android Studio 2026.2 (Quail) o superior, con SDK 37 y build-tools 36.
- Emuladores de la matriz de pruebas: API 21 (Android 5.0, el mínimo que evalúa el curso), API 30 y API 37 (Medium Phone). En API 21 conviene arrancar el emulador con `-gpu host`.
- JDK 17 o superior (Android Studio incluye uno).
- Docker Desktop para el backend local.

## Backend local

La app consume la API REST de [BackVynils](https://github.com/TheSoftwareDesignLab/BackVynils). Para levantarla:

```bash
git clone https://github.com/TheSoftwareDesignLab/BackVynils.git
cd BackVynils
docker compose up --build
```

Queda disponible en `http://localhost:3000`. La capa de red (TR-02) usará `http://10.0.2.2:3000/` desde el emulador como valor por defecto de `BASE_URL` en `debug`. Para un dispositivo físico, se definirá en `local.properties`:

```properties
vinilos.baseUrl=http://<ip-de-tu-pc>:3000/
```

## Compilar y ejecutar

1. Clona el repositorio y ábrelo en Android Studio (`File > Open`). Gradle sincroniza y descarga las dependencias del catálogo `gradle/libs.versions.toml`.
2. Selecciona la configuración `app` y un emulador; ejecuta con `Run`.
3. Desde terminal:

```bash
./gradlew assembleDebug                 # APK en app/build/outputs/apk/debug/
./gradlew installDebug                  # instala en el emulador o dispositivo conectado
```

## Verificación

```bash
./gradlew spotlessCheck detekt lint testDebugUnitTest   # calidad estática y pruebas unitarias
./gradlew connectedDebugAndroidTest                   # pruebas instrumentadas y E2E (emulador encendido)
```

## APK de cada sprint

Cada entrega se publica como GitHub Release con tag `sprint-N` y el APK de release adjunto, firmado con la keystore de depuración del proyecto.

## Documentación

- [docs/](docs/README.md): convenciones de arquitectura, código, diseño, pruebas, desempeño, git y calidad.
- Wiki del repositorio: entregables por sprint (backlog, diseño, estrategia y artefactos de pruebas, actas, retrospectivas).

## Solución de problemas

- **`AAPT2 ... Daemon startup failed` / "Una directiva de Control de aplicaciones bloqueó este archivo".** El control de aplicaciones de Windows bloquea el `aapt2.exe` que Gradle extrae a su caché. Usa el de los build-tools del SDK añadiendo a `%USERPROFILE%\.gradle\gradle.properties` (no al repositorio):

  ```properties
  android.aapt2FromMavenOverride=C:/Users/<usuario>/AppData/Local/Android/Sdk/build-tools/36.0.0/aapt2.exe
  ```

- **`UnknownHostException: plugins-artifacts.gradle.org` o `Could not GET ...gradle.org`.** La JVM intenta IPv6 y falla en algunas redes. El proyecto ya pasa `-Djava.net.preferIPv4Stack=true` al daemon en `gradle.properties`; si el fallo aparece al descargar la distribución del wrapper, define `GRADLE_OPTS=-Djava.net.preferIPv4Stack=true` en la sesión.

- **`local.properties` con `PropertyEscape` en Lint.** Escribe la ruta del SDK con `\:` y barras normales: `sdk.dir=C\:/Users/<usuario>/AppData/Local/Android/Sdk`.

- **Formato.** `./gradlew spotlessApply` corrige el estilo con ktlint; `spotlessCheck` solo verifica. detekt no autocorrige.
