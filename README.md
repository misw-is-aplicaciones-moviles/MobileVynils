# Vinilos (Android)

[![CI](https://github.com/misw-is-aplicaciones-moviles/MobileVynils/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/misw-is-aplicaciones-moviles/MobileVynils/actions/workflows/ci.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-minSdk%2021%20%7C%20targetSdk%2036-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Views](https://img.shields.io/badge/UI-XML%20%2B%20Fragments%20%2B%20ViewBinding-3DDC84)](https://developer.android.com/topic/libraries/view-binding)
[![Material Components](https://img.shields.io/badge/Material%20Components-1.12-6750A4?logo=materialdesign&logoColor=white)](https://github.com/material-components/material-components-android)
[![LiveData](https://img.shields.io/badge/ViewModel%20%2B%20LiveData-2.9-4285F4)](https://developer.android.com/topic/libraries/architecture/livedata)
[![Navigation](https://img.shields.io/badge/Navigation-2.9-4285F4)](https://developer.android.com/guide/navigation)
[![Retrofit](https://img.shields.io/badge/Retrofit-3.0-48B983)](https://square.github.io/retrofit/)
[![Room](https://img.shields.io/badge/Room-2.7-4285F4)](https://developer.android.com/training/data-storage/room)
[![Coroutines](https://img.shields.io/badge/Coroutines-1.11-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/coroutines-overview.html)
[![Glide](https://img.shields.io/badge/Glide-4.16-4CAF50)](https://bumptech.github.io/glide/)
[![Gradle](https://img.shields.io/badge/Gradle-9.8%20%7C%20AGP%209.4-02303A?logo=gradle&logoColor=white)](https://gradle.org/)
[![ktlint](https://img.shields.io/badge/code%20style-ktlint-FF4081)](https://pinterest.github.io/ktlint/)
[![detekt](https://img.shields.io/badge/static%20analysis-detekt-1976D2)](https://detekt.dev/)
[![Espresso](https://img.shields.io/badge/tests-JUnit%20%7C%20Espresso-6DB33F)](docs/06-pruebas.md)

Versión móvil de Vinilos: catálogo de álbumes, artistas y coleccionistas de vinilos, construida en Kotlin con Vistas XML, Fragments y el patrón MVVM (LiveData, Repository y Service Adapter con Retrofit) para el curso Ingeniería de software para aplicaciones móviles (MISO, Uniandes).

Sprint 1 incluye HU01 (catálogo de álbumes), HU02 (detalle de álbum), HU07 (crear álbum) y HU08 (asociar canciones a un álbum). La app funciona desde Android 5.0 Lollipop (API 21).

> **Importante:** la app solo funciona en un emulador de Android que corra en el mismo computador donde está encendido el backend local (`localhost:3000`). No se admiten dispositivos físicos ni emuladores en otros equipos, ni backends remotos.

## Contenido

- [Equipo](#equipo)
- [Requisitos](#requisitos)
- [1. Levantar el backend local](#1-levantar-el-backend-local) (necesario en todos los casos)
- [2. Opción A: instalar el APK publicado](#2-opción-a-instalar-el-apk-publicado)
- [3. Opción B: construir la app desde el código](#3-opción-b-construir-la-app-desde-el-código)
- [4. Ejecutar las pruebas](#4-ejecutar-las-pruebas)
- [5. Publicar el APK de una entrega (equipo)](#5-publicar-el-apk-de-una-entrega-equipo)
- [Documentación](#documentación)
- [Solución de problemas](#solución-de-problemas)

## Equipo

Nombre del equipo: por definir.

| Integrante | Correo | GitHub |
|---|---|---|
| Yoimar Moreno Bertel | y.morenob2@uniandes.edu.co | @yoimar-uniandes |
| Manfred Ariel Martinez Bastos | ma.martinezb123@uniandes.edu.co | @ManfredAriel91122 |
| Daniela Mesa Muñoz | d.mesam2@uniandes.edu.co | @daniela-mesa |
| Jhon Jairo Rincon Castro | j.rinconc23@uniandes.edu.co | @jrinconc23 |

## Requisitos

La app se desarrolló y se probó en un emulador **API 37** (Android 17, perfil Medium Phone) con Android Studio 2026.2. Su versión mínima es API 21 (Android 5.0 Lollipop).

| Herramienta | Versión | Para qué |
|---|---|---|
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) | Reciente, encendido | Backend local (opciones A y B) |
| [Android Studio](https://developer.android.com/studio) | 2026.2 (Quail) o superior | Emulador (A y B) y compilación (B) |
| Emulador de Android (AVD) | API 37 recomendado; API 21 o superior | Ejecutar la app (A y B) |
| Android SDK | Platform 37 y Build-Tools 36 | Compilar (B). Se instalan desde `Tools > SDK Manager` |
| JDK | 17 o superior (Android Studio incluye uno) | Compilar desde terminal (B) |
| Git | Cualquiera reciente | Clonar los repositorios |

Para crear el emulador recomendado: `Tools > Device Manager > Create Virtual Device`, perfil **Medium Phone** e imagen de sistema **API 37**.

## 1. Levantar el backend local

La app consume la API REST de [BackVynils](https://github.com/TheSoftwareDesignLab/BackVynils), que debe correr en tu computador con Docker Desktop encendido.

```bash
git clone https://github.com/TheSoftwareDesignLab/BackVynils.git
cd BackVynils
docker compose up --build
```

La API queda en `http://localhost:3000` con los datos de ejemplo de BackVynils. Compruébalo abriendo `http://localhost:3000/albums` en el navegador: debe devolver una lista de álbumes (A Day at the Races, A Night at the Opera, Buscando América, Poeta del pueblo).

### Cómo llega la app al backend

La URL del backend queda fija dentro de la app al compilarla: `http://10.0.2.2:3000/`. Para el emulador de Android, `10.0.2.2` es el `localhost` del computador que lo ejecuta. Por eso:

- **Sí funciona:** un emulador de Android Studio en el mismo computador donde corre BackVynils en `localhost:3000`.
- **No funciona:** un dispositivo físico, un emulador en otro computador o un backend desplegado en otro servidor. En esos casos la app no encuentra el backend y muestra "No se pudo cargar el catálogo".

Esto aplica al APK publicado y al que compiles con la opción B. La app usa HTTP sin cifrar a propósito, para consumir el backend local del curso.

## 2. Opción A: instalar el APK publicado

Cada entrega trae un APK listo para instalar, en dos lugares:

- La carpeta [`apk/`](apk/) del repositorio, en la rama `main`: `apk/vinilos-1.0.0.apk` (versión actual de la app).
- La página [Releases](https://github.com/misw-is-aplicaciones-moviles/MobileVynils/releases) de GitHub, en el release [`1.0.0`](https://github.com/misw-is-aplicaciones-moviles/MobileVynils/releases/tag/1.0.0).

El APK solo se puede usar en un emulador del mismo computador donde corre el backend en `localhost:3000` (ver [sección 1](#cómo-llega-la-app-al-backend)). No lo instales en un dispositivo físico: la app abre, pero no carga datos.

Pasos:

1. Levanta el backend ([sección 1](#1-levantar-el-backend-local)) y comprueba que `http://localhost:3000/albums` responda.
2. Inicia un emulador en ese mismo computador (API 37 recomendado; API 21 o superior).
3. Instala el APK de una de estas dos formas:
   - Arrastra el archivo `.apk` sobre la ventana del emulador.
   - Desde terminal: `adb install -r apk/vinilos-1.0.0.apk`.
4. Abre **Vinilos**, elige el rol **Visitante** o **Coleccionista** y navega el catálogo.

Si ya había una versión instalada con otra firma (por ejemplo, una compilación `debug` desde Android Studio), desinstálala antes: `adb uninstall co.edu.uniandes.vinilos`.

## 3. Opción B: construir la app desde el código

Necesitas las herramientas de la sección [Requisitos](#requisitos) y el backend local encendido ([sección 1](#1-levantar-el-backend-local)). Desde terminal, `JAVA_HOME` debe apuntar a un JDK 17 o superior.

### Desde Android Studio

1. Clona el repositorio:

   ```bash
   git clone https://github.com/misw-is-aplicaciones-moviles/MobileVynils.git
   ```

2. Abre la carpeta en Android Studio (`File > Open`). Gradle sincroniza y descarga las dependencias declaradas en `gradle/libs.versions.toml`; la primera vez tarda varios minutos. Android Studio crea `local.properties` con la ruta del SDK.
3. Selecciona la configuración `app` y el emulador, y pulsa `Run`.

### Desde terminal

Si no abriste el proyecto en Android Studio, crea `local.properties` con la ruta del SDK (o define `ANDROID_HOME`):

```properties
sdk.dir=C\:/Users/<usuario>/AppData/Local/Android/Sdk
```

En macOS o Linux, por ejemplo, `sdk.dir=/Users/<usuario>/Library/Android/sdk`. Luego, desde la raíz del repositorio:

```bash
./gradlew assembleDebug   # genera app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug    # compila e instala en el emulador encendido
```

En Windows con PowerShell o CMD, usa `gradlew.bat` (o `.\gradlew`) en lugar de `./gradlew`.

El APK `debug` no necesita contraseñas y es suficiente para probar la app. El APK `release` (`assembleRelease`) se firma con la keystore del equipo y requiere sus contraseñas ([sección 5](#5-publicar-el-apk-de-una-entrega-equipo)).

## 4. Ejecutar las pruebas

La estrategia, el inventario de casos y los resultados están en [docs/06-pruebas.md](docs/06-pruebas.md) y en la wiki.

### Calidad estática y pruebas unitarias

No necesitan emulador ni backend. Son las mismas verificaciones que corre la CI en cada pull request.

```bash
./gradlew spotlessCheck detekt lintDebug testDebugUnitTest
```

Los reportes quedan en `app/build/reports/`.

### Pruebas instrumentadas y E2E (Espresso)

Necesitan un emulador encendido (API 37 recomendado) y BackVynils en `localhost:3000` del mismo computador. Las pruebas que crean álbumes y canciones requieren que las secuencias de IDs de la base estén alineadas con la semilla. `scripts/run_e2e.sh` las prepara (sin borrar datos) y luego ejecuta `connectedDebugAndroidTest`:

```bash
./scripts/run_e2e.sh
```

El script requiere bash (en Windows, Git Bash) y acceso a `docker`. Si el backend no usa el puerto 3000 o no lo encuentra, indica el contenedor PostgreSQL con `E2E_DB_CONTAINER=<id> ./scripts/run_e2e.sh`. Repite el script cada vez que vuelvas a cargar la semilla de BackVynils.

Para ejecutar una sola clase, pasa los argumentos de Gradle al script:

```bash
./scripts/run_e2e.sh -Pandroid.testInstrumentationRunnerArguments.class=co.edu.uniandes.vinilos.ui.AlbumListScreenTest
```

### Prueba de conexión con el backend

Comprueba que la app compilada llega a BackVynils: consulta `/albums` con la URL compilada y valida que la respuesta trae álbumes con id y nombre. Se omite si no se pasa `backendSmoke=true`.

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=co.edu.uniandes.vinilos.network.BackendConnectionTest \
  -Pandroid.testInstrumentationRunnerArguments.backendSmoke=true
```

## 5. Publicar el APK de una entrega (equipo)

Esta sección es para el equipo; no hace falta para construir ni usar la app.

El APK de release se firma con la keystore de depuración del proyecto (`keystore/debug.keystore`, alias `vinilos`; ver [keystore/README.md](keystore/README.md)). Para compilarlo en local (`./gradlew assembleRelease`), añade a `local.properties` las contraseñas que comparte el equipo; en CI vienen de los secrets del repositorio. PKCS12 exige la misma contraseña para el almacén y la clave.

| `local.properties` | Secret de GitHub Actions | Valor |
|---|---|---|
| `keystore.password` | `KEYSTORE_PASSWORD` | Contraseña del almacén |
| `keystore.alias` | `KEY_ALIAS` | `vinilos` |
| `keystore.keyPassword` | `KEY_PASSWORD` | Igual a la del almacén |

Sin esos valores, `assembleRelease` falla indicando cuál falta.

Cada release se etiqueta con la versión de la app (`versionName`, formato `X.Y.Z`, sin prefijo); la versión actual es `1.0.0`. Flujo de cada entrega ([docs/08-flujo-de-trabajo-git.md](docs/08-flujo-de-trabajo-git.md), sección 5):

1. Crear `release/sprint-N` desde `develop`.
2. En esa rama, poner la versión en `versionName` de `app/build.gradle.kts`, revisar este README y versionar el APK de release como `apk/vinilos-X.Y.Z.apk` (se copia a mano desde `app/build/outputs/apk/release/app-release.apk`; la CI no lo sube al repositorio).
3. PR de `release/sprint-N` a `main` y merge.
4. Sobre `main`, crear y subir el tag con la versión. Por ejemplo: `git tag -a 1.0.0 -m "Versión 1.0.0: HU01, HU02, HU07, HU08"` y `git push origin 1.0.0`.
5. El job "APK de release" del workflow corre al empujar un tag `X.Y.Z`, después de que "Calidad y pruebas unitarias" pase, y adjunta `vinilos-X.Y.Z.apk` al GitHub Release.

## Documentación

- [docs/](docs/README.md): convenciones de arquitectura, código, diseño, pruebas, desempeño, git y calidad.
- [Wiki del repositorio](https://github.com/misw-is-aplicaciones-moviles/MobileVynils/wiki): entregables por sprint (backlog, diseño arquitectónico, estrategia y resultados de pruebas, actas, retrospectivas).

## Solución de problemas

- **La app muestra "No se pudo cargar el catálogo".** El backend no está encendido o la app no lo alcanza. Revisa que `http://localhost:3000/albums` responda en el computador y que la app corra en un emulador de ese mismo computador, no en un dispositivo físico ([sección 1](#cómo-llega-la-app-al-backend)).

- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE` al instalar el APK.** Hay otra versión instalada con distinta firma. Desinstálala con `adb uninstall co.edu.uniandes.vinilos`.

- **`SDK location not found`.** Falta `sdk.dir` en `local.properties` o la variable `ANDROID_HOME` ([sección 3](#desde-terminal)).

- **`AAPT2 ... Daemon startup failed` / "Una directiva de Control de aplicaciones bloqueó este archivo".** El control de aplicaciones de Windows bloquea el `aapt2.exe` que Gradle extrae a su caché. Usa el de los build-tools del SDK añadiendo a `%USERPROFILE%\.gradle\gradle.properties` (no al repositorio):

  ```properties
  android.aapt2FromMavenOverride=C:/Users/<usuario>/AppData/Local/Android/Sdk/build-tools/36.0.0/aapt2.exe
  ```

- **`UnknownHostException: plugins-artifacts.gradle.org` o `Could not GET ...gradle.org`.** La JVM intenta IPv6 y falla en algunas redes. El proyecto ya pasa `-Djava.net.preferIPv4Stack=true` al daemon en `gradle.properties`; si el fallo aparece al descargar la distribución del wrapper, define `GRADLE_OPTS=-Djava.net.preferIPv4Stack=true` en la sesión.

- **`local.properties` con `PropertyEscape` en Lint.** Escribe la ruta del SDK con `\:` y barras normales: `sdk.dir=C\:/Users/<usuario>/AppData/Local/Android/Sdk`.

- **Formato.** `./gradlew spotlessApply` corrige el estilo con ktlint; `spotlessCheck` solo verifica. detekt no corrige automáticamente.
