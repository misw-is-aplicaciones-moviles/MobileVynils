# Pruebas E2E unificadas — Espresso y BackVynils

## Estructura

- `ui/`: recorridos funcionales existentes.
- `robots/`: acciones y verificaciones de las pantallas, reutilizadas.
- `testing/VinilosE2ERule`: aislamiento, ActivityScenario y cierre por caso.
- `testing/TestAppHarness`: dependencias reales y recursos de sincronización.
- `testing/idling/`: tareas de corutinas, Room y eventos de foco/hojas.
- `testing/network/NetworkFaults`: fallos controlados del cliente de la app.
- `testing/Backend*`: fixtures existentes de Jhon, con su propiedad y limpieza.
- `database/`: pruebas instrumentadas de Room y migraciones, sin la regla E2E.

Se adopta la estructura de `vinilos-e2e-standard`, adaptada al código real. No se copian sus Robots ilustrativos, ni se crea un segundo BackendFixtures. El arranque del backend se integra en Gradle: no se incorporan sus scripts Bash ni sus cambios del locale, pulsación larga o ajustes generales del dispositivo.

## Requisitos y ejecución

JDK 17, SDK del proyecto, Docker con Compose y un dispositivo autorizado (tablet USB o emulador ya iniciado). Se requieren imágenes locales `postgres:17` y `backvynils-web:latest`. No se inicia un AVD automáticamente.

Si faltan las imágenes, prepararlas una vez:

```bash
docker pull postgres:17
git clone https://github.com/TheSoftwareDesignLab/BackVynils.git /tmp/BackVynils-e2e
git -C /tmp/BackVynils-e2e checkout 7178f83662022904dc9e88b8a2292568954b56cf
docker build -t backvynils-web:latest /tmp/BackVynils-e2e
```

Los comandos suponen que `/tmp/BackVynils-e2e` no existe. No reemplazar el checkout de desarrollo. CI construye la imagen desde esa revisión; una imagen local diferente puede tener otro contrato. La preparación registra el ID de imagen utilizado, sin volcar sus variables de entorno.

```bash
# Toda la instrumentación
./gradlew connectedDebugAndroidTest

# Con varios equipos autorizados, elegir el identificador de adb devices
ANDROID_SERIAL=SERIAL ./gradlew connectedDebugAndroidTest

# Solo UI, una clase o un método
./gradlew connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.package=co.edu.uniandes.vinilos.ui'
./gradlew connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=co.edu.uniandes.vinilos.ui.PrizePickerScreenTest'
./gradlew connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=co.edu.uniandes.vinilos.ui.PrizePickerScreenTest#assigns_prize_to_musician_and_updates_its_detail'
```

No combinar `ANDROID_SERIAL` con `-Pandroid.injected.device.serial`: podrían seleccionar equipos diferentes. Se prepara y ejecuta un único dispositivo por entorno.

## Ejecutar desde terminal

Ejecutar los comandos desde la raíz del repositorio, donde está `gradlew`. Antes de la primera ejecución:

```bash
java -version
./gradlew --version
docker info
docker compose version
adb devices
```

Usar **JDK 17**, como en las ejecuciones locales verificadas. Si Gradle utiliza otro JDK, definir `JAVA_HOME` para esa terminal y volver a comprobar `./gradlew --version`. Ejemplo Linux, ajustando la ruta si corresponde:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew --version
```

Si `adb` no está en PATH, usar el ejecutable de `platform-tools` dentro del SDK. En Linux suele estar en `$HOME/Android/Sdk/platform-tools/adb`; la ubicación real del SDK se consulta en Android Studio. Gradle localiza ADB a través del SDK del proyecto, por lo que no exige que el comando `adb` esté en PATH.

En `adb devices`, el equipo elegido debe aparecer como `device`. Si dice `unauthorized`, aceptar la autorización de depuración USB en la tablet; si está `offline` o aún arrancando, esperar a que esté disponible. Para un emulador, iniciarlo desde `Tools > Device Manager`; el nombre del AVD no es su serial: usar el identificador `emulator-…` de ADB.

```bash
# Un solo equipo autorizado
./gradlew connectedDebugAndroidTest

# Elegir entre varios equipos; sustituir por el serial real
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
```

En Windows PowerShell, usar el wrapper de Windows y configurar la variable por separado:

```powershell
$env:ANDROID_SERIAL = "emulator-5554"
.\gradlew.bat connectedDebugAndroidTest
```

Con un único equipo no hace falta definir `ANDROID_SERIAL`; si ya tenía un valor de otra ejecución, actualizarlo o quitarlo para no apuntar al equipo anterior.

## Ejecutar desde Android Studio

La configuración será de tipo **Gradle**, para incluir preparación y descarte del backend. No elegir `Android App` ni `Android Instrumented Tests` para este flujo completo.

1. Abrir la raíz del repositorio y sincronizar con `Sync Project with Gradle Files`.
2. Abrir `Settings > Build, Execution, Deployment > Build Tools > Gradle` y seleccionar JDK 17 en la configuración del JDK de Gradle. La terminal y los botones del IDE pueden utilizar JDK diferentes.
3. Comprobar los requisitos comunes: Docker disponible para el usuario del IDE, imágenes locales preparadas, SDK configurado y tablet autorizada o emulador ya iniciado.
4. Abrir `Run > Edit Configurations…`, pulsar `+` y seleccionar `Gradle`.
5. Completar los campos:

   | Campo | Valor |
   |---|---|
   | Name | `E2E Vinilos` |
   | Gradle project | Raíz de `MobileVynils`, donde está `settings.gradle.kts` |
   | Run / tareas y argumentos | `:app:connectedDebugAndroidTest` |
   | Environment variables | Vacío si hay un solo equipo y no hay un serial heredado; con varios, `ANDROID_SERIAL=SERIAL_REAL` |

6. Si no aparece `Environment variables`, habilitarlo desde `Modify options`. Obtener el serial con `adb devices` desde la terminal; no usar el nombre del AVD ni escribir `ANDROID_SERIAL=…` como tarea Gradle.
7. Pulsar `Apply > OK`, seleccionar `E2E Vinilos` en la barra superior y pulsar Run.
8. Consultar la ventana de ejecución: debe mostrar preparación del entorno, `Running tests on devices`, verificación de resultados y descarte. Al finalizar correctamente, `BUILD SUCCESSFUL`.

El selector de dispositivos que acompaña a una configuración Android nativa no controla esta tarea Gradle. El botón verde al lado de una clase instrumentada tampoco prepara Docker. La configuración anterior es el punto de entrada del ciclo completo; no se necesita ejecutar un Bash previamente.

### Una clase o un método desde el IDE

Duplicar `E2E Vinilos`, conservar el proyecto y las variables de entorno, y sustituir el contenido de **Run** por una de estas líneas completas. No añadir `./gradlew`:

```text
:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=co.edu.uniandes.vinilos.ui.PrizePickerScreenTest
```

```text
:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=co.edu.uniandes.vinilos.ui.PrizePickerScreenTest#assigns_prize_to_musician_and_updates_its_detail
```

Para filtrar el paquete de UI, usar el mismo campo Run:

```text
:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=co.edu.uniandes.vinilos.ui
```

### Equipos con recursos limitados

Las validaciones locales se realizaron con un dispositivo a la vez, un worker de Gradle y heap máximo de 2 GiB. Si hace falta reproducir esos ajustes, agregar los mismos argumentos a la terminal o al campo Run de la configuración Gradle:

```text
:app:connectedDebugAndroidTest --max-workers=1 -Dorg.gradle.parallel=false "-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8"
```

En terminal, anteponer `./gradlew ` a la línea anterior. Son opciones de ejecución; no cambian el AVD ni cierran otras aplicaciones.

### Problemas frecuentes

| Mensaje o situación | Qué comprobar |
|---|---|
| No aparecen tareas o la opción Gradle | Abrir la raíz del proyecto y completar su sincronización. La configuración manual no depende de encontrar la tarea en el árbol. |
| `Disponibles: []` | Conectar/autorizar la tablet o terminar el arranque del emulador; comprobar ADB. |
| Hay varios dispositivos | Definir `ANDROID_SERIAL` en la configuración que se está ejecutando. |
| El serial indicado no está disponible | Actualizar la variable; también puede estar heredada del entorno del IDE. |
| Docker no responde o no se encuentra | Iniciar Docker y comprobar que el proceso del IDE puede ejecutar su CLI y acceder al motor. |
| Falta una imagen | Completar la preparación de imágenes de esta guía; no arrancar el Compose de desarrollo como reemplazo. |
| Run busca una tarea llamada `ANDROID_SERIAL=…` | Mover la asignación a Environment variables. |
| Compilación con un JDK diferente | Revisar el JDK de Gradle del IDE y compararlo con `./gradlew --version` en terminal. |
| El reporte muestra otra ejecución | Consultar el HTML generado después de la ejecución actual; guardar una copia antes de repetir. |

La terminal está validada en API 21 y API 37. Las instrucciones del IDE siguen la configuración Gradle documentada por Android/JetBrains, pero no se afirma una ejecución verificada desde la interfaz de Android Studio.

Fuentes: [selección del JDK de Gradle](https://developer.android.com/build/jdks) y [configuraciones Gradle del IDE](https://www.jetbrains.com/help/idea/run-debug-gradle.html).

## Ciclo del entorno

1. Gradle crea un proyecto Compose `vinilos-e2e-…` nuevo en ejecución; la caché de configuración no conserva su base.
2. PostgreSQL usa almacenamiento temporal, sin puertos públicos ni carpetas de desarrollo. BackVynils publica un puerto dinámico solo en localhost.
3. Se espera salud y se carga `seed.sql`, protegido por el nombre exclusivo `vinilos_e2e`. Se alinean todas las secuencias incluidas tras insertar IDs explícitos.
4. Se prepara la redirección ADB `tcp:31370` en el equipo elegido. Si ya existe, no se reemplaza.
5. El runner debug instala el endpoint reservado antes de construir el cliente. La app y sus fixtures usan el mismo backend; `local.properties`, las migraciones existentes y la URL de release se conservan.
6. Se ejecutan los casos. El finalizador guarda logs y descarta solo ese proyecto y su redirección. Fallos de preparación, instrumentación o descarte no se convierten en aprobaciones.
7. La verificación exige XML de esta invocación con más de cero pruebas; los reportes viejos no acreditan una ejecución nueva.

`assembleDebug`, `assembleRelease` y las unitarias no arrancan Docker. Los scripts antiguos de ejecución y limpieza se retiran de esta rama; el SQL necesario queda versionado en `tests/e2e/seed.sql`.

## Regla común y datos por caso

La regla cierra las dependencias anteriores antes de limpiar Room y crea un contenedor sin rol y sin caché HTTP. Los valores de producción siguen siendo los originales. Los recursos de inactividad se registran antes de lanzar la Activity. La recreación conserva el contenedor durante el caso.

El piloto HU13/HU14 usa `VinilosE2ERule()` y llama a `launch()` después de crear sus datos. Mantiene las verificaciones originales, incluidos los POST y la ausencia de escrituras al cancelar. Las demás clases usan temporalmente `autoLaunch = true` para conservar el orden de ActivityScenarioRule; comparten el aislamiento y migrarán a lanzamiento explícito por grupos tras validar el piloto.

`BackendPrizes` conserva la creación y las comprobaciones de propiedad de Jhon. El piloto API 21 detectó que el DELETE de asociaciones no quitaba el vínculo pedido: los fixtures permanecen hasta descartar la base exclusiva y se comprueba que los registros y vínculos anteriores sigan presentes. Las pruebas HU11 conservan sus datos hasta descartar el entorno para no utilizar el DELETE defectuoso de vínculos. Algunos casos de catálogo/detalle todavía leen la semilla de compatibilidad o datos del backend por contenido: no se afirma que toda la suite haya dejado de usarla.

`NetworkFaults` simula IOException, timeout o HTTP controlado únicamente en el cliente de la app. Los recorridos exitosos siguen llegando a BackVynils real; estos errores controlados no acreditan una caída real del servidor. `BackendFirewall` conserva temporalmente su API de los casos existentes y delega en ese interceptor: ya no abre sockets ni modifica ProxySelector. Los fixtures HTTP independientes siguen funcionando durante la desconexión de la app. Las pruebas JVM con MockWebServer conservan el contrato y los fallos reales de transporte que ya cubrían.

No se eliminan todas las esperas ni se da por cerrado el fallo de hojas por compilar. El recurso de foco usa eventos de ventana, Fragment y estado de BottomSheet, notifica fuera de `isIdleNow()` y libera sus observadores al terminar. Su comportamiento con recreación y calendarios requiere validación en Android. Las esperas de avisos y transiciones se migrarán según esa evidencia.

## Reportes y recuperación

- HTML Android: `app/build/reports/androidTests/connected/debug/index.html`.
- XML: `app/build/outputs/androidTest-results/connected/debug/`.
- Preparación, imagen y logs: `app/build/reports/e2e-environment/vinilos-e2e-…/`.
- Comprobaciones locales host: `reports/e2e-combinada/`.

`stopped.txt` acredita descarte normal, no que se ejecutó una E2E. Conservar resultados antes de otra ejecución o `clean`.

Un apagado o cierre forzado del daemon puede impedir el finalizador. Identificar el nombre exacto del proyecto en el reporte y comprobar que es el creado para esta ejecución. Solo para esos recursos:

```bash
docker compose -p NOMBRE_EXACTO -f tests/e2e/compose.yml down --volumes
adb -s SERIAL reverse --remove tcp:31370
```

No retirar redirecciones ajenas, usar `docker system prune` ni borrar `pgdata`.

## Validación pendiente

Consultar [el avance de la implementación](../../avance-e2e-combinada.md). Compilar instrumentación no significa ejecutarla. En API 21 pasaron el piloto por método, los cinco casos de su clase y dos suites completas de 125 pruebas sin fallos ni omisiones. En API 37 pasaron el piloto por método y una suite completa de 125 pruebas, sin fallos ni omisiones. Faltan la tablet, comprobar el filtro por paquete y el descarte tras una aserción fallida provocada con la versión final. El workflow está preparado; sus resultados no se inventan ni se publican desde esta sesión.
