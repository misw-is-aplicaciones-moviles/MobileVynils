# 14. HU13 · Diseño técnico de la creación de premios

Rama: `feature/HU13-crear-premio`, desde `develop` en `a95cacd`. Fecha: 05/11/2026. Tareas: HU13-01 (#108) a HU13-06 (#113).
Mockups: `HU13 · 2 Nuevo premio (obligatorios)`, `HU13 · 3 Nuevo premio (completo)`, `HU13 · 4 Premio creado` y `HU14 · 1 Elegir premio` (exportaciones 780 x 1688 px, escala 2).

## 1. Alcance y fuentes

Como coleccionista quiero registrar un premio nuevo desde "Elegir premio" para poder asignarlo a un artista que no lo tiene en la lista. HU13 crea el premio; HU14 lo selecciona y lo asigna. HU13 no asigna ni navega a la fecha: devuelve el premio al selector, que lo deja seleccionado.

Fuentes contrastadas:

- [HU13 en la Wiki](https://github.com/misw-is-aplicaciones-moviles/MobileVynils/wiki/HU13) y el contrato "Contrato preparado para HU13" de [12-hu14-diseno-tecnico.md](12-hu14-diseno-tecnico.md), sección 11.
- Código de BackVynils (solo lectura): `src/prize/prize.controller.ts`, `prize.service.ts`, `prize.dto.ts`, `prize.entity.ts`, `src/interceptors/interceptor.ts`.
- Consultas al backend local del 05/11/2026, sin escribir datos: `POST /prizes` con `{"name":"Probe"}` responde 400 `"description" is required`; con `name` vacío, 400 `"name" is not allowed to be empty`; con un campo extra (`id`), 400 `"id" is not allowed`. Con el listado igual antes y después (los dos premios de la semilla). Joi (`Joi.string().required()`) ejecutado dentro del contenedor del backend: `"   "` y `" a "` pasan; solo `""` falla.

No incluye editar ni borrar premios, otra caché, tablas Room, migraciones ni dependencias nuevas.

## 2. Contrato HTTP

| Operación | Petición | Éxito |
|---|---|---|
| Crear premio | `POST /prizes`, cuerpo `{"name","description","organization"}` y nada más | HTTP 200 (`@HttpCode(200)`) con el premio guardado: `id`, `name`, `description`, `organization`; sin `performerPrizes` |

- El esquema exige los tres campos como texto no vacío y **rechaza campos desconocidos**: el cuerpo no lleva `id`.
- Un 400 es `{"statusCode":400,"message":"ValidationError: ..."}`; los fallos no clasificados del backend son 500.
- **El backend acepta un campo de solo espacios y lo guarda** (Joi solo rechaza la cadena vacía). Por eso la validación de la app lo trata como vacío (sección 5).
- **El backend no evita duplicados**: dos POST iguales crean dos premios. Esa es la razón de la política de la sección 6.
- Los espacios al inicio y al final no se recortan en el servidor; la app los recorta antes de enviar.

## 3. Capas (`02-arquitectura.md`, sección 2)

| Capa | Clase | Cambio |
|---|---|---|
| `models/` | `NewPrize(name, description, organization)` | Borrador, como `NewAlbum` |
| `network/dto/` | `NewPrizeRequest` y `NewPrize.toRequest()` | Cuerpo del POST con exactamente tres campos |
| `network/dto/` | `PrizeDto.toCreatedModel(expected)` en `PrizeMappers.kt` | Reutiliza `PrizeDto.toModel()` y, además, exige que el premio de la respuesta coincida con lo enviado |
| `network/` | `VinilosApiService.createPrize`, `PrizeServiceAdapter.createPrize` | Ampliación del adaptador existente |
| `repositories/` | `PrizeRepository.createPrize(draft): DataResult<Prize, DataError>` | Ampliación del Repository existente; borradores en blanco lanzan `IllegalArgumentException` antes de tocar la red |
| `viewmodels/` | `PrizeForm`, `PrizeFormValidator`, `PrizeFormErrors`, `PrizeFormResult`, `PrizeCreateUiState`, `PrizeCreateEvent`, `PrizeCreateViewModel` | Formulario, errores por campo, envío y resultado incierto |
| `ui/artists/` | `PrizeCreateFragment`, `fragment_prize_create.xml` | Mismo paquete que `PrizePickerFragment` |
| `ui/common/` | `FieldErrors.kt` (`fieldErrorText`, `showFieldError`) | El texto de error con ícono de alerta que tenía `AlbumCreateFragment`, ahora compartido; `AlbumCreateFragment` lo usa |

Una respuesta 2xx es un éxito solo si trae `id` positivo, `name`, `description` y `organization` presentes **y** iguales a lo enviado; si no (campo faltante o nulo, `id` no numérico o no positivo, cuerpo vacío, `null`, JSON roto, 204, o un premio distinto) es `DataError.Serialization`. Sin reintento automático del POST: `NetworkClient` conserva `retryOnConnectionFailure(false)`.

## 4. Navegación y contrato con el selector

- Destino `prizeCreateFragment` declarado en `nav_graph.xml`, pantalla completa sin barra inferior (`MainActivity` ya lo oculta). Se elimina `res/values/prize_ids.xml`: el id ya no se reserva aparte.
- El selector abre el formulario con `launchSingleTop`. Sin ello, dos toques en la tarjeta antes de terminar la primera transición apilaban dos formularios (comprobado en `PrizeCreateScreenTest.two_taps_on_the_create_card_open_a_single_form`, sección 8). El formulario no necesita los argumentos del artista y se abre sin ellos.
- **Crear:** el formulario deja en `previousBackStackEntry.savedStateHandle`, bajo `PrizeFlowContract.PRIZE_CREATED`, un `Bundle` con `PRIZE_ID`, `NAME`, `DESCRIPTION` y `ORGANIZATION`, y hace `popBackStack()`. `PrizePickerFragment.observeCreatedPrize` lo borra al consumirlo y `PrizePickerViewModel.onPrizeCreated` lo deja arriba, seleccionado, con la búsqueda limpia, el aviso "Premio creado" una vez y el botón "Continuar con «…»". El selector, además, vuelve al inicio de la lista al recibirlo (`revealCreatedPrize`): con más filas que pantalla, RecyclerView mantiene a la vista la fila que ya se veía y el premio nuevo quedaba fuera de pantalla (hallazgo de la E2E).
- **Cancelar, X y Atrás:** vuelven sin escribir nada y sin tocar la selección anterior del selector.
- **Cancelar con un resultado desconocido** (sección 6): el formulario marca `PrizeFlowContract.REFRESH_REQUESTED = true` en la entrada del selector, que lo consume y recarga el catálogo con revalidación, porque el premio pudo haberse guardado.
- Cambio de rol: igual que `PrizePickerFragment`. Solo el Coleccionista usa la pantalla; cualquier otro rol (el chip lo limpia) lleva al selector de rol y no escribe.

## 5. Formulario y validación

Pantalla según los mockups: cabecera con X, título "Nuevo premio" y chip de rol; "Todos los campos son obligatorios. Al crearlo, el premio queda disponible para cualquier artista."; campos NOMBRE ("Nombre del premio"), DESCRIPCIÓN multilínea ("¿Qué reconoce este premio?") y ORGANIZACIÓN ("Quién otorga el premio"); botones fijos "Cancelar" y "Crear premio". Reutiliza `view_header`, `PaperSheetLayout`, `Widget.Vinilos.TextInputLayout`, `FieldLabel` y los botones del kit.

`PrizeFormValidator` (Kotlin puro): recorta con `trim()`; un campo vacío o de solo espacios (también tabulaciones, saltos de línea y espacio sin separación) es `FieldError.REQUIRED`, reutilizando el tipo de `AlbumFormValidator`. El Fragment lo muestra como "Campo obligatorio" con el ícono de alerta bajo cada campo. Desde el primer envío, cada cambio de un campo limpia su propio error. Los espacios internos y los saltos de línea de la descripción se conservan.

Estado en `SavedStateHandle` (nombre, descripción, organización, qué campos tienen error y si hay una escritura pendiente): sobrevive a recrear la Activity y a la muerte del proceso.

## 6. Envío y resultado incierto

| Resultado del POST | Qué es | Qué hace la pantalla |
|---|---|---|
| 200 válido | Premio guardado | Devuelve el premio al selector una vez y se cierra |
| Cualquier 4xx (en la práctica 400) | El backend rechazó la petición antes de guardar | Conserva lo escrito, aviso "No se pudo crear el premio. Revisa los datos e inténtalo de nuevo." y el botón sigue siendo "Crear premio": se puede volver a enviar |
| Timeout, desconexión, 5xx, 2xx ilegible o distinto de lo enviado | **Incierto**: el backend pudo guardarlo | Bloquea los campos y "Cancelar" no escribe; el aviso dice que no se pudo confirmar; **el botón principal pasa a "Comprobar" y ya no envía** |

Comprobar = `GET /prizes` con revalidación forzada:

- Hay un premio con el mismo nombre, descripción y organización (tras recortar): se devuelve **ese** premio como creado (el de mayor id si hubiera varios). No se envía otro POST.
- No hay ninguno: aviso "El premio no aparece en el catálogo. Puedes intentar crearlo de nuevo.", los campos y "Crear premio" se desbloquean.
- La consulta falla: sigue incierto, aviso "No se pudo comprobar…", sin envío.

Una sola petición en curso: mientras `isSubmitting` o `isChecking`, "Crear premio", "Cancelar", X, Atrás, el chip de rol y los campos no hacen nada (un doble toque envía un POST). La marca de escritura pendiente se guarda **antes** de enviar y, tras un éxito, no se borra (la pantalla se cierra): si el proceso muere con la respuesta sin conocer, el formulario se restaura bloqueado y lo primero que hace es comprobar.

Es la misma política de `PrizePickerViewModel` (resultado incierto bloquea la repetición y se revalida) y distinta de `AlbumCreateViewModel`, que permite reintentar tras cualquier fallo; allí un reintento tras un timeout podía duplicar el álbum y por eso quedó como caso manual (CP-HU07-07).

Riesgo residual documentado: si el servidor sigue procesando un POST que la app ya dio por perdido (timeout de 30 s) y lo guarda después de la comprobación, un reenvío posterior puede duplicar el premio. Es la misma limitación de HU14 y no se puede cerrar desde el cliente sin idempotencia en el backend.

## 7. "Elegir premio": tarjeta "Crear un premio nuevo"

`createPrize` y `createHelp` pasan de botón y texto sueltos a la tarjeta punteada del mockup HU14-1, justo debajo de las filas. Con muchos premios la lista se encoge (`layout_constrainedHeight`) y la tarjeta sigue visible; mientras el catálogo carga o falla, se oculta. Es un único `LinearLayout` clicable con borde carbón discontinuo (`bg_prize_create_card`, con ripple), círculo carbón con "+" (`bg_prize_create_icon` y `ic_add`), título y ayuda. Un solo elemento accesible: `contentDescription` (`create_prize_card_description`), hijos con `noHideDescendants`, `minHeight` de 48 dp (la tarjeta mide unos 78 dp) y rol de botón para TalkBack mediante un `AccessibilityDelegateCompat`. Medidas en `prizes.xml`; sin colores, dimensiones ni textos literales.

El aviso "Premio creado" es la píldora carbón con ✓ de los mockups (`showConfirmationSnackbar`) y se ancla a la franja del botón: queda bajo la tarjeta si las filas dejan espacio, como en el mockup HU13-4. Con muchas filas puede cubrir la tarjeta durante los 2 s que dura, nunca el botón.

### Fidelidad a los mockups HU14 y HU13

La auditoría de la HU comparó cada pantalla con los mockups 70 a 75 y ajustó también lo heredado de HU14, porque el mockup 73 dibuja ese selector:

- `item_prize` se separa en dos filas: la del selector (título y organización en Montserrat mayúscula, trofeo de línea en círculo, ✓ en círculo carbón al elegirla, píldora "YA ASIGNADO", línea inferior curva) y `item_performer_prize` para el detalle del artista (mockup HU14-6: nombre, organización, fecha y resaltado de la recién asignada).
- La X del selector y del formulario va en círculo, igual que la flecha del detalle. La franja de botones lleva la sombra de la hoja.
- Los errores de campo engrosan el borde y se alinean con el borde del campo; por eso usan su propia vista en vez del error de `TextInputLayout`.
- La hoja de fecha llega al borde inferior y su campo lleva el calendario a la derecha.
- Al volver de asignar, el detalle baja hasta el premio recién asignado; deja espacio para que el aviso no lo tape.

## 8. Decisiones y desviaciones del mockup

| # | Decisión | Motivo |
|---|---|---|
| 1 | La tarjeta no es la última fila que se desplaza: va debajo de la lista, que se encoge con muchos premios | Igual al mockup con pocos premios y siempre alcanzable con catálogos largos, vacíos o filtrados sin coincidencias |
| 2 | La hoja de fecha mantiene "FECHA (OBLIGATORIA)" y no tiene "Omitir" ni su ayuda, aunque el mockup HU14-5 los dibuja | `performer_prize.premiationDate` es `NOT NULL` en el backend; decisión registrada en `docs/12` |
| 3 | Textos nuevos en `strings.xml` (claves `create_prize_*`, `field_organization`, `hint_prize_*`, `action_*_prize`) y no en `prizes.xml` | `docs/10`, sección 8, y la plantilla de PR fijan `strings.xml`, y el hook valida sus claves; las medidas de la tarjeta sí van en `prizes.xml`, como las demás de premios |
| 4 | Estado "incierto" con botón "Comprobar" | No existe en los mockups; es la única forma de no crear un duplicado a ciegas |
| 5 | El texto de error con ícono se extrae a `ui/common/FieldErrors.kt` | Evita copiar el código de `AlbumCreateFragment`; el álbum lo reutiliza sin cambiar su aspecto |

## 9. Pruebas

Inventario y resultados en [06-pruebas.md](06-pruebas.md), "Casos de HU13" (CP-HU13-01 a CP-HU13-20).

## 10. Bitácora

| Fecha | Decisión |
|---|---|
| 2026-11-05 | HU13-01: diseño técnico. `createPrize` amplía el `PrizeRepository` y el `PrizeServiceAdapter` de HU14; respuesta 2xx válida solo si coincide con lo enviado; resultado incierto bloquea el reenvío y se resuelve consultando el catálogo; tarjeta punteada con `launchSingleTop`. |
