package com.github.cerealklla.protectyons.enforcement;

import java.util.Set;

import com.github.cerealklla.cartographyr.api.Cartography;
import com.github.cerealklla.cartographyr.geo.GeographicEntity;
import com.github.cerealklla.cartographyr.geo.LifecycleState;
import com.github.cerealklla.cartographyr.geo.ProtectionLevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Cancels solid-block break/place attempts inside a Cartographyr {@link GeographicEntity} whose
 * {@link ProtectionLevel} says not to allow them -- the entire reason this mod exists. Cartographyr
 * itself never enforces {@code ProtectionLevel} (see that class's own Javadoc); this is the
 * concrete enforcement consumer.
 *
 * <p><b>Scope, deliberately narrow for v1</b>: only player-driven block break ({@link
 * BreakBlockEvent}) and player-driven block placement ({@link BlockEvent.EntityPlaceEvent},
 * filtered to {@link Player}) are covered. Explosions, fire spread, fluid flow, sculk spread, and
 * other non-player world changes are not addressed here -- easy to add later (e.g. {@code
 * ExplosionEvent.Detonate}) if it turns out to matter, but out of scope for the first pass.
 *
 * <p><b>{@link BlockEvent.EntityPlaceEvent} also fires for in-place tool transformations</b>
 * (hoe-till, axe-strip/scrape/wax-off, shovel-path) -- confirmed via live diagnostic logging,
 * 2026-09-26 (a real surprise: reading {@code HoeItem}'s decompiled source alone suggested tilling
 * only calls {@code Level#setBlock} directly with no event at all, which turned out to be
 * incomplete -- NeoForge's block-change tracking fires this event for those too). None of those
 * transformations create or destroy a solid block -- they convert one already-solid block into
 * another (dirt/grass -> farmland, log -> stripped log, etc.) -- so per the user's own spec they
 * must never be blocked. Detected generically via {@link BlockEvent.EntityPlaceEvent#getBlockSnapshot()}:
 * if the position already held a solid, non-exempt block *before* this event (the snapshot's own
 * {@code getState()}, captured pre-change), this can only be a transformation of something already
 * there, never a genuine new placement -- vanilla has no way for a player to otherwise swap one
 * existing solid block for an arbitrary different one without breaking it first (which would
 * already be blocked by {@link #onBreakBlock} in its own right). This one general rule covers every
 * tool-modification action without needing to enumerate them by {@code ItemAbility}.
 *
 * <p>Only considers {@link LifecycleState#REALIZED} entities -- a {@code PLANNED} settlement has
 * no real structure to protect yet, and a {@code RETIRED}/{@code ABANDONED}/{@code DESTROYED} one
 * is no longer an active place worth protecting (consistent with the general principle, flagged in
 * Cartographyr's own decisions.md 2026-09-26, that a position-query consumer must filter lifecycle
 * state itself since {@code getEntitiesAt} doesn't).
 */
public final class VoxelProtectionListener {

    @SubscribeEvent
    public void onBreakBlock(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (ProtectionExemptBlocks.isExempt(event.getState())) {
            return;
        }
        if (isProtectedAt(serverLevel, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (ProtectionExemptBlocks.isExempt(event.getPlacedBlock())) {
            return;
        }
        if (isInPlaceToolTransformation(event)) {
            return;
        }
        if (isProtectedAt(serverLevel, event.getPos())) {
            event.setCanceled(true);
        }
    }

    /**
     * True if a solid, non-exempt block already occupied this position before this event -- see
     * this class's own doc for why that can only mean an in-place tool transformation (till/strip/
     * scrape/wax-off/path), never a genuine new placement, and must never be blocked.
     */
    private boolean isInPlaceToolTransformation(BlockEvent.EntityPlaceEvent event) {
        var previousState = event.getBlockSnapshot().getState();
        return !previousState.isAir() && !ProtectionExemptBlocks.isExempt(previousState);
    }

    private boolean isProtectedAt(ServerLevel level, BlockPos pos) {
        Set<GeographicEntity> here = Cartography.getEntitiesAt(level, pos.getX(), pos.getZ());
        if (here.isEmpty()) {
            return false;
        }

        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        for (GeographicEntity entity : here) {
            if (entity.lifecycleState() != LifecycleState.REALIZED) {
                continue;
            }
            if (ProtectedRange.isProtected(entity.protectionLevel(), surfaceY, pos.getY())) {
                return true;
            }
        }
        return false;
    }
}
