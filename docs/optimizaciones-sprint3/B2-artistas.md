# B2 · Microoptimizaciones de artistas (HU04 / HU15)

**Responsable:** @ManfredAriel91122. **Fecha:** 21/11/2026.
**Base:** develop `6a8d5c1`. **Rama:** `perf/s3-b2-artistas`.
**Commit de implementación:** `910011645ddaed28322ac10db2d91cbbbc294f68`.
**Estado:** implementación y validación funcional, con medición controlada antes/después en emulador API 37. Perfilamiento integrado en tres teléfonos físicos pendiente.

## 1. Cambios implementados

### B2-01 · Cabecera independiente de las relaciones del artista

**Archivo:** `app/src/main/java/co/edu/uniandes/vinilos/ui/artists/PerformerDetailAdapter.kt`.
**Componentes:** `PerformerDetailRow.Header`, `toHeaderRow`, `submitDetail`, `HeaderHolder`.

Antes Header conservaba PerformerDetail completo. Un cambio en álbumes, integrantes, contadores de catálogo o referencias de premios cambiaba la igualdad de la fila aunque su contenido visible permaneciera igual.

Ahora Header contiene nombre, URL de imagen, tipo, fecha y descripción. Solo esos campos participan en su igualdad. Las otras secciones conservan sus modelos, contadores y resaltados. DiffUtil puede mantener la cabecera sin volver a enlazarla cuando únicamente cambian las relaciones.

**Comprobación:** `PerformerHeaderRowTest` verifica igualdad de la cabecera ante cambios de relaciones y diferencias ante cambios de cada campo visible. Es una comprobación del contrato de contenido, no una medición del número real de binds ni de frames.

**Beneficio esperado:** menos trabajo de binding y comparaciones de contenido no relacionado. El estado del detalle todavía conserva el modelo completo; no se afirma que se haya eliminado su memoria ni que cada bind hubiera causado una petición HTTP nueva.

### B2-02 · Formateadores y listeners por holder

**Archivo:** `PerformerDetailAdapter.kt`.
**Componentes:** `AwardHolder`, `HeaderHolder`, `AlbumActionHolder`, `PrizeActionHolder`, `AlbumHolder`, `onViewRecycled`.

Los holders de fecha crean un DateTimeFormatter por instancia con el Locale vigente al construirse, en lugar de uno por bind. Las cabeceras accesibles estables se configuran al crear el holder.

Los botones de agregar álbum y premio tienen un listener por holder. El botón de premios consulta el valor actualizado de `canRetry`. La fila de álbum usa el ID del elemento enlazado actualmente y lo elimina al reciclarse; no captura el álbum de un bind anterior.

No se cambió la política ni el tamaño de la caché de Glide. No se agregó limpieza masiva de imágenes ni se modificó la fila de miembros o su animación.

**Beneficio esperado:** menos asignaciones de formateadores/listeners en recorridos con binds repetidos. Un formatter por holder también se crea si ese holder nunca vuelve a enlazarse; el beneficio neto debe medirse bajo el recorrido real. Los cambios normales de configuración recrean la interfaz y sus holders.

### B2-03 · Opciones de HU15 en una pasada y selección por estado

**Archivo:** `app/src/main/java/co/edu/uniandes/vinilos/viewmodels/PerformerAlbumPickerUiState.kt`.
**Componentes:** `selectedAlbum`, `buildArtistAlbumOptions`.

Antes se creaba una lista filtrada y después otra de opciones. Ahora un recorrido filtra y agrega las opciones a la lista de salida, preservando orden, búsqueda por álbum/intérprete y bloqueo de álbum propio/ajeno.

El álbum seleccionado se deriva una vez al construir cada estado inmutable, sin `lazy(NONE)` ni delegado compartido entre Main y Default. Cada `copy` vuelve a calcularlo a partir del catálogo y el ID actuales. Las lecturas repetidas del mismo estado no repiten la búsqueda.

**Compromiso:** el cálculo de selección es anticipado: también se realiza en un estado que luego no se lea. No se declara que el coste mejore en todo patrón de uso; se debe medir el recorrido de búsqueda/selección real.

El cálculo de opciones continúa en el dispatcher existente del ViewModel. No se cambió GET previo, POST, comprobación posterior, invalidación, restauración ni el tratamiento del resultado incierto.

**Comprobación:** `PerformerAlbumOptionsTest` verifica orden y bloqueos, búsqueda y vacío, y selección recalculada ante copias con otro catálogo/ID. Se mantienen las pruebas existentes del ViewModel y las E2E.

## 2. Alcance y reglas entre bloques

Solo cambian dos archivos de producción del bloque B2. Se agregan dos clases JVM de pruebas (cinco casos). No se editaron AppContainer, repositorios, DAO, DTO, archivos de premios ni NocaseOrder.

Las HU03, HU01 y HU11 se comprueban por regresión cuando corresponda; no se presentan como nuevas microoptimizaciones. HU12 se beneficia del detalle compartido y mantiene su selector sin cambios.

## 3. Validación

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew \
  spotlessCheck detekt lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

**Resultado:** correcto. Suite JVM: **570 pruebas, 0 fallos, 0 errores y 0 omitidas**, incluidas las cinco nuevas. Lint, formato y detekt correctos como tareas; que Lint termine correctamente no significa ausencia de advertencias.

Regresión seleccionada en emuladores API21/API37, con runner del proyecto y BackVynils aislado:

- PerformerAlbumPickerScreenTest (HU15).
- PerformerDetailScreenTest (HU04).
- BandMemberPickerScreenTest (HU12).
- PrizePickerScreenTest (HU14).
- PrizeCreateScreenTest (HU13).
- PerformerListScreenTest (HU03, solo regresión).

**Resultados instrumentados:**

| Clase | API 21 | API 37 |
|---|---:|---:|
| PerformerAlbumPickerScreenTest | 9/9 | 9/9 |
| PerformerDetailScreenTest | 6/6 | 6/6 |
| BandMemberPickerScreenTest | 14/14 | 14/14 |
| PrizePickerScreenTest | 5/5 | 5/5 |
| PrizeCreateScreenTest | 6/6 | 6/6 |
| PerformerListScreenTest | 11/11 | 11/11 |
| **Total seleccionado** | **51/51** | **51/51** |

Sin fallos ni omisiones en ambas ejecuciones. No se presenta esta selección como la suite completa del proyecto. Emuladores Medium_Phone_API_37.0 y HU14_API_21; base PostgreSQL17 tmpfs recién recreada y sembrada por API; URL del APK 127.0.0.1:3000 mediante adb reverse hacia el puerto aislado 3007. El arranque frío de API21 resolvió un snapshot desconectado; no se cambió la app para ello.

Los comandos/resultados originales se conservan localmente en `reports/b2-artistas/` (ignorado por Git). Los informes destinados al equipo deben publicarse y enlazarse: una ruta local no es evidencia pública.

## 4. Medición antes/después y perfilamiento físico pendiente

### 4.1. Resultados en emulador API 37

Se publican los scripts y las seis ejecuciones originales en [evidencias-B2/README.md](evidencias-B2/README.md), con las condiciones, valores individuales, medianas, rangos y hashes de APK.

- B2-01: binds reales de cabecera, **20 → 0** al modificar solo las relaciones en 20 actualizaciones.
- B2-02: formateadores **200 → 2** y listeners **300 → 3** en 100 reenlaces por holder, incluyendo construcción; mediana de CPU del bloque **58,718 → 54,675 ms**.
- B2-03: **resultado mixto**; diez lecturas de selección mejoran las medianas en los dos tamaños, pero hay regresiones en otros escenarios. No se declara una mejora general del selector.

Es un experimento controlado de componentes con datos sintéticos, no perfilamiento E2E ni evidencia física. Los bytes asignados del proceso no son memoria residente. No permite afirmar menos jank, fugas o ANR.

### 4.2. Cierre pendiente en teléfonos físicos

Esta implementación no se acompaña aún de mediciones físicas. No afirma ahorro de MB, reducción porcentual de CPU, ausencia de fugas ni corrección de un ANR observado.

Para cerrar el bloque de desempeño:

1. Construir APK base de `6a8d5c1` y APK del commit final; registrar hash/URL/build de ambos y conservar las mismas condiciones.
2. Perfilar en tres teléfonos físicos identificados, con semilla y catálogos equivalentes. Tres repeticiones por caso como propuesta: valores individuales, mediana y rango.
3. B2-01: agregar integrante/álbum y volver; contar binds de cabecera y trazar Main/frames. B2-02: mismo scroll y binds para comparar asignaciones de formateadores/listeners.
4. B2-03: consultas y selecciones iguales con catálogos pequeño/grande; medir asignaciones y tiempo total en Default y Main. No tratar una reducción estructural de listas como una medición de heap retenido.
5. Conservar los resultados incluso si no hay mejora concluyente o aparece una regresión. No comparar un antes con GC forzado y un después sin GC, o caché fría contra caliente.
6. Comprobar restauración, accesibilidad y regresión del conjunto integrado.

| Teléfono físico / escenario | Base | Optimizado | Resultado |
|---|---|---|---|
| 1 / por registrar | No medido | No medido | Pendiente |
| 2 / por registrar | No medido | No medido | Pendiente |
| 3 / por registrar | No medido | No medido | Pendiente |

### 4.3. Separación de HU14 y validación de integración

La corrección de foco de `PrizePickerScreenTest` se retiró de la diferencia de B2 mediante `14e53d2`. Se conserva en la rama independiente `fix/hu14-date-sheet-focus`, commit `dfa4ea1`, para revisión separada de B4 en la [PR #156](https://github.com/misw-is-aplicaciones-moviles/MobileVynils/pull/156). El archivo de HU14 de esta PR coincide con `develop`.

Tras integrar B1 se verificaron 587 unitarias y los controles de calidad. La regresión previa a separar HU14 obtuvo 81/81 en API 21 y 80/81 en API 37, con un fallo intermitente de foco de HU14. La corrección separada pasó diez repeticiones del caso en API 37 y la clase completa 5/5 en ambas APIs sobre la rama integrada. Esos resultados no se atribuyen al estado final de B2; no se han repetido las 81 E2E completas tras la separación.

## 5. Trazabilidad de entrega

El commit de implementación es `910011645ddaed28322ac10db2d91cbbbc294f68`. En el PR/Wiki enlazar ese commit y las líneas de Header/toHeaderRow, listeners, formatters y constructor de opciones. Los archivos/metadatos de ejecución indican el SHA base y el hash del APK ensayado.

Estas son microoptimizaciones de CPU/asignaciones. La carga principal de datos y el filtrado del selector ya utilizaban corrutinas: no se atribuye a este bloque una nueva migración de red fuera de Main ni la resolución de premios reservada a B4.

### Ubicaciones del estado de trabajo

Las líneas siguientes corresponden al commit de implementación indicado arriba.

| Tarea | Archivo / línea |
|---|---|
| B2-01 · proyección | [PerformerDetailAdapter.kt:546](../../app/src/main/java/co/edu/uniandes/vinilos/ui/artists/PerformerDetailAdapter.kt#L546) |
| B2-02 · listeners | [PerformerDetailAdapter.kt:280](../../app/src/main/java/co/edu/uniandes/vinilos/ui/artists/PerformerDetailAdapter.kt#L280) |
| B2-02 · fechas | [PerformerDetailAdapter.kt:305](../../app/src/main/java/co/edu/uniandes/vinilos/ui/artists/PerformerDetailAdapter.kt#L305) |
| B2-02 · fila reciclada | [PerformerDetailAdapter.kt:377](../../app/src/main/java/co/edu/uniandes/vinilos/ui/artists/PerformerDetailAdapter.kt#L377) |
| B2-03 · selección | [PerformerAlbumPickerUiState.kt:26](../../app/src/main/java/co/edu/uniandes/vinilos/viewmodels/PerformerAlbumPickerUiState.kt#L26) |
| B2-03 · opciones | [PerformerAlbumPickerUiState.kt:46](../../app/src/main/java/co/edu/uniandes/vinilos/viewmodels/PerformerAlbumPickerUiState.kt#L46) |
