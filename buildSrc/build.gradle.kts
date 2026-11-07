plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    // Misma versión de JUnit ya utilizada por la app, sin nueva dependencia en sus APK.
    testImplementation("junit:junit:4.13.2")
}
