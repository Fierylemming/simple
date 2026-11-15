package com.simplecolonies.colony;

import net.minecraft.core.BlockPos;

/** Colony territory is a horizontal disk; different elevations do not make overlapping claims safe. */
final class GrowthBounds {
    static boolean separated(BlockPos center, int radius, BlockPos other, int otherRadius) {
        long dx = (long) center.getX() - other.getX();
        long dz = (long) center.getZ() - other.getZ();
        long separation = (long) radius + otherRadius;
        return dx * dx + dz * dz >= separation * separation;
    }
    private GrowthBounds() {}
}
