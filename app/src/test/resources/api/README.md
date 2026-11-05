# Respuestas de BackVynils para pruebas

Respuestas JSON reales del backend, servidas por `MockWebServerRule.enqueueJson("api/<archivo>.json")` en las pruebas de los Service Adapters.

## Cómo capturar una respuesta

Con BackVynils corriendo en local con la semilla (`docker compose up`):

```bash
curl http://localhost:3000/albums -o app/src/test/resources/api/albums.json
curl http://localhost:3000/albums/100 -o app/src/test/resources/api/album_100.json
curl http://localhost:3000/albums/2147483647 -o app/src/test/resources/api/album_not_found.json
curl http://localhost:3000/musicians -o app/src/test/resources/api/musicians.json
curl http://localhost:3000/bands -o app/src/test/resources/api/bands.json
curl http://localhost:3000/collectors -o app/src/test/resources/api/collectors.json
curl http://localhost:3000/collectors/100 -o app/src/test/resources/api/collector_100.json
curl http://localhost:3000/collectors/100/albums -o app/src/test/resources/api/collector_100_albums.json
```

## Nombres

`snake_case`, uno por endpoint y caso: `albums.json`, `album_100.json`, `album_not_found.json`, `musicians.json`, `bands_with_members.json`.

## Archivos actuales

| Archivo | Origen | Uso |
|---|---|---|
| `albums.json` | `GET /albums` con la semilla (4 álbumes) | Prueba de humo de TR-05 y HU01 |
| `albums_single.json` | Ejemplo sintético con la forma de BackVynils (1 álbum) | Pruebas de red de TR-02 |
| `album_100.json` | `GET /albums/100`, HTTP 200; capturado del backend local el 2026-10-28 | HU02: contrato del detalle con canciones, artista y comentarios |
| `album_not_found.json` | `GET /albums/2147483647`, HTTP 404; capturado del backend local el 2026-10-28 | HU02: respuesta real de álbum inexistente |
| `album_created.json` | Ejemplo sintético con la forma de la respuesta 200 de `POST /albums`: el álbum guardado, sin `tracks`, `performers` ni `comments` | HU07, POST válido |
| `album_create_missing_field.json` | `POST /albums` real sin `cover` (400) | HU07, campos faltantes |
| `album_create_invalid_genre.json` | `POST /albums` real con `genre` fuera de la lista (400) | HU07, valores fuera de las listas |
| `musicians.json` | `GET /musicians` con la semilla (Rubén Blades Bellido de Luna, id 100); capturado del backend local el 2026-11-03 | HU03: contrato de `GET /musicians`; el músico no trae banda |
| `bands.json` | `GET /bands` con la semilla (Queen, id 101, sin integrantes); capturado el 2026-11-03 | HU03: contrato de `GET /bands` |
| `musicians_with_band.json` | `GET /musicians` tras crear a Freddie Mercury, Brian May, Roger Taylor y John Deacon con `POST /musicians` y asociarlos a Queen con `POST /bands/101/musicians/:id`; capturado el 2026-11-03 | HU03: aun asociados, los músicos no traen su banda |
| `bands_with_members.json` | `GET /bands` en ese mismo estado; capturado el 2026-11-03 | HU03: `musicians[]` de Queen con los cuatro integrantes, de donde se deriva la pertenencia |
| `collectors.json` | `GET /collectors` con la semilla (Manolo Bellon, id 100, y Jaime Monsalve, id 101); capturado del backend local el 2026-11-04 | HU05: contrato de `GET /collectors`; conteos de `collectorAlbums` y `favoritePerformers` |
| `collector_100.json` | `GET /collectors/100` con la semilla; capturado del backend local el 2026-11-04 | HU06: Manolo, teléfono y favorito músico. `collectorAlbums` no trae el álbum anidado |
| `collector_100_albums.json` | `GET /collectors/100/albums` con la semilla; capturado del backend local el 2026-11-04 | HU06: vínculo con precio, estado y álbum anidado "Buscando América" |

Los casos de HTTP 500, desconexión, JSON inválido, campo requerido ausente,
JSON `null` y HTTP 204 se generan en MockWebServer; no son respuestas capturadas
del backend. El caso de secciones vacías reutiliza Poeta del pueblo de `albums.json`.
Estas pruebas no necesitan backend, tablet ni emulador durante su ejecución.

Las respuestas 400 de `POST /albums` se capturan sin escribir en la base, porque el backend rechaza el cuerpo antes de guardar:

```bash
curl -X POST http://localhost:3000/albums -H "Content-Type: application/json" \
  -d '{"name":"Probe"}' -o app/src/test/resources/api/album_create_missing_field.json
```

La respuesta 200 de `POST /albums` es sintética para no crear álbumes en la base local al capturarla.

HU04-02 reutiliza el primer objeto de `musicians.json` y `bands_with_members.json` como respuesta del detalle en `PerformerDetailServiceAdapterTest`: sus campos se contrastaron con `GET /musicians/:id` y `GET /bands/:id` del backend local. Las variantes inválidas y vacías se preparan en memoria sobre esos objetos, sin duplicar los archivos ni depender del estado del backend durante las pruebas JVM.
