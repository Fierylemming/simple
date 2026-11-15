package com.simplecolonies.colony;

public enum ColonyLevel {
    HAMLET(1, "Hamlet", 0, 8),
    VILLAGE(2, "Village", 3, 16),
    TOWN(3, "Town", 6, 24),
    CITY(4, "City", 9, 40),
    METROPOLIS(5, "Metropolis", 12, 60);

    public final int level;
    public final String displayName;
    public final int requiredBuildings;
    public final int maxCitizens;

    ColonyLevel(int level, String displayName, int requiredBuildings, int maxCitizens) {
        this.level = level;
        this.displayName = displayName;
        this.requiredBuildings = requiredBuildings;
        this.maxCitizens = maxCitizens;
    }

    public static ColonyLevel fromLevel(int lvl) {
        for (ColonyLevel cl : values()) if (cl.level == lvl) return cl;
        return HAMLET;
    }
}
