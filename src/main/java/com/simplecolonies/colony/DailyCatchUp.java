package com.simplecolonies.colony;

/** Bounds work per colony tick without losing a day in a large time jump. */
final class DailyCatchUp {
    static int batchSize(long cursor, long target, int limit) {
        if (target <= cursor || limit <= 0) return 0;
        // Avoid overflow if world time was set to extreme values.
        long difference = target - cursor;
        return difference < 0 ? limit : (int) Math.min(difference, limit);
    }
    private DailyCatchUp() {}
}
