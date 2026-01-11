package com.rhett.rhettjs.mixin;

import com.rhett.rhettjs.config.ConfigManager;
import com.rhett.rhettjs.structure.WorldgenStructurePlacementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to intercept Level.getHeight() calls during structure placement.
 *
 * Terrain-matching structures may query heights directly from the Level's
 * heightmap instead of going through ChunkGenerator.
 */
@Mixin(Level.class)
public class LevelHeightMixin {

    /**
     * Intercept Level.getHeight() to return custom heights during structure placement.
     */
    @Inject(
        method = "getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I",
        at = @At("HEAD"),
        cancellable = true
    )
    private void rhettjs$overrideLevelHeight(
        Heightmap.Types heightmapType,
        int x,
        int z,
        CallbackInfoReturnable<Integer> cir
    ) {
        // Check if we have an active placement context with height overrides
        Integer override = WorldgenStructurePlacementContext.INSTANCE.getHeightOverride(x, z);
        if (override != null) {
            cir.setReturnValue(override);
        }
        // Otherwise, let vanilla handle it
    }
}
