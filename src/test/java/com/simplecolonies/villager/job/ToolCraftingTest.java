package com.simplecolonies.villager.job;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ToolCraftingTest {
    private static class Stock implements ToolCrafting.Stock {
        Map<String, Integer> items = new HashMap<>();
        String fail;
        public int count(String id) { return items.getOrDefault(id, 0); }
        public boolean consume(String id, int n) {
            if (id.equals(fail) || count(id) < n) return false;
            items.put(id, count(id) - n); return true;
        }
        public void add(String id, int n) { items.merge(id, n, Integer::sum); }
    }
    @Test void craftsStoneToolAndRetainsVanillaRecipeSurplus() {
        var s = new Stock(); s.add("minecraft:cobblestone", 3); s.add("minecraft:oak_log", 1);
        assertTrue(ToolCrafting.make(s, "minecraft:cobblestone", "minecraft:stone_pickaxe"));
        assertEquals(0, s.count("minecraft:cobblestone")); assertEquals(0, s.count("minecraft:oak_log"));
        assertEquals(2, s.count("minecraft:oak_planks")); assertEquals(2, s.count("minecraft:stick"));
        assertEquals(1, s.count("minecraft:stone_pickaxe"));
    }
    @Test void refusesMissingMaterialsWithoutSpendingAnything() {
        var s = new Stock(); s.add("minecraft:iron_ingot", 3); s.add("minecraft:stick", 1);
        assertFalse(ToolCrafting.make(s, "minecraft:iron_ingot", "minecraft:iron_axe"));
        assertEquals(3, s.count("minecraft:iron_ingot")); assertEquals(1, s.count("minecraft:stick"));
    }
    @Test void refundsIfLaterConsumptionFails() {
        var s = new Stock(); s.add("minecraft:iron_ingot", 3); s.add("minecraft:stick", 2); s.fail = "minecraft:stick";
        assertFalse(ToolCrafting.make(s, "minecraft:iron_ingot", "minecraft:iron_axe"));
        assertEquals(3, s.count("minecraft:iron_ingot")); assertEquals(2, s.count("minecraft:stick"));
        assertEquals(0, s.count("minecraft:iron_axe"));
    }
    @Test void rejectsInvalidMaterialOrProduct() {
        var s = new Stock(); s.add("minecraft:cobblestone", 3); s.add("minecraft:stick", 2);
        assertFalse(ToolCrafting.make(s, "minecraft:cobblestone", "minecraft:iron_axe"));
        assertEquals(3, s.count("minecraft:cobblestone"));
    }
}
