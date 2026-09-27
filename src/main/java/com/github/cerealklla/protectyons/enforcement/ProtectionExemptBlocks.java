package com.github.cerealklla.protectyons.enforcement;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decides whether a block is exempt from voxel protection entirely -- per the user's own spec,
 * "trees, leaves, plants, snow, seedlings, crops etc can still be destroyed/placed" regardless of
 * {@link com.github.cerealklla.cartographyr.geo.ProtectionLevel}; only solid-block creation/
 * destruction is actually blocked. This class is the "is this one of those" check.
 *
 * <p><b>Reworked 2026-09-26</b> (a real playtest bug -- see decisions.md): the original version
 * enumerated specific tags (`SAPLINGS`/`CROPS`/`FLOWERS`), which missed real vanilla plants that
 * don't happen to carry any of those exact tags -- sweet berry bushes were the reported case, but
 * the same gap would have hit mushrooms, sea pickles, nether wart, cactus flowers, etc. Confirmed
 * against the decompiled source that vanilla already has exactly the right common superclass for
 * this: {@link VegetationBlock} covers essentially every non-solid plant block (crops, flowers,
 * saplings, mushrooms, sweet berry bush, tall grass, sea pickles, seagrass, lily pad, nether wart/
 * fungus/roots/sprouts, cactus flower, azalea, stems, etc.) -- checking `instanceof` against it is
 * far more robust than chasing individual tags one report at a time.
 *
 * <p>Needs a live {@code BlockState} (and the tag registry behind it), so unlike {@link
 * ProtectedRange} this isn't unit-testable without a game bootstrap -- same limitation as other
 * live-state checks elsewhere in the suite (e.g. Cartographyr's {@code NaturalRegionDiscovery}).
 */
public final class ProtectionExemptBlocks {

    private ProtectionExemptBlocks() {
    }

    public static boolean isExempt(BlockState state) {
        return state.getBlock() instanceof VegetationBlock
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.SNOW)
                || state.is(BlockTags.REPLACEABLE); // catches vines and anything else not covered above
    }
}
