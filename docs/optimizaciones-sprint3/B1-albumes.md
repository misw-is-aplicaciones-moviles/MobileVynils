# Microoptimizaciones Sprint 3 · Bloque B1 · Álbumes

**Responsable:** Yoimar Moreno.
**Base:** `develop` en `6a8d5c1` (incluye #152).
**Rama:** `perf/s3-b1-albumes`.
**Tareas:** B1-01 (HU07), B1-02 (HU02) y B1-03 (HU09) del plan consolidado de microoptimizaciones del Sprint 3.

Las tres tareas evitan trabajo de render y de carga de imágenes que no cambia lo que ve el usuario. Ninguna cambia contratos de repositorios o servicios, DTOs, navegación, esquema Room, caché ni dependencias.

## Estado

| Tarea | HU | Commit | Pruebas | Medición en 3 emuladores | Conclusión |
|---|---|---|---|---|---|
| B1-01 | HU07 | `60e3b89` | En verde | Hecha | Mejora en solicitudes a Glide y asignaciones; memoria total no concluyente |
| B1-02 | HU02 | `fd56c1c` | En verde | Hecha | Mejora: la cabecera ya no se vuelve a enlazar al publicar |
| B1-03 | HU09 | `4353f6b` | En verde | Hecha | Mejora en tiempo en Main y asignaciones por letra; frames no concluyentes |

Se midió en tres emuladores con versiones de Android y hardware distintos, en lugar de teléfonos físicos. Los detalles están en [Medición](#medición).

## B1-01 · HU07: vista previa de la portada tras una pausa

| Campo | Registro |
|---|---|
| Tarea / HU | B1-01 / HU07 Crear un álbum |
| Problema observado | `AlbumCreateFragment.renderCover` evitaba repetir la misma URL, pero pedía la portada a Glide con cada valor distinto. Escribir una URL de 40 caracteres iniciaba hasta 40 solicitudes, casi todas con URL incompletas que terminaban en error. |
| Implementación | Nueva clase `ui.albums.CoverPreviewLoader`. Espera 400 ms sin cambios y solo entonces llama a Glide; un cambio cancela la espera; una URL vacía limpia la portada al instante; la primera URL de la vista, por ejemplo la restaurada al rotar, se muestra sin esperar. `AlbumCreateFragment` crea uno por vista con `viewLifecycleOwner.lifecycleScope` (línea 69), le pasa la URL en `renderForm` (línea 178) y `showCover` (línea 194) no escribe si la vista ya no existe. |
| Solicitud que cubre | Microoptimización (solicitudes de carga y decodificación transitorias) y corrutinas con cancelación ligada al ciclo de vida. |
| Qué conserva | Validación de la URL, envío del álbum, restauración del formulario, errores por campo y acceso por rol. Guardar no espera a la imagen. |
| Pruebas | `CoverPreviewLoaderTest` (6): primera URL inmediata, solo la última URL tras la pausa, segunda URL pegada durante la espera, vaciado inmediato, URL repetida sin recarga y vista destruida sin carga. E2E `AlbumCreateScreenTest`. |

## B1-02 · HU02: cabecera del detalle sin el álbum completo

| Campo | Registro |
|---|---|
| Tarea / HU | B1-02 / HU02 Consultar el detalle de un álbum |
| Problema observado | `AlbumDetailRow.Header` guardaba el `Album` completo, incluidos los comentarios. Al publicar o resaltar un comentario, DiffUtil veía la cabecera distinta y la volvía a enlazar: dos solicitudes a Glide (portada y avatar), textos y un `DateTimeFormatter` nuevo en cada enlace. |
| Implementación | `AlbumDetailRow.Header` (línea 255 de `AlbumDetailAdapter.kt`) guarda solo lo que dibuja `HeaderHolder`: nombre, portada, género, fecha, artista, descripción, sello, número de canciones y el primer artista. `Album.toHeaderRow()` (línea 300) arma la fila. `HeaderHolder` crea el formateador de fecha una vez (línea 99). |
| Solicitud que cubre | Microoptimización (enlaces y objetos temporales evitados). No reduce la memoria del estado de pantalla: el `UiState` sigue guardando el `Album`. |
| Qué conserva | Identidad de las filas, orden de canciones y comentarios, resaltado del comentario publicado y enlace al artista. Agregar una canción sí vuelve a enlazar la cabecera porque el contador es visible. |
| Pruebas | `AlbumHeaderRowTest` (5): campos proyectados, comentario nuevo sin cambio de cabecera, canción nueva con cambio, cambio de campo visible y álbum sin artistas. E2E `AlbumDetailScreenTest`, `CommentCreateScreenTest` y `TrackCreateScreenTest` (regresión de HU08). |

## B1-03 · HU09: hoja "Comentar álbum" sin redibujar por cada letra

| Campo | Registro |
|---|---|
| Tarea / HU | B1-03 / HU09 Comentar un álbum |
| Problema observado | Cada letra escrita llamaba a `render` completo: `setImageResource` en las cinco estrellas, `getString` para la etiqueta de valoración, errores, `setBackgroundResource` del campo y los botones. Escribir no cambia ninguna de esas partes. |
| Implementación | Nueva clase `ui.albums.CommentSheetRedraw`. Compara el último estado dibujado con el nuevo y decide qué partes redibujar: etiqueta (valoración), estrellas (valoración o envío), errores, error de envío y botones (envío). Una vista nueva dibuja todo. `CommentCreateBottomSheet.render` (línea 99) la consulta; la lista de estrellas se arma una vez por vista (línea 52) y el estado dibujado se reinicia al crear y destruir la vista. |
| Solicitud que cubre | Microoptimización y menos asignaciones temporales. No demuestra por sí sola un cambio de RAM total. |
| Qué conserva | `isSelected` y descripción de cada estrella, anuncio de la valoración, errores independientes, estados de envío, autor elegido y restauración. |
| Pruebas | `CommentSheetRedrawTest` (6): vista nueva, 26 letras sin redibujar ninguna parte, otra valoración, inicio y fin del envío, error de validación y envío fallido. E2E `CommentCreateScreenTest`. |

## Verificación

**Estática y unitarias**, sobre `4353f6b` el 21/11/2026:

```bash
./gradlew spotlessCheck detekt lintDebug testDebugUnitTest
```

Resultado: 582 pruebas unitarias exitosas, sin fallos ni omisiones (17 nuevas de este bloque). Lint: 0 errores y 73 advertencias, las mismas de la base.

**E2E**, con la semilla de BackVynils restaurada y la app reinstalada antes de cada ejecución:

```bash
adb shell am instrument -w -r --no-window-animation \
  -e class co.edu.uniandes.vinilos.ui.AlbumCreateScreenTest,co.edu.uniandes.vinilos.ui.AlbumDetailScreenTest,co.edu.uniandes.vinilos.ui.CommentCreateScreenTest,co.edu.uniandes.vinilos.ui.TrackCreateScreenTest,co.edu.uniandes.vinilos.ui.AlbumListScreenTest \
  co.edu.uniandes.vinilos.test/co.edu.uniandes.vinilos.testing.VinilosTestRunner
```

| Dispositivo | Resultado | Duración |
|---|---|---|
| Emulador Medium Phone, Android API 37 | 30 de 30 exitosas | 367 s |
| Emulador Vinilos_API_21, Android 5.0 | 30 de 30 exitosas | 181 s |

`AlbumListScreenTest` y `TrackCreateScreenTest` cubren la regresión de HU01 y HU08, que no reciben cambios nuevos.


## Medición

### Dispositivos y condiciones

Se midió en tres emuladores, con una versión de Android y un perfil de hardware distintos en cada uno, en lugar de teléfonos físicos.

| Dispositivo | Android | Perfil | Núcleos | RAM visible (`MemTotal`) | Pantalla |
|---|---|---|---|---|---|
| Vinilos_API_21 | 5.0 (API 21) | Pixel 4 | 4 | 2,0 GB | 1080 × 2280 |
| Vinilos_API_30_Low | 11 (API 30) | Pixel, gama baja | 2 | 1,9 GB | 1080 × 1920 |
| Medium Phone API 37 | API 37 | Medium Phone | 6 | 3,8 GB | 1080 × 2400 |

- **Versiones comparadas:** antes, `6a8d5c1`; después, `4353f6b`. A las dos se les aplicó el mismo parche temporal de contadores ([`evidencias-B1/instrumentacion-temporal.patch`](evidencias-B1/instrumentacion-temporal.patch)), que nunca entra en un commit. Los hashes de las APK medidas empiezan por `5080c6e88e013d5a` (antes) y `249a5921925b1f65` (después).
- **Contadores:** cada render de la pantalla y cada enlace de la cabecera registran en logcat su tiempo en Main y los objetos y bytes que asigna su hilo, con `Debug.getThreadAllocCount` y `Debug.getThreadAllocSize`. También se cuenta cada solicitud de portada a Glide, cada inicio de carga real de Glide (etiqueta `Engine` en modo detallado) y cada redibujo de las cinco estrellas. Las asignaciones de todo el proceso (`Debug.getGlobalAllocCount` y `Debug.getGlobalAllocSize`) se miden entre el primer y el último registro de cada ventana.
- **Memoria:** `dumpsys meminfo` al final de cada ventana: PSS total y heap de Java (en API 21, PSS de "Dalvik Heap"). Es una instantánea, no un pico.
- **Frames:** percentil 90 de `dumpsys gfxinfo` durante la ventana.
- **Condiciones iguales:** antes de cada repetición se restauró la semilla de BackVynils y se borraron los datos de la app (`pm clear`), así que Room y la caché de Glide empezaron vacíos. Se desactivaron las animaciones y el teclado en pantalla, para que adb entregue exactamente las mismas teclas. Se hicieron tres repeticiones por versión y por dispositivo, y se informa la mediana con el rango entre paréntesis.
- **Recorrido automático:** [`evidencias-B1/measure.sh`](evidencias-B1/measure.sh) maneja la app con adb y uiautomator. Los resúmenes por dispositivo están en `evidencias-B1/resumen-*.txt`.

### Escenarios

| Ventana | Recorrido |
|---|---|
| URL escrita (B1-01) | En "Nuevo álbum" se escribe la URL de la portada de "Buscando América" (71 caracteres), cuatro caracteres por llamada a adb. El ritmo resultante es de 95 a 104 ms por carácter en API 21, de 176 a 229 en API 30 y de 64 a 156 en API 37. Termina 3 s después del último carácter. |
| URL pegada (B1-01) | En un formulario nuevo se envía la misma URL en una sola llamada. Termina 3 s después. |
| Comentario escrito (B1-03) | En "Comentar álbum" se elige "Manolo Bellon" y 4 estrellas; luego se escriben 30 caracteres, uno por llamada a adb. |
| Publicación (B1-02) | Se toca "Publicar comentario" y se desplaza el detalle de vuelta al inicio, con la cabecera visible. |

### B1-01 · HU07: vista previa de la portada

**URL escrita:**

| Métrica | API 21 antes | API 21 después | API 30 antes | API 30 después | API 37 antes | API 37 después |
|---|---|---|---|---|---|---|
| Solicitudes de portada a Glide | 71 (71-71) | 1 (1-1) | 71 (71-71) | 18 (18-18) | 71 (71-71) | 3 (2-3) |
| Cargas iniciadas por Glide | 70 (70-70) | 0 (0-0) | 70 (70-70) | 17 (17-17) | 71 (71-71) | 2 (1-2) |
| Asignaciones del proceso (objetos) | 88 439 (87 498-90 417) | 34 032 (31 893-35 060) | 90 497 (89 356-97 030) | 63 551 (59 765-63 892) | 119 313 (118 426-162 920) | 38 812 (38 455-41 077) |
| Asignaciones del proceso (KB) | 6 423 (6 398-6 823) | 1 290 (1 218-1 306) | 7 284 (7 227-7 942) | 4 417 (4 312-4 478) | 8 288 (8 128-11 174) | 1 246 (1 209-1 459) |
| Tiempo de render en Main (ms, suma) | 61,0 (52,4-63,4) | 27,5 (23,2-52,3) | 151,6 (136,7-185,9) | 140,4 (106,7-155,7) | 252,1 (249,6-428,8) | 125,5 (123,2-127,5) |
| Asignaciones del render en Main (objetos) | 15 121 (15 056-15 164) | 18 973 (18 902-18 973) | 18 721 (18 671-18 772) | 6 479 (6 479-6 479) | 22 628 (22 436-22 656) | 6 699 (6 699-6 719) |
| PSS total (KB) | 76 299 (76 054-76 589) | 74 958 (72 455-75 615) | 102 749 (91 643-103 890) | 87 989 (87 306-88 032) | 87 594 (86 662-88 005) | 90 429 (90 286-90 854) |

**URL pegada:**

| Métrica | API 21 antes | API 21 después | API 30 antes | API 30 después | API 37 antes | API 37 después |
|---|---|---|---|---|---|---|
| Solicitudes de portada a Glide | 71 (71-71) | 1 (1-1) | 71 (71-71) | 1 (1-1) | 71 (71-71) | 2 (1-2) |
| Asignaciones del proceso (objetos) | 52 741 (43 172-55 834) | 35 619 (34 562-36 003) | 40 751 (28 812-43 384) | 19 106 (17 636-21 720) | 112 229 (105 288-151 662) | 35 761 (35 108-39 770) |
| Asignaciones del proceso (KB) | 3 845 (3 232-4 056) | 1 317 (1 301-1 345) | 3 086 (2 204-3 470) | 622 (581-695) | 7 610 (6 996-10 815) | 1 088 (1 084-1 370) |
| PSS total (KB) | 83 540 (81 947-84 809) | 79 772 (72 624-82 531) | 97 549 (96 432-98 417) | 92 440 (92 271-93 035) | 91 312 (90 555-95 043) | 98 900 (98 709-99 183) |

**Conclusión:** mejora en solicitudes y asignaciones.
- Antes, cada carácter distinto de la URL era una solicitud a Glide, casi siempre con una URL incompleta. Después queda una sola solicitud cuando la escritura no se detiene 400 ms.
- En el emulador de gama baja, adb tarda más de 400 ms en cada grupo de cuatro caracteres, así que cada grupo dispara su carga: 18 en lugar de 71. Una persona que escribe más despacio que la pausa obtiene menos beneficio.
- Las asignaciones de todo el proceso bajan entre 30 % y 68 % al escribir, y entre 32 % y 68 % al pegar.
- En API 21, el render en Main asigna 25 % más objetos, porque cada tecla lanza y cancela una corrutina. El total del proceso baja igual, porque desaparecen las cargas de Glide.
- La PSS es una instantánea y sube o baja según el dispositivo: **no concluyente**.

### B1-02 · HU02: cabecera del detalle al publicar un comentario

| Métrica | API 21 antes | API 21 después | API 30 antes | API 30 después | API 37 antes | API 37 después |
|---|---|---|---|---|---|---|
| Enlaces de la cabecera | 1 (1-1) | 0 (0-0) | 1 (1-1) | 0 (0-0) | 1 (1-1) | 0 (0-0) |
| Tiempo de esos enlaces en Main (ms) | 3,24 (1,12-3,49) | 0 | 4,37 (2,18-8,42) | 0 | 12,77 (9,13-14,89) | 0 |
| Objetos asignados por esos enlaces | 247 (247-248) | 0 | 366 (366-366) | 0 | 388 (388-394) | 0 |
| Render del detalle en Main (ms) | 0,29 (0,28-0,32) | 0,40 (0,34-0,40) | 0,52 (0,32-0,55) | 0,91 (0,66-2,56) | 1,61 (1,56-1,65) | 1,18 (1,07-1,28) |
| Heap de Java (KB) | 24 426 (24 302-24 502) | 23 806 (23 750-23 902) | 18 936 (18 880-19 040) | 18 056 (18 008-18 152) | 20 640 (20 512-20 932) | 18 184 (18 152-18 284) |

**Conclusión:** mejora.
- Publicar un comentario ya no vuelve a enlazar la cabecera en ninguno de los tres dispositivos. Se ahorran entre 3 y 13 ms en Main, entre 247 y 394 objetos y las dos solicitudes a Glide de la portada y del avatar.
- Construir la proyección de la cabecera en cada render cuesta menos de 0,4 ms. Las diferencias del render del detalle son menores a un milisegundo y van en ambos sentidos: **no concluyente**.
- No se comparan las asignaciones de toda la ventana, porque sin el enlace de la cabecera la ventana termina antes y la comparación quedaría sesgada.

### B1-03 · HU09: escribir un comentario con la valoración fija

| Métrica | API 21 antes | API 21 después | API 30 antes | API 30 después | API 37 antes | API 37 después |
|---|---|---|---|---|---|---|
| Redibujos de las 5 estrellas | 30 (30-30) | 0 (0-0) | 30 (30-30) | 0 (0-0) | 30 (30-30) | 0 (0-0) |
| Tiempo de render en Main (ms, suma de 30 letras) | 23,6 (22,3-24,1) | 1,7 (1,6-1,8) | 51,4 (40,4-51,5) | 5,3 (2,4-5,6) | 69,0 (43,6-93,5) | 8,4 (5,6-8,6) |
| Objetos asignados por el render | 4 260 (4 260-4 260) | 120 (120-120) | 6 330 (6 330-6 330) | 120 (120-120) | 8 730 (8 730-8 730) | 180 (180-180) |
| Asignaciones del proceso (objetos) | 6 902 (6 865-6 922) | 2 759 (2 723-3 276) | 9 917 (9 916-9 919) | 3 745 (3 736-3 896) | 15 549 (15 179-15 594) | 6 387 (6 346-6 455) |
| Asignaciones del proceso (KB) | 296 (295-298) | 111 (108-117) | 339 (339-339) | 122 (120-125) | 540 (536-542) | 214 (214-215) |
| Heap de Java (KB) | 31 438 (22 006-31 638) | 30 874 (30 870-30 954) | 22 660 (22 464-23 016) | 21 384 (21 368-21 436) | 24 552 (24 392-24 832) | 22 092 (22 044-22 136) |
| Frames, percentil 90 (ms) | 16,0 (15,7-16,4) | 18,6 (18,6-19,3) | 23 (21-27) | 27 (26-31) | 85 (77-117) | 200 (65-200) |

**Conclusión:** mejora en tiempo en Main y asignaciones.
- Con la valoración fija, escribir ya no redibuja las estrellas.
- El render por letra baja entre 88 % y 93 % en tiempo y entre 97 % y 98 % en objetos.
- Las asignaciones del proceso bajan alrededor de 60 %.
- El percentil 90 de los frames no mejora y empeora levemente en los tres emuladores; en API 37 los rangos se superponen. En estos emuladores `gfxinfo` marca casi todos los frames como lentos, aun cuando el render dura menos de un milisegundo. Esos tiempos parecen depender de la composición del emulador y de los procesos de `adb input`, no del render: **no concluyente**. Un teléfono físico o System Trace lo confirmaría.

## Limitaciones

- Son emuladores, no teléfonos físicos. Los tiempos absolutos no representan un dispositivo real; las comparaciones son válidas entre versiones en el mismo emulador.
- La escritura se simula con adb. Su ritmo depende del dispositivo y define cuántas cargas de portada se evitan.
- Los contadores agregan una línea de logcat por render a las dos versiones por igual. Se miden con el parche temporal, no en la APK que se entrega.
- La memoria total (PSS) y el heap son instantáneas al final de cada ventana, no picos, y varían entre repeticiones.
- La pausa de 400 ms retrasa la vista previa al escribir. Es un cambio de presentación: no altera la validación ni lo que se envía.
- Los números de línea corresponden a `4353f6b`.
