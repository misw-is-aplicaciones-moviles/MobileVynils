## Historia e issue

- Historia: HU0x
- Cierra: #

## Qué cambia

<!-- Resumen en dos o tres frases: qué se añadió o corrigió y por qué. -->

## Cómo probarlo

<!-- Pasos para verificarlo en el emulador: backend necesario, datos, pantalla, acción, resultado esperado. -->

## Capturas

<!-- Obligatorias si cambia la UI: en API 21 y en API 37, junto al mockup v2 de la historia. -->

## Lista de verificación

- [ ] El título del PR sigue Conventional Commits (será el mensaje del squash).
- [ ] `./gradlew spotlessCheck detekt lintDebug testDebugUnitTest` en verde en local.
- [ ] App abierta y pruebas Espresso ejecutadas en los emuladores API 21 y API 37 si cambia la UI o la navegación.
- [ ] Nueva lógica cubierta por pruebas unitarias; casos añadidos al inventario si aplica.
- [ ] Textos en `strings.xml`; sin valores literales de color, dimensión ni texto.
- [ ] `contentDescription` y 48 dp en elementos interactivos nuevos.
- [ ] Cumple `docs/02-arquitectura.md` (capa, paquete, errores en el Repository).
- [ ] Documentación o wiki actualizadas si cambia una decisión.
