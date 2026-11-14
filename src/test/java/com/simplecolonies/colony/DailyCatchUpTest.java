package com.simplecolonies.colony;

import com.simplecolonies.villager.job.JobType;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DailyCatchUpTest {
    @Test void skippedDaysAreNotCollapsedOrLostAcrossBatches() {
        long cursor = 10, target = 160;
        int[] expected = {64, 64, 22};
        for (int batch : expected) {
            assertEquals(batch, DailyCatchUp.batchSize(cursor, target, 64));
            cursor += batch;
        }
        assertEquals(target, cursor);
        assertEquals(0, DailyCatchUp.batchSize(cursor, target, 64));
        assertEquals(0, DailyCatchUp.batchSize(cursor, target - 50, 64));
        assertEquals(64, DailyCatchUp.batchSize(Long.MIN_VALUE, Long.MAX_VALUE, 64));
    }

    @Test void physicalBirthsStayDeferredAcrossCatchUpBatches() {
        assertFalse(DailyCatchUp.deferBirths(10, 11, false));
        assertTrue(DailyCatchUp.deferBirths(10, 160, false));
        assertTrue(DailyCatchUp.deferBirths(159, 160, true));
        assertTrue(DailyCatchUp.deferBirths(Long.MIN_VALUE, Long.MAX_VALUE, false));
        assertFalse(DailyCatchUp.deferBirths(160, 161, false));
    }

    @Test void detectsForwardClockSkipsWithoutMistakingNormalTimeOrRewinds() {
        assertFalse(DailyCatchUp.clockJumpedForward(23950, 24050, 500, 600));
        assertTrue(DailyCatchUp.clockJumpedForward(23950, 48050, 500, 600));
        assertTrue(DailyCatchUp.clockJumpedForward(23000, 24000, 500, 600));
        assertFalse(DailyCatchUp.clockJumpedForward(23000, 23000, 500, 600));
        assertFalse(DailyCatchUp.clockJumpedForward(24000, 1000, 500, 600));
        assertFalse(DailyCatchUp.clockJumpedForward(24000, 24100, 600, 500));
        assertTrue(DailyCatchUp.clockJumpedForward(Long.MIN_VALUE, Long.MAX_VALUE, 10, 20));
    }

    @Test void longHealthRunsAreCompressedAndProcessedInBoundedOrder() {
        var citizen = new CitizenData(UUID.randomUUID(), "Test", JobType.FARMER);
        for (int i = 0; i < 10000; i++) citizen.queueDailyHealth(true);
        citizen.queueDailyHealth(false);
        var tag = citizen.save();
        assertEquals(2, tag.getList("pendingHealthRuns", net.minecraft.nbt.Tag.TAG_COMPOUND).size());
        var restored = CitizenData.load(tag);
        for (int i = 0; i < 156; i++) {
            var batch = restored.takePendingHealthDays();
            assertEquals(64, batch.size());
            assertTrue(batch.stream().allMatch(day -> day == 1));
        }
        var remaining = CitizenData.load(restored.save()).takePendingHealthDays();
        assertEquals(17, remaining.size());
        assertEquals((byte) 0, remaining.getLast());
    }

    @Test void legacyHealthBytesMigrateWithoutReordering() {
        var citizen = new CitizenData(UUID.randomUUID(), "Old", JobType.FARMER);
        var tag = citizen.save();
        tag.remove("pendingHealthRuns");
        tag.putByteArray("pendingHealthDays", new byte[] {1, 1, 0, 1});
        var restored = CitizenData.load(tag);
        assertEquals(List.of((byte) 1, (byte) 1, (byte) 0, (byte) 1), restored.takePendingHealthDays());
    }

    @Test void unloadedCitizenHealthDaysPersistInOrder() {
        CitizenData citizen = new CitizenData(UUID.randomUUID(), "Test", JobType.FARMER);
        citizen.queueDailyHealth(true);
        citizen.queueDailyHealth(false);
        citizen.queueDailyHealth(true);
        CitizenData restored = CitizenData.load(citizen.save());
        assertEquals(List.of((byte) 1, (byte) 0, (byte) 1), restored.takePendingHealthDays());
        assertTrue(restored.takePendingHealthDays().isEmpty());
    }
}
