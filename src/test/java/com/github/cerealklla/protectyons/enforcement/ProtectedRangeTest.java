package com.github.cerealklla.protectyons.enforcement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.github.cerealklla.cartographyr.geo.ProtectionLevel;

import net.minecraft.resources.Identifier;

class ProtectedRangeTest {

    @Test
    void unprotectedIsNeverProtectedRegardlessOfPosition() {
        assertFalse(ProtectedRange.isProtected(ProtectionLevel.UNPROTECTED, 70, 70));
        assertFalse(ProtectedRange.isProtected(ProtectionLevel.UNPROTECTED, 70, 0));
        assertFalse(ProtectedRange.isProtected(ProtectionLevel.UNPROTECTED, 70, 300));
    }

    @Test
    void fullHeightProtectsEveryYRegardlessOfSurface() {
        ProtectionLevel level = ProtectionLevel.NO_VOXEL_CHANGE_FULL_HEIGHT;
        assertTrue(ProtectedRange.isProtected(level, 70, -60));
        assertTrue(ProtectedRange.isProtected(level, 70, 70));
        assertTrue(ProtectedRange.isProtected(level, 70, 320));
    }

    @Test
    void alongSurfaceAndUpProtectsFromFiveBelowSurfaceUpward() {
        ProtectionLevel level = ProtectionLevel.NO_VOXEL_CHANGE_ALONG_SURFACE_AND_UP;
        int surface = 70;

        assertFalse(ProtectedRange.isProtected(level, surface, surface - 6));
        assertTrue(ProtectedRange.isProtected(level, surface, surface - 5));
        assertTrue(ProtectedRange.isProtected(level, surface, surface));
        assertTrue(ProtectedRange.isProtected(level, surface, surface + 50));
    }

    @Test
    void allowsUndergroundCavingWellBelowTheSurfaceBand() {
        ProtectionLevel level = ProtectionLevel.NO_VOXEL_CHANGE_ALONG_SURFACE_AND_UP;
        assertFalse(ProtectedRange.isProtected(level, 70, -50));
    }

    @Test
    void unrecognizedThirdPartyLevelIsNotEnforcedByThisMod() {
        ProtectionLevel consecratedGround = new ProtectionLevel(Identifier.fromNamespaceAndPath("religyons", "consecrated_ground"));
        assertFalse(ProtectedRange.isProtected(consecratedGround, 70, 70));
    }
}
