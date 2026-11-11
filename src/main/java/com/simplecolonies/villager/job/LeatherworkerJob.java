package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * Leatherworker <-> Butcher supply chain example user requested:
 * "village with a leather worker may ask the butcher for leather from their cows which then allows them to make armour"
 * Leatherworker checks warehouse for leather; if not, requests from colony board which butcher fulfills.
 */
public class LeatherworkerJob extends AbstractJob {
    public LeatherworkerJob(Villager villager, Colony colony) { super(villager, colony, JobType.LEATHERWORKER); }

    @Override
    protected void doWork(ServerLevel level) {
        state = JobState.WORKING;
        int leather = colony.getWarehouseCount("minecraft:leather");
        if (leather >= 4) {
            // Make one leather chestplate
            depositToColony("minecraft:leather_chestplate", 1);
            // consume 4 leather - handled by colony.tryTake in real impl
            colony.depositToWarehouse("minecraft:leather", -4);
        } else {
            requestFromColony("minecraft:leather", 4 - leather);
        }
        ticksUntilNextWork = 150;
    }
}
