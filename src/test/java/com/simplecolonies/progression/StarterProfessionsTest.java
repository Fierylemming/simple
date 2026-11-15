package com.simplecolonies.progression;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StarterProfessionsTest {
    @Test void starterWorkersAreUnlockedWithoutCostOrPrerequisites() {
        assertTrue(ColonyUpgrade.defaultsUnlocked().contains(ColonyUpgrade.UNLOCK_BUILDER));
        assertTrue(ColonyUpgrade.defaultsUnlocked().contains(ColonyUpgrade.UNLOCK_MINER));
        assertTrue(ColonyUpgrade.UNLOCK_MINER.cost.isEmpty());
        assertTrue(ColonyUpgrade.UNLOCK_MINER.prerequisites.isEmpty());
        assertTrue(ColonyUpgrade.defaultsUnlocked().contains(ColonyUpgrade.PROF_FARMER));
        assertTrue(ColonyUpgrade.defaultsUnlocked().contains(ColonyUpgrade.PROF_LUMBERJACK));
        assertTrue(ColonyUpgrade.UNLOCK_BUILDER.cost.isEmpty());
        assertTrue(ColonyUpgrade.UNLOCK_BUILDER.prerequisites.isEmpty());
    }
}
