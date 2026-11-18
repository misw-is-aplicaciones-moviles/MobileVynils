# 15. HU12 · Diseño técnico de agregar músicos a una banda

Rama: `feature/HU12-agregar-musicos-a-banda`, desde `develop` en `3f0106b`. Fecha: 17/11/2026. Tareas: HU12-01 a HU12-06.
Mockups: `HU12 · 1 Elegir músico`, `HU12 · 2 Adam Lambert elegido`, `HU12 · 3 Ringo Starr elegido`, `HU12 · 4 Aviso: ya tiene banda` y `HU12 · 5 Integrante agregado`.

## 1. Alcance y fuentes

Como coleccionista quiero agregar músicos a una banda para actualizar su información. Desde el detalle de una banda (HU04) el botón "Agregar integrante" abre "Elegir músico"; al confirmar, la app llama a `POST /bands/:bandId/musicians/:musicianId` y vuelve al detalle con el integrante nuevo y el aviso "Integrante agregado a la banda".

Fuentes contrastadas:

- Código de BackVynils (solo lectura): `src/bandmusician/bandmusician.controller.ts`, `bandmusician.service.ts`, `src/musician/musician.entity.ts`, `src/interceptors/interceptor.ts`.
- Consultas al backend local del 17/11/2026, sin escribir datos: `POST /bands/2147483647/musicians/100` responde 404 "The band with the given id was not found"; `POST /bands/101/musicians/2147483647` responde 404 "The musician with the given id was not found".

No incluye quitar integrantes, tablas Room nuevas, migraciones ni dependencias nuevas.

## 2. Contrato HTTP y decisiones forzadas por el backend

| Operación | Petición | Éxito | Errores |
|---|---|---|---|
| Agregar integrante | `POST /bands/:bandId/musicians/:musicianId`, sin cuerpo | HTTP 200 (`@HttpCode(200)`) con el músico guardado y su `band` | 404 si la banda o el músico no existen |

- **El backend no aplica la regla "un músico solo puede pertenecer a una banda"**: hace `musician.band = band` y guarda, así que un músico de otra banda se *mueve* sin aviso. Como BackVynils no se modifica, **la app bloquea**: se puede seleccionar a un músico de otra banda, pero al tocar "Agregar a …" se muestra el aviso del mockup HU12 · 4 y no se envía el POST.
- `GET /musicians/:id` no trae la banda del músico; la pertenencia solo se obtiene de `GET /bands[].musicians`. HU03 ya la deriva en `mergePerformers()`, de modo que `Performer.bandId/bandName` es la fuente del selector (HU12-01: reutilizar la lista de músicos).
- **`DELETE /bands/:id/musicians/:id` tiene un error**: su `filter(e => { e.id !== id })` no devuelve nada y deja la banda sin integrantes. Ninguna prueba lo usa sobre datos ajenos.
- El cliente HTTP no reintenta en fallos de conexión (`retryOnConnectionFailure(false)`), así que un POST cortado no se duplica.

## 3. Capas (`02-arquitectura.md`, sección 2)

| Capa | Clase | Cambio |
|---|---|---|
| `network/` | `VinilosApiService.addMusicianToBand` | Nuevo endpoint, `Response<MusicianDto>` |
| `network/dto/` | `MusicianDto.toMemberSummary()` en `PerformerDetailMappers.kt` | Extraído del mapeo de integrantes de HU04; lo reutilizan HU04 y HU12 |
| `network/` | `PerformerServiceAdapter.addMusicianToBand` | Petición en IO, mapeo en Default; rechaza una respuesta con otro músico |
| `database/dao/` | `PerformerDao.applyMembership` (`setMusicianBand` + `incrementMemberCount`) | Actualiza la caché en una transacción; idempotente; sin cambio de esquema |
| `repositories/` | `PerformerRepository.addMusicianToBand(bandId, musicianId): DataResult<PerformerSummary, DataError>` | POST fuera del `mutex`, actualización de Room dentro; un fallo de Room no cambia el resultado |
| `viewmodels/` | `BandMemberPickerViewModel`, `BandMemberPickerUiState`, `BandMemberOption`, `BandConflict`, `BandMemberPickerEvent`, `BandMemberFlowContract` | Selector, regla de una banda, envío y resultado incierto |
| `viewmodels/` | `PerformerDetailViewModel.onAddMemberClicked/onMemberAdded`, `PerformerDetailUiState.highlightedMemberId` | Abre el selector y recibe el integrante |
| `ui/artists/` | `BandMemberPickerFragment`, `fragment_band_member_picker.xml`, `item_band_member_option.xml`, `item_performer_members_action.xml` | Pantalla y filas; fila "Integrantes · n" con botón |

## 4. Flujo

1. El detalle de la banda muestra "Integrantes · n" con "Agregar integrante" solo para Coleccionista.
2. El selector recibe `bandId`, `bandName` y los ids de los integrantes actuales, y carga la lista con `getPerformers(forceRefresh = true)` para que la banda de cada músico sea la vigente.
3. Filas: integrantes con "YA INTEGRANTE" (no seleccionables); músicos de otra banda con "Integrante de …" en la sobrelínea.
4. "Agregar a …": si el músico tiene otra banda, aviso y nada más; si no, POST.
5. Éxito: el Repository actualiza Room (músico con banda, banda con un integrante más) y el selector devuelve el integrante por el `SavedStateHandle` del detalle, que lo agrega al final de la tira y muestra el aviso.
6. 400/404: error definitivo, se puede elegir otro. Red, 500 o respuesta ilegible: resultado incierto; el botón cambia a "Revisar banda" y el detalle se recarga.

## 5. Memoria, hilos y caché

- Filas calculadas en `Dispatchers.Default` una vez por búsqueda o selección; una búsqueda nueva cancela la anterior (mismo patrón que HU11).
- `ListAdapter` con `DiffUtil`, `setHasFixedSize(true)`, listener asignado una vez por ViewHolder y `RequestManager.clear` al reciclar; adaptador y binding liberados en `onDestroyView`.
- La tira de integrantes del detalle conserva un único `ListAdapter` por ViewHolder (antes se creaba uno nuevo en cada `bind`).
- Room no se recarga entera tras el POST: se actualizan dos filas. El `mutex` del Repository garantiza que una recarga traída antes del POST no deshaga el cambio.

## 6. Pruebas

Inventario CP-HU12-01 a CP-HU12-63 en [06-pruebas.md](06-pruebas.md#hu12--agregar-músicos-a-una-banda).
