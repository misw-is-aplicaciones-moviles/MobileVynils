# Respuestas de BackVynils para pruebas

Respuestas JSON reales del backend, servidas por `MockWebServerRule.enqueueJson("api/<archivo>.json")` en las pruebas de los Service Adapters.

## Cómo capturar una respuesta

Con BackVynils corriendo en local con la semilla (`docker compose up`):

```bash
curl http://localhost:3000/albums -o app/src/test/resources/api/albums.json
curl http://localhost:3000/albums/100 -o app/src/test/resources/api/album_100.json
curl http://localhost:3000/albums/2147483647 -o app/src/test/resources/api/album_not_found.json
```

## Nombres

`snake_case`, uno por endpoint y caso: `albums.json`, `album_100.json`, `album_not_found.json`, `musicians.json`.

## Archivos actuales

| Archivo | Origen | Uso |
|---|---|---|
| `albums.json` | `GET /albums` con la semilla (4 álbumes) | Prueba de humo de TR-05 y HU01 |
| `albums_single.json` | Ejemplo sintético con la forma de BackVynils (1 álbum) | Pruebas de red de TR-02 |
| `album_100.json` | `GET /albums/100`, HTTP 200; capturado del backend local el 2026-10-28 | HU02: contrato del detalle con canciones, artista y comentarios |
| `album_not_found.json` | `GET /albums/2147483647`, HTTP 404; capturado del backend local el 2026-10-28 | HU02: respuesta real de álbum inexistente |

Los casos de HTTP 500, desconexión, JSON inválido, campo requerido ausente,
JSON `null` y HTTP 204 se generan en MockWebServer; no son respuestas capturadas
del backend. El caso de secciones vacías reutiliza Poeta del pueblo de `albums.json`.
Estas pruebas no necesitan backend, tablet ni emulador durante su ejecución.
