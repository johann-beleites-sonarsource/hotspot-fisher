plugins {
    kotlin("jvm") version "1.6.20"
    kotlin("plugin.serialization") version "1.5.10"
    application
}

group = "com.sonarsource.jbeleites"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.ajalt.clikt:clikt:3.4.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1")
    //implementation("com.github.kittinunf.fuel:fuel-kotlinx-serialization:2.3.1")
}

application {
    mainClass.set("com.sonarsource.jbeleites.hotspotdownloader.MainKt")
}
