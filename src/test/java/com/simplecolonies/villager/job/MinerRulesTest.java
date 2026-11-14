package com.simplecolonies.villager.job;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MinerRulesTest {
    @Test void minesOnlyExplicitNaturalStoneAndOres() {
        assertEquals("minecraft:cobblestone", MinerRules.dropFor("minecraft:stone"));
        assertEquals("minecraft:cobbled_deepslate", MinerRules.dropFor("minecraft:deepslate"));
        assertEquals("minecraft:raw_iron", MinerRules.dropFor("minecraft:deepslate_iron_ore"));
        assertNull(MinerRules.dropFor("minecraft:oak_log"));
        assertNull(MinerRules.dropFor("minecraft:chest"));
        assertNull(MinerRules.dropFor("minecraft:cobblestone"));
        assertNull(MinerRules.dropFor("minecraft:diamond_ore"));
    }
    @Test void toolAndBuildingRulesAreConservative() {
        assertFalse(MinerRules.allowedForTool("minecraft:stone", 0));
        assertTrue(MinerRules.allowedForTool("minecraft:stone", 1));
        assertTrue(MinerRules.allowedForTool("minecraft:iron_ore", 1));
        assertFalse(MinerRules.allowedForTool("minecraft:gold_ore", 1));
        assertTrue(MinerRules.allowedForTool("minecraft:gold_ore", 2));
        assertFalse(MinerRules.farFromBuilding(12, 12, 0, 0, 12));
        assertTrue(MinerRules.farFromBuilding(13, 0, 0, 0, 12));
    }
}
