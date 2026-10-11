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
import com.github.cerealklla.protectyons.enforcement.TerrainHeightScanner;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
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
 *
 * <p><b>Baked terrain snapshot, added 2026-10-11</b> -- fixes a real bug: the
 * {@code NO_VOXEL_CHANGE_ALONG_SURFACE_AND_UP} protected band used to be recomputed live from
 * {@code Heightmap.Types.WORLD_SURFACE} on every single block check, so once a tall building went
 * up on a settlement, the band recalculated relative to the building's own roof instead of the real
 * ground underneath -- the actual foundation, now far below "surface" by this reading, silently
 * became breakable. Each {@link Area} now also carries a {@code terrainHeights} map (packed column
 * -> baked Y), populated once via {@link #bakeTerrainSnapshot} at settlement finalize (see {@code
 * api.Protectyons#registerProtectedSettlementArea}) and read by {@code
 * enforcement.VoxelProtectionListener} via {@link #getBakedHeight} in place of a live scan. A
 * permitted actor (by construction, a settlement's Mayor or Town Planner -- see {@code
 * enforcement.NaturalTerrainBlocks}' own doc for why no separate role check is needed) can shift the
 * baseline afterward by reshaping genuinely natural terrain via {@link #updateColumnHeight}; building
 * with a crafted material never moves it, regardless of height. Only ever baked for a
 * settlement-wide registration -- a plot's own {@code FULL_HEIGHT} registration has no band depth to
 * begin with, so its {@code terrainHeights} stays permanently empty.
 */
public final class ProtectedAreaRegistry extends SavedData {

    public static final SavedDataType<ProtectedAreaRegistry> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(ProtectyonsMod.MODID, "protected_area_registry"),
            ProtectedAreaRegistry::new,
            codec());

    /**
     * One baked ground-height reading for a single (x, z) column, taken once at settlement finalize
     * (or shifted afterward -- see {@link #updateColumnHeight}). Serialized as a flat list rather than
     * a native map (codecs don't have a clean {@code Long}-keyed map primitive), same reasoning as
     * the outer {@code areas} map's own list-of-records-to-map codec below.
     */
    private record ColumnHeight(int x, int z, int y) {
        static final Codec<ColumnHeight> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(ColumnHeight::x),
                Codec.INT.fieldOf("z").forGetter(ColumnHeight::z),
                Codec.INT.fieldOf("y").forGetter(ColumnHeight::y)
        ).apply(i, ColumnHeight::new));
    }

    private static long packColumn(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private record Area(UUID id, Geometry area, Set<UUID> permittedPlayers, Map<Long, Integer> terrainHeights) {
        static final Codec<Area> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Area::id),
                Geometry.CODEC.fieldOf("area").forGetter(Area::area),
                UUIDUtil.CODEC_SET.fieldOf("permitted_players").forGetter(Area::permittedPlayers),
                Codec.list(ColumnHeight.CODEC).fieldOf("terrain_heights").forGetter(
                        a -> a.terrainHeights().entrySet().stream()
                                .map(e -> new ColumnHeight((int) (e.getKey() >> 32), (int) (long) e.getKey(), e.getValue()))
                                .toList())
        ).apply(i, (id, area, permittedPlayers, heights) -> {
            Map<Long, Integer> map = new HashMap<>();
            for (ColumnHeight h : heights) {
                map.put(packColumn(h.x(), h.z()), h.y());
            }
            return new Area(id, area, permittedPlayers, map);
        }));
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
        areas.put(areaId, new Area(areaId, area, new HashSet<>(permittedPlayers), new HashMap<>()));
        setDirty();
    }

    public void updatePermittedPlayers(UUID areaId, Set<UUID> permittedPlayers) {
        Area existing = areas.get(areaId);
        if (existing == null) {
            return;
        }
        areas.put(areaId, new Area(areaId, existing.area(), new HashSet<>(permittedPlayers), existing.terrainHeights()));
        setDirty();
    }

    public void unregister(UUID areaId) {
        if (areas.remove(areaId) != null) {
            setDirty();
        }
    }

    /**
     * Bakes the real ground height for every column inside {@code areaId}'s registered polygon,
     * once, at the moment a settlement is finalized -- added 2026-10-11 to fix a real bug: the
     * protected band used to be recomputed live from whatever currently occupies a column, so a
     * tall building pushed the band up to its own roof instead of staying at the real ground. Only
     * called for the settlement-wide registration (see {@code api.Protectyons#registerProtectedSettlementArea}),
     * never for a plot's {@code FULL_HEIGHT} registration, which has no band depth to begin with.
     */
    public void bakeTerrainSnapshot(UUID areaId, ServerLevel level) {
        Area existing = areas.get(areaId);
        if (existing == null) {
            return;
        }
        Map<Long, Integer> heights = new HashMap<>();
        ChunkPos min = existing.area().minChunk();
        ChunkPos max = existing.area().maxChunk();
        for (int cx = min.x(); cx <= max.x(); cx++) {
            for (int cz = min.z(); cz <= max.z(); cz++) {
                int baseX = cx << 4;
                int baseZ = cz << 4;
                for (int dx = 0; dx < 16; dx++) {
                    for (int dz = 0; dz < 16; dz++) {
                        int x = baseX + dx;
                        int z = baseZ + dz;
                        if (existing.area().contains(x, z)) {
                            heights.put(packColumn(x, z), TerrainHeightScanner.scanSurfaceY(level, x, z));
                        }
                    }
                }
            }
        }
        areas.put(areaId, new Area(areaId, existing.area(), existing.permittedPlayers(), heights));
        setDirty();
    }

    /**
     * The baked ground height at this column, from whichever registered area covers it and actually
     * has a snapshot (a plot's own {@code FULL_HEIGHT} registration never does, so this naturally
     * only ever resolves against a settlement-wide registration) -- or {@code null} if none do, in
     * which case the caller falls back to a live scan. Not keyed by area id: {@code
     * VoxelProtectionListener} only ever needs one surface height per column, the same way it
     * already computed one live height per column before this feature existed, regardless of how
     * many {@link com.github.cerealklla.cartographyr.geo.GeographicEntity GeographicEntity} values
     * occupy that position (Cartographyr's own entity id is a separate, unrelated namespace from
     * this registry's area id -- see this class' own doc).
     */
    public Integer getBakedHeight(int x, int z) {
        for (Area area : areas.values()) {
            if (!area.terrainHeights().isEmpty() && area.area().contains(x, z)) {
                Integer height = area.terrainHeights().get(packColumn(x, z));
                if (height != null) {
                    return height;
                }
            }
        }
        return null;
    }

    /**
     * Shifts the baked baseline for one column, in every registered area covering it that has a
     * snapshot -- the auto-shift half of the 2026-10-11 fix: a Mayor/Town Planner reshaping *natural*
     * terrain (see {@code enforcement.NaturalTerrainBlocks}) inside their own settlement moves the
     * recorded ground height with them, instead of the baseline staying frozen at whatever it was
     * baked at. No-op (per area) if that area was never baked.
     */
    public void updateColumnHeight(int x, int z, int newY) {
        boolean changed = false;
        for (Area area : areas.values()) {
            if (!area.terrainHeights().isEmpty() && area.area().contains(x, z)) {
                area.terrainHeights().put(packColumn(x, z), newY);
                changed = true;
            }
        }
        if (changed) {
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
