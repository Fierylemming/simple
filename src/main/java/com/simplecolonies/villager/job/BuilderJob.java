package com.simplecolonies.villager.job;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingState;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/** Constructs implemented plans one block at a time, using real resources. */
public class BuilderJob extends AbstractJob {
    private Building currentJob;
    public BuilderJob(Villager villager, Colony colony) { super(villager, colony, JobType.BUILDER); }

    @Override protected void doWork(ServerLevel level) {
        if (currentJob == null || currentJob.getState() == BuildingState.COMPLETE) {
            currentJob = colony.getBuildings().stream()
                .filter(Building::hasSupportedPlan)
                .filter(b -> b.getState() == BuildingState.BLUEPRINT || b.getState() == BuildingState.UNDER_CONSTRUCTION || b.getState() == BuildingState.NEEDS_RESOURCES)
                .findFirst().orElse(null);
        }
        if (currentJob == null) { state = JobState.IDLE; return; }
        state = JobState.WORKING;
        for (int i = 0; i < com.simplecolonies.config.ColonyConfig.MAX_BUILDER_BLOCKS_PER_TICK.get(); i++) {
            Building.BlockPlacement next = currentJob.getNextPlacement();
            if (next == null) {
                currentJob.setState(BuildingState.COMPLETE);
                colony.broadcast(net.minecraft.network.chat.Component.literal("Builder completed " + currentJob.getType().displayName));
                ColonyManager.getInstance().markDirty(colony);
                currentJob = null;
                return;
            }
            if (!level.isLoaded(next.pos)) return;
            var existing = level.getBlockState(next.pos);
            if (existing.equals(next.state)) {
                currentJob.markPlaced(next); // already present, do not charge twice
            } else {
                // Never demolish somebody's building or a solid obstruction.
                if (!existing.isAir() && !existing.canBeReplaced()) { state = JobState.STUCK; return; }
                if (!colony.consumeStock(level, next.itemId, 1)) {
                    currentJob.setState(BuildingState.NEEDS_RESOURCES);
                    state = JobState.NEEDS_RESOURCE;
                    return;
                }
                level.setBlock(next.pos, next.state, 3);
                currentJob.markPlaced(next);
            }
            currentJob.setState(BuildingState.UNDER_CONSTRUCTION);
            ColonyManager.getInstance().markDirty(colony);
        }
    }
}
