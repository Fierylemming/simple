package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public class LeatherworkerJob extends AbstractJob {
    public LeatherworkerJob(Villager villager, Colony colony) { super(villager, colony, JobType.LEATHERWORKER); }

    @Override
    protected void doWork(ServerLevel level) {
        if (!atClaimedWorkstation(level)) return;
        state = JobState.WORKING;
        if (colony.consumeStock(level, "minecraft:leather", 4)) {
            depositToColony(level, "minecraft:leather_chestplate", 1);
        } else {
            state = JobState.NEEDS_RESOURCE;
        }
        ticksUntilNextWork = 150;
    }
}
