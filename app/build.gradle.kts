plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.detekt)
}

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

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Íconos vectoriales con AppCompat en API 21 a 23.
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    }
    lint {
        // NewApi protege el mínimo de API 21; HardcodedText obliga a usar strings.xml.
        error += setOf("NewApi", "HardcodedText")
        abortOnError = true
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom("src/main/java", "src/test/java", "src/androidTest/java")
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
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
}
