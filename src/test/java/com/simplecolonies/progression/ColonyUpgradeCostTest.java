package com.simplecolonies.progression;

import com.simplecolonies.building.WoodCosts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** No research may demand one hardcoded wood species, or a birch forest is stuck. */
class ColonyUpgradeCostTest {
    @Test void noUpgradeDemandsASpecificWoodSpecies() {
        for (ColonyUpgrade upgrade : ColonyUpgrade.values()) {
            for (String item : upgrade.cost.keySet()) {
                assertFalse(item.startsWith("minecraft:oak_"),
                        upgrade + " still names oak: " + item);
                assertTrue(WoodCosts.isFamily(item) || !item.contains("_planks") && !item.endsWith("_log")
                                && !item.endsWith("_fence") && !item.endsWith("_boat"),
                        upgrade + " uses a raw wood id instead of a family: " + item);
            }
        }
    }

    @Test void theCheapUpgradesStillAskForWoodFamilies() {
        assertTrue(ColonyUpgrade.TOOL_STONE.cost.containsKey(WoodCosts.PLANKS));
        assertTrue(ColonyUpgrade.UNLOCK_BUTCHER.cost.containsKey(WoodCosts.FENCE));
        assertTrue(ColonyUpgrade.WAREHOUSE_CAPACITY.cost.get(WoodCosts.PLANKS) >= 32);
    }
}
