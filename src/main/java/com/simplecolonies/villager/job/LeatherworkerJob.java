package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public class LeatherworkerJob extends AbstractJob {
    public LeatherworkerJob(Villager villager, Colony colony) { super(villager, colony, JobType.LEATHERWORKER); }

    @Override
    protected void doWork(ServerLevel level) {
        state = JobState.WORKING;
        int leather = colony.getWarehouseManager().has("minecraft:leather", 4) ? 4 : colony.getWarehouseCount("minecraft:leather");
        // Check chests first
        int chestLeather = level != null ? colony.getWarehouseManager().count("minecraft:leather") : 0;
        int total = Math.max(chestLeather, colony.getWarehouseCount("minecraft:leather"));
        if (total >= 4) {
            if (level != null) colony.getWarehouseManager().extract(level, villager.blockPosition(), "minecraft:leather", 4, false);
            else colony.depositToWarehouse("minecraft:leather", -4);
            depositToColony(level, "minecraft:leather_chestplate", 1);
        } else {
            requestFromColony(level, "minecraft:leather", 4 - total);
        }
        ticksUntilNextWork = 150;
    }
}
