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
        if (!hasTemple) return reputation;
        int base = Math.max(0, reputation);
        return Math.min(TEMPLE_CAP, base + DAILY_GAIN);
    }
}
