plugins {
    kotlin("jvm") version "1.9.22"
}

group = "ru.spbstu"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("io.mockk:mockk:1.13.10")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.8.1-Beta")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0")
    implementation ("org.apache.commons:commons-lang3:3.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1-Beta")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}