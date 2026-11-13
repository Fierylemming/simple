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
        final Set<BlockPos> unloaded = new HashSet<>(), protectedBlocks = new HashSet<>();
        Block block(BlockPos p) { return blocks.getOrDefault(p, Block.AIR); }
        void put(BlockPos p, Block b) { blocks.put(p, b); }
        public boolean loaded(BlockPos p) { return !unloaded.contains(p); }
        public boolean protectedPosition(BlockPos p) { return protectedBlocks.contains(p); }
        public boolean soil(BlockPos p) { return block(p) == Block.SOIL; }
        public boolean trunk(BlockPos p) { return block(p) == Block.LOG; }
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
}
