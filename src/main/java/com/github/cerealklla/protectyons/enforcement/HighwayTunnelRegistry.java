package com.github.cerealklla.protectyons.enforcement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.github.cerealklla.protectyons.ProtectyonsMod;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

/**
 * Permanent, no-exemptions protection for a 3D capsule around a Settlemynts Highway's own stake-to-
 * stake line -- added 2026-10-13, explicit user spec: "find all the blocks that are within 10 of the
 * direct line between stakes and make them invincible to all forms of creation or destruction."
 *
 * <p>Deliberately a separate, Protectyons-local registry rather than a new {@code ProtectionLevel}
 * enforced through {@link ProtectedRange} -- that class's {@code isProtected(ProtectionLevel,
 * surfaceHeightY, blockY)} has no concept of "distance to a 3D line" at all, and Cartographyr's own
 * {@code Geometry} is strictly 2D (X/Z column containment only -- confirmed by reading its sealed
 * interface before building this), so widening either to carry real 3D line-segment geometry would
 * ripple through the spatial index and every exhaustive switch over `Geometry`'s permitted types for
 * one narrow feature. Instead, this keeps its own small, independent map of capsule segments,
 * checked directly by {@link VoxelProtectionListener#isProtectedAt} alongside (but before, and
 * unconditionally unlike) the existing Cartographyr-entity/{@link
 * com.github.cerealklla.protectyons.permission.ProtectedAreaRegistry} checks -- a tunnel segment has
 * no permission-bypass concept at all, by design (immune to everyone, including a settlement's own
 * Mayor), so it deliberately does NOT go through that registry's own exemption list (which would
 * also mean paying its real, accepted linear-scan cost on every single block edit, forever, for as
 * long as the tunnel exists -- not worth it for a feature with no exemptions to check in the first
 * place).
 *
 * <p>{@code segmentId} is the caller's own id -- Settlemynts reuses the exact same Cartographyr
 * {@code EntityId#value()} it already mints for the paved edge's own road entity, so cleanup
 * (unregistering this alongside {@code Cartography#retireEntity}) needs no extra tracking of its
 * own. A plain linear scan over every registered segment per check, same accepted-at-this-suite's-
 * scale tradeoff {@code ProtectedAreaRegistry} itself already documents -- a cheap per-segment
 * bounding-box prefilter (before the real point-to-segment distance calc) keeps the common case fast
 * without needing a full spatial index for what's expected to be a modest number of Highway edges.
 */
public final class HighwayTunnelRegistry extends SavedData {

    public static final SavedDataType<HighwayTunnelRegistry> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(ProtectyonsMod.MODID, "highway_tunnel_registry"),
            HighwayTunnelRegistry::new,
            codec());

    public record Segment(Vec3 start, Vec3 end, double radiusBlocks) {
    }

    private record StoredSegment(long segmentId, Vec3 start, Vec3 end, double radiusBlocks) {
        static final Codec<StoredSegment> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("segment_id").forGetter(StoredSegment::segmentId),
                Vec3.CODEC.fieldOf("start").forGetter(StoredSegment::start),
                Vec3.CODEC.fieldOf("end").forGetter(StoredSegment::end),
                Codec.DOUBLE.fieldOf("radius_blocks").forGetter(StoredSegment::radiusBlocks)
        ).apply(i, StoredSegment::new));
    }

    private final Map<Long, Segment> segments;

    HighwayTunnelRegistry() {
        this(new HashMap<>());
    }

    private HighwayTunnelRegistry(Map<Long, Segment> segments) {
        this.segments = segments;
    }

    private static Codec<HighwayTunnelRegistry> codec() {
        return Codec.list(StoredSegment.CODEC).xmap(
                stored -> {
                    Map<Long, Segment> map = new HashMap<>();
                    for (StoredSegment entry : stored) {
                        map.put(entry.segmentId(), new Segment(entry.start(), entry.end(), entry.radiusBlocks()));
                    }
                    return new HighwayTunnelRegistry(map);
                },
                data -> {
                    List<StoredSegment> stored = new java.util.ArrayList<>(data.segments.size());
                    data.segments.forEach((id, segment) -> stored.add(new StoredSegment(id, segment.start(), segment.end(), segment.radiusBlocks())));
                    return stored;
                });
    }

    public static HighwayTunnelRegistry get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public void register(long segmentId, Vec3 start, Vec3 end, double radiusBlocks) {
        segments.put(segmentId, new Segment(start, end, radiusBlocks));
        setDirty();
    }

    public void unregister(long segmentId) {
        if (segments.remove(segmentId) != null) {
            setDirty();
        }
    }

    public boolean isWithinAnyTunnel(BlockPos pos) {
        double px = pos.getX() + 0.5;
        double py = pos.getY() + 0.5;
        double pz = pos.getZ() + 0.5;
        for (Segment segment : segments.values()) {
            double radius = segment.radiusBlocks();
            double minX = Math.min(segment.start().x, segment.end().x) - radius;
            double maxX = Math.max(segment.start().x, segment.end().x) + radius;
            if (px < minX || px > maxX) {
                continue;
            }
            double minZ = Math.min(segment.start().z, segment.end().z) - radius;
            double maxZ = Math.max(segment.start().z, segment.end().z) + radius;
            if (pz < minZ || pz > maxZ) {
                continue;
            }
            double minY = Math.min(segment.start().y, segment.end().y) - radius;
            double maxY = Math.max(segment.start().y, segment.end().y) + radius;
            if (py < minY || py > maxY) {
                continue;
            }
            if (distanceToSegmentSq(px, py, pz, segment) <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    private static double distanceToSegmentSq(double px, double py, double pz, Segment segment) {
        Vec3 a = segment.start();
        Vec3 b = segment.end();
        double abx = b.x - a.x;
        double aby = b.y - a.y;
        double abz = b.z - a.z;
        double lengthSq = abx * abx + aby * aby + abz * abz;
        double t = lengthSq <= 1.0E-6
                ? 0.0
                : Math.max(0.0, Math.min(1.0, ((px - a.x) * abx + (py - a.y) * aby + (pz - a.z) * abz) / lengthSq));
        double cx = a.x + abx * t;
        double cy = a.y + aby * t;
        double cz = a.z + abz * t;
        double dx = px - cx;
        double dy = py - cy;
        double dz = pz - cz;
        return dx * dx + dy * dy + dz * dz;
    }
}
