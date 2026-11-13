package com.simplecolonies.villager.job;

import net.minecraft.core.BlockPos;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A deliberately bounded recognizer for small, rooted, crooked acacia trees.
 * A complete unobstructed envelope and a natural canopy are required; this is
 * not proof that natural-looking player logs are unowned. */
final class SafeBranchedTree {
    private static final int RADIUS = 5, LOG_RADIUS = 3, TOP = 11, LOG_TOP = 8, MAX_LOGS = 32;
    private static final Comparator<BlockPos> ORDER = Comparator.comparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ);

    static List<BlockPos> find(SafeOakTree.View view, BlockPos base) {
        if (!view.loaded(base) || !view.loaded(base.below()) || !view.soil(base.below()) || !view.trunk(base)) return List.of();
        var logs = new ArrayList<BlockPos>();
        var leaves = new ArrayList<BlockPos>();
        for (int x = -RADIUS; x <= RADIUS; x++) for (int z = -RADIUS; z <= RADIUS; z++)
            for (int y = -1; y <= TOP; y++) {
                BlockPos p = base.offset(x, y, z);
                if (!view.loaded(p)) return List.of();
                if (view.trunk(p)) {
                    if (Math.abs(x) > LOG_RADIUS || Math.abs(z) > LOG_RADIUS || y < 0 || y > LOG_TOP
                            || (y == 0 && (x != 0 || z != 0)) || view.protectedPosition(p)) return List.of();
                    logs.add(p);
                    if (logs.size() > MAX_LOGS) return List.of();
                } else {
                    if (!view.clearOrNatural(p, y < 0)) return List.of();
                    if (view.naturalLeaf(p)) leaves.add(p);
                }
            }
        logs.sort(ORDER);
        if (!validShape(base, logs) || leaves.size() < 8) return List.of();
        var positions = new HashSet<>(logs);
        boolean canopy = false;
        for (BlockPos leaf : leaves) {
            if (leaf.getY() < base.getY() + 2) continue;
            for (int x = -1; x <= 1 && !canopy; x++) for (int y = -1; y <= 1 && !canopy; y++)
                for (int z = -1; z <= 1; z++) if (positions.contains(leaf.offset(x, y, z))) { canopy = true; break; }
            if (canopy) break;
        }
        return canopy ? List.copyOf(logs) : List.of();
    }

    /** Only the saved original positions can be cut. New logs, construction,
     * unloaded positions and changed roots or protected areas abort the job. */
    static boolean safePartial(SafeOakTree.View view, BlockPos base, List<BlockPos> logs, int remaining) {
        if (!validShape(base, logs) || remaining < 1 || remaining > logs.size()
                || !view.loaded(base.below()) || !view.soil(base.below())) return false;
        Map<BlockPos, Integer> indices = new HashMap<>();
        for (int i = 0; i < logs.size(); i++) indices.put(logs.get(i), i);
        for (int x = -RADIUS; x <= RADIUS; x++) for (int z = -RADIUS; z <= RADIUS; z++)
            for (int y = -1; y <= TOP; y++) {
                BlockPos p = base.offset(x, y, z);
                if (!view.loaded(p)) return false;
                Integer index = indices.get(p);
                if (index != null) {
                    if (view.protectedPosition(p)) return false;
                    if (index < remaining ? !view.trunk(p) : !view.clearOrNatural(p, false)) return false;
                } else if (!view.clearOrNatural(p, y < 0)) return false;
            }
        return true;
    }

    static boolean areaLoaded(SafeOakTree.View view, BlockPos base) {
        for (int x = -RADIUS; x <= RADIUS; x++) for (int z = -RADIUS; z <= RADIUS; z++)
            for (int y = -1; y <= TOP; y++) if (!view.loaded(base.offset(x, y, z))) return false;
        return true;
    }

    private static boolean validShape(BlockPos base, List<BlockPos> logs) {
        if (logs.size() < 3 || logs.size() > MAX_LOGS || !logs.getFirst().equals(base)) return false;
        Set<BlockPos> all = new HashSet<>(logs);
        if (all.size() != logs.size()) return false;
        for (int i = 0; i < logs.size(); i++) {
            BlockPos p = logs.get(i);
            int x = p.getX() - base.getX(), y = p.getY() - base.getY(), z = p.getZ() - base.getZ();
            if (Math.abs(x) > LOG_RADIUS || Math.abs(z) > LOG_RADIUS || y < 0 || y > LOG_TOP
                    || (y == 0 && !p.equals(base)) || i > 0 && ORDER.compare(logs.get(i - 1), p) >= 0) return false;
        }
        Set<BlockPos> seen = new HashSet<>();
        var queue = new ArrayDeque<BlockPos>();
        seen.add(base); queue.add(base);
        while (!queue.isEmpty()) {
            var p = queue.removeFirst();
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                BlockPos next = p.offset(x, y, z);
                if (all.contains(next) && seen.add(next)) queue.addLast(next);
            }
        }
        return seen.size() == logs.size();
    }
    private SafeBranchedTree() {}
}
