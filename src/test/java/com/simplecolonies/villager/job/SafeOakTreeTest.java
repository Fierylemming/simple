package com.simplecolonies.villager.job;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SafeOakTreeTest {
    private enum Block { AIR, SOIL, LOG, LEAF, STRUCTURE, PERSISTENT_LEAF }
    private static final BlockPos ROOT = new BlockPos(0, 64, 0);
    private static final class Fake implements SafeOakTree.View {
        final Map<BlockPos, Block> blocks = new HashMap<>();
        final Map<BlockPos, Integer> axes = new HashMap<>();
        final Set<BlockPos> unloaded = new HashSet<>(), protectedBlocks = new HashSet<>();
        Block block(BlockPos p) { return blocks.getOrDefault(p, Block.AIR); }
        void put(BlockPos p, Block b) { blocks.put(p, b); }
        public boolean loaded(BlockPos p) { return !unloaded.contains(p); }
        public boolean protectedPosition(BlockPos p) { return protectedBlocks.contains(p); }
        public boolean soil(BlockPos p) { return block(p) == Block.SOIL; }
        public boolean trunk(BlockPos p) { return block(p) == Block.LOG && axes.getOrDefault(p, 0) == 0; }
        public int branchAxis(BlockPos p) { return block(p) == Block.LOG ? axes.getOrDefault(p, 0) : -1; }
        public boolean naturalLeaf(BlockPos p) { return block(p) == Block.LEAF; }
        public boolean clearOrNatural(BlockPos p, boolean belowBase) {
            return block(p) == Block.AIR || block(p) == Block.SOIL || block(p) == Block.LEAF;
        }
    }
    private Fake tree() {
        Fake f = new Fake();
        f.put(ROOT.below(), Block.SOIL);
        for (int y = 0; y < 4; y++) f.put(ROOT.above(y), Block.LOG);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (x != 0 || z != 0) f.put(ROOT.offset(x, 3, z), Block.LEAF);
        return f;
    }
    @Test void intactRootedOakIsFelledAsOneWholeTrunk() {
        assertEquals(java.util.List.of(ROOT, ROOT.above(), ROOT.above(2), ROOT.above(3)), SafeOakTree.trunk(tree(), ROOT));
    }
    @Test void wallsAndLogsNearLeavesAreNotTrees() {
        Fake f = tree();
        f.put(ROOT.offset(1, 0, 0), Block.STRUCTURE);
        assertTrue(SafeOakTree.trunk(f, ROOT).isEmpty());
        f = tree();
        f.put(ROOT.offset(1, 1, 0), Block.LOG); // another log or timber beam
        assertTrue(SafeOakTree.trunk(f, ROOT).isEmpty());
        f = tree();
        f.put(ROOT.offset(1, 3, 0), Block.PERSISTENT_LEAF);
        assertTrue(SafeOakTree.trunk(f, ROOT).isEmpty());
    }
    @Test void noChoppingWithoutRootedTrunkCanopyOrLoadedUnprotectedSpace() {
        Fake f = tree();
        f.put(ROOT.below(), Block.STRUCTURE);
        assertTrue(SafeOakTree.trunk(f, ROOT).isEmpty());
        f = tree(); f.protectedBlocks.add(ROOT.above(1));
        assertTrue(SafeOakTree.trunk(f, ROOT).isEmpty());
        f = tree(); f.unloaded.add(ROOT.offset(3, 0, 0));
        assertTrue(SafeOakTree.trunk(f, ROOT).isEmpty());
        f = tree();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (x != 0 || z != 0) f.put(ROOT.offset(x, 3, z), Block.AIR);
        assertTrue(SafeOakTree.trunk(f, ROOT).isEmpty());
    }
    @Test void partialTreeMustRemainSafeBeforeEachPhysicalCut() {
        Fake f = tree();
        var trunk = SafeOakTree.trunk(f, ROOT);
        assertTrue(SafeOakTree.safePartialTrunk(f, ROOT, trunk, 4));
        f.put(ROOT.above(3), Block.AIR);
        assertTrue(SafeOakTree.safePartialTrunk(f, ROOT, trunk, 3));
        f.put(ROOT.offset(2, 0, 0), Block.STRUCTURE);
        assertFalse(SafeOakTree.safePartialTrunk(f, ROOT, trunk, 3));
        f.put(ROOT.offset(2, 0, 0), Block.AIR);
        f.protectedBlocks.add(ROOT.above(1));
        assertFalse(SafeOakTree.safePartialTrunk(f, ROOT, trunk, 3));
    }
    private Fake squareTree() {
        Fake f = new Fake();
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            f.put(ROOT.offset(x, -1, z), Block.SOIL);
            for (int y = 0; y < 4; y++) f.put(ROOT.offset(x, y, z), Block.LOG);
        }
        for (int x = -1; x <= 2; x++) for (int z = -1; z <= 2; z++)
            if (x < 0 || x > 1 || z < 0 || z > 1) f.put(ROOT.offset(x, 3, z), Block.LEAF);
        return f;
    }
    @Test void rootedSquareTrunkRequiresAnIntactNaturalEnvelope() {
        Fake f = squareTree();
        var logs = SafeOakTree.squareTrunk(f, ROOT);
        assertEquals(16, logs.size());
        assertEquals(ROOT, logs.getFirst());
        assertTrue(SafeOakTree.safePartialSquare(f, ROOT, logs, logs.size()));
        f.put(logs.getLast(), Block.AIR);
        assertTrue(SafeOakTree.safePartialSquare(f, ROOT, logs, 15));
        f.put(ROOT.offset(3, 1, 0), Block.STRUCTURE);
        assertFalse(SafeOakTree.safePartialSquare(f, ROOT, logs, 15));
        f = squareTree(); f.put(ROOT.offset(2, 2, 0), Block.LOG);
        assertTrue(SafeOakTree.squareTrunk(f, ROOT).isEmpty());
        f = squareTree(); f.put(ROOT.offset(1, -1, 0), Block.STRUCTURE);
        assertTrue(SafeOakTree.squareTrunk(f, ROOT).isEmpty());
        f = squareTree(); f.protectedBlocks.add(ROOT.offset(1, 3, 1));
        assertTrue(SafeOakTree.squareTrunk(f, ROOT).isEmpty());
        f = squareTree(); f.unloaded.add(ROOT.offset(4, 2, 0));
        assertTrue(SafeOakTree.squareTrunk(f, ROOT).isEmpty());
    }

    @Test void plantingRespectsProtectedAndBuiltAreas() {
        Fake f = new Fake(); f.put(ROOT.below(), Block.SOIL);
        assertTrue(SafeOakTree.safePlantingArea(f, ROOT));
        f.put(ROOT.offset(2, 0, 0), Block.STRUCTURE);
        assertFalse(SafeOakTree.safePlantingArea(f, ROOT));
        f.put(ROOT.offset(2, 0, 0), Block.AIR);
        f.protectedBlocks.add(ROOT);
        assertFalse(SafeOakTree.safePlantingArea(f, ROOT));
    }
    private Fake crookedTree() {
        Fake f = new Fake(); f.put(ROOT.below(), Block.SOIL);
        for (int y = 0; y < 3; y++) f.put(ROOT.above(y), Block.LOG);
        f.put(ROOT.offset(1, 3, 0), Block.LOG);
        f.put(ROOT.offset(2, 4, 0), Block.LOG);
        f.put(ROOT.offset(2, 5, 0), Block.LOG);
        for (int x = 1; x <= 3; x++) for (int z = -1; z <= 1; z++)
            if (x != 2 || z != 0) f.put(ROOT.offset(x, 5, z), Block.LEAF);
        return f;
    }

    @Test void crookedTreeRequiresBoundedConnectedRootedLogsAndCanopy() {
        Fake f = crookedTree();
        var logs = SafeBranchedTree.find(f, ROOT);
        assertEquals(6, logs.size());
        assertEquals(ROOT, logs.getFirst());
        assertTrue(SafeBranchedTree.safePartial(f, ROOT, logs, 6));
        f.put(logs.getLast(), Block.AIR);
        assertTrue(SafeBranchedTree.safePartial(f, ROOT, logs, 5));
        f.put(ROOT.offset(4, 0, 0), Block.STRUCTURE);
        assertFalse(SafeBranchedTree.safePartial(f, ROOT, logs, 5));
        f = crookedTree(); f.put(ROOT.offset(4, 4, 0), Block.LOG);
        assertTrue(SafeBranchedTree.find(f, ROOT).isEmpty());
        f = crookedTree(); f.put(ROOT.offset(-2, 2, 0), Block.LOG);
        assertTrue(SafeBranchedTree.find(f, ROOT).isEmpty()); // disconnected log
        f = crookedTree(); f.put(ROOT.offset(1, 0, 0), Block.LOG);
        assertTrue(SafeBranchedTree.find(f, ROOT).isEmpty()); // second root
        f = crookedTree(); f.unloaded.add(ROOT.offset(5, 11, 5));
        assertTrue(SafeBranchedTree.find(f, ROOT).isEmpty());
        assertFalse(SafeBranchedTree.areaLoaded(f, ROOT));
        f = crookedTree(); f.protectedBlocks.add(ROOT.offset(1, 3, 0));
        assertTrue(SafeBranchedTree.find(f, ROOT).isEmpty());
        f = crookedTree(); f.put(ROOT.offset(3, 5, 0), Block.PERSISTENT_LEAF);
        assertTrue(SafeBranchedTree.find(f, ROOT).isEmpty());
    }

    @Test void crookedSavedPositionsCannotSkipBoundariesOrDuplicateLogs() {
        Fake f = crookedTree();
        var logs = SafeBranchedTree.find(f, ROOT);
        var duplicate = new java.util.ArrayList<>(logs); duplicate.set(3, duplicate.get(2));
        assertFalse(SafeBranchedTree.safePartial(f, ROOT, duplicate, duplicate.size()));
        var outOfRange = new java.util.ArrayList<>(logs); outOfRange.set(3, ROOT.offset(10, 3, 0));
        assertFalse(SafeBranchedTree.safePartial(f, ROOT, outOfRange, outOfRange.size()));
        assertFalse(SafeBranchedTree.safePartial(f, ROOT, logs, 0));
        f.put(ROOT.offset(2, 4, 0), Block.STRUCTURE);
        assertFalse(SafeBranchedTree.safePartial(f, ROOT, logs, logs.size()));
    }

    @Test void sidewaysBranchesMustConnectAndRetainSavedAxis() {
        Fake f = crookedTree();
        f.axes.put(ROOT.offset(1, 3, 0), 1);
        f.axes.put(ROOT.offset(2, 4, 0), 1);
        var logs = SafeBranchedTree.find(f, ROOT);
        assertEquals(6, logs.size());
        int[] axes = logs.stream().mapToInt(f::branchAxis).toArray();
        assertTrue(SafeBranchedTree.safePartial(f, ROOT, logs, logs.size(), axes));
        // Changing a remaining block's axis during a job must abort it.
        f.axes.put(ROOT.offset(1, 3, 0), 0);
        assertFalse(SafeBranchedTree.safePartial(f, ROOT, logs, logs.size(), axes));
        f.axes.put(ROOT.offset(1, 3, 0), 2);
        assertTrue(SafeBranchedTree.find(f, ROOT).isEmpty()); // no connected Z-axis neighbor
        assertFalse(SafeBranchedTree.safePartial(f, ROOT, logs, logs.size(), new int[1]));
    }

}
