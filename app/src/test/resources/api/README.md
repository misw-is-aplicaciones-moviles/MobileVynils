# Respuestas de BackVynils para pruebas

Respuestas JSON reales del backend, servidas por `MockWebServerRule.enqueueJson("api/<archivo>.json")` en las pruebas de los Service Adapters.

## Cómo capturar una respuesta

Con BackVynils corriendo en local con la semilla (`docker compose up`):

```bash
curl http://localhost:3000/albums -o app/src/test/resources/api/albums.json
curl http://localhost:3000/albums/100 -o app/src/test/resources/api/album_100.json
```

## Nombres

`snake_case`, uno por endpoint y caso: `albums.json`, `album_100.json`, `album_not_found.json`, `musicians.json`.

## Archivos actuales

| Archivo | Origen | Uso |
|---|---|---|
| `albums.json` | `GET /albums` con la semilla (4 álbumes) | Prueba de humo de TR-05 y HU01 |
| `albums_single.json` | Ejemplo sintético con la forma de BackVynils (1 álbum) | Pruebas de red de TR-02 |
