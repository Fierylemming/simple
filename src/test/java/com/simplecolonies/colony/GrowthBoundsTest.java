package com.simplecolonies.colony;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrowthBoundsTest {
    @Test void preventsOverlapEvenAtDifferentHeights() {
        BlockPos center = new BlockPos(-100, 64, -100);
        assertFalse(GrowthBounds.separated(center, 64, new BlockPos(11, 200, -100), 48));
        assertTrue(GrowthBounds.separated(center, 64, new BlockPos(12, -50, -100), 48));
        assertTrue(GrowthBounds.separated(center, 64, new BlockPos(100, 64, -100), 48));
    }
}
