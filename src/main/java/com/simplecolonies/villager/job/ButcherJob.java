package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * Butcher: breeds cows/pigs near butcher hut, slaughters mature animals, provides
 * meat (food) and leather to supply chain. Leather is requested by Leatherworker.
 */
public class ButcherJob extends AbstractJob {
    public ButcherJob(Villager villager, Colony colony) { super(villager, colony, JobType.BUTCHER); }

    @Override
    protected void doWork(ServerLevel level) {
        state = JobState.WORKING;
        // Simplified: every cycle produces 1 beef + 1 leather if colony has wheat for breeding
        if (colony.getWarehouseCount("minecraft:wheat") >= 2) {
            depositToColony("minecraft:beef", 2);
            depositToColony("minecraft:leather", 1);
            // consume wheat
        } else {
            requestFromColony("minecraft:wheat", 4);
        }
        ticksUntilNextWork = 200;
    }
}
