package com.github.cerealklla.protectyons.enforcement;

import java.util.Set;
import java.util.UUID;

import com.github.cerealklla.cartographyr.api.Cartography;
import com.github.cerealklla.cartographyr.geo.GeographicEntity;
import com.github.cerealklla.cartographyr.geo.LifecycleState;
import com.github.cerealklla.cartographyr.geo.ProtectionLevel;
import com.github.cerealklla.protectyons.permission.ProtectedAreaRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Cancels solid-block break/place attempts inside a Cartographyr {@link GeographicEntity} whose
 * {@link ProtectionLevel} says not to allow them -- the entire reason this mod exists. Cartographyr
 * itself never enforces {@code ProtectionLevel} (see that class's own Javadoc); this is the
 * concrete enforcement consumer.
 *
 * <p><b>Scope</b>: block break ({@link BreakBlockEvent}), block placement ({@link
 * BlockEvent.EntityPlaceEvent}, any entity -- not just players, see {@link #onEntityPlace} below),
 * explosion block destruction ({@link ExplosionEvent.Detonate}, e.g. creepers/TNT/beds-in-the-
 * nether), and Enderman block theft ({@link EntityMobGriefingEvent}, see {@link #onMobGriefing})
 * are all covered. Fire spread, fluid flow, sculk spread, and other non-player, non-explosion,
 * non-Enderman world changes are still not addressed here -- easy to add later if it turns out to
 * matter, but out of scope for now.
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
        UUID actor = event.getPlayer() != null ? event.getPlayer().getUUID() : null;
        if (isProtectedAt(serverLevel, event.getPos(), actor)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (ProtectionExemptBlocks.isExempt(event.getPlacedBlock())) {
            return;
        }
        if (isInPlaceToolTransformation(event)) {
            return;
        }
        UUID actor = event.getEntity() instanceof Player player ? player.getUUID() : null;
        if (isProtectedAt(serverLevel, event.getPos(), actor)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> {
            var state = serverLevel.getBlockState(pos);
            if (ProtectionExemptBlocks.isExempt(state)) {
                return false;
            }
            return isProtectedAt(serverLevel, pos, null);
        });
    }

    /**
     * Denies an Enderman's block-theft attempt (both stealing and placing back down) while it's
     * standing inside a protected zone. Unlike break/place/explosion, block *pickup* has no
     * cancelable {@code BlockEvent}-family hook at all -- {@code EndermanTakeBlockGoal} calls {@code
     * level.removeBlock} directly (confirmed via decompiled 26.1.2 source), gated only by {@link
     * EntityMobGriefingEvent} (checked in the goal's {@code canUse()}, which re-runs on almost every
     * tick attempt since the goal only has a small random chance to fire each tick). Denying grief
     * here is the closest thing to prevention available through public API -- deliberately chosen
     * over reactively restoring a stolen block after the fact, which would still leave one real tick
     * where the block is actually gone (able to trigger neighbor updates, falling sand/gravel, etc.
     * before being patched back).
     *
     * <p>Approximate by construction: the check uses the Enderman's own current position, not the
     * (randomized, up to 2 blocks away) target block position the goal picks internally each tick --
     * that exact position isn't visible from this event. In practice this is a small edge case (an
     * Enderman right at a settlement's boundary could steal a block just inside it, or be denied for
     * one just outside), acceptable given settlements are typically much larger than a couple blocks.
     * The place-back half doesn't share this approximation: {@link #onEntityPlace} above already
     * covers it exactly, since {@code EndermanLeaveBlockGoal} places via {@link
     * BlockEvent.EntityPlaceEvent} like anything else -- this handler is only strictly needed for the
     * pickup half, but denying grief also short-circuits the place-back goal before it even starts.
     */
    @SubscribeEvent
    public void onMobGriefing(EntityMobGriefingEvent event) {
        if (!(event.getEntity() instanceof EnderMan enderman)) {
            return;
        }
        if (!(enderman.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (isProtectedAt(serverLevel, enderman.blockPosition(), null)) {
            event.setCanGrief(false);
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

    /**
     * @param actor the player attempting the change, or {@code null} for a non-player cause
     *              (explosion, Enderman) -- if non-null and {@link ProtectedAreaRegistry} says this
     *              player is permitted at this position (Settlemynts registers a Plot's Mayor, Town
     *              Planners, and specific Owner this way -- 2026-09-29 design discussion), the area
     *              is treated as unprotected for them regardless of its Cartographyr {@link
     *              ProtectionLevel}.
     */
    private boolean isProtectedAt(ServerLevel level, BlockPos pos, UUID actor) {
        if (ProtectedAreaRegistry.get(level.getServer()).isPermitted(pos.getX(), pos.getZ(), actor)) {
            return false;
        }
        Set<GeographicEntity> here = Cartography.getEntitiesAt(level, pos.getX(), pos.getZ());
        if (here.isEmpty()) {
            return false;
        }

        int surfaceY = findSolidSurfaceY(level, pos.getX(), pos.getZ());
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

    /**
     * The real ground surface at this column, ignoring trees/leaves/plants/snow entirely -- fixed
     * 2026-09-26 (a real playtest bug, see decisions.md): {@code Heightmap.Types.WORLD_SURFACE}
     * counts the top of whatever's tallest at a column, including a tree's leaves or trunk, so
     * standing under a tree inside a settlement pushed the "surface" reading well above the real
     * ground and over-protected empty air alongside it. Walks down from the heightmap's own top
     * (a cheap starting point, not scanning from build height every time), skipping any block
     * {@link ProtectionExemptBlocks#isExempt} already treats as non-solid, and returns the position
     * just above the first genuinely solid block found -- the same "height" convention vanilla's
     * own heightmaps use, so this drops in without changing {@link ProtectedRange}'s own math.
     */
    private int findSolidSurfaceY(ServerLevel level, int x, int z) {
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
