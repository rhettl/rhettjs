package com.rhett.rhettjs.structure

import com.rhett.rhettjs.config.ConfigManager
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.levelgen.Heightmap
import java.util.concurrent.ConcurrentHashMap

/**
 * Context for worldgen structure placement with custom height resolution.
 *
 * Manages height overrides during structure generation to allow structures
 * to follow custom platforms/surfaces instead of the world's natural heightmap.
 *
 * Thread-safe: Uses ThreadLocal for active context to support concurrent placements.
 */
object WorldgenStructurePlacementContext {

    /**
     * Surface resolution mode for structure placement.
     */
    sealed class SurfaceMode {
        /** Use vanilla heightmap (default behavior) */
        object Heightmap : SurfaceMode()

        /** Scan actual blocks to find surface (for custom platforms) */
        object Scan : SurfaceMode()

        /** Use a fixed Y level for all placements */
        data class Fixed(val y: Int) : SurfaceMode()

        /** Force rigid placement (all pieces use start Y, no terrain following) */
        object Rigid : SurfaceMode()

        companion object {
            fun parse(value: String): SurfaceMode {
                return when {
                    value == "heightmap" || value == "terrain" -> Heightmap
                    value == "scan" -> Scan
                    value == "rigid" -> Rigid
                    value.startsWith("fixed:") -> {
                        val y = value.removePrefix("fixed:").toIntOrNull()
                            ?: throw IllegalArgumentException("Invalid fixed height: $value")
                        Fixed(y)
                    }
                    else -> throw IllegalArgumentException("Unknown surface mode: $value")
                }
            }
        }
    }

    /**
     * Active placement context per thread.
     */
    private val activeContext = ThreadLocal<PlacementContext?>()

    /**
     * Get the currently active placement context, if any.
     */
    fun getActive(): PlacementContext? = activeContext.get()

    /**
     * Check if there's an active placement context with height overrides.
     */
    fun isOverrideActive(): Boolean {
        val ctx = activeContext.get() ?: return false
        return ctx.surfaceMode != SurfaceMode.Heightmap
    }

    /**
     * Execute a block with a placement context active.
     * The context is automatically cleared when the block completes.
     */
    fun <T> withContext(
        level: ServerLevel,
        surfaceMode: SurfaceMode,
        block: (PlacementContext) -> T
    ): T {
        val context = PlacementContext(level, surfaceMode)
        activeContext.set(context)
        ConfigManager.debug("[WorldgenStructurePlacement] Context activated: mode=$surfaceMode")
        try {
            return block(context)
        } finally {
            activeContext.set(null)
            ConfigManager.debug("[WorldgenStructurePlacement] Context deactivated, ${context.cacheSize} heights cached")
        }
    }

    /**
     * Get height override for a position, if active context exists and has override.
     * Returns null if no override (use vanilla behavior).
     */
    fun getHeightOverride(x: Int, z: Int): Int? {
        val ctx = activeContext.get() ?: return null
        return ctx.getHeight(x, z)
    }

    /**
     * Placement context holding height cache and scanning logic.
     */
    class PlacementContext(
        private val level: ServerLevel,
        val surfaceMode: SurfaceMode
    ) {
        private val heightCache = ConcurrentHashMap<Long, Int>()

        val cacheSize: Int get() = heightCache.size

        /**
         * Get height for a position, scanning and caching as needed.
         * Returns null if using vanilla heightmap mode.
         */
        fun getHeight(x: Int, z: Int): Int? {
            return when (surfaceMode) {
                is SurfaceMode.Heightmap -> null // Use vanilla
                is SurfaceMode.Scan -> getOrScanHeight(x, z)
                is SurfaceMode.Fixed -> surfaceMode.y
                is SurfaceMode.Rigid -> null // Rigid is handled differently (at piece level)
            }
        }

        /**
         * Scan and cache height for a position.
         */
        private fun getOrScanHeight(x: Int, z: Int): Int {
            val key = packXZ(x, z)
            val existingValue = heightCache[key]

            if (existingValue != null) {
                // Cache hit
                return existingValue
            }

            // Cache miss - need to scan
            // scanSurfaceHeight returns the Y of the surface block itself
            // Add +1 to follow heightmap convention (first air above surface)
            val surfaceBlockY = scanSurfaceHeight(x, z)
            val heightValue = surfaceBlockY + 1

            heightCache[key] = heightValue
            return heightValue
        }

        /**
         * Check if a block should be skipped when scanning for surface.
         * Returns true if the block should be ignored (air, vegetation, etc.)
         */
        private fun shouldSkipBlock(state: net.minecraft.world.level.block.state.BlockState): Boolean {
            val block = state.block

            // Skip air
            if (state.isAir) return true

            // Skip vegetation and plant-like blocks
            // Check by tags and properties
            if (state.`is`(net.minecraft.tags.BlockTags.LEAVES)) return true
            if (state.`is`(net.minecraft.tags.BlockTags.FLOWERS)) return true
            if (state.`is`(net.minecraft.tags.BlockTags.CROPS)) return true
            if (state.`is`(net.minecraft.tags.BlockTags.SAPLINGS)) return true

            // Check material/block type
            val material = block.javaClass.simpleName.lowercase()
            when {
                // Grass, ferns, dead bushes
                block is net.minecraft.world.level.block.TallGrassBlock -> return true
                block is net.minecraft.world.level.block.DoublePlantBlock -> return true
                block is net.minecraft.world.level.block.DeadBushBlock -> return true
                block is net.minecraft.world.level.block.FlowerBlock -> return true
                block is net.minecraft.world.level.block.SaplingBlock -> return true

                // Logs and wood (placed or natural)
                material.contains("log") -> return true
                material.contains("wood") -> return true

                // Vines, moss, etc
                block is net.minecraft.world.level.block.VineBlock -> return true

                else -> {
                    // Accept water sources (but not flowing water)
                    if (block is net.minecraft.world.level.block.LiquidBlock) {
                        // Check if it's a source block (not flowing)
                        val fluidState = state.fluidState
                        return !fluidState.isSource // Skip flowing, keep sources
                    }

                    // Accept all other blocks (solid terrain)
                    return false
                }
            }
        }

        /**
         * Scan a column to find the surface height.
         * Custom logic that skips vegetation, logs, and non-source liquids.
         * Accepts water sources as surface.
         *
         * @return The Y coordinate of the surface block itself (NOT +1)
         */
        private fun scanSurfaceHeight(x: Int, z: Int): Int {
            val pos = BlockPos.MutableBlockPos(x, level.maxBuildHeight, z)

            // Scan downward from build limit
            for (y in level.maxBuildHeight downTo level.minBuildHeight) {
                pos.setY(y)
                val state = level.getBlockState(pos)

                // Skip air, vegetation, logs, etc.
                if (shouldSkipBlock(state)) {
                    continue
                }

                // Found a valid surface block (solid or water source)
                return y
            }

            // No solid block found, return min build height
            return level.minBuildHeight
        }

        companion object {
            private fun packXZ(x: Int, z: Int): Long {
                return (x.toLong() and 0xFFFFFFFFL) or ((z.toLong() and 0xFFFFFFFFL) shl 32)
            }
        }
    }
}