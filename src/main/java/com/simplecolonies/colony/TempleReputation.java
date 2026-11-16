package com.simplecolonies.colony;

/**
 * A completed temple quietly earns standing for the players who already have
 * some reputation with a colony. Growth stops well below the leadership
 * threshold, so an election still needs deeds rather than a single building.
 */
public final class TempleReputation {
    public static final int DAILY_GAIN = 1;
    public static final int TEMPLE_CAP = 60;

    private TempleReputation() {}

    public static int boost(int reputation, boolean hasTemple) {
        // A temple cannot win over a player who is still hostile to the colony.
        if (!hasTemple || reputation < 0) return reputation;
        return Math.min(TEMPLE_CAP, reputation + DAILY_GAIN);
    }
}
