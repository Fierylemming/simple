package com.simplecolonies.building;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wooden costs must be payable in whatever species the colony actually has. */
class WoodCostsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void aFamilyCountsEverySpecies() {
        var stock = Map.of("minecraft:birch_planks", 10, "minecraft:spruce_planks", 5,
                "minecraft:oak_log", 3);
        assertEquals(15, WoodCosts.count(stock, WoodCosts.PLANKS));
        assertEquals(3, WoodCosts.count(stock, WoodCosts.LOG));
        assertEquals(10, WoodCosts.count(stock, "minecraft:birch_planks"), "an exact cost stays exact");
        assertTrue(WoodCosts.speciesItems(WoodCosts.PLANKS).contains("minecraft:cherry_planks"));
    }

    @Test void resolutionUsesTheLargestSpeciesAndStaysDeterministic() {
        var stock = Map.of("minecraft:birch_planks", 20, "minecraft:oak_planks", 4);
        var cost = Map.of(WoodCosts.PLANKS, 16, "minecraft:coal", 4);
        var resolved = WoodCosts.resolve(stock, cost);
        assertEquals(Map.of("minecraft:birch_planks", 16, "minecraft:coal", 4), resolved);
        assertEquals(resolved, WoodCosts.resolve(stock, cost), "two colonies with the same stock order the same items");
        assertFalse(resolved.containsKey(WoodCosts.PLANKS), "no family token survives resolution");
    }

    @Test void resolutionSpreadsAcrossSpeciesAndKeepsExactEntries() {
        var stock = Map.of("minecraft:birch_planks", 10, "minecraft:oak_planks", 8);
        var resolved = WoodCosts.resolve(stock, Map.of(WoodCosts.PLANKS, 12));
        assertEquals(12, resolved.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(10, resolved.getOrDefault("minecraft:birch_planks", 0));
        assertEquals(2, resolved.getOrDefault("minecraft:oak_planks", 0));

        var exact = WoodCosts.resolve(stock, Map.of("minecraft:oak_log", 3));
        assertEquals(Map.of("minecraft:oak_log", 3), exact, "a cost that names one species is untouched");
    }

    @Test void timberPreferenceFollowsTheStoredWoodThenTheHomeSpecies() {
        var stock = Map.of("minecraft:birch_log", 30, "minecraft:acacia_log", 30);
        List<HouseTimber> order = WoodCosts.preferredTimbers(stock, HouseTimber.OAK);
        assertEquals(HouseTimber.ACACIA, order.getFirst(), "equal stock breaks alphabetically");
        assertEquals(HouseTimber.BIRCH, order.get(1));
        assertEquals(HouseTimber.OAK, order.get(2), "the home species beats the remaining empty ones");
        assertEquals(HouseTimber.SPRUCE, WoodCosts.preferredTimbers(Map.of(), HouseTimber.SPRUCE).getFirst());
        assertEquals(HouseTimber.SPRUCE, WoodCosts.dominantTimber(Map.of("minecraft:spruce_planks", 4), HouseTimber.OAK));
    }

    @Test void familiesReadAsWordsInMessages() {
        assertEquals("any planks", WoodCosts.describe(WoodCosts.PLANKS));
        assertEquals("birch_planks", WoodCosts.describe("minecraft:birch_planks"));
    }
}
