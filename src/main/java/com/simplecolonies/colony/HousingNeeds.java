package com.simplecolonies.colony;

/** Expansion adds a little room for future births, but must also fix present overcrowding at the population cap. */
final class HousingNeeds {
    static boolean needsHouse(int citizens, int capacity, int maxCitizens) {
        return capacity < citizens || (citizens < maxCitizens && capacity <= citizens + 2);
    }
    private HousingNeeds() {}
}
