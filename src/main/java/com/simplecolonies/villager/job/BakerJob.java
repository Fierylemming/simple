package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;

/** Crafts vanilla bread from real wheat at the baker's claimed workstation. */
public class BakerJob extends AbstractJob {
    public BakerJob(Villager villager, Colony colony) { super(villager, colony, JobType.BAKER); }

    @Override protected void doWork(ServerLevel level) {
        if (villager instanceof com.simplecolonies.entity.ColonyVillager) {
            physicalRecipe(level, net.minecraft.world.item.Items.WHEAT, 3, net.minecraft.world.item.Items.BREAD, false,
                    net.minecraft.world.level.block.Blocks.SMOKER);
            return;
        }
        if (!atClaimedWorkstation(level)) return;
        if (!colony.consumeStock(level, "minecraft:wheat", 3)) {
            state = JobState.NEEDS_RESOURCE;
            ticksUntilNextWork = 100;
            return;
        }
        villager.swing(InteractionHand.MAIN_HAND);
        depositToColony(level, "minecraft:bread", 1);
        state = JobState.WORKING;
        ticksUntilNextWork = 100;
    }
}
