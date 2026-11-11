package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * Blacksmith: consumes miner's ores (iron, copper) + woodcutter's coal, produces tools/weapons.
 * Supplies builder, miner, lumberjack, soldiers.
 */
public class BlacksmithJob extends AbstractJob {
    public BlacksmithJob(Villager villager, Colony colony) { super(villager, colony, JobType.BLACKSMITH); }

    @Override
    protected void doWork(ServerLevel level) {
        state = JobState.WORKING;
        // Priority: pickaxe for miner > axe for lumberjack > sword for soldiers
        int iron = colony.getWarehouseCount("minecraft:raw_iron");
        int coal = colony.getWarehouseCount("minecraft:coal");
        if (iron >= 3 && coal >= 1) {
            // Smelt to ingot then craft
            colony.depositToWarehouse("minecraft:raw_iron", -3);
            colony.depositToWarehouse("minecraft:coal", -1);
            depositToColony("minecraft:iron_ingot", 3);
            // If someone needs tool, craft it
            if (colony.getWarehouseCount("minecraft:iron_pickaxe") < 2) {
                depositToColony("minecraft:iron_pickaxe", 1);
            } else if (colony.getWarehouseCount("minecraft:iron_sword") < 4) {
                depositToColony("minecraft:iron_sword", 1);
            }
        } else {
            if (iron < 3) requestFromColony("minecraft:raw_iron", 3 - iron);
            if (coal < 1) requestFromColony("minecraft:coal", 1);
        }
        ticksUntilNextWork = 180;
    }
}
