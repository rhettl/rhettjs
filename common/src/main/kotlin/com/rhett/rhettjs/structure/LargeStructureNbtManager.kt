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
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors
import kotlin.io.path.*

/**
 * Manager for LargeStructureNbt API operations with JavaScript.
 *
 * Handles multi-chunk .nbt structures stored in rjs-large/ directories.
 * Uses StructureNbtManager as lower-level API for individual piece operations.
 * For single .nbt files, see StructureNbtManager.
 * For worldgen structures (villages, bastions), see WorldgenStructureManager.
 *
 * Large structures are split into chunks (e.g., 48x48x48) and stored as:
 *   world/generated/<namespace>/structures/rjs-large/<name>/<X>_<Y>_<Z>.nbt
 *
 * This is the anti-corruption layer between JavaScript and large structure files.
 * It ensures:
 * - All file I/O is async (return CompletableFuture)
 * - No Minecraft types exposed to JavaScript
 * - Pure JS objects using adapters
 * - Uses StructureNbtManager for piece-level operations
 *
 * Design principles:
 * - Async for I/O: All operations return CompletableFuture
 * - Anti-corruption: Convert all MC types to JS via models
 * - Delegation: Uses StructureNbtManager.capture/place/load for pieces
 * - Namespace format: "[namespace:]name" (defaults to "minecraft:")
 */
object LargeStructureNbtManager {

    @Volatile
    internal var server: MinecraftServer? = null

    @Volatile
    private var graalContext: Context? = null

    @Volatile
    internal var structuresPath: Path? = null

    /**
     * Generated folder structure subdirectory name (same as StructureNbtManager).
     * Per Minecraft wiki: runtime structures are saved to generated/<namespace>/structures/ (plural)
     */
    private const val GENERATED_STRUCTURES_DIR = "structures"

    /**
     * Subdirectory for RhettJS large structures (multi-chunk NBT structures).
     * Stored as: generated/<namespace>/structures/rjs-large/<name>/<X>_<Y>_<Z>.nbt
     */
    private const val RJS_LARGE_SUBDIR = "rjs-large"

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
                ConfigManager.debug("[LargeStructureNbtManager] Created generated directory: $path")
            }
        }
        if (!backupsPath.exists()) {
            Files.createDirectories(backupsPath)
            ConfigManager.debug("[LargeStructureNbtManager] Created backups directory: $backupsPath")
        }

        ConfigManager.debug("[LargeStructureNbtManager] Minecraft server reference set")
    }

    /**
     * Set the GraalVM context reference.
     * Called when GraalEngine initializes the context.
     */
    fun setContext(context: Context) {
        graalContext = context
        ConfigManager.debug("[LargeStructureNbtManager] GraalVM context reference set")
    }

    /**
     * Parse structure name format "[namespace:]name".
     * Returns pair of (namespace, name).
     * Defaults to "minecraft" namespace if not specified.
     *
     * Internal visibility so LargeStructureNbtManager can use it.
     */
    internal fun parseStructureName(nameWithNamespace: String): Pair<String, String> {
        return if (':' in nameWithNamespace) {
            val parts = nameWithNamespace.split(':', limit = 2)
            parts[0] to parts[1]
        } else {
            "minecraft" to nameWithNamespace
        }
    }

    /**
     * Get the file path for a structure piece in the generated folder.
     * Format: generated/<namespace>/structures/name.nbt (may include rjs-large/ in name)
     */
    private fun getStructurePath(namespace: String, name: String): Path? {
        val basePath = structuresPath ?: return null
        return basePath.resolve(namespace).resolve(GENERATED_STRUCTURES_DIR).resolve("$name.nbt")
    }







    /**
     * Load a structure from resource system (async).
     * Delegates to StructureNbtManager for consistency.
     * Returns Promise<StructureData>.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun load(nameWithNamespace: String): CompletableFuture<StructureData> {
        return StructureNbtManager.load(nameWithNamespace)
    }

    /**
     * Save a structure to file (async) with automatic backup.
     * Delegates to StructureNbtManager for consistency.
     * Returns Promise<void>.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param data Structure data to save
     * @param skipBackup If true, skip automatic backup (used for large structure pieces)
     */
    fun save(nameWithNamespace: String, data: StructureData, skipBackup: Boolean = false): CompletableFuture<Void> {
        return StructureNbtManager.save(nameWithNamespace, data, skipBackup)
    }



    /**
     * Backup a large structure directory before modification.
     * Creates timestamped directory backup and maintains retention policy (keeps last 5).
     *
     * @param namespace Structure namespace
     * @param baseName Structure base name (without rjs-large prefix)
     * @return true if backup created or skipped (no existing structure), false if error
     */
    private fun backupLargeStructure(namespace: String, baseName: String): Boolean {
        try {
            // Check if source directory exists
            val sourceDir = structuresPath?.resolve(namespace)?.resolve(GENERATED_STRUCTURES_DIR)?.resolve(RJS_LARGE_SUBDIR)?.resolve(baseName)
            if (sourceDir == null || !sourceDir.exists() || !Files.isDirectory(sourceDir)) {
                ConfigManager.debug("[LargeStructureNbtManager] No existing large structure to backup: $namespace:$baseName")
                return true // Nothing to backup, not an error
            }

            // Check if there are any .nbt files
            val nbtFiles = Files.list(sourceDir)
                .filter { it.extension == "nbt" }
                .toList()

            if (nbtFiles.isEmpty()) {
                ConfigManager.debug("[LargeStructureNbtManager] No piece files to backup: $namespace:$baseName")
                return true
            }

            // Create backup directory with timestamp
            val backupsPath = structuresPath?.parent?.resolve("backups")?.resolve(GENERATED_STRUCTURES_DIR)?.resolve(RJS_LARGE_SUBDIR)
            if (backupsPath == null) {
                ConfigManager.debug("[LargeStructureNbtManager] Backups path not available")
                return false
            }

            val timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            val backupDirName = "$baseName.$timestamp"
            val backupDir = backupsPath.resolve(backupDirName)

            // Create backup directory
            Files.createDirectories(backupDir)

            // Copy all .nbt files
            nbtFiles.forEach { file ->
                val targetFile = backupDir.resolve(file.fileName)
                Files.copy(file, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }

            ConfigManager.debug("[LargeStructureNbtManager] Backed up large structure: $namespace:$baseName → $backupDirName (${nbtFiles.size} pieces)")

            // Cleanup old backups (keep only 5 most recent)
            val allBackups = Files.list(backupsPath)
                .filter { Files.isDirectory(it) }
                .filter { it.fileName.toString().startsWith("$baseName.") }
                .sorted(Comparator.reverseOrder()) // Newest first (by name timestamp)
                .toList()

            if (allBackups.size > 5) {
                val toDelete = allBackups.drop(5)
                toDelete.forEach { oldBackup ->
                    Files.walk(oldBackup)
                        .sorted(Comparator.reverseOrder())
                        .forEach { Files.delete(it) }
                    ConfigManager.debug("[LargeStructureNbtManager] Deleted old backup: ${oldBackup.fileName}")
                }
            }

            return true
        } catch (e: Exception) {
            ConfigManager.debug("[LargeStructureNbtManager] Failed to backup large structure: ${e.message}")
            return false
        }
    }

    /**
     * Capture a large region split into multiple piece files (async).
     * Returns Promise<void> after saving all piece files.
     *
     * Large structures are stored in: generated/<namespace>/structures/rjs-large/<name>/X_Y_Z.nbt
     * The 0_0_0.nbt file contains metadata.requires[] with required mod namespaces.
     *
     * @param pos1 First corner position {x, y, z, dimension?}
     * @param pos2 Second corner position {x, y, z, dimension?}
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param options Optional options {pieceSize?: {x,y,z}, author?: string, description?: string}
     */
    fun capture(pos1: Value, pos2: Value, nameWithNamespace: String, options: Value?): CompletableFuture<Void> {
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

            // Get dimension
            val dimension = if (options != null && options.hasMember("dimension")) {
                options.getMember("dimension").asString()
            } else if (pos1.hasMember("dimension")) {
                pos1.getMember("dimension").asString()
            } else {
                "minecraft:overworld"
            }

            // Get piece size (default 48x48x48)
            val pieceSizeX = if (options != null && options.hasMember("pieceSize")) {
                val ps = options.getMember("pieceSize")
                if (ps.hasMember("x")) ps.getMember("x").asInt() else 48
            } else 48

            val pieceSizeY = if (options != null && options.hasMember("pieceSize")) {
                val ps = options.getMember("pieceSize")
                if (ps.hasMember("y")) ps.getMember("y").asInt() else 48
            } else 48

            val pieceSizeZ = if (options != null && options.hasMember("pieceSize")) {
                val ps = options.getMember("pieceSize")
                if (ps.hasMember("z")) ps.getMember("z").asInt() else 48
            } else 48

            // Normalize coordinates (min to max)
            val minX = minOf(x1, x2)
            val minY = minOf(y1, y2)
            val minZ = minOf(z1, z2)
            val maxX = maxOf(x1, x2)
            val maxY = maxOf(y1, y2)
            val maxZ = maxOf(z1, z2)

            val totalSizeX = maxX - minX + 1
            val totalSizeY = maxY - minY + 1
            val totalSizeZ = maxZ - minZ + 1

            // Calculate grid dimensions
            val gridMaxX = (totalSizeX - 1) / pieceSizeX
            val gridMaxY = (totalSizeY - 1) / pieceSizeY
            val gridMaxZ = (totalSizeZ - 1) / pieceSizeZ

            ConfigManager.debug("[LargeStructureNbtManager] Capturing large structure: $nameWithNamespace")
            ConfigManager.debug("[LargeStructureNbtManager] Region: ${totalSizeX}x${totalSizeY}x${totalSizeZ}")
            ConfigManager.debug("[LargeStructureNbtManager] Piece size: ${pieceSizeX}x${pieceSizeY}x${pieceSizeZ}")
            ConfigManager.debug("[LargeStructureNbtManager] Grid: ${gridMaxX + 1}x${gridMaxY + 1}x${gridMaxZ + 1} pieces")

            // Parse structure name
            val (namespace, baseName) = parseStructureName(nameWithNamespace)

            // Validate structure name contains only valid characters for ResourceLocations
            if (!baseName.matches(Regex("^[a-z0-9/._-]+$"))) {
                future.completeExceptionally(IllegalArgumentException(
                    "Invalid structure name '$baseName'. Structure names must contain only lowercase letters, numbers, and characters /._-\n" +
                    "Example: 'my_structure' or 'buildings/house_1'"
                ))
                return future
            }

            // Backup existing large structure (if it exists)
            backupLargeStructure(namespace, baseName)

            // Clean up old pieces in write directory
            val largeStructDir = structuresPath?.resolve(namespace)?.resolve(GENERATED_STRUCTURES_DIR)?.resolve(RJS_LARGE_SUBDIR)?.resolve(baseName)
            if (largeStructDir != null && largeStructDir.exists() && Files.isDirectory(largeStructDir)) {
                // Delete all existing .nbt files in the directory
                Files.list(largeStructDir)
                    .filter { it.extension == "nbt" }
                    .forEach { Files.delete(it) }
                ConfigManager.debug("[LargeStructureNbtManager] Cleared write directory for: $nameWithNamespace")
            }

            // Track all required mod namespaces (excluding minecraft)
            val requiredMods = mutableSetOf<String>()

            // Capture each piece
            val captureFutures = mutableListOf<CompletableFuture<Void>>()

            for (gridX in 0..gridMaxX) {
                for (gridY in 0..gridMaxY) {
                    for (gridZ in 0..gridMaxZ) {
                        // Calculate piece bounds
                        val pieceMinX = minX + (gridX * pieceSizeX)
                        val pieceMinY = minY + (gridY * pieceSizeY)
                        val pieceMinZ = minZ + (gridZ * pieceSizeZ)

                        val pieceMaxX = minOf(pieceMinX + pieceSizeX - 1, maxX)
                        val pieceMaxY = minOf(pieceMinY + pieceSizeY - 1, maxY)
                        val pieceMaxZ = minOf(pieceMinZ + pieceSizeZ - 1, maxZ)

                        // Create piece name: rjs-large/<name>/X_Y_Z
                        val pieceName = "$namespace:$RJS_LARGE_SUBDIR/$baseName/${gridX}_${gridY}_${gridZ}"

                        // Create position values for capture
                        val context = graalContext
                        if (context != null) {
                            context.enter()
                            try {
                                val piecePos1 = context.eval("js", "({x: $pieceMinX, y: $pieceMinY, z: $pieceMinZ, dimension: '$dimension'})")
                                val piecePos2 = context.eval("js", "({x: $pieceMaxX, y: $pieceMaxY, z: $pieceMaxZ, dimension: '$dimension'})")

                                // Capture this piece (delegate to StructureNbtManager, skip backup since we backed up the whole directory)
                                val pieceFuture = StructureNbtManager.capture(piecePos1, piecePos2, pieceName, options, skipBackup = true)

                                // Add to futures list
                                captureFutures.add(pieceFuture)

                                // TODO: Collect required mod namespaces from blocks in this piece
                                // For now, we'll add this after all pieces are captured

                            } finally {
                                context.leave()
                            }
                        }
                    }
                }
            }

            // Wait for all pieces to complete
            CompletableFuture.allOf(*captureFutures.toTypedArray()).whenComplete { _, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                // Now update 0_0_0.nbt with requires[] metadata
                try {
                    val originPath = getStructurePath(namespace, "rjs-large/$baseName/0_0_0")

                    if (originPath != null && originPath.exists()) {
                        // Load 0_0_0.nbt
                        val originNBT = NbtIo.readCompressed(originPath, net.minecraft.nbt.NbtAccounter.unlimitedHeap())

                        // TODO: Scan all pieces to collect required mods
                        // For now, just create empty requires array
                        val metadata = if (originNBT.contains("metadata")) {
                            originNBT.getCompound("metadata")
                        } else {
                            CompoundTag()
                        }

                        // Add requires array (empty for now)
                        val requiresList = ListTag()
                        metadata.put("requires", requiresList)
                        originNBT.put("metadata", metadata)

                        // Write back
                        NbtIo.writeCompressed(originNBT, originPath)

                        ConfigManager.debug("[LargeStructureNbtManager] Large structure captured: $nameWithNamespace")
                        future.complete(null)
                    } else {
                        future.completeExceptionally(IllegalStateException("Origin piece 0_0_0 not found"))
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
     * Place a large structure (async).
     * Returns Promise<void> after placing all pieces.
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

            // Get dimension
            val dimension = if (options != null && options.hasMember("dimension")) {
                options.getMember("dimension").asString()
            } else if (position.hasMember("dimension")) {
                position.getMember("dimension").asString()
            } else {
                "minecraft:overworld"
            }

            // Get rotation (default 0)
            val rotation = if (options != null && options.hasMember("rotation")) {
                options.getMember("rotation").asInt()
            } else {
                0
            }

            // Get centered flag (default false)
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

            // Parse structure name
            val (namespace, baseName) = parseStructureName(nameWithNamespace)

            // Find all pieces using StructureTemplateManager (searches all resource sources)
            srv.execute {
                try {
                    val allTemplates = srv.structureManager.listTemplates()
                        .collect(Collectors.toList())

                    // DEBUG: Log all rjs-large paths for this namespace
                    ConfigManager.debug("[LargeStructureNbtManager] === Debugging placeLarge for $namespace:$baseName ===")
                    ConfigManager.debug("[LargeStructureNbtManager] Looking for paths starting with: $RJS_LARGE_SUBDIR/$baseName/")

                    val allRjsLargePaths = allTemplates
                        .filter { it.namespace == namespace && it.path.startsWith("$RJS_LARGE_SUBDIR/") }
                    ConfigManager.debug("[LargeStructureNbtManager] Found ${allRjsLargePaths.size} rjs-large paths in namespace $namespace:")
                    allRjsLargePaths.forEach {
                        ConfigManager.debug("[LargeStructureNbtManager]   - ${it.namespace}:${it.path}")
                    }

                    // Filter for pieces belonging to this large structure
                    val pieceFiles = allTemplates
                        .filter { loc ->
                            val matches = loc.namespace == namespace &&
                                loc.path.startsWith("$RJS_LARGE_SUBDIR/$baseName/")
                            if (!matches && loc.namespace == namespace && loc.path.startsWith("$RJS_LARGE_SUBDIR/")) {
                                ConfigManager.debug("[LargeStructureNbtManager] Piece ${loc.path} does NOT match filter $RJS_LARGE_SUBDIR/$baseName/")
                            }
                            matches
                        }
                        .mapNotNull { loc ->
                            // Extract piece name (e.g., "rjs-large/castle/0_0_0" -> "0_0_0")
                            val pathParts = loc.path.split("/")
                            val pieceName = if (pathParts.size >= 3) pathParts[2] else null
                            ConfigManager.debug("[LargeStructureNbtManager] Extracted piece name: $pieceName from path: ${loc.path}")
                            pieceName
                        }
                        .toList()

                    ConfigManager.debug("[LargeStructureNbtManager] Final pieceFiles list size: ${pieceFiles.size}")
                    ConfigManager.debug("[LargeStructureNbtManager] Piece names: $pieceFiles")

                    if (pieceFiles.isEmpty()) {
                        ConfigManager.debug("[LargeStructureNbtManager] ERROR: No pieces found!")
                        future.completeExceptionally(IllegalArgumentException("Large structure not found or has no pieces: $nameWithNamespace"))
                        return@execute
                    }

                    continueWithPlacement(namespace, baseName, pieceFiles, x, y, z, dimension, rotation, centered, mode, future)
                } catch (e: Exception) {
                    ConfigManager.debug("[LargeStructureNbtManager] Exception during piece discovery: ${e.message}")
                    e.printStackTrace()
                    future.completeExceptionally(e)
                }
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Continue with large structure placement after discovering pieces.
     * Separated to handle async discovery from StructureTemplateManager.
     */
    private fun continueWithPlacement(
        namespace: String,
        baseName: String,
        pieceFiles: List<String>,
        x: Int,
        y: Int,
        z: Int,
        dimension: String,
        rotation: Int,
        centered: Boolean,
        mode: String,
        future: CompletableFuture<Void>
    ) {
        try {

            // Load 0_0_0 to get piece size
            val originLoadFuture = load("$namespace:$RJS_LARGE_SUBDIR/$baseName/0_0_0")

            originLoadFuture.whenComplete { originData, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                try {
                    val pieceSizeX = originData.size.x
                    val pieceSizeY = originData.size.y
                    val pieceSizeZ = originData.size.z

                    // Calculate total size if centered is needed
                    val totalSize = if (centered) {
                        // Find max grid coordinates
                        var maxGridX = 0
                        var maxGridY = 0
                        var maxGridZ = 0

                        pieceFiles.forEach { pieceName ->
                            val parts = pieceName.split("_")
                            if (parts.size == 3) {
                                maxGridX = maxOf(maxGridX, parts[0].toInt())
                                maxGridY = maxOf(maxGridY, parts[1].toInt())
                                maxGridZ = maxOf(maxGridZ, parts[2].toInt())
                            }
                        }

                        // Load max piece to get remainder size
                        val maxPieceLoadFuture = load("$namespace:$RJS_LARGE_SUBDIR/$baseName/${maxGridX}_${maxGridY}_${maxGridZ}")
                        val maxPieceSize = maxPieceLoadFuture.get() // Blocking wait - TODO: make this async
                        intArrayOf(
                            maxGridX * pieceSizeX + maxPieceSize.size.x,
                            maxGridY * pieceSizeY + maxPieceSize.size.y,
                            maxGridZ * pieceSizeZ + maxPieceSize.size.z
                        )
                    } else {
                        null
                    }

                    // Calculate base position (apply centering if requested)
                    val baseX = if (centered && totalSize != null) x - totalSize[0] / 2 else x
                    val baseY = y
                    val baseZ = if (centered && totalSize != null) z - totalSize[2] / 2 else z

                    // Create position calculator using RotationHelper
                    val posCalc = com.rhett.rhettjs.world.logic.RotationHelper.createPositionCalculator(
                        startPos = intArrayOf(baseX, baseY, baseZ),
                        rotation = rotation,
                        pieceSize = intArrayOf(pieceSizeX, pieceSizeY, pieceSizeZ)
                    )

                    // Place each piece
                    val placeFutures = mutableListOf<CompletableFuture<Void>>()

                    pieceFiles.forEach { pieceName ->
                        val parts = pieceName.split("_")
                        if (parts.size == 3) {
                            val gridX = parts[0].toInt()
                            val gridY = parts[1].toInt()
                            val gridZ = parts[2].toInt()

                            // Calculate world position for this piece
                            val worldPos = posCalc(intArrayOf(gridX, gridY, gridZ))

                            // Create position value for place()
                            val context = graalContext
                            if (context != null) {
                                context.enter()
                                try {
                                    val piecePos = context.eval("js", "({x: ${worldPos[0]}, y: ${worldPos[1]}, z: ${worldPos[2]}, dimension: '$dimension'})")
                                    val pieceOptions = if (rotation != 0 || mode != "replace") {
                                        val optionsObj = mutableMapOf<String, Any>()
                                        if (rotation != 0) optionsObj["rotation"] = rotation
                                        if (mode != "replace") optionsObj["mode"] = mode
                                        val optionsJson = optionsObj.entries.joinToString(", ") { (k, v) ->
                                            if (v is String) "\"$k\": \"$v\"" else "\"$k\": $v"
                                        }
                                        context.eval("js", "({$optionsJson})")
                                    } else {
                                        null
                                    }

                                    // Place this piece (delegate to StructureNbtManager)
                                    val pieceFuture = StructureNbtManager.place(piecePos, "$namespace:$RJS_LARGE_SUBDIR/$baseName/$pieceName", pieceOptions)
                                    placeFutures.add(pieceFuture)

                                } finally {
                                    context.leave()
                                }
                            }
                        }
                    }

                    // Wait for all pieces to be placed
                    CompletableFuture.allOf(*placeFutures.toTypedArray()).whenComplete { _, placeThrowable ->
                        if (placeThrowable != null) {
                            future.completeExceptionally(placeThrowable)
                        } else {
                            ConfigManager.debug("[LargeStructureNbtManager] Placed large structure: $namespace:$baseName (${pieceFiles.size} pieces, rotation=$rotation)")
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
    }

    /**
     * Get the size of a large structure (async).
     * For regular structures, use StructureNbt.getSize().
     * Returns Promise<{x, y, z}>.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun getSize(nameWithNamespace: String): CompletableFuture<Map<String, Int>> {
        val future = CompletableFuture<Map<String, Int>>()

        try {
            val (namespace, baseName) = parseStructureName(nameWithNamespace)

            // Verify this is a large structure
            val largeDir = structuresPath?.resolve(namespace)?.resolve(GENERATED_STRUCTURES_DIR)?.resolve(RJS_LARGE_SUBDIR)?.resolve(baseName)
            if (largeDir == null || !largeDir.exists() || !Files.isDirectory(largeDir)) {
                future.completeExceptionally(IllegalArgumentException("Large structure not found: $nameWithNamespace"))
                return future
            }

            // Load origin piece to get piece size
            val originLoadFuture = load("$namespace:$RJS_LARGE_SUBDIR/$baseName/0_0_0")

            originLoadFuture.whenComplete { originData, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                try {
                    val pieceSizeX = originData.size.x
                    val pieceSizeY = originData.size.y
                    val pieceSizeZ = originData.size.z

                    // Find max grid coordinates
                    var maxGridX = 0
                    var maxGridY = 0
                    var maxGridZ = 0

                    Files.list(largeDir).use { files ->
                        files.filter { it.isRegularFile() && it.extension == "nbt" }
                            .forEach { file ->
                                val parts = file.nameWithoutExtension.split("_")
                                if (parts.size == 3) {
                                    maxGridX = maxOf(maxGridX, parts[0].toInt())
                                    maxGridY = maxOf(maxGridY, parts[1].toInt())
                                    maxGridZ = maxOf(maxGridZ, parts[2].toInt())
                                }
                            }
                    }

                    // Load max piece to get remainder size
                    val maxPieceLoadFuture = load("$namespace:$RJS_LARGE_SUBDIR/$baseName/${maxGridX}_${maxGridY}_${maxGridZ}")

                    maxPieceLoadFuture.whenComplete { maxPieceData, maxThrowable ->
                        if (maxThrowable != null) {
                            future.completeExceptionally(maxThrowable)
                        } else {
                            // Calculate total size
                            val totalX = maxGridX * pieceSizeX + maxPieceData.size.x
                            val totalY = maxGridY * pieceSizeY + maxPieceData.size.y
                            val totalZ = maxGridZ * pieceSizeZ + maxPieceData.size.z

                            future.complete(mapOf("x" to totalX, "y" to totalY, "z" to totalZ))
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
     * List large structures from resource system (async).
     * Uses StructureTemplateManager to find rjs-large structures from all sources.
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
            // Execute on main thread to access StructureTemplateManager
            srv.execute {
                try {
                    // List all structures, filter for rjs-large paths
                    // Convert Java Stream to Kotlin list first
                    val allTemplates = srv.structureManager.listTemplates()
                        .collect(Collectors.toList())

                    val largeStructures = allTemplates
                        .filter { loc ->
                            // Filter by namespace if specified
                            (namespaceFilter == null || loc.namespace == namespaceFilter) &&
                            // Only include rjs-large pieces
                            loc.path.startsWith("$RJS_LARGE_SUBDIR/")
                        }
                        .mapNotNull { loc ->
                            // Extract structure name from path (e.g., "rjs-large/castle/0_0_0" -> "castle")
                            val pathParts = loc.path.split("/")
                            if (pathParts.size >= 2) {
                                val structureName = pathParts[1]
                                "${loc.namespace}:$structureName"
                            } else {
                                null
                            }
                        }
                        .distinct()  // Each structure appears once, even though it has multiple pieces
                        .sorted()

                    ConfigManager.debug("[LargeStructureNbtManager] Listed ${largeStructures.size} large structures from resource system")
                    future.complete(largeStructures)
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
     * Remove a large structure (all pieces) (async).
     * Returns Promise<boolean> (true if removed, false if didn't exist).
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun remove(nameWithNamespace: String): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()

        try {
            val (namespace, baseName) = parseStructureName(nameWithNamespace)

            // Get large structure directory
            val largeDir = structuresPath?.resolve(namespace)?.resolve(GENERATED_STRUCTURES_DIR)?.resolve(RJS_LARGE_SUBDIR)?.resolve(baseName)
            if (largeDir == null || !largeDir.exists() || !Files.isDirectory(largeDir)) {
                future.complete(false)
                return future
            }

            // Backup before deletion
            backupLargeStructure(namespace, baseName)

            // Delete all files in the directory
            Files.walk(largeDir).use { paths ->
                paths.sorted(Comparator.reverseOrder())
                    .forEach { Files.delete(it) }
            }

            ConfigManager.debug("[LargeStructureNbtManager] Removed large structure: $nameWithNamespace")
            future.complete(true)
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }


    /**
     * Replace blocks in all pieces of a large structure (async).
     * Also updates metadata.requires[] in 0_0_0.nbt with new namespace requirements.
     * Returns Promise<void> after saving all modified pieces (with backup).
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param replacementMap Map of oldBlockId → newBlockId
     */
    fun blocksReplace(nameWithNamespace: String, replacementMap: Map<String, String>): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        try {
            val (namespace, baseName) = parseStructureName(nameWithNamespace)

            // Get large structure directory
            val largeDir = structuresPath?.resolve(namespace)?.resolve(GENERATED_STRUCTURES_DIR)?.resolve(RJS_LARGE_SUBDIR)?.resolve(baseName)
            if (largeDir == null || !largeDir.exists() || !Files.isDirectory(largeDir)) {
                future.completeExceptionally(IllegalArgumentException("Large structure not found: $nameWithNamespace"))
                return future
            }

            // Backup before modification
            backupLargeStructure(namespace, baseName)

            // Find all piece files
            val pieceFiles = Files.list(largeDir)
                .filter { it.isRegularFile() && it.extension == "nbt" }
                .map { it.nameWithoutExtension }
                .toList()

            if (pieceFiles.isEmpty()) {
                future.completeExceptionally(IllegalArgumentException("Large structure has no pieces: $nameWithNamespace"))
                return future
            }

            // Replace blocks in each piece (delegate to StructureNbtManager)
            val replaceFutures = pieceFiles.map { pieceName ->
                val pieceFullName = "$namespace:$RJS_LARGE_SUBDIR/$baseName/$pieceName"
                StructureNbtManager.blocksReplace(pieceFullName, replacementMap)
            }

            // Wait for all replacements to complete
            CompletableFuture.allOf(*replaceFutures.toTypedArray()).whenComplete { _, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                // Update metadata.requires[] in 0_0_0.nbt
                try {
                    updateLargeStructureMetadata(namespace, baseName)
                    ConfigManager.debug("[LargeStructureNbtManager] Replaced blocks in large structure: $nameWithNamespace (${pieceFiles.size} pieces, ${replacementMap.size} mappings)")
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
     * Update metadata.requires[] in 0_0_0.nbt with current namespace requirements.
     * Scans all pieces to find unique namespaces (excluding "minecraft").
     *
     * @param namespace Structure namespace
     * @param baseName Structure base name (without rjs-large prefix)
     */
    private fun updateLargeStructureMetadata(namespace: String, baseName: String) {
        val originPath = getStructurePath(namespace, "$RJS_LARGE_SUBDIR/$baseName/0_0_0")
        if (originPath == null || !originPath.exists()) {
            ConfigManager.debug("[LargeStructureNbtManager] Warning: 0_0_0.nbt not found for large structure")
            return
        }

        // Load 0_0_0.nbt
        val originNBT = NbtIo.readCompressed(originPath, net.minecraft.nbt.NbtAccounter.unlimitedHeap())

        // Scan all pieces to collect required namespaces
        val largeDir = structuresPath?.resolve(namespace)?.resolve(GENERATED_STRUCTURES_DIR)?.resolve(RJS_LARGE_SUBDIR)?.resolve(baseName)
        val allNamespaces = mutableSetOf<String>()

        if (largeDir != null && largeDir.exists()) {
            Files.list(largeDir)
                .filter { it.extension == "nbt" }
                .forEach { pieceFile ->
                    try {
                        val pieceNBT = NbtIo.readCompressed(pieceFile, net.minecraft.nbt.NbtAccounter.unlimitedHeap())
                        val palette = pieceNBT.getList("palette", 10) // 10 = CompoundTag

                        for (i in 0 until palette.size) {
                            val blockTag = palette.getCompound(i)
                            val blockName = blockTag.getString("Name")
                            val blockNamespace = blockName.substringBefore(':')
                            if (blockNamespace != "minecraft") {
                                allNamespaces.add(blockNamespace)
                            }
                        }
                    } catch (e: Exception) {
                        ConfigManager.debug("[LargeStructureNbtManager] Warning: Failed to read piece ${pieceFile.fileName}: ${e.message}")
                    }
                }
        }

        // Update metadata
        val metadata = if (originNBT.contains("metadata")) {
            originNBT.getCompound("metadata")
        } else {
            CompoundTag()
        }

        // Create requires list
        val requiresList = ListTag()
        allNamespaces.sorted().forEach { ns ->
            requiresList.add(net.minecraft.nbt.StringTag.valueOf(ns))
        }
        metadata.put("requires", requiresList)
        originNBT.put("metadata", metadata)

        // Write back (skip backup since we already backed up the directory)
        NbtIo.writeCompressed(originNBT, originPath)

        ConfigManager.debug("[LargeStructureNbtManager] Updated metadata.requires[] in 0_0_0.nbt: ${allNamespaces.sorted()}")
    }


    /**
     * List available directory backups for a large structure (async).
     * Returns Promise<List<String>> of backup timestamps.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun listBackups(nameWithNamespace: String): CompletableFuture<List<String>> {
        val future = CompletableFuture<List<String>>()

        try {
            val (namespace, baseName) = parseStructureName(nameWithNamespace)

            // Get backups directory
            val backupsPath = structuresPath?.parent?.resolve("backups")?.resolve(GENERATED_STRUCTURES_DIR)?.resolve(RJS_LARGE_SUBDIR)
            if (backupsPath == null || !backupsPath.exists()) {
                future.complete(emptyList())
                return future
            }

            // Find all backup directories for this structure
            val backupDirs = Files.list(backupsPath)
                .filter { Files.isDirectory(it) }
                .filter { it.fileName.toString().startsWith("$baseName.") }
                .map { dir ->
                    // Extract timestamp from directory name (e.g., "castle.2026-01-05_15-30-45" -> "2026-01-05_15-30-45")
                    dir.fileName.toString().substringAfter("$baseName.")
                }
                .sorted(Comparator.reverseOrder()) // Newest first
                .toList()

            future.complete(backupDirs)
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Restore large structure from directory backup (async).
     * Returns Promise<void> after restoration.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param timestamp Optional specific backup timestamp, or null for most recent
     */
    fun restoreBackup(nameWithNamespace: String, timestamp: String?): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        try {
            val (namespace, baseName) = parseStructureName(nameWithNamespace)

            // Get backups directory
            val backupsPath = structuresPath?.parent?.resolve("backups")?.resolve(GENERATED_STRUCTURES_DIR)?.resolve(RJS_LARGE_SUBDIR)
            if (backupsPath == null || !backupsPath.exists()) {
                future.completeExceptionally(IllegalArgumentException("No backups found for: $nameWithNamespace"))
                return future
            }

            // Find backup directory
            val backupDir = if (timestamp != null) {
                // Use specific timestamp
                backupsPath.resolve("$baseName.$timestamp")
            } else {
                // Find most recent backup
                Files.list(backupsPath)
                    .filter { Files.isDirectory(it) }
                    .filter { it.fileName.toString().startsWith("$baseName.") }
                    .sorted(Comparator.reverseOrder()) // Newest first
                    .findFirst()
                    .orElse(null)
            }

            if (backupDir == null || !backupDir.exists()) {
                future.completeExceptionally(IllegalArgumentException("Backup not found for: $nameWithNamespace"))
                return future
            }

            // Get target directory
            val targetDir = structuresPath?.resolve(namespace)?.resolve(GENERATED_STRUCTURES_DIR)?.resolve(RJS_LARGE_SUBDIR)?.resolve(baseName)
            if (targetDir == null) {
                future.completeExceptionally(IllegalStateException("Target directory not available"))
                return future
            }

            // Create target directory if doesn't exist
            if (!targetDir.exists()) {
                Files.createDirectories(targetDir)
            }

            // Delete existing files in target
            Files.list(targetDir)
                .filter { it.extension == "nbt" }
                .forEach { Files.delete(it) }

            // Copy all .nbt files from backup
            Files.list(backupDir)
                .filter { it.extension == "nbt" }
                .forEach { backupFile ->
                    val targetFile = targetDir.resolve(backupFile.fileName)
                    Files.copy(backupFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }

            ConfigManager.debug("[LargeStructureNbtManager] Restored large structure: $nameWithNamespace from backup ${timestamp ?: "latest"}")
            future.complete(null)
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
        ConfigManager.debug("[LargeStructureNbtManager] Reset complete")
    }
}
