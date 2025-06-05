plugins {
    kotlin("jvm") version "2.1.21"
    application
    id("org.jmailen.kotlinter") version "5.1.0"
}

group = "se.kodverket.collage"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.github.ajalt.clikt:clikt:5.0.3")
    implementation("org.apache.commons:commons-imaging:1.0-alpha3")
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "se.kodverket.collage.MainKt"
}
