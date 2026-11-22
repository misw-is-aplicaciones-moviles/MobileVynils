# Reporte de perfilamiento de desempeño: Sprint 3 — HUAWEI P20

## 1. Objetivo

Comparar CPU App y memoria Total en diez historias, antes y después de las optimizaciones, sobre el mismo teléfono físico. Esta contribución cubre únicamente el Huawei P20; no presenta resultados de otros dispositivos.

## 2. Historias evaluadas

- **HU02:** Consultar detalle de álbum.
- **HU04:** Consultar detalle de artista.
- **HU05:** Consultar listado de coleccionistas.
- **HU07:** Crear álbum.
- **HU09:** Comentar álbum.
- **HU10:** Agregar artista favorito.
- **HU12:** Agregar músico a banda.
- **HU13:** Crear premio.
- **HU14:** Asignar premio a artista.
- **HU15:** Agregar álbum a artista.

## 3. Entorno

Huawei P20 EML-L09, Android 10 / API 29, Kirin 970 de ocho núcleos, RAM instalada 4 GB, pantalla 1080 × 2244, densidad física 480 con override 408 y fuente 0.85. USB conectado durante las pruebas. Backend local exclusivo en el puerto 3013, alcanzado mediante `adb reverse tcp:3000 tcp:3013`.

## 4. Metodología

- Antes: `develop-bo2`, commit `6a8d5c15d6ff32b2355ecc7c7eb90da147680b65`.
- Después: `origin/develop`, commit `e69c12b902b7b6998dfe489a58cc69d1a9056463`.
- Tres pasadas completas antes y tres después; diez historias por pasada: 60 escenarios. APK debuggable en ambos casos. Solo se igualaron los metadatos de versión para permitir reinstalación.
- Datos de app y base aislada reiniciados por pasada. Mismo rol, fixtures y orden; proceso conservado entre historias, sin GC manual. Las imágenes usan URLs de la semilla.
- Preparación y verificación por jerarquía fuera de las ventanas. Dentro de cada ventana: toques, desplazamientos y escritura por ADB, con pausas iguales y 10 s de reposo final.
- Memoria: Total de `MEMORY_USAGE`, KB/1024. CPU: serie App calculada desde los contadores `CPU_USAGE` con la normalización y los límites de Studio; se conserva también su representación entera en los CSV. No se usa PSS como sustituto.
- Mediana y rango mínimo–máximo de los tres máximos de cada HU. Tiempo E2E no medido. System Trace y Heap Dump no ejecutados. Los picos incluyen todo el flujo y la escritura.

Los archivos de cada pasada conservan PID, temperatura/batería, SHA-256 del APK y ventanas con el reloj exacto del SDK de Profiler. El catálogo es pequeño; esta comparación de versiones no aísla el efecto de cada cambio ni permite extrapolar a catálogos grandes. Los pilotos y las validaciones descartadas no se incluyen.

## 5. Resultados

Valores: **mediana (mínimo–máximo)**. Δ negativo indica reducción del máximo observado; no demuestra por sí solo mejora perceptible.

| HU | Total antes MB | Total después MB | Δ memoria | CPU App antes % | CPU App después % | Δ CPU |
|---|---:|---:|---:|---:|---:|---:|
| HU02 | 212.1 (187.5–217.1) | 187.8 (186.7–191.4) | -11.4% | 26.6 (23.2–27.3) | 24.4 (23.9–26.9) | -8.5% |
| HU04 | 180.6 (169.4–183.4) | 165.8 (155.4–175.7) | -8.2% | 23.9 (22.5–24.0) | 23.8 (22.9–27.5) | -0.5% |
| HU05 | 178.0 (169.5–185.2) | 171.9 (167.6–188.0) | -3.4% | 25.5 (22.2–27.0) | 22.4 (21.6–25.5) | -11.9% |
| HU07 | 194.3 (193.0–197.3) | 188.1 (185.8–188.7) | -3.2% | 23.4 (23.2–25.6) | 25.5 (22.8–29.7) | +8.8% |
| HU09 | 203.5 (199.2–217.8) | 211.1 (206.4–214.4) | +3.7% | 20.8 (20.5–24.7) | 19.5 (18.1–20.9) | -6.2% |
| HU10 | 217.3 (217.1–221.6) | 201.0 (195.6–227.6) | -7.5% | 26.4 (24.1–33.5) | 24.5 (23.6–25.8) | -7.2% |
| HU12 | 197.8 (192.3–197.9) | 198.5 (190.5–206.1) | +0.4% | 25.9 (23.1–26.9) | 24.4 (21.7–26.4) | -6.1% |
| HU13 | 188.8 (186.1–197.4) | 192.5 (181.7–192.6) | +2.0% | 21.2 (20.5–22.8) | 21.2 (18.1–21.4) | -0.5% |
| HU14 | 192.3 (188.2–193.0) | 188.7 (185.8–189.9) | -1.9% | 28.4 (26.9–32.5) | 26.4 (22.4–27.0) | -7.0% |
| HU15 | 200.0 (199.1–200.2) | 194.1 (193.7–199.3) | -2.9% | 30.2 (26.2–35.7) | 27.4 (25.0–28.5) | -9.3% |

![Memoria](graficas/memoria.png)

![CPU](graficas/cpu.png)

### 5.1. Lectura por historia

- **HU02:** memoria 212.1 → 187.8 MB (-11.4%); CPU App 26.6 → 24.4% (-8.5%).
- **HU04:** memoria 180.6 → 165.8 MB (-8.2%); CPU App 23.9 → 23.8% (-0.5%).
- **HU05:** memoria 178.0 → 171.9 MB (-3.4%); CPU App 25.5 → 22.4% (-11.9%).
- **HU07:** memoria 194.3 → 188.1 MB (-3.2%); CPU App 23.4 → 25.5% (+8.8%).
- **HU09:** memoria 203.5 → 211.1 MB (+3.7%); CPU App 20.8 → 19.5% (-6.2%).
- **HU10:** memoria 217.3 → 201.0 MB (-7.5%); CPU App 26.4 → 24.5% (-7.2%).
- **HU12:** memoria 197.8 → 198.5 MB (+0.4%); CPU App 25.9 → 24.4% (-6.1%).
- **HU13:** memoria 188.8 → 192.5 MB (+2.0%); CPU App 21.2 → 21.2% (-0.5%).
- **HU14:** memoria 192.3 → 188.7 MB (-1.9%); CPU App 28.4 → 26.4% (-7.0%).
- **HU15:** memoria 200.0 → 194.1 MB (-2.9%); CPU App 30.2 → 27.4% (-9.3%).

### 5.2. Memoria

La mediana de los máximos baja en siete historias y sube en HU09 (+3.7 %), HU12 (+0.4 %) y HU13 (+2.0 %). Las reducciones de mayor magnitud relativa se observan en HU02 (−11.4 %), HU04 (−8.2 %) y HU10 (−7.5 %). En HU07 los tres máximos posteriores son menores que los tres anteriores; en las demás historias se superponen los rangos de memoria. El comportamiento de HU05 se considera regresión y no se atribuye a una optimización propia.

### 5.3. CPU y tiempos

La mediana del pico App baja en nueve historias, con cambios de solo −0.5 % en HU04 y HU13. HU07 sube de 23.4 % a 25.5 % (+8.8 % relativo; +2.1 puntos porcentuales). Los rangos de CPU se superponen en las diez historias; no se demuestra una mejora estadística ni un cambio en tiempos de respuesta. No se midió E2E.

### 5.4. Fragmentación

Esta contribución evalúa solo el Huawei P20. La comparación entre dispositivos debe completarse al integrar los aportes de los otros equipos.

### 5.5. ANR y estabilidad

Las 60 verificaciones funcionales completadas comprueban los resultados de los recorridos medidos. Debe revisarse `estabilidad-eventos.log` junto a los seis `logcat-app.txt` para describir ANR, cierres y errores no fatales con su timestamp; el hecho de completar los recorridos no demuestra ausencia de ANR en otras condiciones. No se infieren fugas de memoria de estas curvas. El orden antes–después y el estado térmico pueden influir en los resultados.

## 6. Optimizaciones implementadas

La versión posterior incluye los cambios B1–B4 identificados en el historial entre ambos commits:

- [60e3b89](https://github.com/misw-is-aplicaciones-moviles/MobileVynils/commit/60e3b89): HU07: esperar una pausa antes de cargar la vista previa de portada.
- [fd56c1c](https://github.com/misw-is-aplicaciones-moviles/MobileVynils/commit/fd56c1c): HU02: conservar en la cabecera solo los campos dibujados.
- [4353f6b](https://github.com/misw-is-aplicaciones-moviles/MobileVynils/commit/4353f6b): HU09: redibujar partes de la hoja solo cuando cambian sus entradas.
- [7d0334a](https://github.com/misw-is-aplicaciones-moviles/MobileVynils/commit/7d0334a): B2: microoptimizaciones de HU04 y HU15.
- [9e2a326](https://github.com/misw-is-aplicaciones-moviles/MobileVynils/commit/9e2a326): HU02/HU09: leer de Room únicamente los autores solicitados.
- [d6aefce](https://github.com/misw-is-aplicaciones-moviles/MobileVynils/commit/d6aefce): HU10: ordenar el catálogo una vez por carga.
- [d5d088f](https://github.com/misw-is-aplicaciones-moviles/MobileVynils/commit/d5d088f): B4: microoptimizaciones del flujo de premios HU14.

El perfilamiento compara las versiones completas; no demuestra el ahorro aislado de cada commit ni prueba la ausencia de ANR o fugas. HU05 se mantiene como regresión.

## 7. Problemas identificados

| Registro | Glide: fallos de imágenes | Sockets sin etiqueta | FATAL EXCEPTION |
|---|---:|---:|---:|
| antes 1 | 64 | 65 | 0 |
| antes 2 | 62 | 64 | 0 |
| antes 3 | 63 | 64 | 0 |
| despues 1 | 51 | 23 | 0 |
| despues 2 | 51 | 24 | 0 |
| despues 3 · Logcat incompleto | N/D | N/D | N/D |

En la tercera pasada posterior, Logcat terminó con `read: unexpected EOF!` al inicio: sus conteos no son válidos como cero. Se conservó el original y se recuperó el remanente disponible en `despues/pasada-3/logcat-recuperado-parcial.txt`; ese remanente no completa la cobertura. La exportación .asdb tiene CPU y memoria en las diez ventanas y no depende de ese proceso de Logcat.

Los conteos corresponden a mensajes de Logcat del PID medido, no a defectos distintos ni a fallos de guardado. Las imágenes remotas de la semilla fallan en estos recorridos; por ello, el consumo gráfico no representa necesariamente un catálogo con todas sus imágenes cargadas. Revisar los timestamps antes de atribuir causalidad.

Eventos am_anr/am_crash del paquete: 0. No identificados en el registro de eventos conservado; esto no asegura su ausencia en otras condiciones.

## 8. Conclusiones

La tabla presenta las reducciones y aumentos observados sin descartar mediciones desfavorables. El resultado es favorable en varias medianas, pero mixto: no todas las historias reducen memoria y HU07 aumenta su pico de CPU. Tres repeticiones constituyen una comparación exploratoria: la superposición de los rangos, la variabilidad de CPU y el tamaño reducido de los datos limitan la atribución de cambios a las optimizaciones. HU05 funciona como regresión. No se cuantificó respuesta E2E ni se demostró ausencia de fugas.

## 9. Evidencias del perfilamiento

Las siguientes capturas se obtuvieron directamente del panel de Android Studio; las gráficas comparativas anteriores se calcularon a partir de las exportaciones originales.

### Antes

- Pasada 1: [grabación .asdb](antes/pasada-1/grabacion.asdb), [resultados](antes/pasada-1/resultados.json), [ventanas](antes/pasada-1/ventanas.json), [entorno](antes/pasada-1/entorno.json), [Logcat](antes/pasada-1/logcat-app.txt).
- Pasada 2: [grabación .asdb](antes/pasada-2/grabacion.asdb), [resultados](antes/pasada-2/resultados.json), [ventanas](antes/pasada-2/ventanas.json), [entorno](antes/pasada-2/entorno.json), [Logcat](antes/pasada-2/logcat-app.txt).
- Pasada 3: [grabación .asdb](antes/pasada-3/grabacion.asdb), [resultados](antes/pasada-3/resultados.json), [ventanas](antes/pasada-3/ventanas.json), [entorno](antes/pasada-3/entorno.json), [Logcat](antes/pasada-3/logcat-app.txt).

#### HU02

**Pasada 1:**

![antes HU02 pasada 1](antes/pasada-1/HU02/profiler.png)

**Pasada 2:**

![antes HU02 pasada 2](antes/pasada-2/HU02/profiler.png)

**Pasada 3:**

![antes HU02 pasada 3](antes/pasada-3/HU02/profiler.png)

#### HU04

**Pasada 1:**

![antes HU04 pasada 1](antes/pasada-1/HU04/profiler.png)

**Pasada 2:**

![antes HU04 pasada 2](antes/pasada-2/HU04/profiler.png)

**Pasada 3:**

![antes HU04 pasada 3](antes/pasada-3/HU04/profiler.png)

#### HU05

**Pasada 1:**

![antes HU05 pasada 1](antes/pasada-1/HU05/profiler.png)

**Pasada 2:**

![antes HU05 pasada 2](antes/pasada-2/HU05/profiler.png)

**Pasada 3:**

![antes HU05 pasada 3](antes/pasada-3/HU05/profiler.png)

#### HU07

**Pasada 1:**

![antes HU07 pasada 1](antes/pasada-1/HU07/profiler.png)

**Pasada 2:**

![antes HU07 pasada 2](antes/pasada-2/HU07/profiler.png)

**Pasada 3:**

![antes HU07 pasada 3](antes/pasada-3/HU07/profiler.png)

#### HU09

**Pasada 1:**

![antes HU09 pasada 1](antes/pasada-1/HU09/profiler.png)

**Pasada 2:**

![antes HU09 pasada 2](antes/pasada-2/HU09/profiler.png)

**Pasada 3:**

![antes HU09 pasada 3](antes/pasada-3/HU09/profiler.png)

#### HU10

**Pasada 1:**

![antes HU10 pasada 1](antes/pasada-1/HU10/profiler.png)

**Pasada 2:**

![antes HU10 pasada 2](antes/pasada-2/HU10/profiler.png)

**Pasada 3:**

![antes HU10 pasada 3](antes/pasada-3/HU10/profiler.png)

#### HU12

**Pasada 1:**

![antes HU12 pasada 1](antes/pasada-1/HU12/profiler.png)

**Pasada 2:**

![antes HU12 pasada 2](antes/pasada-2/HU12/profiler.png)

**Pasada 3:**

![antes HU12 pasada 3](antes/pasada-3/HU12/profiler.png)

#### HU13

**Pasada 1:**

![antes HU13 pasada 1](antes/pasada-1/HU13/profiler.png)

**Pasada 2:**

![antes HU13 pasada 2](antes/pasada-2/HU13/profiler.png)

**Pasada 3:**

![antes HU13 pasada 3](antes/pasada-3/HU13/profiler.png)

#### HU14

**Pasada 1:**

![antes HU14 pasada 1](antes/pasada-1/HU14/profiler.png)

**Pasada 2:**

![antes HU14 pasada 2](antes/pasada-2/HU14/profiler.png)

**Pasada 3:**

![antes HU14 pasada 3](antes/pasada-3/HU14/profiler.png)

#### HU15

**Pasada 1:**

![antes HU15 pasada 1](antes/pasada-1/HU15/profiler.png)

**Pasada 2:**

![antes HU15 pasada 2](antes/pasada-2/HU15/profiler.png)

**Pasada 3:**

![antes HU15 pasada 3](antes/pasada-3/HU15/profiler.png)

### Despues

- Pasada 1: [grabación .asdb](despues/pasada-1/grabacion.asdb), [resultados](despues/pasada-1/resultados.json), [ventanas](despues/pasada-1/ventanas.json), [entorno](despues/pasada-1/entorno.json), [Logcat](despues/pasada-1/logcat-app.txt).
- Pasada 2: [grabación .asdb](despues/pasada-2/grabacion.asdb), [resultados](despues/pasada-2/resultados.json), [ventanas](despues/pasada-2/ventanas.json), [entorno](despues/pasada-2/entorno.json), [Logcat](despues/pasada-2/logcat-app.txt).
- Pasada 3: [grabación .asdb](despues/pasada-3/grabacion.asdb), [resultados](despues/pasada-3/resultados.json), [ventanas](despues/pasada-3/ventanas.json), [entorno](despues/pasada-3/entorno.json), [Logcat](despues/pasada-3/logcat-app.txt).

#### HU02

**Pasada 1:**

![despues HU02 pasada 1](despues/pasada-1/HU02/profiler.png)

**Pasada 2:**

![despues HU02 pasada 2](despues/pasada-2/HU02/profiler.png)

**Pasada 3:**

![despues HU02 pasada 3](despues/pasada-3/HU02/profiler.png)

#### HU04

**Pasada 1:**

![despues HU04 pasada 1](despues/pasada-1/HU04/profiler.png)

**Pasada 2:**

![despues HU04 pasada 2](despues/pasada-2/HU04/profiler.png)

**Pasada 3:**

![despues HU04 pasada 3](despues/pasada-3/HU04/profiler.png)

#### HU05

**Pasada 1:**

![despues HU05 pasada 1](despues/pasada-1/HU05/profiler.png)

**Pasada 2:**

![despues HU05 pasada 2](despues/pasada-2/HU05/profiler.png)

**Pasada 3:**

![despues HU05 pasada 3](despues/pasada-3/HU05/profiler.png)

#### HU07

**Pasada 1:**

![despues HU07 pasada 1](despues/pasada-1/HU07/profiler.png)

**Pasada 2:**

![despues HU07 pasada 2](despues/pasada-2/HU07/profiler.png)

**Pasada 3:**

![despues HU07 pasada 3](despues/pasada-3/HU07/profiler.png)

#### HU09

**Pasada 1:**

![despues HU09 pasada 1](despues/pasada-1/HU09/profiler.png)

**Pasada 2:**

![despues HU09 pasada 2](despues/pasada-2/HU09/profiler.png)

**Pasada 3:**

![despues HU09 pasada 3](despues/pasada-3/HU09/profiler.png)

#### HU10

**Pasada 1:**

![despues HU10 pasada 1](despues/pasada-1/HU10/profiler.png)

**Pasada 2:**

![despues HU10 pasada 2](despues/pasada-2/HU10/profiler.png)

**Pasada 3:**

![despues HU10 pasada 3](despues/pasada-3/HU10/profiler.png)

#### HU12

**Pasada 1:**

![despues HU12 pasada 1](despues/pasada-1/HU12/profiler.png)

**Pasada 2:**

![despues HU12 pasada 2](despues/pasada-2/HU12/profiler.png)

**Pasada 3:**

![despues HU12 pasada 3](despues/pasada-3/HU12/profiler.png)

#### HU13

**Pasada 1:**

![despues HU13 pasada 1](despues/pasada-1/HU13/profiler.png)

**Pasada 2:**

![despues HU13 pasada 2](despues/pasada-2/HU13/profiler.png)

**Pasada 3:**

![despues HU13 pasada 3](despues/pasada-3/HU13/profiler.png)

#### HU14

**Pasada 1:**

![despues HU14 pasada 1](despues/pasada-1/HU14/profiler.png)

**Pasada 2:**

![despues HU14 pasada 2](despues/pasada-2/HU14/profiler.png)

**Pasada 3:**

![despues HU14 pasada 3](despues/pasada-3/HU14/profiler.png)

#### HU15

**Pasada 1:**

![despues HU15 pasada 1](despues/pasada-1/HU15/profiler.png)

**Pasada 2:**

![despues HU15 pasada 2](despues/pasada-2/HU15/profiler.png)

**Pasada 3:**

![despues HU15 pasada 3](despues/pasada-3/HU15/profiler.png)
