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
        if (isProtectedAt(serverLevel, event.getPos())) {
            event.setCanceled(true);
        }
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
