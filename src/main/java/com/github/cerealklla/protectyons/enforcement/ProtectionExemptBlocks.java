package com.github.cerealklla.protectyons.enforcement;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decides whether a block is exempt from voxel protection entirely -- per the user's own spec,
 * "trees, leaves, plants, snow, seedlings, crops etc can still be destroyed/placed" regardless of
 * {@link com.github.cerealklla.cartographyr.geo.ProtectionLevel}; only solid-block creation/
 * destruction is actually blocked. This class is the "is this one of those" check.
 *
 * <p>Needs a live {@code BlockState} (and the tag registry behind it), so unlike {@link
 * ProtectedRange} this isn't unit-testable without a game bootstrap -- same limitation as other
 * live-state checks elsewhere in the suite (e.g. Cartographyr's {@code NaturalRegionDiscovery}).
 */
public final class ProtectionExemptBlocks {

    private ProtectionExemptBlocks() {
    }

    public static boolean isExempt(BlockState state) {
        return state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.CROPS)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SNOW)
                || state.is(BlockTags.REPLACEABLE);
    }
}
