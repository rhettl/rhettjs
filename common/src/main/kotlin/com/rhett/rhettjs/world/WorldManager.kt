package com.rhett.rhettjs.world

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.adapter.PlayerAdapter
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.world.adapter.WorldAdapter
import com.rhett.rhettjs.world.models.BlockData
import com.rhett.rhettjs.world.models.PositionedBlock
import com.rhett.rhettjs.world.models.Region
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyObject
import java.util.concurrent.CompletableFuture

/**
 * Manager for World API operations with JavaScript.
 *
 * This is the anti-corruption layer between JavaScript and Minecraft world.
 * It ensures:
 * - All world access is on main thread
 * - All operations are async (return CompletableFuture)
 * - No Minecraft types exposed to JavaScript
 * - Pure JS objects using adapters
 *
 * Design principles:
 * - Async for I/O: All world operations return CompletableFuture
 * - Main thread safety: Use server.execute() for all world access
 * - Anti-corruption: Convert all MC types to JS via adapters
 */
object WorldManager {

    @Volatile
    private var server: MinecraftServer? = null

    @Volatile
    private var graalContext: Context? = null

    @Volatile
    private var worldAdapter: WorldAdapter? = null

    /**
     * Set the Minecraft server reference.
     * Called during server startup.
     */
    fun setServer(minecraftServer: MinecraftServer) {
        server = minecraftServer
        worldAdapter = WorldAdapter(minecraftServer)
        ConfigManager.debug("[WorldManager] Minecraft server reference set")
    }

    /**
     * Set the GraalVM context reference.
     * Called when GraalEngine initializes the context.
     */
    fun setContext(context: Context) {
        graalContext = context
        ConfigManager.debug("[WorldManager] GraalVM context reference set")
    }

    /**
     * Get block at position (async).
     * Returns Promise<Block> where Block is {id: string, properties: object}.
     */
    fun getBlock(position: Value): CompletableFuture<Value> {
        val future = CompletableFuture<Value>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val context = graalContext ?: run {
            future.completeExceptionally(IllegalStateException("GraalVM context not available"))
            return future
        }
        val adapter = worldAdapter ?: run {
            future.completeExceptionally(IllegalStateException("WorldAdapter not initialized"))
            return future
        }

        try {
            // Extract position from JS
            val x = position.getMember("x").asInt()
            val y = position.getMember("y").asInt()
            val z = position.getMember("z").asInt()
            val dimension = if (position.hasMember("dimension")) {
                position.getMember("dimension").asString()
            } else {
                "minecraft:overworld"
            }

            // Execute on main thread
            srv.execute {
                try {
                    val level = adapter.getLevel(dimension)
                    if (level == null) {
                        future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dimension"))
                        return@execute
                    }

                    val blockPos = BlockPos(x, y, z)
                    val blockState = level.getBlockState(blockPos)

                    // Convert to BlockData model
                    val blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blockState.block)?.toString()
                        ?: "minecraft:air"

                    val properties = mutableMapOf<String, String>()
                    blockState.values.forEach { (property, value) ->
                        properties[property.name] = value.toString()
                    }

                    // Create JS block object
                    val blockObj = ProxyObject.fromMap(mapOf(
                        "id" to blockId,
                        "properties" to properties
                    ))

                    future.complete(context.asValue(blockObj))
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
     * Get block entity data at position (async).
     * Returns Promise<object | null> where object contains block entity NBT data.
     *
     * For lecterns: { Book: {...}, Page: number }
     * For signs: { front_text: { messages: [...] }, back_text: { messages: [...] } }
     * For chests: { Items: [...] }
     * etc.
     *
     * Returns null if no block entity exists at position.
     */
    fun getBlockEntity(position: Value): CompletableFuture<Value?> {
        val future = CompletableFuture<Value?>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val context = graalContext ?: run {
            future.completeExceptionally(IllegalStateException("GraalVM context not available"))
            return future
        }
        val adapter = worldAdapter ?: run {
            future.completeExceptionally(IllegalStateException("WorldAdapter not initialized"))
            return future
        }

        try {
            // Extract position from JS
            val x = position.getMember("x").asInt()
            val y = position.getMember("y").asInt()
            val z = position.getMember("z").asInt()
            val dimension = if (position.hasMember("dimension")) {
                position.getMember("dimension").asString()
            } else {
                "minecraft:overworld"
            }

            // Execute on main thread
            srv.execute {
                try {
                    val level = adapter.getLevel(dimension)
                    if (level == null) {
                        future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dimension"))
                        return@execute
                    }

                    val pos = BlockPos(x, y, z)
                    val blockEntity = level.getBlockEntity(pos)

                    if (blockEntity == null) {
                        // No block entity at this position
                        future.complete(null)
                        return@execute
                    }

                    // Convert block entity to JS-friendly map
                    val registryAccess = level.registryAccess()
                    val nbtTag = blockEntity.saveWithoutMetadata(registryAccess)
                    val dataMap = adapter.convertNbtToMap(nbtTag) as? Map<*, *>

                    if (dataMap != null) {
                        // Convert to ProxyObject for JavaScript
                        @Suppress("UNCHECKED_CAST")
                        val jsData = context.asValue(dataMap as Map<String, Any>)
                        future.complete(jsData)
                    } else {
                        future.complete(null)
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
     * Set block at position (async).
     * Returns Promise<void>.
     */
    fun setBlock(position: Value, blockId: String, properties: Value?): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val adapter = worldAdapter ?: run {
            future.completeExceptionally(IllegalStateException("WorldAdapter not initialized"))
            return future
        }

        try {
            // Extract position from JS
            val x = position.getMember("x").asInt()
            val y = position.getMember("y").asInt()
            val z = position.getMember("z").asInt()
            val dimension = if (position.hasMember("dimension")) {
                position.getMember("dimension").asString()
            } else {
                "minecraft:overworld"
            }

            // Extract properties if provided
            val propsMap = mutableMapOf<String, String>()
            if (properties != null && properties.hasMembers()) {
                properties.memberKeys.forEach { key ->
                    propsMap[key] = properties.getMember(key).asString()
                }
            }

            // Execute on main thread
            srv.execute {
                try {
                    val level = adapter.getLevel(dimension)
                    if (level == null) {
                        future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dimension"))
                        return@execute
                    }

                    // Create PositionedBlock
                    val block = PositionedBlock(
                        x = x,
                        y = y,
                        z = z,
                        block = BlockData(name = blockId, properties = propsMap),
                        blockEntityData = null
                    )

                    // Place block using adapter
                    adapter.setBlocksInRegion(level, listOf(block), updateNeighbors = true)

                    future.complete(null)
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
     * Fill region with blocks (async).
     * Returns Promise<number> (count of blocks placed).
     */
    fun fill(pos1: Value, pos2: Value, blockId: String, options: Value? = null): CompletableFuture<Int> {
        val future = CompletableFuture<Int>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val adapter = worldAdapter ?: run {
            future.completeExceptionally(IllegalStateException("WorldAdapter not initialized"))
            return future
        }

        try {
            // Extract positions
            val x1 = pos1.getMember("x").asInt()
            val y1 = pos1.getMember("y").asInt()
            val z1 = pos1.getMember("z").asInt()
            val x2 = pos2.getMember("x").asInt()
            val y2 = pos2.getMember("y").asInt()
            val z2 = pos2.getMember("z").asInt()

            val dimension = if (pos1.hasMember("dimension")) {
                pos1.getMember("dimension").asString()
            } else {
                "minecraft:overworld"
            }

            // Execute on main thread
            srv.execute {
                try {
                    val level = adapter.getLevel(dimension)
                    if (level == null) {
                        future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dimension"))
                        return@execute
                    }

                    // Create region
                    val region = Region.fromCorners(x1, y1, z1, x2, y2, z2)

                    // Parse exclusion zones from options
                    val exclusionZones = mutableListOf<Region>()
                    if (options != null && options.hasMember("exclude")) {
                        val excludeArray = options.getMember("exclude")
                        if (excludeArray.hasArrayElements()) {
                            val size = excludeArray.arraySize
                            for (i in 0 until size) {
                                val box = excludeArray.getArrayElement(i)
                                if (box.hasMember("min") && box.hasMember("max")) {
                                    val min = box.getMember("min")
                                    val max = box.getMember("max")
                                    val minX = min.getMember("x").asInt()
                                    val minY = min.getMember("y").asInt()
                                    val minZ = min.getMember("z").asInt()
                                    val maxX = max.getMember("x").asInt()
                                    val maxY = max.getMember("y").asInt()
                                    val maxZ = max.getMember("z").asInt()
                                    exclusionZones.add(Region.fromCorners(minX, minY, minZ, maxX, maxY, maxZ))
                                }
                            }
                        }
                    }

                    // Helper to check if position is in any exclusion zone
                    fun isExcluded(x: Int, y: Int, z: Int): Boolean {
                        return exclusionZones.any { zone ->
                            x >= zone.minX && x <= zone.maxX &&
                            y >= zone.minY && y <= zone.maxY &&
                            z >= zone.minZ && z <= zone.maxZ
                        }
                    }

                    // Create blocks list (excluding zones)
                    val blocks = mutableListOf<PositionedBlock>()
                    for (x in region.minX..region.maxX) {
                        for (y in region.minY..region.maxY) {
                            for (z in region.minZ..region.maxZ) {
                                // Skip if in exclusion zone
                                if (!isExcluded(x, y, z)) {
                                    blocks.add(
                                        PositionedBlock(
                                            x = x,
                                            y = y,
                                            z = z,
                                            block = BlockData(name = blockId, properties = emptyMap()),
                                            blockEntityData = null
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Place blocks using adapter
                    adapter.setBlocksInRegion(level, blocks, updateNeighbors = false)

                    future.complete(blocks.size)
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
     * Get all online players as wrapped JS objects (async).
     * Returns Promise<Player[]>.
     */
    fun getPlayers(): CompletableFuture<List<Value>> {
        val future = CompletableFuture<List<Value>>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val context = graalContext ?: run {
            future.completeExceptionally(IllegalStateException("GraalVM context not available"))
            return future
        }

        srv.execute {
            try {
                val players = srv.playerList.players.map { player ->
                    PlayerAdapter.toJS(player, context)
                }
                future.complete(players)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return future
    }

    /**
     * Get player by name or UUID (async).
     * Returns Promise<Player | null>.
     */
    fun getPlayer(nameOrUuid: String): CompletableFuture<Value?> {
        val future = CompletableFuture<Value?>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val context = graalContext ?: run {
            future.completeExceptionally(IllegalStateException("GraalVM context not available"))
            return future
        }

        srv.execute {
            try {
                // Try to find by name first
                var player = srv.playerList.getPlayerByName(nameOrUuid)

                // If not found by name, try UUID
                if (player == null) {
                    try {
                        val uuid = java.util.UUID.fromString(nameOrUuid)
                        player = srv.playerList.getPlayer(uuid)
                    } catch (e: IllegalArgumentException) {
                        // Not a valid UUID
                    }
                }

                val result = player?.let { PlayerAdapter.toJS(it, context) }
                future.complete(result)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return future
    }

    /**
     * Get current time in dimension (async).
     * Returns Promise<number> (ticks).
     */
    fun getTime(dimension: String?): CompletableFuture<Long> {
        val future = CompletableFuture<Long>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val adapter = worldAdapter ?: run {
            future.completeExceptionally(IllegalStateException("WorldAdapter not initialized"))
            return future
        }

        srv.execute {
            try {
                val dim = dimension ?: "minecraft:overworld"
                val level = adapter.getLevel(dim)
                if (level == null) {
                    future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dim"))
                    return@execute
                }

                future.complete(level.dayTime)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return future
    }

    /**
     * Set time in dimension (async).
     * Returns Promise<void>.
     */
    fun setTime(time: Long, dimension: String?): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val adapter = worldAdapter ?: run {
            future.completeExceptionally(IllegalStateException("WorldAdapter not initialized"))
            return future
        }

        srv.execute {
            try {
                val dim = dimension ?: "minecraft:overworld"
                val level = adapter.getLevel(dim)
                if (level == null) {
                    future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dim"))
                    return@execute
                }

                level.dayTime = time
                future.complete(null)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return future
    }

    /**
     * Get current weather in dimension (async).
     * Returns Promise<string> ("clear", "rain", or "thunder").
     */
    fun getWeather(dimension: String?): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val adapter = worldAdapter ?: run {
            future.completeExceptionally(IllegalStateException("WorldAdapter not initialized"))
            return future
        }

        srv.execute {
            try {
                val dim = dimension ?: "minecraft:overworld"
                val level = adapter.getLevel(dim)
                if (level == null) {
                    future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dim"))
                    return@execute
                }

                val weather = when {
                    level.isThundering -> "thunder"
                    level.isRaining -> "rain"
                    else -> "clear"
                }

                future.complete(weather)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return future
    }

    /**
     * Set weather in dimension (async).
     * Returns Promise<void>.
     */
    fun setWeather(weather: String, dimension: String?): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val adapter = worldAdapter ?: run {
            future.completeExceptionally(IllegalStateException("WorldAdapter not initialized"))
            return future
        }

        srv.execute {
            try {
                val dim = dimension ?: "minecraft:overworld"
                val level = adapter.getLevel(dim)
                if (level == null) {
                    future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dim"))
                    return@execute
                }

                when (weather.lowercase()) {
                    "clear" -> {
                        level.setWeatherParameters(6000, 0, false, false)
                    }
                    "rain" -> {
                        level.setWeatherParameters(0, 6000, true, false)
                    }
                    "thunder" -> {
                        level.setWeatherParameters(0, 6000, true, true)
                    }
                    else -> {
                        future.completeExceptionally(IllegalArgumentException("Invalid weather: $weather (must be clear, rain, or thunder)"))
                        return@execute
                    }
                }

                future.complete(null)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return future
    }

    /**
     * Get entities in radius (async).
     * Returns Promise<Entity[]>.
     * TODO: Implement entity wrapping with EntityAdapter.
     */
    fun getEntities(position: Value, radius: Double): CompletableFuture<List<Value>> {
        val future = CompletableFuture<List<Value>>()
        // TODO: Implement entity queries
        future.completeExceptionally(UnsupportedOperationException("World.getEntities() not yet implemented"))
        return future
    }

    /**
     * Spawn entity at position (async).
     * Returns Promise<Entity>.
     * TODO: Implement entity spawning.
     */
    fun spawnEntity(position: Value, entityId: String, nbt: Value?): CompletableFuture<Value> {
        val future = CompletableFuture<Value>()
        // TODO: Implement entity spawning
        future.completeExceptionally(UnsupportedOperationException("World.spawnEntity() not yet implemented"))
        return future
    }

    /**
     * Get a ServerLevel by dimension name.
     * Returns null if dimension doesn't exist.
     * Exposed for use by other managers (e.g., StructureManager).
     */
    fun getLevel(dimension: String): ServerLevel? {
        return worldAdapter?.getLevel(dimension)
    }

    /**
     * Get all available dimension names.
     * Returns list of dimension resource locations (e.g., "minecraft:overworld", "rhettjs:structure-test").
     */
    fun getDimensions(): List<String> {
        val srv = server ?: return listOf("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")

        return srv.levelKeys().map { dimensionKey ->
            dimensionKey.location().toString()
        }.sorted()
    }

    /**
     * Get dimension height bounds (async).
     * Returns Promise<DimensionBounds> with minY, maxY, minBuildHeight, maxBuildHeight.
     */
    fun getDimensionBounds(dimension: String?): CompletableFuture<Value> {
        val future = CompletableFuture<Value>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val adapter = worldAdapter ?: run {
            future.completeExceptionally(IllegalStateException("WorldAdapter not initialized"))
            return future
        }

        srv.execute {
            try {
                val dim = dimension ?: "minecraft:overworld"
                val level = adapter.getLevel(dim)
                if (level == null) {
                    future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dim"))
                    return@execute
                }

                val context = graalContext ?: run {
                    future.completeExceptionally(IllegalStateException("GraalVM context not available"))
                    return@execute
                }

                // Get dimension height bounds
                val minY = level.minBuildHeight
                val maxY = level.maxBuildHeight

                // Create result object
                val result = context.eval("js", """
                    ({
                        minY: ${minY},
                        maxY: ${maxY},
                        minBuildHeight: ${minY},
                        maxBuildHeight: ${maxY}
                    })
                """.trimIndent())

                future.complete(result)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return future
    }

    /**
     * Get vertical bounds of non-air blocks in a horizontal region (async).
     * Scans the region to find min/max Y with blocks.
     * Returns Promise<FilledBounds | null> - null if entire region is empty.
     */
    fun getFilledBounds(pos1: Value, pos2: Value, dimension: String?): CompletableFuture<Value?> {
        val future = CompletableFuture<Value?>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val adapter = worldAdapter ?: run {
            future.completeExceptionally(IllegalStateException("WorldAdapter not initialized"))
            return future
        }

        try {
            // Extract positions (only x/z matter, y is ignored)
            val x1 = pos1.getMember("x").asInt()
            val z1 = pos1.getMember("z").asInt()
            val x2 = pos2.getMember("x").asInt()
            val z2 = pos2.getMember("z").asInt()

            val dim = dimension ?: if (pos1.hasMember("dimension")) {
                pos1.getMember("dimension").asString()
            } else {
                "minecraft:overworld"
            }

            srv.execute {
                try {
                    val level = adapter.getLevel(dim)
                    if (level == null) {
                        future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dim"))
                        return@execute
                    }

                    val context = graalContext ?: run {
                        future.completeExceptionally(IllegalStateException("GraalVM context not available"))
                        return@execute
                    }

                    // Determine scan bounds
                    val minX = minOf(x1, x2)
                    val maxX = maxOf(x1, x2)
                    val minZ = minOf(z1, z2)
                    val maxZ = maxOf(z1, z2)

                    var foundMinY: Int? = null
                    var foundMaxY: Int? = null

                    // Scan columns to find min/max Y with blocks
                    for (x in minX..maxX) {
                        for (z in minZ..maxZ) {
                            val pos = net.minecraft.core.BlockPos(x, 0, z)

                            // Scan from bottom to top to find first non-air block
                            for (y in level.minBuildHeight until level.maxBuildHeight) {
                                val blockPos = net.minecraft.core.BlockPos(x, y, z)
                                val blockState = level.getBlockState(blockPos)
                                if (!blockState.isAir) {
                                    if (foundMinY == null || y < foundMinY) {
                                        foundMinY = y
                                    }
                                    if (foundMaxY == null || y > foundMaxY) {
                                        foundMaxY = y
                                    }
                                }
                            }
                        }
                    }

                    // Return null if no blocks found
                    if (foundMinY == null || foundMaxY == null) {
                        future.complete(null)
                        return@execute
                    }

                    // Create result object
                    val result = context.eval("js", """
                        ({
                            minY: ${foundMinY},
                            maxY: ${foundMaxY}
                        })
                    """.trimIndent())

                    future.complete(result)
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
     * Remove all entities in a region (without killing/dropping items).
     * Removes entities directly so no drops occur.
     */
    fun removeEntities(pos1: Value, pos2: Value, options: Value?): CompletableFuture<Int> {
        val future = CompletableFuture<Int>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val adapter = worldAdapter ?: run {
            future.completeExceptionally(IllegalStateException("WorldAdapter not initialized"))
            return future
        }

        try {
            // Extract positions
            val x1 = pos1.getMember("x").asInt()
            val y1 = pos1.getMember("y").asInt()
            val z1 = pos1.getMember("z").asInt()
            val x2 = pos2.getMember("x").asInt()
            val y2 = pos2.getMember("y").asInt()
            val z2 = pos2.getMember("z").asInt()

            ConfigManager.debug("[WorldManager] removeEntities positions: ($x1,$y1,$z1) to ($x2,$y2,$z2)")

            // Get dimension
            val dim = if (options != null && options.hasMember("dimension")) {
                options.getMember("dimension").asString()
            } else if (pos1.hasMember("dimension")) {
                pos1.getMember("dimension").asString()
            } else {
                "minecraft:overworld"
            }

            ConfigManager.debug("[WorldManager] removeEntities dimension: $dim")

            // Parse options
            val excludePlayers = if (options != null && options.hasMember("excludePlayers")) {
                options.getMember("excludePlayers").asBoolean()
            } else {
                true  // Default: exclude players
            }

            val typeFilter: Set<String>? = if (options != null && options.hasMember("types")) {
                val typesArray = options.getMember("types")
                if (typesArray.hasArrayElements()) {
                    val types = mutableSetOf<String>()
                    val size = typesArray.arraySize
                    for (i in 0 until size) {
                        types.add(typesArray.getArrayElement(i).asString())
                    }
                    types
                } else {
                    null
                }
            } else {
                null
            }

            srv.execute {
                try {
                    val level = adapter.getLevel(dim)
                    if (level == null) {
                        ConfigManager.debug("[WorldManager] ERROR: Level is null for dimension $dim")
                        future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dim"))
                        return@execute
                    }

                    ConfigManager.debug("[WorldManager] Got level for dimension: ${level.dimension().location()}")

                    // Create bounding box
                    val minX = minOf(x1, x2).toDouble()
                    val minY = minOf(y1, y2).toDouble()
                    val minZ = minOf(z1, z2).toDouble()
                    val maxX = maxOf(x1, x2).toDouble()
                    val maxY = maxOf(y1, y2).toDouble()
                    val maxZ = maxOf(z1, z2).toDouble()

                    val aabb = net.minecraft.world.phys.AABB(minX, minY, minZ, maxX, maxY, maxZ)

                    // Calculate chunk bounds
                    val minChunkX = (minX.toInt() shr 4)
                    val maxChunkX = (maxX.toInt() shr 4)
                    val minChunkZ = (minZ.toInt() shr 4)
                    val maxChunkZ = (maxZ.toInt() shr 4)

                    val chunkCount = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1)
                    ConfigManager.debug("[WorldManager] Force-loading $chunkCount chunks ($minChunkX,$minChunkZ to $maxChunkX,$maxChunkZ) for entity removal")

                    // Force-load all chunks
                    for (chunkX in minChunkX..maxChunkX) {
                        for (chunkZ in minChunkZ..maxChunkZ) {
                            // Force chunk to be fully loaded with entities
                            level.getChunk(chunkX, chunkZ, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true)
                        }
                    }

                    // Get all entities from all loaded entities in the level (not using AABB filter)
                    val allLevelEntities = level.allEntities
                    ConfigManager.debug("[WorldManager] Total entities in level: ${allLevelEntities.count()}")

                    // Filter to only entities in our bounding box
                    val entitiesInRegion = allLevelEntities.filter { entity ->
                        val pos = entity.position()
                        pos.x >= minX && pos.x <= maxX &&
                        pos.y >= minY && pos.y <= maxY &&
                        pos.z >= minZ && pos.z <= maxZ
                    }

                    ConfigManager.debug("[WorldManager] Found ${entitiesInRegion.size} entities in region")
                    ConfigManager.debug("[WorldManager] excludePlayers=$excludePlayers, typeFilter=$typeFilter")

                    val entities = entitiesInRegion

                    var removedCount = 0
                    entities.forEach { entity ->
                        val entityType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()
                        val isPlayer = entity is net.minecraft.world.entity.player.Player

                        // Check exclusions
                        val shouldRemove = when {
                            excludePlayers && isPlayer -> {
                                ConfigManager.debug("[WorldManager] Skipping player: ${entity.name.string}")
                                false
                            }
                            typeFilter != null -> {
                                val inFilter = entityType in typeFilter
                                ConfigManager.debug("[WorldManager] Entity $entityType, in filter: $inFilter")
                                inFilter
                            }
                            else -> {
                                ConfigManager.debug("[WorldManager] Removing entity: $entityType at ${entity.position()}")
                                true
                            }
                        }

                        if (shouldRemove) {
                            ConfigManager.debug("[WorldManager] Calling discard() on $entityType")
                            entity.discard()  // Remove without drops
                            removedCount++
                        }
                    }

                    ConfigManager.debug("[WorldManager] Removed $removedCount entities total")
                    future.complete(removedCount)
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
     * Clear all state (called on reset/reload).
     * Clears context reference (context will be recreated).
     */
    fun reset() {
        graalContext = null
        ConfigManager.debug("[WorldManager] Reset complete")
    }
}
