import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ksp)
}

// Firma del APK de release con la keystore de depuración del proyecto (docs/08, sección 5).
// Las credenciales vienen del entorno (secrets de GitHub Actions) o, en local, de local.properties.
val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

// Se compila también en release: el APK del curso consume el backend HTTP local.
val backendBaseUrl = localProperties.getProperty("vinilos.baseUrl") ?: "http://10.0.2.2:3000/"
val backendUri = URI(backendBaseUrl)
require(
    backendUri.scheme in setOf("http", "https") &&
        !backendUri.host.isNullOrBlank() &&
        backendUri.rawUserInfo == null &&
        backendUri.rawQuery == null &&
        backendUri.rawFragment == null &&
        backendBaseUrl.endsWith("/"),
) {
    "vinilos.baseUrl debe ser una URL HTTP(S) con host y / final, sin credenciales, query ni fragmento."
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
        // La rúbrica evalúa desde Lollipop (API 21). El catálogo fija versiones que admiten 21.
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "BASE_URL", "\"${backendUri.toASCIIString()}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Íconos vectoriales con AppCompat en API 21 a 23.
        vectorDrawables.useSupportLibrary = true
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
        // java.time (fechas de lanzamiento) en API 21 a 25.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        viewBinding = true
        // BuildConfig.DEBUG activa StrictMode en VinilosApplication.
        buildConfig = true
    }
    lint {
        // NewApi protege el mínimo de API 21; HardcodedText obliga a usar strings.xml.
        error += setOf("NewApi", "HardcodedText")
        abortOnError = true
    }
    testOptions {
        // Espresso falla de forma intermitente con animaciones activas (docs/06-pruebas.md, sección 4).
        animationsDisabled = true
        // OkHttp 5 detecta android.jar en las unitarias y llama a android.util.Log; sin esto imprime una excepción.
        unitTests.isReturnDefaultValues = true
    }
}

// Esquema de Room versionado en git para escribir y probar migraciones.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom("src/main/java", "src/debug/java", "src/release/java", "src/test/java", "src/androidTest/java")
}

// Un release sin credenciales de firma debe fallar con un mensaje claro, nunca producir un APK sin firmar.
tasks.matching { it.name == "validateSigningRelease" || it.name == "packageRelease" }.configureEach {
    // Copia local: la acción no puede referenciar el script (caché de configuración).
    val missing = missingSigningValues.toList()
    doFirst {
        check(missing.isEmpty()) {
            "Faltan credenciales para firmar el APK de release: ${missing.joinToString()}. " +
                "Defínelas como variables de entorno (CI) o en local.properties (ver keystore/README.md)."
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
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    // Explícita para que la app use la misma versión que kotlinx-coroutines-test; la que trae Room es más vieja.
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    debugImplementation(libs.okhttp.logging)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.arch.core.testing) // InstantTaskExecutorRule
    testImplementation(libs.kotlinx.coroutines.test) // runTest, MainDispatcherRule
    testImplementation(libs.okhttp.mockwebserver) // pruebas de API
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.contrib) // RecyclerViewActions
}
