plugins {
    kotlin("jvm") version "2.4.0"
    alias(libs.plugins.kotlin.serialization)
    id("org.jetbrains.exposed.plugin") version "1.3.0"
}

group = "com.fantamomo.hc"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(libs.exposed.core)
    implementation(libs.exposed.r2dbc)
    implementation(libs.exposed.datetime)
    implementation(libs.exposed.json)
    implementation(libs.logback.classic)
    implementation(libs.postgresql)
    runtimeOnly(libs.postgresql.r2dbc)
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0-0.6.x-compat")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

exposed {
    migrations {
        tablesPackage.set("com.fantamomo.hc.stardancegraph.db")
        testContainersImageName.set("postgres:latest")
    }
}