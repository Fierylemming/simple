package com.simplecolonies.colony;

/** Bounds work per colony tick without losing a day in a large time jump. */
final class DailyCatchUp {
    static int batchSize(long cursor, long target, int limit) {
        if (target <= cursor || limit <= 0) return 0;
        // Avoid overflow if world time was set to extreme values.
        long difference = target - cursor;
        return difference < 0 ? limit : (int) Math.min(difference, limit);
    }
    /** Keep physical births disabled until a multi-day catch-up finishes, even
     * when that catch-up crosses ticks or a server restart. */
    static boolean deferBirths(long cursor, long target, boolean alreadyDeferred) {
        return alreadyDeferred || batchSize(cursor, target, 2) > 1;
    }
    /** Detect a forward clock jump even if only one day boundary was crossed.
     * A normal day clock advances no faster than elapsed game ticks. */
    static boolean clockJumpedForward(long oldDayTime, long newDayTime, long oldGameTime, long newGameTime) {
        if (newDayTime <= oldDayTime || newGameTime < oldGameTime) return false;
        try {
            return Math.subtractExact(newDayTime, oldDayTime) > Math.subtractExact(newGameTime, oldGameTime);
        } catch (ArithmeticException overflow) {
            // A wildly reset game clock is not evidence of a time skip; an
            // overflowing positive day-time jump certainly is.
            return newGameTime - oldGameTime >= 0 && newDayTime - oldDayTime < 0;
        }
    }
    private DailyCatchUp() {}
}
