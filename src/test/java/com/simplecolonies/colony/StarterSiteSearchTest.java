package com.simplecolonies.colony;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StarterSiteSearchTest {
    @Test void searchesAWholeGridFromCenterOutwards() {
        var offsets = StarterSiteSearch.offsets(12, 4);
        assertEquals(new StarterSiteSearch.Offset(0, 0), offsets.getFirst());
        assertTrue(offsets.contains(new StarterSiteSearch.Offset(8, 4)));
        assertTrue(offsets.contains(new StarterSiteSearch.Offset(-8, -4)));
        assertFalse(offsets.contains(new StarterSiteSearch.Offset(12, 4)));
        long previous = -1;
        for (var o : offsets) {
            long distance = (long)o.x() * o.x() + (long)o.z() * o.z();
            assertTrue(distance >= previous);
            previous = distance;
        }
    }

    @Test void protectsWaterContainersAndStructuresWhileAllowingTerrain() {
        assertFalse(StarterSiteSearch.safeBlock(0, true, false, false, true));
        assertFalse(StarterSiteSearch.safeBlock(-2, false, true, false, false));
        assertFalse(StarterSiteSearch.safeBlock(0, false, false, false, false)); // timber or a building
        assertFalse(StarterSiteSearch.safeBlock(-1, false, false, false, false)); // foundation
        assertTrue(StarterSiteSearch.safeBlock(-2, false, false, false, false)); // untouched stone
        assertTrue(StarterSiteSearch.safeBlock(2, false, false, false, true)); // natural foliage
        assertTrue(StarterSiteSearch.safeBlock(0, false, false, true, false)); // levelled soil
    }

    @Test void footprintsMustBeAtLeastFifteenBlocksApartHorizontally() {
        assertFalse(StarterSiteSearch.separated(0, 0, 14, 0, 15));
        assertTrue(StarterSiteSearch.separated(0, 0, 15, 0, 15));
        assertTrue(StarterSiteSearch.separated(0, 0, 9, 12, 15));
    }
}
