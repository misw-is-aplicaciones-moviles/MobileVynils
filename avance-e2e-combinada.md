# Implementación combinada de pruebas E2E

**Rama:** `test/e2e-espresso-unificado`. **Base:** `ajuste-pruebas`, commit `4dd58f9`. **Fecha:** 07/11/2026.

Se adapta `vinilos-e2e-standard` al proyecto real, reutilizando los fixtures y Robots de Jhon. Gradle controla el backend y la base exclusivos; no hay scripts Bash de ejecución obligatorios. No se crean commits ni se publica la rama. Los ocho documentos que estaban añadidos al índice y el paquete de referencia se conservan.

## Cambios preparados

| Responsabilidad | Implementación |
|---|---|
| Entorno | Servicio Gradle que prepara Compose, PostgreSQL temporal, BackVynils real, semilla y secuencias, disponibilidad y ADB. Finalizador limitado a sus propios recursos. |
| Dependencias de la app | DispatcherProvider, ejecutor de Room y personalización HTTP inyectables; se conservan construcción diferida, migraciones, URL y configuración predeterminada de producción. |
| Regla común | VinilosE2ERule y TestAppHarness: cierre del contenedor anterior antes de limpiar Room; sesión nueva; HTTP sin caché en pruebas; registro y liberación de recursos y preservación de errores de limpieza. |
| Sincronización | Contadores separados para IO, Default y Room, recurso OkHttp existente ampliado a llamadas en cola y recurso de foco/transición de hojas basado en eventos. No emite callbacks desde isIdleNow. |
| Datos | BackendPrizes conserva la creación y propiedad de Jhon. El piloto API 21 detectó un DELETE defectuoso de asociaciones: premios y artistas quedan en la base efímera hasta el descarte; se verifica conservación de registros y vínculos anteriores. Se reutilizan los demás Backend* y Robots; HU11 conserva sus datos hasta descartar la base para evitar el DELETE defectuoso. |
| Desconexión | NetworkFaults afecta solo al cliente de la app. BackendFirewall mantiene su API para los casos anteriores y delega al interceptor; no usa sockets ni ProxySelector. |
| Piloto | HU13/HU14 crean sus fixtures antes de launch(), conservando sus verificaciones funcionales. Se retira el reintento de toque de pestaña de HU13. Las otras clases usan autoLaunch temporalmente para conservar su orden previo. |
| Ejecución y CI | Comando Gradle único, filtros por clase/método/paquete, selección por ANDROID_SERIAL, workflow API 21/37 y reportes. Se rechazan cero pruebas y reportes antiguos. |

Los scripts antiguos de ejecución/limpieza se retiran de esta rama; el SQL necesario está en `tests/e2e/seed.sql`. El paquete `vinilos-e2e-standard/` es referencia y puede seguir conteniendo sus scripts originales: no se ejecutan ni se integran al flujo.

## Comprobaciones realizadas

- 343 unitarias JVM: 0 fallos, errores y omisiones. Incluyen una regresión nueva con MockWebServer de personalización del cliente y desactivación de caché, manteniendo el contrato HTTP y los POST sin reintentos.
- Seis pruebas de selección de dispositivo de buildSrc: correctas.
- Seis pruebas puras de contadores y NetworkFaults: correctas con JUnitCore en JVM sobre las clases compiladas en androidTest. No equivalen a instrumentación ni a recorridos E2E.
- Dos proyectos exclusivos de PostgreSQL/BackVynils arrancaron, recibieron la semilla de cuatro álbumes y generaron ID 104 en el primer POST. Ambos se descartaron. El inventario de contenedores ajenos permaneció igual; no se comparó el contenido de bases ajenas.
- Spotless, detekt y Lint debug: correctos, sin desactivar reglas. Compilación de instrumentación y Kotlin release: correctas. La comprobación completa terminó con BUILD SUCCESSFUL.

- La preparación sin dispositivo falló explícitamente y ejecutó su finalizador. La segunda invocación reutilizó la caché de configuración y generó otra identidad de entorno. Es una comprobación negativa de infraestructura, no un fallo funcional de la app.

Las evidencias locales quedan en `reports/e2e-combinada/`, excluido por las reglas existentes de Git. Los reportes anteriores se conservaron como referencia histórica; no acreditan esta versión. [Inventario por clase](tests/e2e/inventario.md): 125 métodos instrumentados declarados, de los cuales 119 ya existían y seis corresponden a infraestructura. No son 125 recorridos E2E.

## Validación API 21 — 07/11/2026

Emulador `HU14_API_21`, Android 5.0.2, visible y con arranque en frío; se reutiliza su configuración y no se borran sus datos. Cada invocación Gradle prepara otro backend/base y conserva su descarte.

| Ejecución | Resultado real |
|---|---|
| Primer piloto, antes de corregir limpieza | 1 prueba fallida: el DELETE de asociación respondió pero dejó el vínculo propio. Se conservó el XML. |
| Piloto tras ajustar limpieza a la base efímera | 1 aprobada, 0 fallidas, errores u omitidas. BUILD SUCCESSFUL. |
| Clase PrizePickerScreenTest con el ajuste | 5 aprobadas, 0 fallidas, errores u omitidas. Incluye recreación, cancelación, búsqueda y recuperación de red. BUILD SUCCESSFUL. |
| Primera suite completa | 125 aprobadas, 0 fallidas, errores u omitidas. BUILD SUCCESSFUL en 6 min 8 s. |
| Segunda suite completa | 125 aprobadas, 0 fallidas, errores u omitidas. BUILD SUCCESSFUL en 6 min 24 s. Otra base exclusiva. |

Se conserva la creación y propiedad de los fixtures de Jhon; las asociaciones no se borran por el endpoint defectuoso. Se comprueba que los registros y vínculos previos sigan presentes y Gradle descarta la base exclusiva al terminar. Los recorridos y sus verificaciones no se debilitan.

Los reportes por fase y logcat están en `reports/e2e-combinada/api21/`. El aviso de AGP al recoger salidas adicionales en API 21 (`File name too long`) apareció también en ejecuciones con BUILD SUCCESSFUL: no sustituye la revisión de fallos reales del XML.

## Validación API 37 — 07/11/2026

Emulador `Medium_Phone_API_37.0`, Android 17 / API 37, visible y con arranque en frío. Se cerró la instancia API 21 antes de abrirlo. Se usaron dos núcleos por argumento temporal; el emulador elevó automáticamente la RAM a 4096 MiB y seleccionó renderizado por software. No se editaron archivos del AVD, borraron sus datos ni modificaron controladores.

| Ejecución | Resultado real |
|---|---|
| Piloto por método de asignación de premio | 1 aprobada, 0 fallidas, errores u omitidas. BUILD SUCCESSFUL en 35 s. |
| Suite completa | 125 aprobadas, 0 fallidas, errores u omitidas. BUILD SUCCESSFUL en 10 min 17 s. |

El backend/base se creó y descartó por invocación. Se conservan logcat, identificación de APK, HTML/XML y resultados en `reports/e2e-combinada/api37/`. Es una ejecución completa API 37; no se afirma que se hayan completado veinte repeticiones de los casos intermitentes ni que GitHub Actions haya ejecutado su workflow.

## Qué falta para cerrar la migración

La preparación inicial se realizó sin dispositivos. La validación API 21 anterior ya utiliza el emulador; API 37 también pasó; la tablet sigue pendiente. Las comprobaciones JVM/host y la compilación no acreditan por sí solas la sincronización de Android ni resuelven la intermitencia de las hojas.

1. Completar la validación física en la tablet. El piloto aislado y la clase de premios ya pasaron en API 21 con proyectos distintos.
2. Ejecutar las clases HU13/HU14, especialmente recreación, cierre/reapertura de hoja y recuperación de red. Conservar el fallo si reaparece y diagnosticarlo; no añadir reintentos ni cambiar el tiempo de pulsación del dispositivo para ocultarlo.
3. Verificar clase, método y paquete; ejecutar regresión de catálogo, detalle de coleccionista y navegación entre pestañas.
4. Las dos suites completas API 21 ya pasaron y sus HTML/XML están conservados. Se comprobó descarte tras éxito, fallo de limpieza del primer piloto y preparación sin dispositivo; falta provocar y verificar expresamente una aserción fallida con la versión final.
5. Matriz local API 21/37 completada para esta versión: dos suites en API 21 y una en API 37. El workflow queda configurado, pendiente de ejecutarse en GitHub.
6. Migrar por grupos los casos con autoLaunch y las esperas restantes según la evidencia. No se afirma que todos los casos creen todos sus datos o que hayan desaparecido todos los sondeos.

```bash
# Piloto: un dispositivo autorizado conectado
./gradlew connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=co.edu.uniandes.vinilos.ui.PrizePickerScreenTest#assigns_prize_to_musician_and_updates_its_detail'

# Toda la suite; con varios equipos, anteponer ANDROID_SERIAL=SERIAL
./gradlew connectedDebugAndroidTest
```

[Guía de requisitos, ejecución en Android Studio, reportes y recuperación](tests/e2e/README.md).
