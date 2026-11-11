package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public class BlacksmithJob extends AbstractJob {
    public BlacksmithJob(Villager villager, Colony colony) { super(villager, colony, JobType.BLACKSMITH); }

    @Override
    protected void doWork(ServerLevel level) {
        state = JobState.WORKING;
        int iron = colony.getWarehouseCount(level, "minecraft:raw_iron");
        int coal = colony.getWarehouseCount(level, "minecraft:coal");
        if (iron >= 3 && coal >= 1 && colony.consumeStock(level, "minecraft:raw_iron", 3)
                && colony.consumeStock(level, "minecraft:coal", 1)) {
            // Smelting is a conversion; no free tools are created from the same ingots.
            depositToColony(level, "minecraft:iron_ingot", 3);
        } else {
            state = JobState.NEEDS_RESOURCE;
        }
        ticksUntilNextWork = 180;
    }
}
