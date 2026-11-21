# Medición controlada B2 · HU04 y HU15

**Fecha:** 21/11/2026. **Entorno:** `Medium_Phone_API_37.0`, Android API 37, x86_64, 6 CPU virtuales y 4 005 304 kB de RAM del emulador. Tres repeticiones por versión, APK debuggables, animaciones desactivadas, sin GC forzado. Orden: antes/después, después/antes, antes/después.

## Comparación aislada

Ambas variantes parten de `14e53d2`, con B1 integrado. En «antes» se restauran únicamente `PerformerDetailAdapter.kt` y `PerformerAlbumPickerUiState.kt` desde `6a8d5c1`; en «después» se mantienen las versiones B2. La instrumentación temporal es idéntica en ambas; los APK de pruebas tienen el mismo SHA-256. Las sondas no forman parte de producción. No se necesita backend ni se modifican datos compartidos.

## HU04 · B2-01 y B2-02

B2-01 monta un RecyclerView con el adaptador real, espera cada commit de DiffUtil y dos callbacks de frame para completar el layout. Tras la carga inicial agrega 20 álbumes de uno en uno y actualiza el contador del artista, sin cambiar los campos visibles de cabecera. El contador se incrementa en el bind real.

B2-02 crea un holder real por fila y ejecuta 100 reenlaces de cada uno, incluyendo el coste de construcción. Hay calentamiento previo de 20 reenlaces excluidos. Es una carga controlada de reutilización, no un scroll humano ni un ensayo de jank.

| Medida | Antes, mediana (rango) | Después, mediana (rango) |
|---|---:|---:|
| Binds de cabecera / 20 cambios | 20 (20–20) | 0 (0–0) |
| Formateadores / 100 reenlaces por holder | 200 (200–200) | 2 (2–2) |
| Listeners / 100 reenlaces por holder | 300 (300–300) | 3 (3–3) |
| CPU de Main / ensayo de holders (ms) | 58.718 (56.197–59.037) | 54.675 (54.374–55.033) |
| Bytes asignados del proceso / ensayo de holders | 1278272 (1278272–1278272) | 1179968 (1179968–1179968) |

La eliminación de binds ajenos y la reducción de creaciones se confirman en este recorrido. La mediana de CPU del ensayo de holders baja un 6,9 % y los bytes asignados del proceso un 7,7 %. Estos porcentajes no representan toda la app.

## HU15 · B2-03

Cada muestra ejecuta 500 operaciones, con 200 de calentamiento excluidas. Una operación copia el estado, genera las opciones con el código real y lee la selección las veces indicadas. Incluye el coste anticipado de construir el estado. Catálogos sintéticos de 100 y 1 000 álbumes; selección al final, excepto en `reads0`, sin selección ni lecturas.

| Álbumes / consulta / lecturas | CPU antes (ms), mediana (rango) | CPU después (ms), mediana (rango) | Cambio |
|---|---:|---:|---:|
| `100_all_reads1` | 56.931 (46.693–82.479) | 70.864 (63.004–77.809) | +24.5 % |
| `100_Album9_reads10` | 95.922 (95.449–105.316) | 86.809 (85.625–100.950) | -9.5 % |
| `100_missing_reads1` | 14.981 (14.663–17.024) | 14.329 (14.256–14.523) | -4.4 % |
| `100_all_reads0` | 6.336 (5.573–6.352) | 5.235 (4.749–6.009) | -17.4 % |
| `1000_all_reads1` | 60.808 (59.972–75.136) | 63.027 (51.574–71.629) | +3.6 % |
| `1000_Album9_reads10` | 246.606 (246.487–249.517) | 206.793 (193.132–219.553) | -16.1 % |
| `1000_missing_reads1` | 154.700 (153.234–161.670) | 158.774 (154.623–159.237) | +2.6 % |
| `1000_all_reads0` | 44.198 (42.460–49.117) | 39.741 (39.206–40.631) | -10.1 % |

`all`: consulta vacía; `Album9`: búsqueda por nombre; `missing`: sin coincidencias. Un cambio positivo es más tiempo de CPU, es decir, regresión en esa muestra.

**Veredicto B2-03: mixto.** Con diez lecturas de selección, la mediana de CPU baja un 9,5 % en 100 álbumes y un 16,1 % en 1 000. La consulta vacía con una lectura aumenta un 24,5 % y un 3,6 %, respectivamente. No se declara una mejora general de velocidad del selector. Los bytes asignados bajan en siete de ocho escenarios; sin selección ni lecturas, con 1 000 álbumes, suben un 6,4 %. Se conserva y documenta ese compromiso sin cambiar la implementación para favorecer el ensayo.

## Límites

Tres repeticiones no establecen significación estadística. Los bytes asignados de ART incluyen otros hilos del proceso y no miden PSS, heap retenido ni fugas. Los tiempos son del bloque de código, no latencia E2E ni porcentaje de CPU App. Las imágenes tienen URL vacía. El ensayo no evalúa red, ANR, jank, fluidez percibida ni comportamiento físico. El perfilamiento del conjunto integrado en tres teléfonos sigue pendiente.

## Evidencias y reproducción

Los seis logs originales, [metadatos y hashes](results-api37/metadata.json) y [valores individuales, medianas y rangos](results-api37/summary.json) se publican en `results-api37/`. No son capturas reconstruidas manualmente. Los APK temporales están localmente en `reports/b2-medicion-emulador/`, ignorado por Git, y pueden reconstruirse.

Requisitos: Java 17, Python 3, SDK Android, emulador dedicado API 23 o superior, `local.properties` configurado. No necesita BackVynils. El script instala APK de medición y borra los datos de Vinilos solo en el emulador indicado.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
bash docs/optimizaciones-sprint3/evidencias-B2/build.sh /tmp/b2-apks
python3 docs/optimizaciones-sprint3/evidencias-B2/run.py \
  emulator-5554 /tmp/b2-apks /tmp/b2-resultados
python3 docs/optimizaciones-sprint3/evidencias-B2/analyze.py /tmp/b2-resultados
```

Comprobar el serial con `adb devices`; `ADB` permite indicar otra ruta. `build.sh` trabaja en un worktree temporal y lo elimina al terminar, sin editar la rama de entrega. No publicar estos APK como release.

`prepare.py` añade contadores a los puntos reales de bind, creación de formateador e instalación de listener e instala `B2MeasurementTest.kt.txt` en el source set temporal `androidTest`. Las sondas añaden sobrecoste. `run.py` alterna versiones y conserva la salida original; `analyze.py` no elimina resultados desfavorables.
