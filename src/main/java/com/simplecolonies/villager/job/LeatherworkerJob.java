package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;

/** Crafts a small reserve using the vanilla leather chestplate recipe. */
public class LeatherworkerJob extends AbstractJob {
    private static final String LEATHER = "minecraft:leather";
    private static final String CHESTPLATE = "minecraft:leather_chestplate";

    public LeatherworkerJob(Villager villager, Colony colony) { super(villager, colony, JobType.LEATHERWORKER); }

    @Override
    protected void doWork(ServerLevel level) {
        if (!atClaimedWorkstation(level)) return;
        // Keep one spare, not an unlimited drain on the herd's leather. Armor in
        // somebody's inventory does not count, so a withdrawn spare can be replaced.
        if (colony.getWarehouseCount(level, CHESTPLATE) > 0) {
            state = JobState.IDLE;
        } else if (colony.consumeStock(level, LEATHER, 8)) {
            depositToColony(level, CHESTPLATE, 1);
            villager.swing(InteractionHand.MAIN_HAND);
            state = JobState.WORKING;
        } else {
            state = JobState.NEEDS_RESOURCE;
        }
        ticksUntilNextWork = 150;
    }
}
