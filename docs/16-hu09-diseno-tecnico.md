# 16. HU09 · Diseño técnico de comentar un álbum

Rama: `feature/HU09-comentar-album`, desde `develop` en `3f0106b`. Fecha: 17/11/2026. Tareas: HU09-01 (#55) a HU09-06 (#60).
Mockups v2: `32-hu09-1-hoja-vacia`, `33-hu09-2-campos-obligatorios`, `34-hu09-3-datos-completos`, `35-hu09-4-comentario-publicado` y la combinación `50` a `56-hu08-hu09-*` (exportaciones 780 px de ancho).

## 1. Alcance y fuentes

Como coleccionista quiero comentar un álbum para dar a conocer mi opinión. Desde el detalle del álbum, el botón "Comentar" abre la hoja "Comentar álbum": se elige quién comenta, la valoración y el texto, y al publicar el comentario aparece al final de la lista con el nombre de su autor.

Fuentes contrastadas:

- [HU09 en la Wiki](https://github.com/misw-is-aplicaciones-moviles/MobileVynils/wiki/HU09), `10-diseno-de-la-aplicacion.md` (secciones 3, 6 y 8, hallazgo 1) y los mockups v2.
- Código de BackVynils (solo lectura): `src/comment/comment.controller.ts`, `comment.service.ts`, `comment.dto.ts`; `src/collector/collector.service.ts`.
- Consultas al backend local del 17/11/2026, con la semilla restaurada antes y después (sección 2).
- Implementación de HU08 en `develop` (hoja de canción), que es el patrón que se repite.

No incluye editar ni borrar comentarios ni cambios en el backend.

## 2. Contrato HTTP

| Operación | Petición | Éxito |
|---|---|---|
| Publicar | `POST /albums/:albumId/comments`, cuerpo `{"description","rating","collector":{"id"}}` | HTTP **200** (`@HttpCode(200)`) con el comentario guardado: `id`, `description`, `rating`, `collector` completo (`id`, `name`, `telephone`, `email`) y `album` sin listas |
| Autores | `GET /collectors` | Cada coleccionista trae `comments: [{id, description, rating}]` |

Comportamiento comprobado:

| Caso | Respuesta |
|---|---|
| Álbum inexistente | 404 `The album with the given id was not found` (se comprueba primero) |
| Coleccionista inexistente | 404 `The collector with the given id was not found` (antes de validar el cuerpo) |
| `rating` 6 | 400 `"rating" must be less than 6` |
| `description` `""` | 400 `"description" is not allowed to be empty` |
| `description` `"   "` | **200: lo guarda**. La app lo trata como vacío |
| `rating` 0 | 200 |
| `rating` 2.5 | 500 |
| Sin `collector` | 500 |

`GET /albums/:id` devuelve cada comentario **sin** su coleccionista (hallazgo 1 de `docs/10`). Por eso el nombre del autor se resuelve con `GET /collectors` (sección 5).

## 3. Capas (`02-arquitectura.md`, sección 2)

| Capa | Clase | Cambio |
|---|---|---|
| `models/` | `NewComment(description, rating, collectorId)`, `CommentAuthor(collectorId, name)` | Nuevos |
| `models/` | `Comment` | Gana `author: CommentAuthor? = null` |
| `network/dto/` | `NewCommentRequest` + `NewComment.toRequest()`; `CommentDto.collector`; `CollectorDto.comments` | El cuerpo lleva exactamente tres claves |
| `network/` | `VinilosApiService.addComment`; `AlbumServiceAdapter.addComment` | Como `addTrack`: valida que la respuesta sea del álbum pedido y que traiga `id` y autor |
| `network/` | `CollectorServiceAdapter.getCollectors()` devuelve `CollectorDirectory(collectors, commentAuthors)` | La misma respuesta llena la lista y el índice de autores, sin otra petición |
| `database/` | `CommentAuthorEntity` (`comment_authors`), `CommentAuthorDao`, `MIGRATION_4_5` | Índice local id de comentario → autor. La versión 5 solo agrega la tabla |
| `repositories/` | `AlbumRepository.addComment(albumId, draft): DataResult<Comment, DataError>` | Firma de `docs/10`; guarda el autor en el índice; un fallo de Room no cambia el éxito |
| `repositories/` | `CollectorRepository.getCommentAuthors(commentIds, revalidate)` | Room primero; red una vez si falta algún autor o si la sesión aún no comparó el índice con el backend |
| `viewmodels/` | `CommentCreateViewModel`, `CommentCreateUiState`, `CommentCreateEvent`, `CommentCreateValidator`, `CommentCollectorOption` | Formulario de la hoja |
| `viewmodels/` | `AlbumDetailViewModel` | Autores desde Room y red solo para los que faltan, iniciales en el `UiState`, `onCommentCreated`, comentario resaltado |
| `ui/albums/` | `CommentCreateBottomSheet`, `fragment_comment_create_sheet.xml`; `AlbumDetailAdapter` | Hoja nueva; encabezado "Comentarios" con el botón y tarjetas con autor |

## 4. Navegación y resultado

- El detalle abre la hoja con `show(childFragmentManager, TAG)`, como HU08. Solo con el rol coleccionista se ve el botón "Comentar", en el encabezado de la sección (mockups 35 y 53). El "+" de la barra sigue siendo "Canción".
- Al publicar, la hoja entrega con `setFragmentResult` solo primitivos: id del álbum, id, texto y valoración del comentario, e id y nombre del autor. El detalle llama a `onCommentCreated(comment)`, que lo agrega **al final** sin otro `GET /albums/:id` (igual que CP-HU08-15) y lo marca como resaltado (borde oscuro del mockup).
- Aviso "Comentario publicado" con `showConfirmationSnackbar` (check del mockup), anclado sobre el "+".
- Recreación: el formulario vive en el `SavedStateHandle` de la hoja (coleccionista, valoración y texto); el comentario agregado vive en el `UiState` del detalle.

## 5. Autor de cada comentario y carga rápida

El índice `comment_authors` (Room) guarda id de comentario, id y nombre del coleccionista. Se llena desde dos fuentes:

1. Cada `GET /collectors` (el listado de HU05, la hoja de esta historia o el paso 3 de abajo) **agrega** sus autores al índice. El índice solo crece: la app no borra comentarios, así que una fila nunca queda equivocada, y una respuesta vieja que llega tarde no puede borrar el autor de una publicación reciente.
2. Cada publicación exitosa agrega su fila con el coleccionista de la respuesta.

Al abrir el detalle:

| Paso | Qué ocurre |
|---|---|
| 1 | `GET /albums/:id`, como antes. Si el álbum no tiene comentarios, se dibuja y no se pide nada más |
| 2 | Se leen de Room los autores de los comentarios de ese álbum (`getCommentAuthors(ids, revalidate = false)`) y el álbum se dibuja con ellos y sus iniciales, calculados fuera del hilo principal. Esta lectura nunca espera a la red |
| 3 | Con el álbum ya en pantalla, `getCommentAuthors(ids, revalidate = true)`. El repositorio pide **una vez** `GET /collectors` solo si algún comentario no está en el índice (lo escribió otro usuario) o si en esta sesión de la app todavía no se comparó el índice con el backend. La respuesta del backend reemplaza lo que decía Room; si nada cambió, la pantalla no se vuelve a dibujar |
| 4 | Si los autores fallan (red o Room), el detalle sigue igual con "Coleccionista": el autor nunca retrasa ni convierte en error el detalle |

**Backend recreado (D-HU09-2).** El backend no da ninguna señal de que su base se recreó (sin identificador de instancia; el `ETag` es un hash del cuerpo) y, al recrearla, vuelve a numerar los comentarios desde 1, así que una fila guardada en una sesión anterior puede nombrar a otro coleccionista. Por eso Room solo se da por bueno después de que la sesión guardó un `GET /collectors` completo: el de este paso, el de la hoja o el del listado de coleccionistas. Es el mismo patrón de las demás cachés de la app (Room primero y revalidación), aplicado una vez por sesión. Cuesta como mucho una petición por sesión, después de mostrar el álbum, y no compara textos ni depende de ningún elemento. Una recreación del backend con la app abierta se corrige en la siguiente sesión.

Efecto: con el índice al día, una sola petición de red por apertura (presupuesto de `docs/07`) y el nombre desde el primer dibujo; si falta algún autor, una segunda petición después de mostrar el álbum, nunca antes. Un álbum sin comentarios nunca pide `GET /collectors`. Dos pantallas que piden autores a la vez comparten el `Mutex` del repositorio: la segunda vuelve a leer Room y no repite la petición.

El índice se lee completo (`SELECT *`) y se filtra por los ids del álbum fuera del hilo principal, no con `WHERE id IN (:ids)`: así no hay un parámetro por id y no aplica el límite de 999 variables de #139. Todo `SQLiteException` se trata como caché vacía (patrón OPT-02 de #140/#142). Como `getCollectors()` comparte ese camino, también se le aplica el mismo manejo, que cierra el cierre de la app por Room que #139 dejó pendiente en coleccionistas. La lista de coleccionistas y el índice se escriben por separado: si una de las dos escrituras falla, la otra sigue siendo válida y Room se pone al día en la siguiente petición.

## 6. Formulario y validación

| Campo | Regla | Error (mockup 33) |
|---|---|---|
| ¿Quién comenta? | Un coleccionista elegido | "Selecciona un coleccionista" |
| Valoración | 1 a 5 estrellas (decisión de Yoimar: como el mockup; el 0 no se puede elegir) | "Selecciona una valoración" |
| Comentario | Texto sin espacios al inicio y al final, no vacío | "Campo obligatorio" |

- Los errores se ven solo después del primer "Publicar comentario" y cada uno se limpia al corregir su campo, como HU08.
- La etiqueta pasa de "Valoración (0 a 5)" a "Valoración · 4 de 5" al elegir.
- Los coleccionistas salen de `CollectorRepository.getCollectors()` (Room primero) y sus chips se calculan una vez fuera del hilo principal. Si no cargan, se muestra el aviso con "Reintentar" en el lugar de los chips.
- Envío: igual que HU08. Mientras envía, todo queda bloqueado y la hoja no se puede cerrar. Un error deja el formulario intacto y se puede reintentar. OkHttp no reintenta el POST por su cuenta (`retryOnConnectionFailure(false)`). No se usa la comprobación de HU13: un comentario duplicado es leve y la pantalla es la misma de HU08.

## 7. Accesibilidad (`Sprint3/accesibilidad`)

- Toques de al menos 48 × 48 dp: cada estrella, cada chip (tamaño mínimo de Material), el botón "Comentar", "Reintentar" y la X.
- Cada estrella tiene una descripción única ("Valoración 3 de 5") y se marca como seleccionada; la elección no depende solo del color: también cambia la etiqueta.
- Los chips son elementos marcables (TalkBack anuncia "marcado"); el avatar con iniciales es decorativo.
- La etiqueta "Comentario" usa `labelFor`; los errores son regiones vivas (`accessibilityLiveRegion="polite"`).
- Cada tarjeta de comentario se lee como un solo elemento: "Ana Lucía Pardo, 4 de 5 estrellas. Una obra que no envejece."; avatar y estrellas no se leen por separado.
- Colores del kit (carbón sobre crema) para el texto, sin colores nuevos.

## 8. Decisiones y desviaciones

| Decisión | Motivo |
|---|---|
| Estrellas 1 a 5 en lugar de seis chips 0 a 5 (`docs/10`, sección 6) | Mockup v2; decisión de Yoimar del 17/11/2026 |
| Autor por índice local alimentado con `GET /collectors`, en lugar de guardar solo lo publicado (`docs/10`, hallazgo 1) | El mockup muestra el nombre en todos los comentarios, también en los de la semilla |
| El detalle agrega el comentario sin recargar (`docs/10`, línea 102, decía recargar) | Patrón de HU08 ya integrado (CP-HU08-15) |
| Pruebas E2E con el estilo de `develop` (`docs/06`) | Decisión de Yoimar del 17/11/2026 |
| Migración 4 → 5 que solo agrega `comment_authors` | Conserva lo guardado, como 2 → 3 y 3 → 4 |
| Botón "Comentar" relleno (`docs/10`, sección 7, lo lista como secundario) | Los mockups v2 35 y 53 lo dibujan relleno en carbón |
| La etiqueta dice "Valoración (0 a 5)" aunque las estrellas van de 1 a 5 | Es el texto del mockup y de `docs/10`, sección 8, y el rango que acepta el backend; la valoración elegida se lee como "N de 5" |
| Los chips de "¿Quién comenta?" se crean todos al abrir la hoja | Con la semilla son pocos; el backend no pagina, así que con cientos de coleccionistas habría que paginar o buscar (anotado para el informe de desempeño) |

## 9. Pruebas

Casos CP-HU09 en el inventario de `06-pruebas.md`. Unitarias del validador y de los ViewModel; Repository con fakes (éxito, fallo de red, fallo de Room, id inválido, índice frío y caliente); API del adaptador con respuestas reales capturadas (`comment_created.json`, `comment_collector_not_found.json`, `comment_rating_out_of_range.json`, `collectors.json` con comentarios); DAO y migración 4 → 5; E2E de la hoja y del detalle.

## 10. Bitácora

| Fecha | Cambio |
|---|---|
| 17/11/2026 | Diseño inicial (HU09-01) con el contrato comprobado en el backend local |
| 17/11/2026 | Implementación HU09-02 a HU09-04 y verificación visual en API 37 contra los mockups 32 a 35 (capturas en `Sprint3/HU09/evidencias-visuales` del espacio de trabajo). Revisión independiente: la carga de autores pasa de "en paralelo" a "Room primero y red solo para los que faltan", el índice pasa a solo agregar, las iniciales se calculan fuera del hilo principal y "Reintentar" sube a 48 dp |
| 17/11/2026 | Pruebas: 422 unitarias; E2E `CommentCreateScreenTest` 8 de 8 en API 37 y en API 21 (dos ejecuciones limpias); Room y migración 4 → 5 en API 37. Las E2E encontraron D-HU09-1 (la hoja no mostraba coleccionistas nuevos): la hoja ahora muestra Room y revalida con `GET /collectors`. Pendiente D-HU09-2 (ver `06-pruebas.md`): si el backend se reinicia y la app conserva sus datos, un id de comentario reutilizado puede mostrar el autor guardado del comentario anterior con ese id |
| 17/11/2026 | D-HU09-2 corregido: el índice de autores se revalida una vez por sesión con `GET /collectors` y la respuesta del backend reemplaza a Room (sección 5) |
