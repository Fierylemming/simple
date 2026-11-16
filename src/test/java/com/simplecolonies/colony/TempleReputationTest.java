package com.simplecolonies.colony;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TempleReputationTest {
    @Test void aTempleGrowsStandingSlowly() {
        assertEquals(11, TempleReputation.boost(10, true));
        assertEquals(TempleReputation.TEMPLE_CAP, TempleReputation.boost(TempleReputation.TEMPLE_CAP, true));
        assertEquals(11, TempleReputation.boost(10, false), "without a temple nothing changes");
        assertEquals(0, TempleReputation.boost(-5, true));
    }

    @Test void templeGrowthStaysBelowLeadership() {
        assertEquals(60, TempleReputation.TEMPLE_CAP);
        assertEquals(75.0, com.simplecolonies.config.ColonyConfig.REPUTATION_NEEDED_TO_LEAD.getDefault().doubleValue());
    }
}
