plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    kotlin("jvm")
}

val minecraftVersion: String by rootProject
val enabledPlatforms: String by rootProject
val archivesBaseName: String by rootProject

base {
    archivesName.set(archivesBaseName)
}

architectury {
    common(enabledPlatforms.split(","))
}

loom {
    silentMojangMappingsLicense()
}

repositories {
    maven("https://repo.spongepowered.org/maven/")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())

    // Mixin
    compileOnly("org.spongepowered:mixin:0.8.5")
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")

    // JSON5 parser
    implementation("de.marhali:json5-java:3.0.0")

    // GraalVM JavaScript engine
    // Note: We depend on the actual JAR artifacts, not the POM-only js-community
    // to avoid IntelliJ incorrectly adding POM files to the classpath
    implementation("org.graalvm.polyglot:polyglot:24.1.0")
    implementation("org.graalvm.js:js-community:24.1.0")
    implementation("org.graalvm.js:js-language:24.1.0")
    implementation("org.graalvm.truffle:truffle-runtime:24.1.0")

    // JUnit 5 for testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")

    // Mockito for mocking Minecraft classes in tests
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
}

java {
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()

    // Ensure tests run in headless mode
    systemProperty("java.awt.headless", "true")

    // Provide more memory for tests
    maxHeapSize = "2G"

    // Show test output
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }

    // Fail fast in CI
    if (System.getenv("CI") != null) {
        failFast = true
    }
}

// TypeScript definitions are now generated via /rjs probe in-game
// This ensures accurate introspection with full runtime context
