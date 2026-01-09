package com.rhett.rhettjs.structure

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.structure.models.StructureData
import com.rhett.rhettjs.structure.models.StructureSize
import com.rhett.rhettjs.world.models.BlockData
import com.rhett.rhettjs.world.models.PositionedBlock
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtIo
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors
import kotlin.io.path.*

/**
 * Manager for Structure API operations with JavaScript.
 *
 * This is the anti-corruption layer between JavaScript and Minecraft structure files.
 * It ensures:
 * - All file I/O is async (return CompletableFuture)
 * - No Minecraft types exposed to JavaScript
 * - Pure JS objects using adapters
 * - Structure files stored in world/structures/ directory
 *
 * Design principles:
 * - Async for I/O: All operations return CompletableFuture
 * - Anti-corruption: Convert all MC types to JS via models
 * - Namespace format: "[namespace:]name" (defaults to "minecraft:")
 * - Position objects: {x, y, z, dimension?}
 */
object StructureNbtManager {

    @Volatile
    private var server: MinecraftServer? = null

    @Volatile
    private var graalContext: Context? = null

    @Volatile
    private var structuresPath: Path? = null

    @Volatile
    private var nbtApi: com.rhett.rhettjs.api.NBTAPI? = null

    /**
     * Generated folder structure subdirectory name.
     * Per Minecraft wiki: runtime structures are saved to generated/<namespace>/structures/ (plural)
     * This differs from datapack structures which use data/<namespace>/structure/ (singular).
     * Minecraft's StructureTemplateManager searches both locations.
     */
    private const val GENERATED_STRUCTURES_DIR = "structures"

    /**
     * Set the Minecraft server reference.
     * Called during server startup.
     */
    fun setServer(minecraftServer: MinecraftServer) {
        server = minecraftServer
        // Structures stored in world/generated/ (namespaced)
        structuresPath = minecraftServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("generated")

        val backupsPath = minecraftServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("backups").resolve(GENERATED_STRUCTURES_DIR)

        // Ensure base directories exist
        structuresPath?.let { path ->
            if (!path.exists()) {
                Files.createDirectories(path)
                ConfigManager.debug("[StructureManager] Created generated directory: $path")
            }
        }
        if (!backupsPath.exists()) {
            Files.createDirectories(backupsPath)
            ConfigManager.debug("[StructureManager] Created backups directory: $backupsPath")
        }

        // Note: NBTAPI still uses structuresPath as root, but we organize as <root>/<namespace>/GENERATED_STRUCTURES_DIR/
        // This matches Minecraft's convention: generated/<namespace>/structures/ (plural for runtime saves)
        nbtApi = com.rhett.rhettjs.api.NBTAPI(structuresPath, backupsPath)

        ConfigManager.debug("[StructureManager] Minecraft server reference set")
    }

    /**
     * Set the GraalVM context reference.
     * Called when GraalEngine initializes the context.
     */
    fun setContext(context: Context) {
        graalContext = context
        ConfigManager.debug("[StructureManager] GraalVM context reference set")
    }

    /**
     * Parse structure name format "[namespace:]name".
     * Returns pair of (namespace, name).
     * Defaults to "minecraft" namespace if not specified.
     */
    private fun parseStructureName(nameWithNamespace: String): Pair<String, String> {
        return if (':' in nameWithNamespace) {
            val parts = nameWithNamespace.split(':', limit = 2)
            parts[0] to parts[1]
        } else {
            "minecraft" to nameWithNamespace
        }
    }

    /**
     * Get the file path for a structure in the generated folder.
     * Uses Minecraft's convention: generated/<namespace>/structures/name.nbt (plural)
     * Note: Datapack structures use data/<namespace>/structure/ (singular), but we save to generated/.
     * Minecraft's StructureTemplateManager reads from both locations.
     */
    private fun getStructurePath(namespace: String, name: String): Path? {
        val basePath = structuresPath ?: return null
        return basePath.resolve(namespace).resolve(GENERATED_STRUCTURES_DIR).resolve("$name.nbt")
    }

    /**
     * Check if a structure exists (async).
     * Returns Promise<boolean>.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun exists(nameWithNamespace: String): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()

        try {
            val (namespace, name) = parseStructureName(nameWithNamespace)
            val path = getStructurePath(namespace, name)

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
        val srv = server

        if (srv == null) {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }

        try {
            val task: () -> Unit = {
                try {
                    // List all structures from resource system
                    // This includes: world/generated/, datapacks/, mod resources
                    // Convert Java Stream to Kotlin list first
                    val allTemplates = srv.structureManager.listTemplates()
                        .collect(Collectors.toList())

                    val structures = allTemplates
                        .filter { loc ->
                            // Filter by namespace if specified
                            namespaceFilter == null || loc.namespace == namespaceFilter
                        }
                        .filter { loc ->
                            // Exclude rjs-large pieces from regular list
                            // (they're listed separately via LargeStructureNbt.list())
                            !loc.path.startsWith("rjs-large/")
                        }
                        .map { loc -> "${loc.namespace}:${loc.path}" }
                        .sorted()

                    ConfigManager.debug("[StructureManager] Listed ${structures.size} structures from resource system")
                    future.complete(structures)
                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
            }

            // If already on server thread, execute immediately (e.g., during tab completion)
            // Otherwise schedule on server thread
            if (srv.isSameThread()) {
                task()
            } else {
                srv.execute(task)
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
            val basePath = structuresPath ?: run {
                future.completeExceptionally(IllegalStateException("Structures path not initialized"))
                return future
            }

            if (!basePath.exists()) {
                future.complete(emptyList())
                return future
            }

            val structures = mutableListOf<String>()

            // Walk through world/generated/ directory
            Files.walk(basePath)
                .filter { path ->
                    // Only .nbt files
                    path.toString().endsWith(".nbt") && Files.isRegularFile(path)
                }
                .forEach { nbtPath ->
                    try {
                        // Convert filesystem path back to namespace:name format
                        // Path: world/generated/minecraft/structures/myhouse.nbt
                        // Result: minecraft:myhouse
                        val relativePath = basePath.relativize(nbtPath)
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
                                if (!structureName.startsWith("rjs-large/")) {
                                    structures.add("$namespace:$structureName")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        ConfigManager.debug("[StructureManager] Failed to parse structure path: ${nbtPath}: ${e.message}")
                    }
                }

            structures.sort()
            ConfigManager.debug("[StructureManager] Listed ${structures.size} structures from generated/ (namespace=${namespaceFilter ?: "all"})")
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
            val (namespace, name) = parseStructureName(nameWithNamespace)
            val path = getStructurePath(namespace, name)

            if (path == null || !path.exists()) {
                future.complete(false)
                return future
            }

            Files.delete(path)
            ConfigManager.debug("[StructureManager] Removed structure: $nameWithNamespace")
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
        val srv = server

        if (srv == null) {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }

        try {
            val (namespace, name) = parseStructureName(nameWithNamespace)
            val resourceLocation = ResourceLocation.fromNamespaceAndPath(namespace, name)

            // Execute on main thread to access StructureTemplateManager
            srv.execute {
                try {
                    // Load structure using Minecraft's resource system
                    // This searches: world/generated/, datapacks/, mod resources (in priority order)
                    val templateOpt = srv.structureManager.get(resourceLocation)

                    if (templateOpt.isEmpty) {
                        future.completeExceptionally(IllegalArgumentException("Structure not found: $nameWithNamespace"))
                        return@execute
                    }

                    val template = templateOpt.get()

                    // Convert StructureTemplate to NBT then parse to StructureData
                    val registryAccess = srv.registryAccess()
                    val nbt = template.save(CompoundTag())

                    // Parse structure data from NBT
                    val structureData = parseStructureNBT(nbt)

                    ConfigManager.debug("[StructureManager] Loaded structure from resource system: $nameWithNamespace (${structureData.blocks.size} blocks)")
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
            val api = nbtApi
            if (api == null) {
                future.completeExceptionally(IllegalStateException("NBTAPI not initialized"))
                return future
            }

            val (namespace, name) = parseStructureName(nameWithNamespace)

            // Validate structure name contains only valid characters for ResourceLocations
            if (!name.matches(Regex("^[a-z0-9/._-]+$"))) {
                future.completeExceptionally(IllegalArgumentException(
                    "Invalid structure name '$name'. Structure names must contain only lowercase letters, numbers, and characters /._-\n" +
                    "Example: 'my_structure' or 'buildings/house_1'"
                ))
                return future
            }

            // Calculate relative path: namespace/structures/name.nbt
            // This matches Minecraft's generated folder format (plural): generated/namespace/structures/name.nbt
            val relativePath = "$namespace/$GENERATED_STRUCTURES_DIR/$name.nbt"

            // Convert structure data to JS-friendly map
            val jsData = structureDataToJsMap(data)

            // Write using NBTAPI (handles backups automatically unless skipBackup=true)
            api.write(relativePath, jsData, skipBackup = skipBackup)

            ConfigManager.debug("[StructureManager] Saved structure: $nameWithNamespace (${data.blocks.size} blocks, backup=${!skipBackup})")
            future.complete(null)
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Parse structure data from Minecraft NBT format.
     * Converts NBT → StructureData (anti-corruption shield).
     */
    private fun parseStructureNBT(nbt: CompoundTag): StructureData {
        // Read size (handle both int array and list formats)
        val size = if (nbt.contains("size", 11)) {
            // TAG_INT_ARRAY (type 11) - our write format
            val sizeList = nbt.getIntArray("size")
            StructureSize(
                x = sizeList[0],
                y = sizeList[1],
                z = sizeList[2]
            )
        } else if (nbt.contains("size", 9)) {
            // TAG_LIST (type 9) - StructureTemplate.save() format
            val sizeList = nbt.getList("size", 3) // 3 = TAG_INT
            if (sizeList.size < 3) {
                throw IllegalArgumentException("Structure NBT 'size' list has ${sizeList.size} elements, expected 3")
            }
            StructureSize(
                x = sizeList.getInt(0),
                y = sizeList.getInt(1),
                z = sizeList.getInt(2)
            )
        } else {
            throw IllegalArgumentException("Structure NBT missing 'size' field")
        }

        // Read palette (block states)
        val paletteList = nbt.getList("palette", 10) // 10 = CompoundTag
        val palette = mutableListOf<BlockData>()

        for (i in 0 until paletteList.size) {
            val blockNBT = paletteList.getCompound(i)
            val blockId = blockNBT.getString("Name")

            val properties = mutableMapOf<String, String>()
            if (blockNBT.contains("Properties")) {
                val propsNBT = blockNBT.getCompound("Properties")
                propsNBT.allKeys.forEach { key ->
                    properties[key] = propsNBT.getString(key)
                }
            }

            palette.add(BlockData(name = blockId, properties = properties))
        }

        // Read blocks (positions + palette indices)
        val blocksList = nbt.getList("blocks", 10) // 10 = CompoundTag
        val blocks = mutableListOf<PositionedBlock>()

        for (i in 0 until blocksList.size) {
            val blockNBT = blocksList.getCompound(i)

            // Read position (handle both int array and list formats)
            val (posX, posY, posZ) = if (blockNBT.contains("pos", 11)) {
                // TAG_INT_ARRAY (type 11) - our write format
                val pos = blockNBT.getIntArray("pos")
                Triple(pos[0], pos[1], pos[2])
            } else if (blockNBT.contains("pos", 9)) {
                // TAG_LIST (type 9) - StructureTemplate.save() format
                val posList = blockNBT.getList("pos", 3) // 3 = TAG_INT
                Triple(posList.getInt(0), posList.getInt(1), posList.getInt(2))
            } else {
                continue // Skip blocks without valid position
            }

            val state = blockNBT.getInt("state")

            val blockData = palette.getOrNull(state)
            if (blockData != null) {
                blocks.add(
                    PositionedBlock(
                        x = posX,
                        y = posY,
                        z = posZ,
                        block = blockData,
                        blockEntityData = if (blockNBT.contains("nbt")) blockNBT.getCompound("nbt") else null
                    )
                )
            }
        }

        // Read entities
        val entities = mutableListOf<com.rhett.rhettjs.structure.models.StructureEntity>()
        if (nbt.contains("entities", 9)) { // 9 = TAG_LIST
            val entitiesList = nbt.getList("entities", 10) // 10 = CompoundTag

            for (i in 0 until entitiesList.size) {
                val entityNBT = entitiesList.getCompound(i)

                // Read blockPos (int array or list)
                val blockPos = if (entityNBT.contains("blockPos", 11)) {
                    // TAG_INT_ARRAY (type 11)
                    val arr = entityNBT.getIntArray("blockPos")
                    listOf(arr[0], arr[1], arr[2])
                } else if (entityNBT.contains("blockPos", 9)) {
                    // TAG_LIST (type 9)
                    val list = entityNBT.getList("blockPos", 3) // 3 = TAG_INT
                    listOf(list.getInt(0), list.getInt(1), list.getInt(2))
                } else {
                    continue // Skip entities without blockPos
                }

                // Read pos (double list)
                val pos = if (entityNBT.contains("pos", 9)) {
                    val posList = entityNBT.getList("pos", 6) // 6 = TAG_DOUBLE
                    listOf(posList.getDouble(0), posList.getDouble(1), posList.getDouble(2))
                } else {
                    continue // Skip entities without pos
                }

                // Read entity NBT data
                val entityData = if (entityNBT.contains("nbt")) {
                    entityNBT.getCompound("nbt")
                } else {
                    CompoundTag() // Empty NBT if not present
                }

                entities.add(
                    com.rhett.rhettjs.structure.models.StructureEntity(
                        blockPos = blockPos,
                        pos = pos,
                        nbt = entityData
                    )
                )
            }
        }

        // Read metadata (custom)
        val metadata = mutableMapOf<String, String>()
        if (nbt.contains("metadata")) {
            val metaNBT = nbt.getCompound("metadata")
            metaNBT.allKeys.forEach { key ->
                metadata[key] = metaNBT.getString(key)
            }
        }

        return StructureData(
            size = size,
            blocks = blocks,
            entities = entities,
            metadata = metadata
        )
    }

    /**
     * Create Minecraft NBT structure format from StructureData.
     * Converts StructureData → NBT (anti-corruption shield).
     */
    private fun createStructureNBT(data: StructureData): CompoundTag {
        val nbt = CompoundTag()

        // Write size
        nbt.putIntArray("size", intArrayOf(data.size.x, data.size.y, data.size.z))

        // Build palette (unique block states)
        val palette = mutableListOf<BlockData>()
        val paletteIndices = mutableMapOf<BlockData, Int>()

        data.blocks.forEach { block ->
            if (block.block !in paletteIndices) {
                paletteIndices[block.block] = palette.size
                palette.add(block.block)
            }
        }

        // Write palette
        val paletteList = ListTag()
        palette.forEach { blockData ->
            val blockNBT = CompoundTag()
            blockNBT.putString("Name", blockData.name)

            if (blockData.properties.isNotEmpty()) {
                val propsNBT = CompoundTag()
                blockData.properties.forEach { (key, value) ->
                    propsNBT.putString(key, value)
                }
                blockNBT.put("Properties", propsNBT)
            }

            paletteList.add(blockNBT)
        }
        nbt.put("palette", paletteList)

        // Write blocks
        val blocksList = ListTag()
        data.blocks.forEach { block ->
            val blockNBT = CompoundTag()
            blockNBT.putIntArray("pos", intArrayOf(block.x, block.y, block.z))
            blockNBT.putInt("state", paletteIndices[block.block] ?: 0)

            // Write block entity data if present
            if (block.blockEntityData != null && block.blockEntityData is CompoundTag) {
                blockNBT.put("nbt", block.blockEntityData as CompoundTag)
            }

            blocksList.add(blockNBT)
        }
        nbt.put("blocks", blocksList)

        // Write entities
        if (data.entities.isNotEmpty()) {
            val entitiesList = ListTag()
            data.entities.forEach { entity ->
                val entityNBT = CompoundTag()

                // Write blockPos as int array
                entityNBT.putIntArray("blockPos", intArrayOf(
                    entity.blockPos[0],
                    entity.blockPos[1],
                    entity.blockPos[2]
                ))

                // Write pos as double list
                val posList = net.minecraft.nbt.ListTag()
                posList.add(net.minecraft.nbt.DoubleTag.valueOf(entity.pos[0]))
                posList.add(net.minecraft.nbt.DoubleTag.valueOf(entity.pos[1]))
                posList.add(net.minecraft.nbt.DoubleTag.valueOf(entity.pos[2]))
                entityNBT.put("pos", posList)

                // Write entity NBT data
                entityNBT.put("nbt", entity.nbt)

                entitiesList.add(entityNBT)
            }
            nbt.put("entities", entitiesList)
        }

        // Write metadata
        if (data.metadata.isNotEmpty()) {
            val metaNBT = CompoundTag()
            data.metadata.forEach { (key, value) ->
                metaNBT.putString(key, value)
            }
            nbt.put("metadata", metaNBT)
        }

        // Write data version (current MC version)
        nbt.putInt("DataVersion", net.minecraft.SharedConstants.getCurrentVersion().dataVersion.version)

        return nbt
    }

    /**
     * Convert StructureData to JS-friendly Map format.
     * This format can be passed to NBTAPI.write() which will convert it to NBT.
     */
    private fun structureDataToJsMap(data: StructureData): Map<String, Any> {
        val map = mutableMapOf<String, Any>()

        // Write size
        map["size"] = listOf(data.size.x, data.size.y, data.size.z)

        // Build palette (unique block states)
        val palette = mutableListOf<BlockData>()
        val paletteIndices = mutableMapOf<BlockData, Int>()

        data.blocks.forEach { block ->
            if (block.block !in paletteIndices) {
                paletteIndices[block.block] = palette.size
                palette.add(block.block)
            }
        }

        // Write palette
        val paletteList = mutableListOf<Map<String, Any>>()
        palette.forEach { blockData ->
            val blockMap = mutableMapOf<String, Any>()
            blockMap["Name"] = blockData.name

            if (blockData.properties.isNotEmpty()) {
                blockMap["Properties"] = blockData.properties.toMap()
            }

            paletteList.add(blockMap)
        }
        map["palette"] = paletteList

        // Write blocks
        val blocksList = mutableListOf<Map<String, Any>>()
        data.blocks.forEach { block ->
            val blockMap = mutableMapOf<String, Any>()
            blockMap["pos"] = listOf(block.x, block.y, block.z)
            blockMap["state"] = paletteIndices[block.block] ?: 0

            // Write block entity data if present
            // Note: NBTAPI will handle CompoundTag → Map conversion if needed
            if (block.blockEntityData != null) {
                blockMap["nbt"] = block.blockEntityData as Any
            }

            blocksList.add(blockMap)
        }
        map["blocks"] = blocksList

        // Write entities
        if (data.entities.isNotEmpty()) {
            val entitiesList = mutableListOf<Map<String, Any>>()
            data.entities.forEach { entity ->
                val entityMap = mutableMapOf<String, Any>()
                entityMap["blockPos"] = entity.blockPos
                entityMap["pos"] = entity.pos
                entityMap["nbt"] = entity.nbt
                entitiesList.add(entityMap)
            }
            map["entities"] = entitiesList
        }

        // Write metadata
        if (data.metadata.isNotEmpty()) {
            map["metadata"] = data.metadata.toMap()
        }

        // Write data version (current MC version)
        map["DataVersion"] = net.minecraft.SharedConstants.getCurrentVersion().dataVersion.version

        return map
    }

    /**
     * Capture a region and convert to structure data (async).
     * Returns Promise<void> after saving structure file.
     *
     * @param pos1 First corner position {x, y, z, dimension?}
     * @param pos2 Second corner position {x, y, z, dimension?}
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param options Optional options {author?: string, description?: string}
     * @param skipBackup If true, skip automatic backup (used for large structure pieces)
     */
    fun capture(pos1: Value, pos2: Value, nameWithNamespace: String, options: Value?, skipBackup: Boolean = false): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }

        try {
            // Extract positions from JS
            val x1 = pos1.getMember("x").asInt()
            val y1 = pos1.getMember("y").asInt()
            val z1 = pos1.getMember("z").asInt()
            val x2 = pos2.getMember("x").asInt()
            val y2 = pos2.getMember("y").asInt()
            val z2 = pos2.getMember("z").asInt()

            // Get dimension from pos1 unless options.dimension is specified
            val dimension = if (options != null && options.hasMember("dimension")) {
                options.getMember("dimension").asString()
            } else if (pos1.hasMember("dimension")) {
                pos1.getMember("dimension").asString()
            } else {
                "minecraft:overworld"
            }

            // Extract metadata from options
            val metadata = mutableMapOf<String, String>()
            if (options != null) {
                if (options.hasMember("author")) {
                    metadata["author"] = options.getMember("author").asString()
                }
                if (options.hasMember("description")) {
                    metadata["description"] = options.getMember("description").asString()
                }
            }

            // Execute capture on main thread
            srv.execute {
                try {
                    val worldAdapter = com.rhett.rhettjs.world.WorldManager
                    val level = worldAdapter.getLevel(dimension)

                    if (level == null) {
                        future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dimension"))
                        return@execute
                    }

                    // Normalize coordinates (min to max)
                    val minX = minOf(x1, x2)
                    val minY = minOf(y1, y2)
                    val minZ = minOf(z1, z2)
                    val maxX = maxOf(x1, x2)
                    val maxY = maxOf(y1, y2)
                    val maxZ = maxOf(z1, z2)

                    val sizeX = maxX - minX + 1
                    val sizeY = maxY - minY + 1
                    val sizeZ = maxZ - minZ + 1

                    // Capture blocks in the region
                    val blocks = mutableListOf<PositionedBlock>()

                    for (x in minX..maxX) {
                        for (y in minY..maxY) {
                            for (z in minZ..maxZ) {
                                val blockPos = net.minecraft.core.BlockPos(x, y, z)
                                val blockState = level.getBlockState(blockPos)

                                // Convert to BlockData model
                                val blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                    .getKey(blockState.block)?.toString() ?: "minecraft:air"

                                val properties = mutableMapOf<String, String>()
                                blockState.values.forEach { (property, value) ->
                                    properties[property.name] = value.toString()
                                }

                                // Get block entity data if present
                                val blockEntity = level.getBlockEntity(blockPos)
                                val blockEntityData = if (blockEntity != null) {
                                    blockEntity.saveWithoutMetadata(level.registryAccess())
                                } else {
                                    null
                                }

                                // Store as relative position
                                blocks.add(
                                    PositionedBlock(
                                        x = x - minX,
                                        y = y - minY,
                                        z = z - minZ,
                                        block = BlockData(name = blockId, properties = properties),
                                        blockEntityData = blockEntityData
                                    )
                                )
                            }
                        }
                    }

                    // Create structure data
                    val structureData = StructureData(
                        size = StructureSize(sizeX, sizeY, sizeZ),
                        blocks = blocks,
                        metadata = metadata
                    )

                    // Save structure to file
                    val saveFuture = save(nameWithNamespace, structureData, skipBackup)
                    saveFuture.whenComplete { _, throwable ->
                        if (throwable != null) {
                            future.completeExceptionally(throwable)
                        } else {
                            ConfigManager.debug("[StructureManager] Captured structure: $nameWithNamespace (${blocks.size} blocks, backup=${!skipBackup})")
                            future.complete(null)
                        }
                    }

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
     * Place a structure at a position (async).
     * Returns Promise<void> after placing all blocks.
     *
     * @param position Position to place structure {x, y, z, dimension?}
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param options Optional options {rotation?: 0|90|180|270, centered?: boolean}
     */
    fun place(position: Value, nameWithNamespace: String, options: Value?): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }

        try {
            // Extract position from JS
            val x = position.getMember("x").asInt()
            val y = position.getMember("y").asInt()
            val z = position.getMember("z").asInt()

            // Get dimension from position unless options.dimension is specified
            val dimension = if (options != null && options.hasMember("dimension")) {
                options.getMember("dimension").asString()
            } else if (position.hasMember("dimension")) {
                position.getMember("dimension").asString()
            } else {
                "minecraft:overworld"
            }

            // Extract options
            val rotation = if (options != null && options.hasMember("rotation")) {
                options.getMember("rotation").asInt()
            } else {
                0
            }

            val centered = if (options != null && options.hasMember("centered")) {
                options.getMember("centered").asBoolean()
            } else {
                false
            }

            val mode = if (options != null && options.hasMember("mode")) {
                options.getMember("mode").asString()
            } else {
                "replace"
            }

            // Load structure first
            val loadFuture = load(nameWithNamespace)

            loadFuture.whenComplete { structureData, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                // Execute placement on main thread
                srv.execute {
                    try {
                        val worldAdapter = com.rhett.rhettjs.world.WorldManager
                        val level = worldAdapter.getLevel(dimension)

                        if (level == null) {
                            future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dimension"))
                            return@execute
                        }

                        // Calculate base position (apply centering if requested)
                        val baseX = if (centered) x - structureData.size.x / 2 else x
                        val baseY = y
                        val baseZ = if (centered) z - structureData.size.z / 2 else z

                        // Apply rotation to blocks if needed
                        val rotatedBlocks = if (rotation == 0) {
                            // No rotation - use blocks as-is with base offset
                            structureData.blocks.map { block ->
                                PositionedBlock(
                                    x = baseX + block.x,
                                    y = baseY + block.y,
                                    z = baseZ + block.z,
                                    block = block.block,
                                    blockEntityData = block.blockEntityData
                                )
                            }
                        } else {
                            // Apply rotation using RotationHelper
                            val rotationHelper = com.rhett.rhettjs.world.logic.RotationHelper

                            // For single structure placement, we don't use grid positioning
                            // We rotate each block around the structure's origin (0,0,0)
                            val blockStateRotator = rotationHelper.createBlockStateRotator(rotation)

                            structureData.blocks.map { block ->
                                // Rotate position manually for single-block rotation
                                val (rotatedX, rotatedZ) = when (rotation) {
                                    90 -> Pair(-block.z, block.x)
                                    180 -> Pair(-block.x, -block.z)
                                    270 -> Pair(block.z, -block.x)
                                    else -> Pair(block.x, block.z)
                                }

                                // Rotate block state properties
                                val rotatedBlockData = blockStateRotator(block.block)

                                PositionedBlock(
                                    x = baseX + rotatedX,
                                    y = baseY + block.y,
                                    z = baseZ + rotatedZ,
                                    block = rotatedBlockData,
                                    blockEntityData = block.blockEntityData
                                )
                            }
                        }

                        // Place blocks using world adapter
                        val adapter = com.rhett.rhettjs.world.adapter.WorldAdapter(srv)
                        adapter.setBlocksInRegion(level, rotatedBlocks, updateNeighbors = true, mode = mode)

                        ConfigManager.debug("[StructureManager] Placed structure: $nameWithNamespace (${rotatedBlocks.size} blocks, rotation=$rotation, mode=$mode)")
                        future.complete(null)

                    } catch (e: Exception) {
                        future.completeExceptionally(e)
                    }
                }
            }
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

    /**
     * List all unique blocks in a structure with their counts (async).
     * Returns Promise<Map<String, Int>> of blockId → count.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun blocksList(nameWithNamespace: String): CompletableFuture<Map<String, Int>> {
        val future = CompletableFuture<Map<String, Int>>()

        try {
            val loadFuture = load(nameWithNamespace)
            loadFuture.whenComplete { structureData, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                // Count blocks by ID
                val blockCounts = mutableMapOf<String, Int>()
                structureData.blocks.forEach { block ->
                    val blockId = block.block.name
                    blockCounts[blockId] = (blockCounts[blockId] ?: 0) + 1
                }

                future.complete(blockCounts.toSortedMap())
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Extract unique mod namespaces from structure blocks (async).
     * Returns Promise<List<String>> of unique namespaces (e.g., ["minecraft", "terralith"]).
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun blocksNamespaces(nameWithNamespace: String): CompletableFuture<List<String>> {
        val future = CompletableFuture<List<String>>()

        try {
            val loadFuture = load(nameWithNamespace)
            loadFuture.whenComplete { structureData, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                // Extract unique namespaces
                val namespaces = structureData.blocks
                    .map { block -> block.block.name.substringBefore(':') }
                    .toSet()
                    .sorted()

                future.complete(namespaces)
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Replace blocks in a structure according to replacement map (async).
     * Returns Promise<void> after saving modified structure (with backup).
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param replacementMap Map of oldBlockId → newBlockId (e.g., {"terralith:stone": "minecraft:stone"})
     */
    fun blocksReplace(nameWithNamespace: String, replacementMap: Map<String, String>): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        try {
            val loadFuture = load(nameWithNamespace)
            loadFuture.whenComplete { structureData, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                // Replace blocks
                val newBlocks = structureData.blocks.map { block ->
                    val oldId = block.block.name
                    val newId = replacementMap[oldId]

                    if (newId != null) {
                        // Replace with new block ID
                        block.copy(
                            block = BlockData(name = newId, properties = block.block.properties)
                        )
                    } else {
                        // Keep original
                        block
                    }
                }

                val newStructureData = structureData.copy(blocks = newBlocks)

                // Save modified structure (with backup)
                val saveFuture = save(nameWithNamespace, newStructureData, skipBackup = false)
                saveFuture.whenComplete { _, saveThrowable ->
                    if (saveThrowable != null) {
                        future.completeExceptionally(saveThrowable)
                    } else {
                        ConfigManager.debug("[StructureManager] Replaced blocks in: $nameWithNamespace (${replacementMap.size} mappings)")
                        future.complete(null)
                    }
                }
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * List available backups for a structure (async).
     * Returns Promise<List<String>> of backup timestamps.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun listBackups(nameWithNamespace: String): CompletableFuture<List<String>> {
        val future = CompletableFuture<List<String>>()

        try {
            val api = nbtApi
            if (api == null) {
                future.completeExceptionally(IllegalStateException("NBTAPI not initialized"))
                return future
            }

            val (namespace, name) = parseStructureName(nameWithNamespace)
            val relativePath = "$namespace/$GENERATED_STRUCTURES_DIR/$name.nbt"

            // Use NBTAPI's backup listing
            val backups = api.listBackups(relativePath)
            future.complete(backups)
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Restore structure from backup (async).
     * Returns Promise<void> after restoration.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param timestamp Optional specific backup timestamp (e.g., "2026-01-05_15-30-45"), or null for most recent
     */
    fun restoreBackup(nameWithNamespace: String, timestamp: String?): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        try {
            val api = nbtApi
            if (api == null) {
                future.completeExceptionally(IllegalStateException("NBTAPI not initialized"))
                return future
            }

            val (namespace, name) = parseStructureName(nameWithNamespace)
            val relativePath = "$namespace/$GENERATED_STRUCTURES_DIR/$name.nbt"

            // Use NBTAPI's restore functionality
            val success = api.restoreFromBackup(relativePath, timestamp)
            if (success) {
                ConfigManager.debug("[StructureManager] Restored structure: $nameWithNamespace from backup ${timestamp ?: "latest"}")
                future.complete(null)
            } else {
                future.completeExceptionally(IllegalStateException("Failed to restore structure from backup"))
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Clear all state (called on reset/reload).
     * Clears context reference (context will be recreated).
     */
    fun reset() {
        graalContext = null
        ConfigManager.debug("[StructureManager] Reset complete")
    }
}
