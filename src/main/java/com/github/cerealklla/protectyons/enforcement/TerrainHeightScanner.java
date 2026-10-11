package com.github.cerealklla.protectyons.enforcement;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Finds the real ground surface at a column, ignoring trees/leaves/plants/snow entirely -- extracted
 * 2026-10-11 from {@code VoxelProtectionListener#findSolidSurfaceY} so both the live enforcement
 * fallback and {@code permission.ProtectedAreaRegistry}'s one-time terrain bake share a single
 * implementation. Walks down from {@link Heightmap.Types#WORLD_SURFACE} (a cheap starting point, not
 * scanning from build height every time), skipping any block {@link ProtectionExemptBlocks#isExempt}
 * already treats as non-solid, and returns the position just above the first genuinely solid block
 * found -- the same "height" convention vanilla's own heightmaps use.
 */
public final class TerrainHeightScanner {

    private TerrainHeightScanner() {
    }

    public static int scanSurfaceY(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int minY = level.getMinY();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        while (y > minY) {
            cursor.set(x, y - 1, z);
            var state = level.getBlockState(cursor);
            if (!state.isAir() && !ProtectionExemptBlocks.isExempt(state)) {
                return y;
            }
            y--;
        }
        return minY;
    }
}
