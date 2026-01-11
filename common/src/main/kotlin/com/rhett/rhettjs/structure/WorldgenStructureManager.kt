package com.rhett.rhettjs.structure

import com.rhett.rhettjs.config.ConfigManager
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure
import org.graalvm.polyglot.Context
import java.util.concurrent.CompletableFuture

/**
 * Manager for WorldgenStructure API operations with JavaScript.
 *
 * This is the anti-corruption layer between JavaScript and Minecraft's worldgen structure system.
 * It provides access to structure definitions (like minecraft:village_plains) that are used
 * for natural world generation.
 *
 * This is different from StructureNbtManager which handles .nbt template files.
 * WorldgenStructure handles the JSON-defined structures in data/worldgen/structure/.
 *
 * Design principles:
 * - Async for registry access: All operations return CompletableFuture
 * - Anti-corruption: Convert all MC types to JS via pure objects
 * - Namespace format: "[namespace:]name" (defaults to "minecraft:")
 */
object WorldgenStructureManager {

    @Volatile
    private var server: MinecraftServer? = null

    @Volatile
    private var graalContext: Context? = null

    /**
     * Set the Minecraft server reference.
     * Called during server startup.
     */
    fun setServer(minecraftServer: MinecraftServer) {
        server = minecraftServer
        ConfigManager.debug("[WorldgenStructureManager] Minecraft server reference set")
    }

    /**
     * Set the GraalVM context reference.
     * Called when GraalEngine initializes the context.
     */
    fun setContext(context: Context) {
        graalContext = context
        ConfigManager.debug("[WorldgenStructureManager] GraalVM context reference set")
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
     * List available worldgen structures (async).
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
            // Execute on main thread to access registry
            srv.execute {
                try {
                    val structureRegistry = srv.registryAccess().registryOrThrow(Registries.STRUCTURE)

                    val structures = structureRegistry.keySet()
                        .filter { loc ->
                            namespaceFilter == null || loc.namespace == namespaceFilter
                        }
                        .map { loc -> "${loc.namespace}:${loc.path}" }
                        .sorted()

                    ConfigManager.debug("[WorldgenStructureManager] Listed ${structures.size} worldgen structures")
                    future.complete(structures)
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
     * Check if a worldgen structure exists (async).
     * Returns Promise<boolean>.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun exists(nameWithNamespace: String): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        val srv = server

        if (srv == null) {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }

        try {
            val (namespace, name) = parseStructureName(nameWithNamespace)
            val resourceLocation = ResourceLocation.fromNamespaceAndPath(namespace, name)

            srv.execute {
                try {
                    val structureRegistry = srv.registryAccess().registryOrThrow(Registries.STRUCTURE)
                    val exists = structureRegistry.containsKey(resourceLocation)
                    future.complete(exists)
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
     * Get detailed information about a worldgen structure (async).
     * Returns Promise<StructureInfo> with type, biomes, terrain adaptation, etc.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun info(nameWithNamespace: String): CompletableFuture<Map<String, Any?>> {
        val future = CompletableFuture<Map<String, Any?>>()
        val srv = server

        if (srv == null) {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }

        try {
            val (namespace, name) = parseStructureName(nameWithNamespace)
            val resourceLocation = ResourceLocation.fromNamespaceAndPath(namespace, name)

            srv.execute {
                try {
                    val structureRegistry = srv.registryAccess().registryOrThrow(Registries.STRUCTURE)
                    val structure = structureRegistry.get(resourceLocation)

                    if (structure == null) {
                        future.completeExceptionally(IllegalArgumentException("Worldgen structure not found: $nameWithNamespace"))
                        return@execute
                    }

                    val info = extractStructureInfo(nameWithNamespace, structure, srv)
                    future.complete(info)
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
     * Extract structure information into a JS-friendly map.
     */
    private fun extractStructureInfo(name: String, structure: Structure, srv: MinecraftServer): Map<String, Any?> {
        val info = mutableMapOf<String, Any?>()

        info["name"] = name

        // Get structure type
        val structureType = structure.type()
        val typeRegistry = srv.registryAccess().registryOrThrow(Registries.STRUCTURE_TYPE)
        val typeKey = typeRegistry.getKey(structureType)
        info["type"] = typeKey?.toString() ?: "unknown"

        // Get biomes (as HolderSet, extract the tag or list)
        val biomes = structure.biomes()
        val biomeList = mutableListOf<String>()

        // Try to get the biome tag name if it's a tag
        try {
            // Check if it's a named tag (like #minecraft:has_structure/village_plains)
            val biomeTagKey = biomes.unwrapKey()
            if (biomeTagKey.isPresent) {
                info["biomesTag"] = "#${biomeTagKey.get().location()}"
            }
        } catch (e: Exception) {
            // Ignore - not a tag
        }

        // Also list individual biomes if available
        try {
            biomes.forEach { biomeHolder ->
                biomeHolder.unwrapKey().ifPresent { key ->
                    biomeList.add(key.location().toString())
                }
            }
            if (biomeList.isNotEmpty()) {
                info["biomes"] = biomeList
            }
        } catch (e: Exception) {
            // Some holder sets may not be iterable
        }

        // Get terrain adaptation
        info["terrainAdaptation"] = structure.terrainAdaptation().name.lowercase()

        // Get generation step
        info["step"] = structure.step().name.lowercase()

        // Get spawn overrides
        val spawnOverrides = structure.spawnOverrides()
        if (spawnOverrides.isNotEmpty()) {
            val overridesMap = mutableMapOf<String, Any>()
            spawnOverrides.forEach { (category, override) ->
                overridesMap[category.name.lowercase()] = mapOf(
                    "boundingBox" to override.boundingBox().name.lowercase()
                )
            }
            info["spawnOverrides"] = overridesMap
        }

        // Note: Jigsaw-specific properties (startPool, maxDepth, etc.) are private in MC 1.21.1
        // They could be exposed via reflection or accessWidener if needed in the future
        info["isJigsaw"] = structure is JigsawStructure

        return info
    }

    /**
     * Apply bearding (foundation blocks) to rigid structure pieces.
     * Mimics vanilla terrain adaptation by filling gaps between structures and terrain.
     *
     * @param level The level to place blocks in
     * @param structureStart The structure with pieces to beard
     * @param beardingBlockId Block ID to use for bearding (e.g., "minecraft:redstone_block")
     */
    private fun applyBearding(
        level: net.minecraft.server.level.ServerLevel,
        structureStart: net.minecraft.world.level.levelgen.structure.StructureStart,
        beardingBlockId: String
    ) {
        // Parse bearding block
        val (namespace, path) = parseStructureName(beardingBlockId)
        val blockLocation = ResourceLocation.fromNamespaceAndPath(namespace, path)
        val block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(blockLocation)

        if (block == net.minecraft.world.level.block.Blocks.AIR) {
            ConfigManager.debug("[WorldgenStructureManager] Invalid bearding block: $beardingBlockId")
            return
        }

        val beardingState = block.defaultBlockState()

        // Process each piece
        for (piece in structureStart.pieces) {
            // Only beard rigid (non-terrain-matching) pieces
            // Skip terrain-matching pieces as they already follow terrain
            if (piece is net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece) {
                val projection = piece.element.projection
                // Skip if terrain matching
                if (projection == net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.Projection.TERRAIN_MATCHING) {
                    continue
                }
            } else {
                // Non-pool pieces don't need bearding
                continue
            }

            // Get piece bounding box
            val boundingBox = piece.boundingBox
            val minX = boundingBox.minX()
            val minY = boundingBox.minY()
            val minZ = boundingBox.minZ()
            val maxX = boundingBox.maxX()
            val maxY = boundingBox.maxY()
            val maxZ = boundingBox.maxZ()

            ConfigManager.debug("[WorldgenStructureManager] Applying bearding to rigid piece at ($minX, $minY, $minZ) to ($maxX, $maxY, $maxZ)")

            // Apply thin beard by default for rigid pieces
            applyThinBeard(level, boundingBox, beardingState, minY)
        }
    }

    /**
     * Apply thin bearding - creates cloud-puff foundation using vanilla's exponential falloff.
     * Extends horizontally beyond structure and tapers with distance.
     */
    private fun applyThinBeard(
        level: net.minecraft.server.level.ServerLevel,
        boundingBox: net.minecraft.world.level.levelgen.structure.BoundingBox,
        beardingState: net.minecraft.world.level.block.state.BlockState,
        structureMinY: Int
    ) {
        // Vanilla constants
        val BEARD_KERNEL_RADIUS = 1  // Max horizontal spread (scan area)
        val MAX_VERTICAL_DELTA = 6    // Max vertical gap before no bearding
        val DENSITY_THRESHOLD = 0.9   // Minimum density to place block

        val minX = boundingBox.minX()
        val minZ = boundingBox.minZ()
        val maxX = boundingBox.maxX()
        val maxZ = boundingBox.maxZ()

        // Expand search area by kernel radius for cloud puff
        for (x in (minX - BEARD_KERNEL_RADIUS)..(maxX + BEARD_KERNEL_RADIUS)) {
            for (z in (minZ - BEARD_KERNEL_RADIUS)..(maxZ + BEARD_KERNEL_RADIUS)) {
                applyBeardCloudColumn(level, x, z, boundingBox, structureMinY, beardingState, MAX_VERTICAL_DELTA, DENSITY_THRESHOLD)
            }
        }
    }

    /**
     * Apply cloud bearding to a single column.
     * Uses vanilla's exponential falloff: density = e^(-distance²/16)
     *
     * Bearding fills air gaps from the piece's BOTTOM (minY) downward.
     * Uses the bounding box minY as reference height, not individual block heights.
     */
    private fun applyBeardCloudColumn(
        level: net.minecraft.server.level.ServerLevel,
        x: Int,
        z: Int,
        boundingBox: net.minecraft.world.level.levelgen.structure.BoundingBox,
        structureMinY: Int,
        beardingState: net.minecraft.world.level.block.state.BlockState,
        maxVerticalDelta: Int,
        densityThreshold: Double
    ) {
        val pos = BlockPos.MutableBlockPos(x, structureMinY - 1, z)

        // Scan downward from structure bottom (minY - 1)
        for (y in (structureMinY - 1) downTo (structureMinY - maxVerticalDelta - 1)) {
            if (y < level.minBuildHeight) break

            pos.setY(y)
            val existingState = level.getBlockState(pos)

            // Stop if we hit non-replaceable blocks (solid terrain, structure blocks, etc.)
            if (!existingState.canBeReplaced()) {
                break
            }

            // Calculate 3D distance to nearest point on structure bounding box
            // For horizontal distance, measure to structure footprint edge
            val dx = maxOf(0, maxOf(boundingBox.minX() - x, x - boundingBox.maxX()))
            val dz = maxOf(0, maxOf(boundingBox.minZ() - z, z - boundingBox.maxZ()))

            // Vertical distance from structure bottom
            val dy = structureMinY - y

            // Vanilla formula: density = e^(-distance²/16)
            val distanceSquared = (dx * dx + dy * dy + dz * dz).toDouble()
            val density = Math.exp(-distanceSquared / 16.0)

            // Place block if density exceeds threshold and block is replaceable
            if (density >= densityThreshold && existingState.canBeReplaced()) {
                level.setBlock(pos, beardingState, 3)
            }
        }
    }

    /**
     * Apply box bearding - fills entire area under structure down to terrain.
     * (Keeping simple implementation for BEARD_BOX type)
     */
    private fun applyBoxBeard(
        level: net.minecraft.server.level.ServerLevel,
        boundingBox: net.minecraft.world.level.levelgen.structure.BoundingBox,
        beardingState: net.minecraft.world.level.block.state.BlockState,
        structureMinY: Int
    ) {
        val minX = boundingBox.minX()
        val minZ = boundingBox.minZ()
        val maxX = boundingBox.maxX()
        val maxZ = boundingBox.maxZ()

        val pos = BlockPos.MutableBlockPos()

        // Fill entire box volume
        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                for (y in (structureMinY - 1) downTo (structureMinY - 6)) {
                    if (y < level.minBuildHeight) break

                    pos.set(x, y, z)
                    val existingState = level.getBlockState(pos)

                    // Stop at solid terrain
                    if (!existingState.isAir && existingState.isSolidRender(level, pos)) {
                        break
                    }

                    // Fill air
                    if (existingState.isAir) {
                        level.setBlock(pos, beardingState, 3)
                    }
                }
            }
        }
    }

    /**
     * Place a worldgen structure at a position.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param x X coordinate (center)
     * @param z Z coordinate (center)
     * @param dimensionId Dimension to place in (e.g., "minecraft:overworld")
     * @param seed Optional seed for randomization (null = random)
     * @param surface Surface mode: "heightmap" (default), "scan", "fixed:Y", "rigid"
     * @param rotation Optional rotation: "none", "clockwise_90", "180", "counterclockwise_90" (null = seed-based)
     * @param simulateBearding Optional block ID (e.g., "minecraft:redstone_block") to visualize bearding. null = no bearding
     */
    fun place(
        nameWithNamespace: String,
        x: Int,
        z: Int,
        dimensionId: String?,
        seed: Long?,
        surface: String?,
        rotation: String?,
        simulateBearding: String?
    ): CompletableFuture<Map<String, Any?>> {
        val future = CompletableFuture<Map<String, Any?>>()
        val srv = server

        if (srv == null) {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }

        try {
            val (namespace, name) = parseStructureName(nameWithNamespace)
            val resourceLocation = ResourceLocation.fromNamespaceAndPath(namespace, name)
            val surfaceMode = WorldgenStructurePlacementContext.SurfaceMode.parse(surface ?: "heightmap")

            srv.execute {
                try {
                    // Get the target dimension
                    val level = getDimension(srv, dimensionId)

                    // Get the structure
                    val structureRegistry = srv.registryAccess().registryOrThrow(Registries.STRUCTURE)
                    val structureHolder = structureRegistry.getHolder(ResourceKey.create(Registries.STRUCTURE, resourceLocation))
                        .orElseThrow { IllegalArgumentException("Structure not found: $nameWithNamespace") }
                    val structure = structureHolder.value()

                    // Determine seed
                    val actualSeed = seed ?: level.random.nextLong()
                    val random = RandomSource.create(actualSeed)

                    // Determine rotation
                    val actualRotation = if (rotation != null) {
                        parseRotation(rotation)
                    } else {
                        Rotation.getRandom(random)
                    }

                    // Position for generation
                    val blockPos = BlockPos(x, level.seaLevel, z)
                    val chunkPos = ChunkPos(blockPos)

                    // Execute within placement context for height overrides
                    val result = WorldgenStructurePlacementContext.withContext(level, surfaceMode) { ctx ->
                        // Generate the structure (Phase 1: create pieces)
                        val chunkGenerator = level.chunkSource.generator
                        val structureStart = structure.generate(
                            srv.registryAccess(),
                            chunkGenerator,
                            chunkGenerator.biomeSource,
                            level.chunkSource.randomState(),
                            level.structureManager,
                            actualSeed,
                            chunkPos,
                            0,
                            level
                        ) { true } // Bypass biome check

                        if (!structureStart.isValid) {
                            return@withContext mapOf(
                                "success" to false,
                                "error" to "Structure generation failed"
                            )
                        }

                        // Get bounding box before placement
                        val boundingBox = structureStart.boundingBox

                        // Ensure chunks are loaded
                        val minChunk = ChunkPos(SectionPos.blockToSectionCoord(boundingBox.minX()), SectionPos.blockToSectionCoord(boundingBox.minZ()))
                        val maxChunk = ChunkPos(SectionPos.blockToSectionCoord(boundingBox.maxX()), SectionPos.blockToSectionCoord(boundingBox.maxZ()))

                        // Phase 2: Place blocks
                        var pieceCount = 0
                        ChunkPos.rangeClosed(minChunk, maxChunk).forEach { cp ->
                            val chunkBounds = BoundingBox(
                                cp.minBlockX, level.minBuildHeight, cp.minBlockZ,
                                cp.maxBlockX, level.maxBuildHeight, cp.maxBlockZ
                            )
                            structureStart.placeInChunk(
                                level,
                                level.structureManager(),
                                chunkGenerator,
                                random,
                                chunkBounds,
                                cp
                            )
                        }

                        // Count pieces
                        pieceCount = structureStart.pieces.size

                        // Apply bearding if requested
                        if (simulateBearding != null) {
                            applyBearding(level, structureStart, simulateBearding)
                        }

                        mapOf(
                            "success" to true,
                            "seed" to actualSeed,
                            "rotation" to actualRotation.name.lowercase(),
                            "pieceCount" to pieceCount,
                            "boundingBox" to mapOf(
                                "min" to mapOf("x" to boundingBox.minX(), "y" to boundingBox.minY(), "z" to boundingBox.minZ()),
                                "max" to mapOf("x" to boundingBox.maxX(), "y" to boundingBox.maxY(), "z" to boundingBox.maxZ())
                            )
                        )
                    }

                    ConfigManager.debug("[WorldgenStructureManager] Placed structure $nameWithNamespace at $x, $z")
                    future.complete(result)
                } catch (e: Exception) {
                    ConfigManager.debug("[WorldgenStructureManager] Failed to place structure: ${e.message}")
                    future.completeExceptionally(e)
                }
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Place a jigsaw structure from a template pool.
     *
     * @param pool Template pool name (e.g., "minecraft:village/plains/town_centers")
     * @param target Target jigsaw identifier (e.g., "minecraft:bottom")
     * @param maxDepth Maximum jigsaw depth (1-20)
     * @param x X coordinate
     * @param z Z coordinate
     * @param dimensionId Dimension to place in (null = overworld)
     * @param seed Optional seed for randomization
     * @param surface Surface mode: "heightmap", "scan", "fixed:Y"
     * @param simulateBearding Optional block ID (e.g., "minecraft:redstone_block") to visualize bearding. null = no bearding
     */
    fun placeJigsaw(
        pool: String,
        target: String,
        maxDepth: Int,
        x: Int,
        z: Int,
        dimensionId: String?,
        seed: Long?,
        surface: String?,
        simulateBearding: String?
    ): CompletableFuture<Map<String, Any?>> {
        val future = CompletableFuture<Map<String, Any?>>()
        val srv = server

        if (srv == null) {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }

        if (maxDepth < 1 || maxDepth > 20) {
            future.completeExceptionally(IllegalArgumentException("maxDepth must be between 1 and 20"))
            return future
        }

        try {
            val (poolNamespace, poolPath) = parseStructureName(pool)
            val poolLocation = ResourceLocation.fromNamespaceAndPath(poolNamespace, poolPath)
            val targetLocation = ResourceLocation.tryParse(target)
                ?: throw IllegalArgumentException("Invalid target: $target")
            val surfaceMode = WorldgenStructurePlacementContext.SurfaceMode.parse(surface ?: "heightmap")

            srv.execute {
                try {
                    // Get the target dimension
                    val level = getDimension(srv, dimensionId)

                    // Get the template pool
                    val poolRegistry = srv.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL)
                    val poolHolder = poolRegistry.getHolder(ResourceKey.create(Registries.TEMPLATE_POOL, poolLocation))
                        .orElseThrow { IllegalArgumentException("Template pool not found: $pool") }

                    // Position
                    val blockPos = BlockPos(x, level.seaLevel, z)

                    // Seed handling - if custom seed, we need to manipulate the level's random
                    // For now, use the level's random (seeded placement is more complex for jigsaw)
                    val actualSeed = seed ?: level.random.nextLong()

                    // Execute within placement context
                    val result = WorldgenStructurePlacementContext.withContext(level, surfaceMode) { ctx ->
                        val success = JigsawPlacement.generateJigsaw(
                            level,
                            poolHolder,
                            targetLocation,
                            maxDepth,
                            blockPos,
                            false // useExpansionHack
                        )

                        // TODO: Apply bearding for jigsaw placement
                        // JigsawPlacement doesn't return StructureStart, so we can't easily apply bearding
                        // Would need to query placed structures from level's structure manager
                        if (simulateBearding != null) {
                            ConfigManager.debug("[WorldgenStructureManager] Bearding not yet supported for placeJigsaw")
                        }

                        if (success) {
                            mapOf(
                                "success" to true,
                                "pool" to pool,
                                "target" to target,
                                "maxDepth" to maxDepth,
                                "position" to mapOf("x" to x, "z" to z)
                            )
                        } else {
                            mapOf(
                                "success" to false,
                                "error" to "Jigsaw placement failed"
                            )
                        }
                    }

                    ConfigManager.debug("[WorldgenStructureManager] Placed jigsaw from pool $pool at $x, $z")
                    future.complete(result)
                } catch (e: Exception) {
                    ConfigManager.debug("[WorldgenStructureManager] Failed to place jigsaw: ${e.message}")
                    future.completeExceptionally(e)
                }
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Get a dimension level by ID.
     */
    private fun getDimension(srv: MinecraftServer, dimensionId: String?): ServerLevel {
        if (dimensionId == null) {
            return srv.overworld()
        }

        val (namespace, path) = parseStructureName(dimensionId)
        val dimLocation = ResourceLocation.fromNamespaceAndPath(namespace, path)
        val dimKey = ResourceKey.create(Registries.DIMENSION, dimLocation)

        return srv.getLevel(dimKey)
            ?: throw IllegalArgumentException("Dimension not found: $dimensionId")
    }

    /**
     * Parse rotation string to Rotation enum.
     */
    private fun parseRotation(rotation: String): Rotation {
        return when (rotation.lowercase()) {
            "none" -> Rotation.NONE
            "clockwise_90", "cw_90", "90" -> Rotation.CLOCKWISE_90
            "180" -> Rotation.CLOCKWISE_180
            "counterclockwise_90", "ccw_90", "270" -> Rotation.COUNTERCLOCKWISE_90
            else -> throw IllegalArgumentException("Unknown rotation: $rotation")
        }
    }

    /**
     * Clear all state (called on reset/reload).
     */
    fun reset() {
        graalContext = null
        ConfigManager.debug("[WorldgenStructureManager] Reset complete")
    }
}