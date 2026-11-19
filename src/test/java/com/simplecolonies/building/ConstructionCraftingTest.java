package com.simplecolonies.building;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ConstructionCraftingTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

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
    @Test void craftsEachWoodWithoutTurningItIntoAnotherSpecies() {
        for (HouseTimber wood : HouseTimber.values()) {
            Stock s = new Stock();
            String prefix = "minecraft:" + wood.id + "_";
            s.add(prefix + "log", 3);
            assertTrue(ConstructionCrafting.take(s, prefix + "door"));
            assertEquals(1, s.count(prefix + "log"));
            assertEquals(2, s.count(prefix + "planks"));
            assertEquals(2, s.count(prefix + "door"));
            if (wood != HouseTimber.OAK) assertEquals(0, s.count("minecraft:oak_door"));
        }
        Stock s = new Stock(); s.add("minecraft:birch_log", 10);
        assertFalse(ConstructionCrafting.take(s, "minecraft:oak_door"));
        assertEquals(10, s.count("minecraft:birch_log"));
        s.add("minecraft:coal", 1);
        assertTrue(ConstructionCrafting.take(s, "minecraft:torch"));
        assertEquals(2, s.count("minecraft:birch_planks"));
    }
    @Test void consumesExistingOutputBeforeCrafting() {
        Stock s = new Stock(); s.add("minecraft:oak_slab", 1);
        assertTrue(ConstructionCrafting.take(s, "minecraft:oak_slab"));
        assertEquals(0, s.count("minecraft:oak_slab"));
    }
    @Test void buildsFurnaceOnlyFromEightCobblestone() {
        Stock s = new Stock(); s.add("minecraft:cobblestone", 7);
        assertFalse(ConstructionCrafting.take(s, "minecraft:furnace"));
        assertEquals(7, s.count("minecraft:cobblestone"));
        s.add("minecraft:cobblestone", 1);
        assertTrue(ConstructionCrafting.take(s, "minecraft:furnace"));
        assertEquals(0, s.count("minecraft:cobblestone"));
        assertEquals(0, s.count("minecraft:furnace"));
    }

    @Test void physicalInputPlanningAccountsForIntermediateAndExistingItems() {
        assertEquals(Map.of("minecraft:oak_log", 1, "minecraft:oak_planks", 2),
                ConstructionCrafting.requiredInputs(Map.of("minecraft:oak_log", 1, "minecraft:oak_planks", 2), "minecraft:oak_stairs"));
        assertEquals(Map.of("minecraft:birch_log", 1, "minecraft:coal", 1),
                ConstructionCrafting.requiredInputs(Map.of("minecraft:birch_log", 1, "minecraft:coal", 1), "minecraft:torch"));
        assertEquals(Map.of("minecraft:oak_stairs", 1),
                ConstructionCrafting.requiredInputs(Map.of("minecraft:oak_stairs", 1, "minecraft:oak_log", 9), "minecraft:oak_stairs"));
        assertNull(ConstructionCrafting.requiredInputs(Map.of("minecraft:oak_log", 1), "minecraft:oak_door"));
    }

    @Test void stonePickaxeUsesPhysicalInputsAndRetainsMatchingLeftovers() {
        var available = Map.of("minecraft:cobblestone", 3, "minecraft:birch_log", 1);
        assertEquals(available, ConstructionCrafting.requiredInputs(available, "minecraft:stone_pickaxe"));
        Stock s = new Stock(); available.forEach(s::add);
        assertTrue(ConstructionCrafting.take(s, "minecraft:stone_pickaxe"));
        assertEquals(0, s.count("minecraft:cobblestone"));
        assertEquals(0, s.count("minecraft:birch_log"));
        assertEquals(2, s.count("minecraft:birch_planks"));
        assertEquals(2, s.count("minecraft:stick"));
        assertEquals(0, s.count("minecraft:stone_pickaxe"));
        assertNull(ConstructionCrafting.requiredInputs(Map.of("minecraft:cobblestone", 2, "minecraft:stick", 2), "minecraft:stone_pickaxe"));
    }


    @Test void sticksAreCraftableFromLogsWithoutATool() {
        Stock s = new Stock(); s.add("minecraft:oak_log", 1);
        assertTrue(ConstructionCrafting.take(s, "minecraft:stick"));
        assertEquals(0, s.count("minecraft:oak_log"));
        assertEquals(2, s.count("minecraft:oak_planks"));
        assertEquals(3, s.count("minecraft:stick"));
        assertNull(ConstructionCrafting.requiredInputs(Map.of(), "minecraft:stick"));
    }

    @Test void shortfallNamesOnlyWhatIsActuallyMissing() {
        var plan = java.util.List.of(
                new Building.BlockPlacement(net.minecraft.core.BlockPos.ZERO,
                        net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(), "minecraft:oak_planks"),
                new Building.BlockPlacement(net.minecraft.core.BlockPos.ZERO.above(),
                        net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(), "minecraft:oak_planks"),
                new Building.BlockPlacement(net.minecraft.core.BlockPos.ZERO.above(2),
                        net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(), "minecraft:oak_planks"),
                new Building.BlockPlacement(net.minecraft.core.BlockPos.ZERO.above(3),
                        net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(), "minecraft:oak_planks"),
                new Building.BlockPlacement(net.minecraft.core.BlockPos.ZERO.above(4),
                        net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState(), "minecraft:oak_door"),
                new Building.BlockPlacement(net.minecraft.core.BlockPos.ZERO.above(5),
                        net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(), "minecraft:cobblestone"));
        assertTrue(ConstructionCrafting.shortfall(Map.of("minecraft:oak_log", 8, "minecraft:cobblestone", 8), plan).isEmpty(),
                "a colony with logs and cobble must not be told anything is missing");
        var missing = ConstructionCrafting.shortfall(Map.of("minecraft:oak_log", 1), plan);
        assertEquals(Map.of("minecraft:oak_door", 1, "minecraft:cobblestone", 1), missing);
    }

    @Test void shortfallTextStaysShortForChat() {
        var missing = new java.util.LinkedHashMap<String, Integer>();
        missing.put("minecraft:oak_planks", 12);
        missing.put("minecraft:oak_door", 2);
        missing.put("minecraft:cobblestone", 8);
        assertEquals("nothing", ConstructionCrafting.describe(Map.of(), 4));
        assertEquals("12x oak_planks, 2x oak_door, 8x cobblestone", ConstructionCrafting.describe(missing, 4));
        assertEquals("12x oak_planks, 2x oak_door, +1 more", ConstructionCrafting.describe(missing, 2));
    }

}
