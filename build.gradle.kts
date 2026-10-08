// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt) apply false
}

// Formato con ktlint (reglas en .editorconfig). Tareas: spotlessCheck y spotlessApply.
spotless {
    // LF en todos los sistemas, igual que .editorconfig y .gitattributes.
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    kotlin {
        // fileTree anclado en app/src: nunca entra en app/build (que AGP modifica en paralelo).
        target(fileTree("app/src") { include("**/*.kt") })
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}
