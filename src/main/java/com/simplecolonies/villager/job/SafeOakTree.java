package com.simplecolonies.villager.job;

import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.List;

/** Conservative tree recognizer. Anything unknown, unloaded or built is a veto. */
final class SafeOakTree {
    interface View {
        boolean loaded(BlockPos pos);
        boolean protectedPosition(BlockPos pos);
        boolean soil(BlockPos pos);
        boolean trunk(BlockPos pos);
        /** 0=vertical, 1=X, 2=Z, -1=not this species' log. */
        default int branchAxis(BlockPos pos) { return trunk(pos) ? 0 : -1; }
        boolean naturalLeaf(BlockPos pos);
        boolean clearOrNatural(BlockPos pos, boolean belowBase);
    }

    static List<BlockPos> trunk(View view, BlockPos base) {
        if (!view.loaded(base) || !view.loaded(base.below()) || !view.soil(base.below()) || !view.trunk(base)) return List.of();
        List<BlockPos> logs = new ArrayList<>();
        for (int y = 0; y <= 10; y++) {
            BlockPos pos = base.above(y);
            if (!view.loaded(pos)) return List.of();
            if (!view.trunk(pos)) break;
            if (y == 10 || view.protectedPosition(pos)) return List.of();
            logs.add(pos);
        }
        if (logs.size() < 3) return List.of();
        int leaves = 0;
        boolean touchingCanopy = false;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            for (int dy = -1; dy <= logs.size() + 2; dy++) {
                BlockPos pos = base.offset(dx, dy, dz);
                if (!view.loaded(pos)) return List.of();
                if (dx == 0 && dz == 0 && dy >= 0 && dy < logs.size()) continue;
                if (!view.clearOrNatural(pos, dy < 0)) return List.of();
                if (dy >= logs.size() - 2 && view.naturalLeaf(pos)) {
                    leaves++;
                    if (dy < logs.size() && Math.abs(dx) + Math.abs(dz) == 1) touchingCanopy = true;
                }
            }
        }
        return leaves >= 5 && touchingCanopy ? List.copyOf(logs) : List.of();
    }

    /** Recheck the entire envelope after each cut; remaining logs must still match the original tree.
     * Removed logs are allowed to be air, but any new construction aborts the job. */
    static boolean safePartialTrunk(View view, BlockPos base, List<BlockPos> original, int remaining) {
        if (remaining < 1 || remaining > original.size() || !view.loaded(base.below()) || !view.soil(base.below())) return false;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            for (int dy = -1; dy <= original.size() + 2; dy++) {
                BlockPos pos = base.offset(dx, dy, dz);
                if (!view.loaded(pos)) return false;
                if (dx == 0 && dz == 0 && dy >= 0 && dy < original.size()) {
                    if (view.protectedPosition(pos)) return false;
                    if (dy < remaining ? !view.trunk(pos) : !view.clearOrNatural(pos, false)) return false;
                } else if (!view.clearOrNatural(pos, dy < 0)) return false;
            }
        }
        return true;
    }

    /** A small, complete 2x2 trunk, with four rooted columns and a natural canopy.
     * Extra logs, branches, construction and unloaded space veto the whole tree. */
    static List<BlockPos> squareTrunk(View view, BlockPos base) {
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            var root = base.offset(x, 0, z);
            if (!view.loaded(root) || !view.loaded(root.below()) || !view.soil(root.below()) || !view.trunk(root)) return List.of();
        }
        var logs = new ArrayList<BlockPos>();
        int height = 0;
        for (int y = 0; y <= 6; y++) {
            int present = 0;
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
                var p = base.offset(x, y, z);
                if (!view.loaded(p)) return List.of();
                if (view.trunk(p)) present++;
            }
            if (present == 0) break;
            if (present != 4 || y == 6) return List.of();
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
                var p = base.offset(x, y, z);
                if (view.protectedPosition(p)) return List.of();
                logs.add(p);
            }
            height++;
        }
        if (height < 3) return List.of();
        int leaves = 0;
        boolean touching = false;
        for (int x = -3; x <= 4; x++) for (int z = -3; z <= 4; z++) for (int y = -1; y <= height + 2; y++) {
            var p = base.offset(x, y, z);
            if (!view.loaded(p)) return List.of();
            if (x >= 0 && x < 2 && z >= 0 && z < 2 && y >= 0 && y < height) continue;
            if (!view.clearOrNatural(p, y < 0)) return List.of();
            if (y >= height - 2 && view.naturalLeaf(p)) {
                leaves++;
                if (y < height && ((x == -1 || x == 2) && z >= 0 && z < 2
                        || (z == -1 || z == 2) && x >= 0 && x < 2)) touching = true;
            }
        }
        return leaves >= 8 && touching ? List.copyOf(logs) : List.of();
    }

    /** Revalidate the complete square envelope before every cut, including after reload. */
    static boolean safePartialSquare(View view, BlockPos base, List<BlockPos> original, int remaining) {
        int height = original.size() / 4;
        if (height < 3 || height > 6 || original.size() != height * 4 || remaining < 1 || remaining > original.size()) return false;
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            var soil = base.offset(x, -1, z);
            if (!view.loaded(soil) || !view.soil(soil)) return false;
        }
        for (int x = -3; x <= 4; x++) for (int z = -3; z <= 4; z++) for (int y = -1; y <= height + 2; y++) {
            var p = base.offset(x, y, z);
            if (!view.loaded(p)) return false;
            if (x >= 0 && x < 2 && z >= 0 && z < 2 && y >= 0 && y < height) {
                int index = y * 4 + x * 2 + z;
                if (!original.get(index).equals(p) || view.protectedPosition(p)) return false;
                if (index < remaining ? !view.trunk(p) : !view.clearOrNatural(p, false)) return false;
            } else if (!view.clearOrNatural(p, y < 0)) return false;
        }
        return true;
    }

    static boolean safePlantingArea(View view, BlockPos base) {
        if (!view.loaded(base) || !view.loaded(base.below()) || !view.soil(base.below()) || view.protectedPosition(base)) return false;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int y = -1; y <= 5; y++) {
            BlockPos pos = base.offset(dx, y, dz);
            if (!view.loaded(pos) || !view.clearOrNatural(pos, y < 0)) return false;
        }
        return true;
    }
    private SafeOakTree() {}
}
