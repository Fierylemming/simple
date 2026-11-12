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
