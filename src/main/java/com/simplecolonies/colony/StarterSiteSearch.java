package com.simplecolonies.colony;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Deterministic, dense search rather than sparse radial spokes. */
final class StarterSiteSearch {
    record Offset(int x, int z) {}
    private static final Map<Long, List<Offset>> CACHE = new ConcurrentHashMap<>();

    static List<Offset> offsets(int radius, int step) {
        long key = ((long) radius << 32) | (step & 0xffffffffL);
        return CACHE.computeIfAbsent(key, ignored -> {
            List<Offset> result = new ArrayList<>();
            int extent = radius / step;
            for (int x = -extent; x <= extent; x++) for (int z = -extent; z <= extent; z++) {
                int dx = x * step, dz = z * step;
                if ((long) dx * dx + (long) dz * dz <= (long) radius * radius) result.add(new Offset(dx, dz));
            }
            result.sort(Comparator.comparingLong(o -> (long) o.x() * o.x() + (long) o.z() * o.z()));
            return List.copyOf(result);
        });
    }

    // The foundation and clearance are replaced; deeper solid blocks are left alone.
    static boolean safeBlock(int relativeY, boolean fluid, boolean blockEntity, boolean naturalGround, boolean clearable) {
        return !fluid && !blockEntity && (relativeY < -1 || naturalGround || clearable);
    }

    /** True when a generated structure (village, outpost, ruin, ...) actually
     * reaches into the radius. Structure references alone cover whole chunks,
     * so every candidate is confirmed with StructureManager#getStructureAt at
     * several heights before the site is rejected. */
    static boolean structureNear(net.minecraft.server.level.ServerLevel level,
                                 BlockPos center, int radius) {
        if (radius <= 0) return false;
        for (int dx = -radius; dx <= radius; dx += 8) for (int dz = -radius; dz <= radius; dz += 8) {
            if ((long) dx * dx + (long) dz * dz > (long) radius * radius) continue;
            BlockPos sample = center.offset(dx, 0, dz);
            if (!level.isLoaded(sample)) continue;
            int surface = level.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    sample.getX(), sample.getZ());
            BlockPos column = new BlockPos(sample.getX(), surface, sample.getZ());
            for (var structure : level.structureManager().getAllStructuresAt(column).keySet())
                for (int dy = -10; dy <= 6; dy += 2)
                    if (level.structureManager().getStructureAt(column.offset(0, dy, 0), structure).isValid())
                        return true;
        }
        return false;
    }

    static boolean separated(int x1, int z1, int x2, int z2, int distance) {
        long dx = (long) x1 - x2, dz = (long) z1 - z2;
        return dx * dx + dz * dz >= (long) distance * distance;
    }
    private StarterSiteSearch() {}
}
