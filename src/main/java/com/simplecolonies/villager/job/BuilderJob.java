package com.simplecolonies.villager.job;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingState;
import com.simplecolonies.colony.Colony;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.state.BlockState;

public class BuilderJob extends AbstractJob {
    private Building currentJob;

    public BuilderJob(Villager villager, Colony colony) {
        super(villager, colony, JobType.BUILDER);
    }

    @Override
    protected void doWork(ServerLevel level) {
        if (currentJob == null || currentJob.getState() == BuildingState.COMPLETE) {
            currentJob = colony.getBuildings().stream()
                    .filter(b -> b.getState() == BuildingState.BLUEPRINT || b.getState() == BuildingState.UNDER_CONSTRUCTION)
                    .findFirst().orElse(null);
            if (currentJob == null) { state = JobState.IDLE; ticksUntilNextWork = 100; return; }
        }

        state = JobState.WORKING;
        if (!currentJob.hasResources(colony)) {
            currentJob.setState(BuildingState.NEEDS_RESOURCES);
            for (var entry : currentJob.getMissingResources(colony).entrySet()) {
                requestFromColony(level, entry.getKey(), entry.getValue());
            }
            ticksUntilNextWork = 100;
            return;
        }

        currentJob.setState(BuildingState.UNDER_CONSTRUCTION);
        int maxPerTick = com.simplecolonies.config.ColonyConfig.MAX_BUILDER_BLOCKS_PER_TICK.get();
        for (int i = 0; i < maxPerTick; i++) {
            Building.BlockPlacement next = currentJob.getNextPlacement();
            if (next == null) {
                currentJob.setState(BuildingState.COMPLETE);
                colony.broadcast(net.minecraft.network.chat.Component.literal("§aBuilder finished " + currentJob.getType().displayName + " at " + currentJob.getPos().toShortString()));
                currentJob = null;
                break;
            }
            // Try to consume from chests first
            int chestHave = colony.getWarehouseManager().count(next.itemId);
            if (chestHave > 0) colony.getWarehouseManager().extract(level, currentJob.getPos(), next.itemId, 1, false);
            else colony.depositToWarehouse(next.itemId, -1);
            BlockPos placePos = next.pos;
            BlockState toPlace = next.state;
            if (level.getBlockState(placePos).isAir() || level.getBlockState(placePos).canBeReplaced()) {
                level.setBlock(placePos, toPlace, 3);
            }
            currentJob.markPlaced(next);
        }
        ticksUntilNextWork = 10;
    }
}
