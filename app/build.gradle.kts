import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
}

// Firma del APK de release con la keystore de depuración del proyecto (docs/08, sección 5).
// Las credenciales vienen del entorno (secrets de GitHub Actions) o, en local, de local.properties.
val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

fun signingValue(
    envName: String,
    propertyName: String,
): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }

val releaseStorePassword = signingValue("KEYSTORE_PASSWORD", "keystore.password")
val releaseKeyAlias = signingValue("KEY_ALIAS", "keystore.alias")
val releaseKeyPassword = signingValue("KEY_PASSWORD", "keystore.keyPassword")
val missingSigningValues =
    listOfNotNull(
        "KEYSTORE_PASSWORD o keystore.password".takeIf { releaseStorePassword == null },
        "KEY_ALIAS o keystore.alias".takeIf { releaseKeyAlias == null },
        "KEY_PASSWORD o keystore.keyPassword".takeIf { releaseKeyPassword == null },
    )

android {
    namespace = "co.edu.uniandes.vinilos"
    compileSdk = 37

    defaultConfig {
        applicationId = "co.edu.uniandes.vinilos"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom("src/main/java", "src/test/java", "src/androidTest/java")
}

// Un release sin credenciales de firma debe fallar con un mensaje claro, nunca producir un APK sin firmar.
tasks.matching { it.name == "validateSigningRelease" || it.name == "packageRelease" }.configureEach {
    // Copia local: la acción no puede referenciar el script (caché de configuración).
    val missing = missingSigningValues.toList()
    doFirst {
        check(missing.isEmpty()) {
            "Faltan credenciales para firmar el APK de release: ${missing.joinToString()}. " +
                "Defínelas como variables de entorno (CI) o en local.properties (ver README, sección de setup)."
        }
    }
}

// El compilador embebido de detekt 1.23 no acepta el JDK 25 con el que corre Gradle:
// se fija jvmTarget 17 y no se le pasa --jdk-home (su parser falla con "25.0.2").
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    jdkHome.unsetConvention()
    jdkHome.set(null as Directory?)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
