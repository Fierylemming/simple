package com.simplecolonies.colony;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyClaimsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void recordsUniquePositionsAndForgetsRemovedBlocks() {
        PropertyClaims claims = new PropertyClaims();
        BlockPos wall = new BlockPos(10, 64, 10);
        assertTrue(claims.record(wall));
        assertFalse(claims.record(wall), "duplicate placement must not grow the registry");
        assertTrue(claims.contains(wall));
        assertEquals(1, claims.size());
        assertTrue(claims.remove(wall));
        assertFalse(claims.contains(wall));
        assertTrue(claims.isEmpty());
    }

    @Test void proximityUsesAllThreeAxesAndCrossesChunkBorders() {
        PropertyClaims claims = new PropertyClaims();
        // Adjacent chunks: a naive chunk lookup would miss this pair.
        claims.record(new BlockPos(15, 64, 3));
        assertTrue(claims.near(new BlockPos(16, 64, 3), 1));
        assertFalse(claims.near(new BlockPos(17, 64, 3), 1));
        assertFalse(claims.near(new BlockPos(16, 66, 3), 1), "vertical margin must be respected");
        assertTrue(claims.near(new BlockPos(16, 66, 3), 2));
        assertTrue(claims.near(new BlockPos(15, 64, 3), 0));
        assertFalse(claims.near(new BlockPos(15, 64, 4), 0));
    }

    @Test void evictionKeepsTheNewestClaimsWithinTheCap() {
        PropertyClaims claims = new PropertyClaims();
        for (int i = 0; i < PropertyClaims.MAX_CLAIMS + 5; i++) claims.record(new BlockPos(i, 64, 0));
        assertEquals(PropertyClaims.MAX_CLAIMS, claims.size());
        assertFalse(claims.contains(new BlockPos(0, 64, 0)), "oldest claim should be evicted");
        assertFalse(claims.contains(new BlockPos(4, 64, 0)));
        assertTrue(claims.contains(new BlockPos(5, 64, 0)));
        assertTrue(claims.contains(new BlockPos(PropertyClaims.MAX_CLAIMS + 4, 64, 0)));
    }

    @Test void pruneDropsOnlyBlocksThatAreGone() {
        PropertyClaims claims = new PropertyClaims();
        Set<Long> keep = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            BlockPos pos = new BlockPos(i, 70, 0);
            claims.record(pos);
            if (i % 2 == 0) keep.add(pos.asLong());
        }
        int removed = claims.prune(pos -> keep.contains(pos.asLong()));
        assertEquals(5, removed);
        assertEquals(5, claims.size());
        assertEquals(0, claims.prune(pos -> keep.contains(pos.asLong())), "prune is idempotent");
    }

    @Test void savesAndLoadsAcrossReload() {
        PropertyClaims claims = new PropertyClaims();
        claims.record(new BlockPos(100, 70, -100));
        claims.record(new BlockPos(-5, 12, 900));
        CompoundTag tag = claims.save();

        PropertyClaims restored = new PropertyClaims();
        restored.load(tag);
        assertEquals(2, restored.size());
        assertTrue(restored.contains(new BlockPos(100, 70, -100)));
        assertTrue(restored.near(new BlockPos(101, 70, -101), 1));

        // Loading replaces, rather than merges with, an existing registry.
        restored.load(new CompoundTag());
        assertTrue(restored.isEmpty());
    }
}
