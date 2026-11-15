package com.simplecolonies.villager;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VillagerNamesTest {
    @Test void assignsAJobWithoutRegexFailure() {
        assertEquals("Marcus [Farmer]", VillagerNames.withJob("Marcus", "Farmer"));
    }
    @Test void replacesAnExistingJobRatherThanAccumulatingSuffixes() {
        assertEquals("Marcus [Builder]", VillagerNames.withJob("Marcus [Farmer]", "Builder"));
    }
    @Test void handlesNamesContainingRegexCharacters() {
        assertEquals("A.(mose) [Farmer]", VillagerNames.withJob("A.(mose)", "Farmer"));
    }
}
