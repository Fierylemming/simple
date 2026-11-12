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
