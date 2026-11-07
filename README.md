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

> **APK del Sprint 1:** descarga [`vinilos-1.0.0.apk`](vinilos-1.0.0.apk), ubicado en la raíz del repositorio. Este APK consume el backend desplegado en **https://backvynils-0c3a5834efca.herokuapp.com/** y necesita conexión a Internet. Puede instalarse en un dispositivo Android físico o en un emulador; no requiere levantar el backend local.

## Contenido

- [Equipo](#equipo)
- [Requisitos](#requisitos)
- [1. Levantar el backend local](#1-levantar-el-backend-local) (solo para desarrollo y pruebas locales)
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

Para instalar y probar el APK publicado, se recomienda un dispositivo o emulador con **Android 17 (API 37)** y conexión a Internet. Para compilar y ejecutar pruebas locales, utiliza las herramientas de la siguiente tabla.

| Herramienta | Versión | Para qué |
|---|---|---|
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) | Reciente, encendido | Backend y pruebas locales |
| [Android Studio](https://developer.android.com/studio) | 2026.2 (Quail) o superior | Emulador y compilación desde el código |
| Emulador de Android (AVD) | API 37 recomendado; API 21 o superior | Ejecutar la app si no usas un dispositivo físico |
| Android SDK | Platform 37 y Build-Tools 36 | Compilar (B). Se instalan desde `Tools > SDK Manager` |
| JDK | 17 o superior (Android Studio incluye uno) | Compilar desde terminal (B) |
| Git | Cualquiera reciente | Clonar los repositorios |

Para crear el emulador recomendado: `Tools > Device Manager > Create Virtual Device`, perfil **Medium Phone** e imagen de sistema **API 37**.

## 1. Levantar el backend local

Para desarrollar y probar la app **manualmente** con un backend local, levanta [BackVynils](https://github.com/TheSoftwareDesignLab/BackVynils) con Docker y Docker Compose. Las **pruebas E2E automatizadas** de la [sección 4](#4-ejecutar-las-pruebas) preparan su propio backend y base aislados; no necesitan este servidor de desarrollo. El APK publicado utiliza Heroku y tampoco necesita este paso.

```bash
git clone https://github.com/TheSoftwareDesignLab/BackVynils.git
cd BackVynils
docker compose up --build
```

La API queda en `http://localhost:3000` con los datos de ejemplo de BackVynils. Compruébalo abriendo `http://localhost:3000/albums` en el navegador: debe devolver una lista de álbumes (A Day at the Races, A Night at the Opera, Buscando América, Poeta del pueblo).

### Cómo llega la app al backend

La URL se incorpora al APK durante la compilación mediante `vinilos.baseUrl` en `local.properties`. Modificar ese archivo no cambia un APK ya generado: hay que compilarlo e instalarlo de nuevo.

| Entorno | `vinilos.baseUrl` | Conexión necesaria |
|---|---|---|
| APK publicado / Heroku | `https://backvynils-0c3a5834efca.herokuapp.com/` | Internet; sin Docker ni redirección USB |
| Backend local y emulador de Android Studio | `http://10.0.2.2:3000/` | BackVynils en `localhost:3000` del computador que ejecuta el emulador |
| Backend local y dispositivo físico por USB | `http://127.0.0.1:3000/` | BackVynils en el computador y `adb -d reverse tcp:3000 tcp:3000` |

`localhost` en Android se refiere al propio dispositivo. En el emulador, `10.0.2.2` permite acceder al computador; en el dispositivo físico, la redirección USB conecta su puerto 3000 con el del computador. La app admite HTTP para el backend local y HTTPS para Heroku.

## 2. Opción A: instalar el APK publicado

El APK de esta entrega está en la **raíz del repositorio**: [`vinilos-1.0.0.apk`](vinilos-1.0.0.apk). Es una compilación **debug** y tiene configurado el backend `https://backvynils-0c3a5834efca.herokuapp.com/`.

Pasos:

1. Descarga el APK desde el enlace anterior; en GitHub utiliza la opción de descargar el archivo.
2. En un dispositivo Android con Internet, permite la instalación desde la aplicación con la que abriste el APK y completa la instalación. También puedes instalarlo por USB:

   ```bash
   adb -d install -r vinilos-1.0.0.apk
   ```

   En un emulador, arrastra el APK a su ventana o utiliza `adb -e install -r vinilos-1.0.0.apk`.

3. Abre **Vinilos**, elige **Visitante** o **Coleccionista** y comprueba el catálogo y el detalle de un álbum.

**Backend de la versión publicada:** `vinilos-1.0.0.apk` se conecta a `https://backvynils-0c3a5834efca.herokuapp.com/`. No necesita Docker, un backend local ni `adb reverse`. Cambiar `local.properties` no cambia este APK descargado. Para conectarse a un backend local hay que generar e instalar un APK propio, como se explica a continuación.

## 3. Opción B: construir la app desde el código

Necesitas las herramientas de la sección [Requisitos](#requisitos). Para ejecutar la app, elige Heroku o levanta el backend local de la [sección 1](#1-levantar-el-backend-local). Desde terminal, `JAVA_HOME` debe apuntar a un JDK 17 o superior.

Abre o crea `local.properties` en la raíz del repositorio, junto a `gradlew`. Conserva la línea `sdk.dir` si ya existe y agrega o reemplaza una única línea `vinilos.baseUrl`, con `/` final. Por ejemplo, para el emulador con backend local:

```properties
vinilos.baseUrl=http://10.0.2.2:3000/
```

Para un dispositivo físico por USB, usa `vinilos.baseUrl=http://127.0.0.1:3000/` y ejecuta `adb -d reverse tcp:3000 tcp:3000`. Para generar un APK conectado a Heroku, usa `vinilos.baseUrl=https://backvynils-0c3a5834efca.herokuapp.com/`. Si no defines esta propiedad, el código utiliza `http://10.0.2.2:3000/`. No versiones `local.properties`.

### Paso a paso: generar y probar tu APK con backend local

1. Clona este repositorio y entra en su raíz:

   ```bash
   git clone https://github.com/misw-is-aplicaciones-moviles/MobileVynils.git
   cd MobileVynils
   ```

2. Levanta BackVynils siguiendo la [sección 1](#1-levantar-el-backend-local). Déjalo ejecutándose y comprueba que `http://localhost:3000/albums` devuelve los álbumes en el navegador del computador.
3. Configura el SDK como se indica en [Desde terminal](#desde-terminal). En el mismo `local.properties`, agrega **solo una** de estas opciones según dónde probarás:

   | Dónde ejecutarás tu APK | Línea que debes guardar en `local.properties` |
   |---|---|
   | Emulador de Android Studio en el computador del backend | `vinilos.baseUrl=http://10.0.2.2:3000/` |
   | Dispositivo físico conectado por USB al computador del backend | `vinilos.baseUrl=http://127.0.0.1:3000/` |

4. Inicia el emulador o conecta el dispositivo físico con depuración USB habilitada. Comprueba la conexión con `adb devices`. Si usas el dispositivo físico, ejecuta también:

   ```bash
   adb -d reverse tcp:3000 tcp:3000
   ```

5. Desde la raíz del repositorio, genera tu APK:

   ```bash
   ./gradlew assembleDebug
   ```

6. Cuando termine con `BUILD SUCCESSFUL`, tu APK estará en `app/build/outputs/apk/debug/app-debug.apk`. Instálalo según tu dispositivo:

   ```bash
   # Dispositivo físico por USB:
   adb -d install -r app/build/outputs/apk/debug/app-debug.apk

   # O emulador:
   adb -e install -r app/build/outputs/apk/debug/app-debug.apk
   ```

7. Abre **Vinilos**, selecciona un rol, comprueba que carga el catálogo y abre el detalle de un álbum. Como coleccionista también puedes crear un álbum y asociarle una canción; los cambios se guardarán en tu backend local. Mantén el backend encendido y, si usas USB, el dispositivo conectado.

Si vuelves a instalar `vinilos-1.0.0.apk` de la raíz, la aplicación volverá a utilizar Heroku. Si Android informa que las firmas son incompatibles, consulta [Solución de problemas](#solución-de-problemas).

### Desde Android Studio

1. Clona el repositorio:

   ```bash
   git clone https://github.com/misw-is-aplicaciones-moviles/MobileVynils.git
   ```

2. Abre la carpeta en Android Studio (`File > Open`). Gradle sincroniza y descarga las dependencias declaradas en `gradle/libs.versions.toml`; la primera vez tarda varios minutos. Android Studio crea `local.properties` con la ruta del SDK.
3. Configura `vinilos.baseUrl` en `local.properties`, selecciona la configuración `app` y el dispositivo o emulador, y pulsa `Run`.

### Desde terminal

Si no abriste el proyecto en Android Studio, crea `local.properties` con la ruta del SDK (o define `ANDROID_HOME`):

```properties
sdk.dir=C\:/Users/<usuario>/AppData/Local/Android/Sdk
```

En Linux suele ser `sdk.dir=/home/<usuario>/Android/Sdk`; en macOS, `sdk.dir=/Users/<usuario>/Library/Android/sdk`. Utiliza la ruta real de tu instalación y configura `vinilos.baseUrl` como se indicó arriba. Luego, desde la raíz del repositorio:

```bash
./gradlew assembleDebug   # genera app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug    # compila e instala en el dispositivo o emulador conectado
```

Para generar tu APK basta con `./gradlew assembleDebug`; instalarlo es opcional. El archivo resultante es `app/build/outputs/apk/debug/app-debug.apk`. Cada vez que cambies `vinilos.baseUrl`, ejecuta de nuevo la compilación e instala el nuevo APK para que use esa dirección. Esto no modifica el APK de entrega `vinilos-1.0.0.apk` guardado en la raíz.

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

Se ejecutan con Espresso y AndroidX Test mediante Gradle, sin scripts Bash. Se necesitan JDK 17, Docker con Compose, las imágenes locales `postgres:17` y `backvynils-web:latest`, y una tablet autorizada por USB o un emulador ya iniciado. [Preparación del entorno E2E](tests/e2e/README.md).

```bash
./gradlew connectedDebugAndroidTest
```

Gradle crea PostgreSQL y BackVynils exclusivos por ejecución, carga la semilla de compatibilidad, alinea sus secuencias y prepara la conexión USB. Al terminar intenta descartar únicamente ese entorno. No se usa el backend de desarrollo ni se modifica `local.properties` o el APK de entrega.

Si hay varios dispositivos conectados, elegir uno con su identificador de `adb devices`:

```bash
ANDROID_SERIAL=SERIAL ./gradlew connectedDebugAndroidTest
```

```bash
# Solo UI
./gradlew connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.package=co.edu.uniandes.vinilos.ui'

# Una clase o un método
./gradlew connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=co.edu.uniandes.vinilos.ui.PrizePickerScreenTest'
./gradlew connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=co.edu.uniandes.vinilos.ui.PrizePickerScreenTest#assigns_prize_to_musician_and_updates_its_detail'
```

El comando completo incluye UI, componentes, integración de red y pruebas instrumentadas de Room; las unitarias JVM se ejecutan aparte con `testDebugUnitTest`. Los reportes Android quedan en `app/build/reports/androidTests/connected/debug/index.html`; los de preparación y descarte, en `app/build/reports/e2e-environment/`. Un reporte de una ejecución anterior no demuestra que la actual haya pasado.

Para ejecutarlas desde **Android Studio**:

1. Abrir la raíz `MobileVynils` y sincronizar Gradle (`Sync Project with Gradle Files`).
2. En `Settings > Build, Execution, Deployment > Build Tools > Gradle`, seleccionar **JDK 17** para Gradle, que es el utilizado en la validación local. El JDK de la terminal puede ser distinto del configurado en el IDE.
3. Iniciar Docker y preparar las imágenes según la [guía E2E](tests/e2e/README.md#requisitos-y-ejecución).
4. Conectar la tablet y autorizar depuración USB, o iniciar un emulador desde `Tools > Device Manager`. Mantener un solo dispositivo conectado simplifica la selección.
5. Abrir `Run > Edit Configurations…`, pulsar `+` y elegir **Gradle**.
6. Asignar el nombre `E2E Vinilos`, seleccionar la raíz del proyecto en **Gradle project** y escribir `:app:connectedDebugAndroidTest` en **Run** (tareas y argumentos). No escribir `./gradlew` en ese campo.
7. Si hay varios equipos, abrir **Environment variables** —puede estar en `Modify options`— y definir `ANDROID_SERIAL` con el identificador obtenido mediante `adb devices`, por ejemplo `emulator-5554`. No poner esa asignación en el campo Run.
8. Guardar con `Apply > OK`, seleccionar `E2E Vinilos` en la barra superior y pulsar Run. Gradle prepara el entorno y abre la app para ejecutar las pruebas.

El selector de dispositivo de una configuración Android nativa no selecciona el equipo de una tarea Gradle. El botón Run al lado de una clase de prueba no prepara automáticamente Docker: para este ciclo completo se usa la configuración **Gradle** anterior. [Filtros, errores frecuentes y reportes](tests/e2e/README.md#ejecutar-desde-android-studio).

La ejecución por terminal está validada en API 21 y API 37. Los pasos del IDE están documentados; todavía falta comprobar este flujo de extremo a extremo desde Android Studio.

### Prueba de conexión con el backend

Se conserva la prueba de contrato real de conexión y JSON; Gradle la habilita automáticamente:

```bash
./gradlew connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=co.edu.uniandes.vinilos.network.BackendConnectionTest'
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

El workflow actual genera y publica el APK de release al subir un tag `sprint-*` (por ejemplo, `sprint-1`), después de pasar las verificaciones de calidad y pruebas unitarias. Un push a `develop` o `main` compila debug, pero no publica ese APK.

Flujo de cada entrega:

1. Crear `release/sprint-N` desde `develop` y revisar versión, documentación y backend de destino.
2. Configurar `vinilos.baseUrl` antes de compilar. El APK actual `vinilos-1.0.0.apk` se compiló para Heroku. Para reemplazarlo por otra compilación debug de entrega, después de `./gradlew assembleDebug` copiar el resultado a la raíz:

   ```bash
   cp app/build/outputs/apk/debug/app-debug.apk vinilos-1.0.0.apk
   ```

   Verificar la instalación y el backend antes de agregar el archivo a Git. Los directorios `build/` permanecen ignorados; el APK de entrega se versiona en la raíz. Si se entrega un APK release, generarlo con `./gradlew assembleRelease`, identificarlo como `vinilos-sprint-N-release.apk` en la raíz y actualizar el enlace de descarga.
3. Abrir el PR de la rama de release hacia `main` y hacer merge después de las revisiones.
4. Crear y subir el tag correspondiente sobre el commit de entrega en `main`.
5. El job "APK de release" adjunta `vinilos-sprint-N.apk` a [GitHub Releases](https://github.com/misw-is-aplicaciones-moviles/MobileVynils/releases). Este job no agrega archivos al repositorio.

**Backend del APK generado en CI:** el workflow actual no configura `vinilos.baseUrl` y `local.properties` no se versiona; por tanto, el APK generado por Actions utiliza el valor predeterminado `http://10.0.2.2:3000/`. No es equivalente al APK de Heroku guardado en la raíz. Antes de distribuir un release de CI para Heroku, el equipo debe configurar ese destino en el proceso de compilación.

## Documentación

- [docs/](docs/README.md): convenciones de arquitectura, código, diseño, pruebas, desempeño, git y calidad.
- [Wiki del repositorio](https://github.com/misw-is-aplicaciones-moviles/MobileVynils/wiki): entregables por sprint (backlog, diseño arquitectónico, estrategia y resultados de pruebas, actas, retrospectivas).

## Solución de problemas

- **La app muestra "No se pudo cargar el catálogo".** Con el APK de la raíz, comprueba Internet y que `https://backvynils-0c3a5834efca.herokuapp.com/albums` responda. Con una compilación local, comprueba `http://localhost:3000/albums` en el computador y la URL compilada: `10.0.2.2` en emulador o `127.0.0.1` más `adb -d reverse tcp:3000 tcp:3000` en dispositivo físico. Si cambias `vinilos.baseUrl`, vuelve a compilar e instalar.

- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE` al instalar el APK.** Hay otra versión instalada con distinta firma. Para reemplazarla se requiere la misma firma o desinstalar la anterior con `adb uninstall co.edu.uniandes.vinilos`; la desinstalación elimina sus datos locales.

- **`SDK location not found`.** Falta `sdk.dir` en `local.properties` o la variable `ANDROID_HOME` ([sección 3](#desde-terminal)).

- **`AAPT2 ... Daemon startup failed` / "Una directiva de Control de aplicaciones bloqueó este archivo".** El control de aplicaciones de Windows bloquea el `aapt2.exe` que Gradle extrae a su caché. Usa el de los build-tools del SDK añadiendo a `%USERPROFILE%\.gradle\gradle.properties` (no al repositorio):

  ```properties
  android.aapt2FromMavenOverride=C:/Users/<usuario>/AppData/Local/Android/Sdk/build-tools/36.0.0/aapt2.exe
  ```

- **`UnknownHostException: plugins-artifacts.gradle.org` o `Could not GET ...gradle.org`.** La JVM intenta IPv6 y falla en algunas redes. El proyecto ya pasa `-Djava.net.preferIPv4Stack=true` al daemon en `gradle.properties`; si el fallo aparece al descargar la distribución del wrapper, define `GRADLE_OPTS=-Djava.net.preferIPv4Stack=true` en la sesión.

- **`local.properties` con `PropertyEscape` en Lint.** Escribe la ruta del SDK con `\:` y barras normales: `sdk.dir=C\:/Users/<usuario>/AppData/Local/Android/Sdk`.

- **Formato.** `./gradlew spotlessApply` corrige el estilo con ktlint; `spotlessCheck` solo verifica. detekt no corrige automáticamente.
