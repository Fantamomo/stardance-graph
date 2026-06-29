plugins {
    kotlin("jvm") version "2.4.0"
    alias(libs.plugins.kotlin.serialization)
    id("org.jetbrains.exposed.plugin") version "1.3.0"
    id("com.gradleup.shadow") version "9.4.3"
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
    implementation(ktorLibs.client.core)
    implementation(ktorLibs.client.okhttp)
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0-0.6.x-compat")
    implementation("org.jsoup:jsoup:1.22.2")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.concurrent.atomics.ExperimentalAtomicApi")
    }
}

tasks.test {
    useJUnitPlatform()
}

exposed {
    migrations {
        tablesPackage.set("com.fantamomo.hc.stardancegraph.db")
        testContainersImageName.set("postgres:latest")
        fileDirectory.set(layout.projectDirectory.dir("src/main/resources/db/migration"))
    }
}
tasks {
    shadowJar {
        mergeServiceFiles()

        manifest {
            attributes(
                "Main-Class" to "com.fantamomo.hc.stardancegraph.MainKt"
            )
        }
    }
}