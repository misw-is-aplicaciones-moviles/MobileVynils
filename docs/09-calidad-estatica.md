# 09. Calidad estática

Herramientas que verifican el código sin ejecutarlo, su configuración y cómo se usan en local, en el hook del espacio de trabajo y en la CI.

## 1. Herramientas

| Herramienta | Versión | Qué verifica | Configuración |
|---|---|---|---|
| ktlint (vía Spotless 8.10) | 1.8.0 | Formato y estilo oficial de Kotlin y Android | `.editorconfig` (raíz del repositorio); tareas `spotlessCheck` y `spotlessApply` |
| detekt | 1.23.8 | Complejidad, nombres, excepciones, corrutinas, estilo (sin reglas de formato, que son de ktlint) | `config/detekt/detekt.yml` sobre la configuración por defecto |
| Android Lint | el de AGP | Recursos, manifest, API (`NewApi` con `minSdk 21`), textos literales (`HardcodedText`), desempeño, accesibilidad | `lint { }` en `app/build.gradle.kts` |

## 2. Dónde corren

| Momento | Qué | Cómo |
|---|---|---|
| Al escribir con el IDE | ktlint (formato al guardar) y Lint en el editor | Plugin ktlint de Android Studio o `./gradlew spotlessApply`; inspecciones de Lint activas |
| Al editar con Claude Code | ktlint + detekt sobre el archivo editado | Hook `PostToolUse` del espacio de trabajo (`.claude/hooks/check-kotlin-rules.sh`); reporta y pide corregir |
| Antes del PR | ktlint, detekt, Lint, unitarias | `./gradlew spotlessCheck detekt lintDebug testDebugUnitTest` |
| En cada PR | lo mismo | GitHub Actions, obligatorio en verde para integrar |

## 3. Configuración en Gradle

`build.gradle.kts` (raíz). Se usa Spotless y no el plugin ktlint-gradle porque este último no encuentra las fuentes con el Kotlin integrado de AGP 9:

```kotlin
plugins {
    alias(libs.plugins.spotless)
}

spotless {
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    kotlin {
        // fileTree anclado en app/src: nunca entra en app/build.
        target(fileTree("app/src") { include("**/*.kt") })
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}
```

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom("src/main/java", "src/test/java", "src/androidTest/java")
}

// detekt 1.23 corre sobre un compilador Kotlin embebido que no acepta el JDK 25 con el
// que corre Gradle: se fija jvmTarget 17 y no se le pasa --jdk-home.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    jdkHome.unsetConvention()
    jdkHome.set(null as Directory?)
}

android {
    lint {
        // NewApi protege el mínimo de API 21; HardcodedText obliga a usar strings.xml.
        error += setOf("NewApi", "HardcodedText")
        abortOnError = true
    }
}
```

## 4. Reglas destacadas de detekt para el proyecto

- `GlobalCoroutineUsage`, `InjectDispatcher`, `SleepInsteadOfDelay`: concurrencia correcta.
- `TooGenericExceptionCaught`, `SwallowedException`: errores explícitos; `CancellationException` nunca se traga.
- `MagicNumber`, `MaxLineLength` 120, `ReturnCount` 3, `LongMethod` 60, `LongParameterList` 8, `TooManyFunctions` 20.
- `ForbiddenComment`: `FIXME` y `STOPSHIP` prohibidos (se registran como issues).
- `WildcardImport`, `MatchingDeclarationName`, `FunctionNaming` (las pruebas con nombres entre backticks quedan fuera por la configuración por defecto).

## 5. Excepciones

- Una infracción justificada se marca con `@Suppress("NombreDeRegla") // motivo` en la línea anterior y se revisa en el PR.
- Un hallazgo de Lint heredado que no se puede corregir en el sprint entra en `lint-baseline.xml`; la línea base solo crece con aprobación del equipo y se reduce en cada sprint.
- Nunca se desactiva una regla globalmente para resolver un caso puntual.

## 6. Hook del espacio de trabajo

Vive en el espacio de trabajo (`mobile-apps/.claude/`), no en este repositorio, porque es una herramienta del asistente. Las herramientas (ktlint y detekt como jar) se instalan una vez con `bash .claude/hooks/setup-tools.sh`, con versiones y sumas sha256 fijadas, en un directorio ignorado por git. Para formatear a mano un archivo:

```bash
java -jar .claude/hooks/bin/ktlint -F MobileVynils/app/src/main/java/.../Archivo.kt
```

## 7. Bitácora de decisiones

| Fecha | Decisión |
|---|---|
| 2026-10-07 | ktlint 1.8.0 + detekt 1.23.8 + Lint con warnings como errores; Konsist para capas; hook de validación por archivo en el espacio de trabajo. |
| 2026-10-26 | Reinicio con Vistas: Lint con `NewApi` y `HardcodedText` como errores; se retiran Konsist, el reporte de estabilidad de Compose y las excepciones de detekt y ktlint para `@Composable`. |
