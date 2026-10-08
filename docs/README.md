# Documentación técnica de Vinilos

Convenciones de ingeniería de la app Android Vinilos. Se acordaron antes de escribir código y aplican a los tres sprints de desarrollo. Son la fuente de verdad para el equipo y para las herramientas de asistencia (skills y hook de validación de Claude Code en el espacio de trabajo).

| Documento | Contenido | Cuándo leerlo |
|---|---|---|
| [01-stack-y-versiones.md](01-stack-y-versiones.md) | Lenguaje, SDK, librerías, versiones fijadas, catálogo de Gradle | Al configurar el proyecto o añadir una dependencia |
| [02-arquitectura.md](02-arquitectura.md) | MVVM con flujo unidireccional, capas, paquetes, Repository, Service Adapter, Room, inyección, errores, glosario | Antes de crear cualquier clase |
| [03-lineamientos-kotlin.md](03-lineamientos-kotlin.md) | Estilo y reglas de código Kotlin, nombres, nulabilidad, corrutinas, visibilidad | Al escribir o revisar código |
| [04-patrones.md](04-patrones.md) | Patrones de diseño usados y antipatrones prohibidos, con ejemplos | Al diseñar una funcionalidad |
| [05-reglas-de-diseno-ui.md](05-reglas-de-diseno-ui.md) | Material 3, tema, componentes Compose, navegación, recursos, accesibilidad | Al construir una pantalla |
| [06-pruebas.md](06-pruebas.md) | Estrategia de pruebas, herramientas, inventario, reportes de defectos, pruebas E2E, exploratorias y de accesibilidad | Al planear o escribir pruebas |
| [07-desempeno.md](07-desempeno.md) | Reglas de CPU, memoria y red; análisis estático y dinámico; plantilla de informe | Sprint 2 en adelante |
| [08-flujo-de-trabajo-git.md](08-flujo-de-trabajo-git.md) | GitFlow, ramas, commits, pull requests, CI, releases y APK, wiki | Desde el primer commit |
| [09-calidad-estatica.md](09-calidad-estatica.md) | ktlint, detekt, Android Lint, Konsist; configuración y uso | Al configurar el proyecto y al fallar una verificación |

## Principios

1. **Simplicidad primero.** Un módulo, tres capas, una pantalla por historia de usuario. Se añade complejidad cuando un problema concreto la exige, no antes.
2. **La especificación del curso manda.** MVVM, Repository, Service Adapter y Room son requisitos del curso y se nombran así en el código y en la wiki.
3. **Estado inmutable y flujo unidireccional.** El estado baja, los eventos suben. Ninguna vista muta el estado.
4. **Todo cambio de lógica llega con su prueba.** La estrategia de pruebas es parte de la entrega, no un añadido.
5. **Medir antes de optimizar.** El desempeño se evalúa con herramientas y se documenta con números.
6. **Código en inglés, documentación en español.**

## Cómo se mantienen

Cambiar una convención es una decisión de equipo: se discute en la reunión de los lunes, se registra en la bitácora de decisiones del documento afectado y se actualiza en el mismo pull request que la aplica.
