package com.simplecolonies.villager.job;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelWatchTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    private static final BlockPos GOAL = new BlockPos(10, 64, 10);

    @Test void reportsProgressArrivalAndIdle() {
        TravelWatch watch = new TravelWatch();
        assertEquals(TravelWatch.Status.IDLE, watch.observe(100, 0));
        watch.begin(GOAL, 0);
        assertTrue(watch.isTracking(GOAL));
        assertEquals(TravelWatch.Status.MOVING, watch.observe(64, 0));
        assertEquals(TravelWatch.Status.MOVING, watch.observe(16, 20));
        assertEquals(TravelWatch.Status.ARRIVED, watch.observe(1, 40));
        watch.clear();
        assertNull(watch.target());
        assertEquals(TravelWatch.Status.IDLE, watch.observe(1, 60));
    }

    @Test void stallsThenGivesUpInsteadOfRetryingForever() {
        TravelWatch watch = new TravelWatch(80, 600, 3, 2.25);
        watch.begin(GOAL, 0);
        assertEquals(TravelWatch.Status.MOVING, watch.observe(100, 0));
        assertEquals(TravelWatch.Status.MOVING, watch.observe(100, 40));
        assertEquals(TravelWatch.Status.STALLED, watch.observe(100, 80));
        assertEquals(TravelWatch.Status.STALLED, watch.observe(100, 160));
        assertEquals(TravelWatch.Status.STALLED, watch.observe(100, 240));
        assertEquals(TravelWatch.Status.GAVE_UP, watch.observe(100, 320));
        // Giving up schedules a cooldown rather than allowing an immediate retry.
        assertFalse(watch.canRetry(320));
        assertTrue(watch.canRetry(400));
    }

    @Test void timesOutEvenWhileStillCreepingForward() {
        TravelWatch watch = new TravelWatch(80, 600, 3, 2.25);
        watch.begin(GOAL, 0);
        assertEquals(TravelWatch.Status.MOVING, watch.observe(500, 0));
        // Small but real progress each frame: never stalls, but the attempt is
        // bounded so a worker cannot follow an endless path forever.
        assertEquals(TravelWatch.Status.MOVING, watch.observe(400, 200));
        assertEquals(TravelWatch.Status.MOVING, watch.observe(300, 400));
        assertEquals(TravelWatch.Status.GAVE_UP, watch.observe(200, 600));
    }

    @Test void clearingOnlyMatchingGoalsKeepsAnotherAttempt() {
        TravelWatch watch = new TravelWatch();
        BlockPos other = GOAL.east(4);
        watch.begin(GOAL, 0);
        watch.clearIf(other);
        assertTrue(watch.isTracking(GOAL));
        watch.clearIf(GOAL);
        assertNull(watch.target());
    }

    @Test void giveUpBlocksRetryForTheStallWindow() {
        TravelWatch watch = new TravelWatch(80, 600, 3, 2.25);
        watch.begin(GOAL, 100);
        watch.giveUp(100);
        assertFalse(watch.canRetry(150));
        assertTrue(watch.canRetry(180));
    }
}
