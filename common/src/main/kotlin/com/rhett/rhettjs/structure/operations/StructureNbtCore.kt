package com.rhett.rhettjs.structure.operations

import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.structure.models.StructureData
import com.rhett.rhettjs.structure.utils.NbtConversionUtils
import com.rhett.rhettjs.structure.utils.StructureNbtUtils
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors
import kotlin.io.path.*

/**
 * Core file operations for structure NBT files.
 * Handles load, save, exists, list, remove, and getSize operations.
 */
class StructureNbtCore(
    private val server: MinecraftServer,
    private val structuresPath: Path,
    private val nbtApi: com.rhett.rhettjs.api.NBTAPI
) {

    /**
     * Check if a structure exists (async).
     * Returns Promise<boolean>.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun exists(nameWithNamespace: String): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()

        try {
            val (namespace, name) = StructureNbtUtils.parseStructureName(nameWithNamespace)
            val path = StructureNbtUtils.getStructurePath(structuresPath, namespace, name)

            if (path == null) {
                future.complete(false)
                return future
            }

            future.complete(path.exists() && path.isRegularFile())
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * List available structures from resource system (async).
     * Uses StructureTemplateManager to list from world/generated/, datapacks, and mods.
     * Returns Promise<string[]> where each name is in format "namespace:name".
     *
     * @param namespaceFilter Optional namespace filter (null = all namespaces)
     */
    fun list(namespaceFilter: String?): CompletableFuture<List<String>> {
        val future = CompletableFuture<List<String>>()

        try {
            val task: () -> Unit = {
                try {
                    // List all structures from resource system
                    // This includes: world/generated/, datapacks/, mod resources
                    // Convert Java Stream to Kotlin list first
                    val allTemplates = server.structureManager.listTemplates()
                        .collect(Collectors.toList())

                    val structures = allTemplates
                        .filter { loc ->
                            // Filter by namespace if specified
                            namespaceFilter == null || loc.namespace == namespaceFilter
                        }
                        .filter { loc ->
                            // Exclude rjs-large pieces from regular list
                            // (they're listed separately via LargeStructureNbt.list())
                            !loc.path.startsWith("${StructureNbtUtils.RJS_LARGE_SUBDIR}/")
                        }
                        .map { loc -> "${loc.namespace}:${loc.path}" }
                        .sorted()

                    ConfigManager.debug("[StructureNbtCore] Listed ${structures.size} structures from resource system")
                    future.complete(structures)
                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
            }

            // If already on server thread, execute immediately (e.g., during tab completion)
            // Otherwise schedule on server thread
            if (server.isSameThread()) {
                task()
            } else {
                server.execute(task)
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * List structures from world/generated/ directory only (async).
     * Unlike list(), this ONLY reads from world/generated/<namespace>/structures/
     * and excludes vanilla datapacks, mods, etc.
     * Returns Promise<string[]> where each name is in format "namespace:name".
     *
     * @param namespaceFilter Optional namespace filter (null = all namespaces in generated/)
     */
    fun listGenerated(namespaceFilter: String?): CompletableFuture<List<String>> {
        val future = CompletableFuture<List<String>>()

        try {
            if (!structuresPath.exists()) {
                future.complete(emptyList())
                return future
            }

            val structures = mutableListOf<String>()

            // Walk through world/generated/ directory
            Files.walk(structuresPath)
                .filter { path ->
                    // Only .nbt files
                    path.toString().endsWith(".nbt") && Files.isRegularFile(path)
                }
                .forEach { nbtPath ->
                    try {
                        // Convert filesystem path back to namespace:name format
                        // Path: world/generated/minecraft/structures/myhouse.nbt
                        // Result: minecraft:myhouse
                        val relativePath = structuresPath.relativize(nbtPath)
                        val parts = relativePath.toString().split(File.separator)

                        if (parts.size >= 3) {
                            val namespace = parts[0]
                            // parts[1] should be "structures"
                            // parts[2...] is the structure path (may have subdirs)

                            // Filter by namespace if specified
                            if (namespaceFilter == null || namespace == namespaceFilter) {
                                // Reconstruct structure name (remove .nbt extension)
                                val structurePath = parts.drop(2).joinToString("/")
                                val structureName = structurePath.removeSuffix(".nbt")

                                // Exclude rjs-large pieces
                                if (!structureName.startsWith("${StructureNbtUtils.RJS_LARGE_SUBDIR}/")) {
                                    structures.add("$namespace:$structureName")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        ConfigManager.debug("[StructureNbtCore] Failed to parse structure path: ${nbtPath}: ${e.message}")
                    }
                }

            structures.sort()
            ConfigManager.debug("[StructureNbtCore] Listed ${structures.size} structures from generated/ (namespace=${namespaceFilter ?: "all"})")
            future.complete(structures)
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Remove a structure file (async).
     * Returns Promise<boolean> (true if removed, false if didn't exist).
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun remove(nameWithNamespace: String): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()

        try {
            val (namespace, name) = StructureNbtUtils.parseStructureName(nameWithNamespace)
            val path = StructureNbtUtils.getStructurePath(structuresPath, namespace, name)

            if (path == null || !path.exists()) {
                future.complete(false)
                return future
            }

            Files.delete(path)
            ConfigManager.debug("[StructureNbtCore] Removed structure: $nameWithNamespace")
            future.complete(true)
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Load a structure from resource system (async).
     * Uses StructureTemplateManager to load from world/generated/, datapacks, or mods.
     * Returns Promise<StructureData>.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun load(nameWithNamespace: String): CompletableFuture<StructureData> {
        val future = CompletableFuture<StructureData>()

        try {
            val (namespace, name) = StructureNbtUtils.parseStructureName(nameWithNamespace)
            val resourceLocation = ResourceLocation.fromNamespaceAndPath(namespace, name)

            // Execute on main thread to access StructureTemplateManager
            server.execute {
                try {
                    // Load structure using Minecraft's resource system
                    // This searches: world/generated/, datapacks/, mod resources (in priority order)
                    val templateOpt = server.structureManager.get(resourceLocation)

                    if (templateOpt.isEmpty) {
                        future.completeExceptionally(IllegalArgumentException("Structure not found: $nameWithNamespace"))
                        return@execute
                    }

                    val template = templateOpt.get()

                    // Convert StructureTemplate to NBT then parse to StructureData
                    val nbt = template.save(CompoundTag())

                    // Parse structure data from NBT
                    val structureData = NbtConversionUtils.parseStructureNBT(nbt)

                    ConfigManager.debug("[StructureNbtCore] Loaded structure from resource system: $nameWithNamespace (${structureData.blocks.size} blocks)")
                    future.complete(structureData)
                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Save a structure to file (async) with automatic backup.
     * Returns Promise<void>.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param data Structure data to save
     * @param skipBackup If true, skip automatic backup (used for large structure pieces)
     */
    fun save(nameWithNamespace: String, data: StructureData, skipBackup: Boolean = false): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        try {
            val (namespace, name) = StructureNbtUtils.parseStructureName(nameWithNamespace)

            // Validate structure name
            StructureNbtUtils.validateStructureName(name)

            // Calculate relative path: namespace/structures/name.nbt
            // This matches Minecraft's generated folder format (plural): generated/namespace/structures/name.nbt
            val relativePath = "$namespace/${StructureNbtUtils.GENERATED_STRUCTURES_DIR}/$name.nbt"

            // Convert structure data to JS-friendly map
            val jsData = NbtConversionUtils.structureDataToJsMap(data)

            // Write using NBTAPI (handles backups automatically unless skipBackup=true)
            nbtApi.write(relativePath, jsData, skipBackup = skipBackup)

            ConfigManager.debug("[StructureNbtCore] Saved structure: $nameWithNamespace (${data.blocks.size} blocks, backup=${!skipBackup})")
            future.complete(null)
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Get the size of a single structure (async).
     * For large structures, use LargeStructureNbt.getSize().
     * Returns Promise<{x, y, z}>.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun getSize(nameWithNamespace: String): CompletableFuture<Map<String, Int>> {
        val future = CompletableFuture<Map<String, Int>>()

        try {
            val loadFuture = load(nameWithNamespace)

            loadFuture.whenComplete { data, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                } else {
                    future.complete(mapOf("x" to data.size.x, "y" to data.size.y, "z" to data.size.z))
                }
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }
}
