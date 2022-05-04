plugins {
    kotlin("jvm") version "1.5.10"
    kotlin("plugin.serialization") version "1.5.10"

}

group = "com.sonarsource.jbeleites"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.2")
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    //implementation("com.github.kittinunf.fuel:fuel-kotlinx-serialization:2.3.1")
}
