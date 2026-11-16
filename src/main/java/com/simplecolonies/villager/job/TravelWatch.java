package com.simplecolonies.villager.job;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Small, deterministic watch for a single walking attempt.
 *
 * <p>Colony workers previously requested a path and then gave up (or retried
 * forever) with no memory of the attempt. This watch turns an observed position
 * stream into a clear status: has the worker made progress, has it stopped
 * moving, is the attempt simply taking too long, or has it arrived? It does not
 * touch the world; callers reissue the path when {@link Status#STALLED} is
 * reported.</p>
 */
public final class TravelWatch {
    public enum Status {
        /** No attempt is in progress. */
        IDLE,
        /** Distance is decreasing; keep walking. */
        MOVING,
        /** The goal distance has been reached. */
        ARRIVED,
        /** No meaningful progress within the stall window; reissue the path. */
        STALLED,
        /** Too many stalls or the attempt timed out; choose another goal. */
        GAVE_UP
    }

    public static final int DEFAULT_STALL_TICKS = 80;
    public static final int DEFAULT_TIMEOUT_TICKS = 600;
    public static final int DEFAULT_MAX_STALLS = 3;
    public static final double DEFAULT_ARRIVAL_DISTANCE_SQR = 2.25;

    private final int stallTicks;
    private final int timeoutTicks;
    private final int maxStalls;
    private final double arrivalDistanceSqr;

    private @Nullable BlockPos target;
    private long deadline;
    private long lastProgressTick;
    private long retryAt;
    private double bestDistanceSqr = Double.MAX_VALUE;
    private int stalls;
    private int attempts;

    public TravelWatch() {
        this(DEFAULT_STALL_TICKS, DEFAULT_TIMEOUT_TICKS, DEFAULT_MAX_STALLS, DEFAULT_ARRIVAL_DISTANCE_SQR);
    }

    public TravelWatch(int stallTicks, int timeoutTicks, int maxStalls, double arrivalDistanceSqr) {
        this.stallTicks = Math.max(10, stallTicks);
        this.timeoutTicks = Math.max(this.stallTicks, timeoutTicks);
        this.maxStalls = Math.max(1, maxStalls);
        this.arrivalDistanceSqr = Math.max(0.0, arrivalDistanceSqr);
    }

    public @Nullable BlockPos target() {
        return target;
    }

    public boolean isTracking(@Nullable BlockPos pos) {
        return target != null && target.equals(pos);
    }

    public int attempts() {
        return attempts;
    }

    /** Start (or restart) watching a goal. */
    public void begin(BlockPos goal, long gameTime) {
        this.target = goal.immutable();
        this.deadline = gameTime + timeoutTicks;
        this.lastProgressTick = gameTime;
        this.bestDistanceSqr = Double.MAX_VALUE;
        this.stalls = 0;
        this.attempts = 1;
    }

    /**
     * Feed the current squared distance to the goal.
     *
     * @return the current status; {@code ARRIVED} and {@code GAVE_UP} do not clear
     *         the attempt so the caller can decide when to stop reporting it
     */
    public Status observe(double distanceSqr, long gameTime) {
        if (target == null) return Status.IDLE;
        if (distanceSqr <= arrivalDistanceSqr) {
            lastProgressTick = gameTime;
            return Status.ARRIVED;
        }
        if (gameTime >= deadline || stalls >= maxStalls) {
            retryAt = gameTime + stallTicks;
            return Status.GAVE_UP;
        }
        if (distanceSqr < bestDistanceSqr - 0.05) {
            bestDistanceSqr = distanceSqr;
            lastProgressTick = gameTime;
            return Status.MOVING;
        }
        if (gameTime - lastProgressTick >= stallTicks) {
            stalls++;
            lastProgressTick = gameTime;
            bestDistanceSqr = distanceSqr;
            attempts++;
            return Status.STALLED;
        }
        return Status.MOVING;
    }

    /** Stop watching without suggesting a retry (arrived, goal removed, ...). */
    public void clear() {
        target = null;
        stalls = 0;
        bestDistanceSqr = Double.MAX_VALUE;
    }

    /** Abandon this goal; {@link #canRetry(long)} gates the next attempt. */
    public void giveUp(long gameTime) {
        target = null;
        retryAt = gameTime + stallTicks;
    }

    public boolean canRetry(long gameTime) {
        return gameTime >= retryAt;
    }

    public long retryAt() {
        return retryAt;
    }

    /** Reset only when the previous attempt finished; used by shared goals. */
    public void clearIf(@Nullable BlockPos goal) {
        if (isTracking(goal)) clear();
    }
}
