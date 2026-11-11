package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public class BlacksmithJob extends AbstractJob {
    public BlacksmithJob(Villager villager, Colony colony) { super(villager, colony, JobType.BLACKSMITH); }

    @Override
    protected void doWork(ServerLevel level) {
        state = JobState.WORKING;
        int iron = level != null ? colony.getWarehouseManager().count("minecraft:raw_iron") : colony.getWarehouseCount("minecraft:raw_iron");
        int coal = level != null ? colony.getWarehouseManager().count("minecraft:coal") : colony.getWarehouseCount("minecraft:coal");
        if (iron >= 3 && coal >= 1) {
            if (level != null) {
                colony.getWarehouseManager().extract(level, villager.blockPosition(), "minecraft:raw_iron", 3, false);
                colony.getWarehouseManager().extract(level, villager.blockPosition(), "minecraft:coal", 1, false);
            } else {
                colony.depositToWarehouse("minecraft:raw_iron", -3);
                colony.depositToWarehouse("minecraft:coal", -1);
            }
            depositToColony(level, "minecraft:iron_ingot", 3);
            int picks = level != null ? colony.getWarehouseManager().count("minecraft:iron_pickaxe") : colony.getWarehouseCount("minecraft:iron_pickaxe");
            int swords = level != null ? colony.getWarehouseManager().count("minecraft:iron_sword") : colony.getWarehouseCount("minecraft:iron_sword");
            if (picks < 2) depositToColony(level, "minecraft:iron_pickaxe", 1);
            else if (swords < 4) depositToColony(level, "minecraft:iron_sword", 1);
        } else {
            if (iron < 3) requestFromColony(level, "minecraft:raw_iron", 3 - iron);
            if (coal < 1) requestFromColony(level, "minecraft:coal", 1);
        }
        ticksUntilNextWork = 180;
    }
}
