package com.simplecolonies.colony;

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

    static boolean separated(int x1, int z1, int x2, int z2, int distance) {
        long dx = (long) x1 - x2, dz = (long) z1 - z2;
        return dx * dx + dz * dz >= (long) distance * distance;
    }
    private StarterSiteSearch() {}
}
