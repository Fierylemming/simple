package com.simplecolonies.building;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ConstructionCraftingTest {
    static final class Stock implements ConstructionCrafting.Stock {
        final Map<String, Integer> items = new HashMap<>();
        public int count(String id) { return items.getOrDefault(id, 0); }
        public boolean consume(String id, int n) { if (count(id) < n) return false; items.put(id, count(id) - n); return true; }
        public void add(String id, int n) { items.merge(id, n, Integer::sum); }
    }
    @Test void craftsStairsWithCorrectVanillaYieldAndKeepsLeftovers() {
        Stock s = new Stock(); s.add("minecraft:oak_log", 2);
        assertTrue(ConstructionCrafting.take(s, "minecraft:oak_stairs"));
        assertEquals(0, s.count("minecraft:oak_log"));
        assertEquals(2, s.count("minecraft:oak_planks"));
        assertEquals(3, s.count("minecraft:oak_stairs"));
    }
    @Test void noMaterialsAreSpentIfRecipeCannotBeCompleted() {
        Stock s = new Stock(); s.add("minecraft:oak_log", 1);
        assertFalse(ConstructionCrafting.take(s, "minecraft:oak_door"));
        assertEquals(1, s.count("minecraft:oak_log"));
        assertFalse(ConstructionCrafting.take(s, "minecraft:diamond_block"));
    }
    @Test void craftsTorchesAndSticksWithoutCreatingFreeFuel() {
        Stock s = new Stock(); s.add("minecraft:oak_log", 1);
        assertFalse(ConstructionCrafting.take(s, "minecraft:torch"));
        assertEquals(1, s.count("minecraft:oak_log"));
        s.add("minecraft:coal", 1);
        assertTrue(ConstructionCrafting.take(s, "minecraft:torch"));
        assertEquals(0, s.count("minecraft:coal"));
        assertEquals(3, s.count("minecraft:stick"));
        assertEquals(3, s.count("minecraft:torch"));
        assertEquals(2, s.count("minecraft:oak_planks"));
    }
    @Test void consumesExistingOutputBeforeCrafting() {
        Stock s = new Stock(); s.add("minecraft:oak_slab", 1);
        assertTrue(ConstructionCrafting.take(s, "minecraft:oak_slab"));
        assertEquals(0, s.count("minecraft:oak_slab"));
    }
}
