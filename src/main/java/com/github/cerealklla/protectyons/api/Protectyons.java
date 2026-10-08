package com.github.cerealklla.protectyons.api;

import java.util.Set;
import java.util.UUID;

import com.github.cerealklla.cartographyr.geo.Geometry;
import com.github.cerealklla.protectyons.permission.ProtectedAreaRegistry;

import net.minecraft.server.MinecraftServer;

/**
 * The stable public entry point for other mods to register their own player-permission areas with
 * Protectyons -- same "stable facade, don't reach into internals" pattern as Cartographyr's {@code
 * Cartography}, Blueprynts' {@code api.Blueprynts}, etc.
 *
 * <p>Deliberately separate from Cartographyr's own entity model (2026-09-29 design discussion): a
 * caller (Settlemynts, for a Plot) registers the same {@link Geometry} it already gave Cartographyr
 * for world tracking, plus the set of players who should be exempt from this area's protection --
 * Protectyons owns that permission data entirely, Cartographyr never sees it.
 */
public final class Protectyons {

    private Protectyons() {
    }

    /** {@code areaId} is the caller's own id for this area (e.g. a Settlemynts {@code PlotRecord#plotId}) -- reused for {@link #updatePermittedPlayers}/{@link #unregisterProtectedArea}, not minted here. */
    public static void registerProtectedArea(MinecraftServer server, UUID areaId, Geometry area, Set<UUID> permittedPlayers) {
        ProtectedAreaRegistry.get(server).register(areaId, area, permittedPlayers);
    }

    /** Replaces the full permitted-players set for an already-registered area (e.g. a Town Planner granted/revoked, or the plot's Owner changed) -- no-op if {@code areaId} isn't registered. */
    public static void updatePermittedPlayers(MinecraftServer server, UUID areaId, Set<UUID> permittedPlayers) {
        ProtectedAreaRegistry.get(server).updatePermittedPlayers(areaId, permittedPlayers);
    }

    public static void unregisterProtectedArea(MinecraftServer server, UUID areaId) {
        ProtectedAreaRegistry.get(server).unregister(areaId);
    }
}
