package com.github.cerealklla.protectyons.permission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.github.cerealklla.cartographyr.geo.Geometry;
import com.github.cerealklla.protectyons.ProtectyonsMod;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Save-wide registry of "this area is owned/administered by these players" -- deliberately separate
 * from Cartographyr's own {@link com.github.cerealklla.cartographyr.geo.GeographicEntity} model
 * (2026-09-29 design discussion with the user): "Cartographyr doesn't need to know anything about
 * player permissions. Cartographyr knows areas of the world." This is Protectyons' own concern --
 * who's allowed to edit blocks where -- keyed by whatever id the registering mod already uses for
 * that area (Settlemynts uses its own {@code PlotRecord#plotId}), not tied to any Cartographyr
 * entity id at all.
 *
 * <p>Populated by {@code api.Protectyons}, consulted by {@code enforcement.VoxelProtectionListener}
 * to exempt a permitted player from an otherwise-protected area's block-change restriction. A linear
 * scan over every registered area per check is fine at this suite's scale (same precedent as
 * Cartographyr's own spatial-index deferral) -- there's no dedicated spatial index here either.
 *
 * <p>{@code MinecraftServer}-scoped (mirrors Blueprynts' own {@code ConstructionBoxIndex}), not
 * {@code ServerLevel#getDataStorage()} -- a plot's polygon coordinates are dimension-specific in
 * practice (Settlemynts plots are overworld-only today), but there's no reason to force a second,
 * disconnected copy per dimension if that ever changes.
 */
public final class ProtectedAreaRegistry extends SavedData {

    public static final SavedDataType<ProtectedAreaRegistry> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(ProtectyonsMod.MODID, "protected_area_registry"),
            ProtectedAreaRegistry::new,
            codec());

    private record Area(UUID id, Geometry area, Set<UUID> permittedPlayers) {
        static final Codec<Area> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Area::id),
                Geometry.CODEC.fieldOf("area").forGetter(Area::area),
                UUIDUtil.CODEC_SET.fieldOf("permitted_players").forGetter(Area::permittedPlayers)
        ).apply(i, Area::new));
    }

    private final Map<UUID, Area> areas;

    ProtectedAreaRegistry() {
        this(new HashMap<>());
    }

    private ProtectedAreaRegistry(Map<UUID, Area> areas) {
        this.areas = areas;
    }

    private static Codec<ProtectedAreaRegistry> codec() {
        return Codec.list(Area.CODEC).xmap(
                entries -> {
                    Map<UUID, Area> map = new HashMap<>();
                    for (Area entry : entries) {
                        map.put(entry.id(), entry);
                    }
                    return new ProtectedAreaRegistry(map);
                },
                data -> new ArrayList<>(data.areas.values()));
    }

    public static ProtectedAreaRegistry get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public void register(UUID areaId, Geometry area, Set<UUID> permittedPlayers) {
        areas.put(areaId, new Area(areaId, area, new HashSet<>(permittedPlayers)));
        setDirty();
    }

    public void updatePermittedPlayers(UUID areaId, Set<UUID> permittedPlayers) {
        Area existing = areas.get(areaId);
        if (existing == null) {
            return;
        }
        areas.put(areaId, new Area(areaId, existing.area(), new HashSet<>(permittedPlayers)));
        setDirty();
    }

    public void unregister(UUID areaId) {
        if (areas.remove(areaId) != null) {
            setDirty();
        }
    }

    /** True if {@code player} is in the permitted set of any registered area containing {@code (x, z)}. */
    public boolean isPermitted(int x, int z, UUID player) {
        if (player == null) {
            return false;
        }
        for (Area area : areas.values()) {
            if (area.permittedPlayers().contains(player) && area.area().contains(x, z)) {
                return true;
            }
        }
        return false;
    }
}
