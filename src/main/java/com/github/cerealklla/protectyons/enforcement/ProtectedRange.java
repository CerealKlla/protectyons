package com.github.cerealklla.protectyons.enforcement;

import com.github.cerealklla.cartographyr.geo.ProtectionLevel;

/**
 * Pure geometry: given a {@link ProtectionLevel} and the ground's surface height at a column,
 * decides whether a specific block Y coordinate falls inside that level's protected vertical
 * range. No Minecraft/game-bootstrap dependency (unlike most of this package) -- {@link
 * ProtectionLevel} is just an {@code Identifier}-keyed record, so this is genuinely unit-testable,
 * the same "extract the pure geometry" precedent as Settlemynts' {@code PerimeterFit}/{@code
 * BoundaryWallLayout}.
 *
 * <p>Only the two built-in levels this mod actually enforces are recognized here -- {@link
 * ProtectionLevel#UNPROTECTED} and any third-party/unrecognized level (e.g. a future Religyons
 * "consecrated_ground") are never protected by this class, since enforcing those isn't
 * Protectyons' job (see {@code ProtectionLevel}'s own Javadoc: Cartographyr never enforces
 * anything itself, and a level's *meaning* is up to whichever mod defined it).
 */
public final class ProtectedRange {

    /**
     * How far below the surface {@link ProtectionLevel#NO_VOXEL_CHANGE_ALONG_SURFACE_AND_UP}'s
     * protected band starts -- e.g. a surface at Y=70 protects Y=65 and up, leaving natural caves
     * and underground mining below that untouched. Per the user's own spec: "surface layer - 5".
     */
    public static final int SURFACE_BAND_BELOW_BLOCKS = 5;

    private ProtectedRange() {
    }

    /**
     * @param level the entity's protection level at this column
     * @param surfaceHeightY the column's surface height (e.g. {@code Heightmap.Types.WORLD_SURFACE})
     * @param blockY the Y coordinate of the block being broken/placed
     * @return whether this specific position is protected
     */
    public static boolean isProtected(ProtectionLevel level, int surfaceHeightY, int blockY) {
        if (level.equals(ProtectionLevel.NO_VOXEL_CHANGE_FULL_HEIGHT)) {
            return true; // The entire vertical column, bedrock to build height -- Y is irrelevant.
        }
        if (level.equals(ProtectionLevel.NO_VOXEL_CHANGE_ALONG_SURFACE_AND_UP)) {
            return blockY >= surfaceHeightY - SURFACE_BAND_BELOW_BLOCKS;
        }
        return false; // Unprotected, or a level this mod doesn't know how to enforce.
    }
}
