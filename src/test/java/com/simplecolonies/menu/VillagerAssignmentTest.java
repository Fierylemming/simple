package com.simplecolonies.menu;

import com.simplecolonies.villager.job.JobType;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VillagerAssignmentTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void everyProfessionHasExactlyOneButtonAndCommandsAreNotAssignments() {
        var jobs = EnumSet.noneOf(JobType.class);
        for (int id = 100; id <= 116; id++) {
            var job = VillagerInteractionMenu.assignmentForButton(id);
            if (job != null) assertTrue(jobs.add(job), "Duplicate button for " + job);
        }
        assertEquals(EnumSet.allOf(JobType.class), jobs);
        assertNull(VillagerInteractionMenu.assignmentForButton(VillagerInteractionMenu.BTN_TRADE));
        assertNull(VillagerInteractionMenu.assignmentForButton(VillagerInteractionMenu.BTN_COMMAND_FOLLOW));
        assertNull(VillagerInteractionMenu.assignmentForButton(VillagerInteractionMenu.BTN_LEVEL_UP_JOB));
    }
}
