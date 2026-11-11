package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public class ButcherJob extends AbstractJob {
    public ButcherJob(Villager villager, Colony colony) { super(villager, colony, JobType.BUTCHER); }

    @Override
    protected void doWork(ServerLevel level) {
        state = JobState.WORKING;
        if (colony.getWarehouseManager().has("minecraft:wheat", 2) || colony.getWarehouseCount("minecraft:wheat") >= 2) {
            // consume wheat from chests
            if (level != null) colony.getWarehouseManager().extract(level, villager.blockPosition(), "minecraft:wheat", 2, false);
            depositToColony(level, "minecraft:beef", 2);
            depositToColony(level, "minecraft:leather", 1);
        } else {
            requestFromColony(level, "minecraft:wheat", 4);
        }
        ticksUntilNextWork = 200;
    }
}
