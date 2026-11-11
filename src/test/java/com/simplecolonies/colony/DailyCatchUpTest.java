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
