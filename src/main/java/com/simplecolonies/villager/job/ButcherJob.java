package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public class ButcherJob extends AbstractJob {
    public ButcherJob(Villager villager, Colony colony) { super(villager, colony, JobType.BUTCHER); }

    @Override
    protected void doWork(ServerLevel level) {
        if (!atClaimedWorkstation(level)) return;
        state = JobState.WORKING;
        if (colony.consumeStock(level, "minecraft:wheat", 2)) {
            depositToColony(level, "minecraft:beef", 2);
            depositToColony(level, "minecraft:leather", 1);
        } else {
            state = JobState.NEEDS_RESOURCE;
        }
        ticksUntilNextWork = 200;
    }
}
