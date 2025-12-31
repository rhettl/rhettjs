plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("com.github.johnrengelman.shadow")
    kotlin("jvm")
}

val minecraftVersion: String by rootProject
val archivesBaseName: String by rootProject
val neoforgeVersion: String by rootProject
val modVersion: String by rootProject

// Get the common project for this version
val common = stonecutter.node.sibling("common")?.project
    ?: error("No common project for $project")

base {
    archivesName.set("$archivesBaseName-neoforge")
}

architectury {
    platformSetupLoomIde()
    neoForge()
}

loom {
    silentMojangMappingsLicense()
}

repositories {
    maven("https://maven.neoforged.net/releases/")
}

// Configuration for including common module and shaded dependencies
val commonBundle by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val shadowCommon by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val shade by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

configurations {
    compileClasspath.get().extendsFrom(commonBundle)
    runtimeClasspath.get().extendsFrom(commonBundle)
    named("developmentNeoForge").get().extendsFrom(commonBundle)
    implementation.get().extendsFrom(shade)
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())

    // NeoForge
    "neoForge"("net.neoforged:neoforge:$neoforgeVersion")

    // Common module
    commonBundle(project(path = common.path, configuration = "namedElements")) { isTransitive = false }
    shadowCommon(project(path = common.path, configuration = "transformProductionNeoForge")) { isTransitive = false }

    // JSON5 parser (shaded into jar)
    shade("de.marhali:json5-java:3.0.0")

    // GraalVM JavaScript engine (shaded into jar)
    shade("org.graalvm.polyglot:polyglot:24.1.0")
    shade("org.graalvm.polyglot:js:24.1.0")
    shade("org.graalvm.polyglot:js-community:24.1.0")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testImplementation(project(common.path))
}

java {
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    inputs.property("mod_version", modVersion)

    filesMatching("META-INF/neoforge.mods.toml") {
        expand("mod_version" to modVersion)
    }
}

tasks.shadowJar {
    exclude("fabric.mod.json")
    exclude("architectury.common.json")
    // Exclude common mixin config - we use NeoForge-specific one without refmap
    exclude("rhettjs.mixins.json")
    exclude("rhettjs-common-refmap.json")
    configurations = listOf(shadowCommon, shade)
    archiveClassifier.set("dev-shadow")

    // Relocate JSON5 to avoid conflicts with other mods
    relocate("de.marhali.json5", "com.rhett.rhettjs.shadow.json5")
}

tasks.remapJar {
    injectAccessWidener.set(true)
    inputFile.set(tasks.shadowJar.get().archiveFile)
    dependsOn(tasks.shadowJar)
    archiveClassifier.set(null as String?)
}

tasks.jar {
    archiveClassifier.set("dev")
}

tasks.named<Jar>("sourcesJar") {
    val commonSources = common.tasks.named<Jar>("sourcesJar")
    dependsOn(commonSources)
    from(commonSources.map { zipTree(it.archiveFile) })
}

components.named<AdhocComponentWithVariants>("java") {
    withVariantsFromConfiguration(configurations.named("shadowRuntimeElements").get()) {
        skip()
    }
}
