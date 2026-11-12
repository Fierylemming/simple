package com.simplecolonies.colony;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HousingNeedsTest {
    @Test void reservesSpaceWhileGrowthIsPossible() {
        assertTrue(HousingNeeds.needsHouse(4, 4, 60));
        assertTrue(HousingNeeds.needsHouse(4, 6, 60));
        assertFalse(HousingNeeds.needsHouse(4, 7, 60));
    }
    @Test void stillFixesOvercrowdingAtTheCapButDoesNotReserveExtraCapacity() {
        assertTrue(HousingNeeds.needsHouse(60, 56, 60));
        assertFalse(HousingNeeds.needsHouse(60, 60, 60));
        assertFalse(HousingNeeds.needsHouse(60, 64, 60));
    }
}
