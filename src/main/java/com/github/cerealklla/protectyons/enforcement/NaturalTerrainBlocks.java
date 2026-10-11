package com.github.cerealklla.protectyons.enforcement;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decides whether a block reads as "raw ground" rather than a crafted building material -- added
 * 2026-10-11 alongside the baked-terrain-snapshot fix (see {@code permission.ProtectedAreaRegistry}'s
 * own doc). Used to decide whether a Mayor/Town Planner's edit inside their own settlement's
 * protected band should shift the baked ground-height baseline: reshaping *natural* terrain
 * (digging into or piling up dirt/stone/sand/etc.) moves the baseline with it, but building a real
 * structure out of planks/bricks/stone-brick/concrete/etc. never does, regardless of height -- which
 * is exactly what keeps a tall building from re-triggering the original "protection band follows the
 * roof" bug this feature fixes.
 *
 * <p>Deliberately a simple, no-new-UI rule rather than an explicit recalibration action -- accepted
 * limitation, called out to the user: a tower built out of plain stone or dirt (both natural-tagged)
 * still shifts the band up, same as the original bug, since this class can't distinguish "terraforming"
 * from "building with a natural-looking block" by block identity alone.
 */
public final class NaturalTerrainBlocks {

    private NaturalTerrainBlocks() {
    }

    public static boolean isNatural(BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.MUD)
                || state.is(Blocks.TERRACOTTA);
    }
}
