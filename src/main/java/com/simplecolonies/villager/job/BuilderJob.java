package com.simplecolonies.villager.job;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingState;
import com.simplecolonies.colony.Colony;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builder: constructs buildings from blueprints.
 * Workflow:
 *  1. Find blueprint building in colony with state BLUEPRINT or NEEDS_RESOURCES
 *  2. Check colony warehouse has required resources; if not, request them (posts to supply board)
 *  3. Place blocks one by one, consuming resources
 * Mirrors MineColonies but with simpler NBT schematics stored per culture.
 */
public class BuilderJob extends AbstractJob {
    private Building currentJob;
    private int blocksPlacedThisTick = 0;

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
        // Verify resources
        if (!currentJob.hasResources(colony)) {
            currentJob.setState(BuildingState.NEEDS_RESOURCES);
            // Request missing
            for (var entry : currentJob.getMissingResources(colony).entrySet()) {
                requestFromColony(entry.getKey(), entry.getValue());
            }
            ticksUntilNextWork = 100;
            return;
        }

        currentJob.setState(BuildingState.UNDER_CONSTRUCTION);

        // Place up to N blocks per tick (config to avoid lag)
        int maxPerTick = com.simplecolonies.config.ColonyConfig.MAX_BUILDER_BLOCKS_PER_TICK.get();
        for (int i = 0; i < maxPerTick; i++) {
            Building.BlockPlacement next = currentJob.getNextPlacement();
            if (next == null) {
                currentJob.setState(BuildingState.COMPLETE);
                colony.broadcast(net.minecraft.network.chat.Component.literal("§aBuilder finished " + currentJob.getType().displayName + " at " + currentJob.getPos().toShortString()));
                currentJob = null;
                break;
            }
            // Consume resource
            // colony.tryTake(next.itemId, 1) - simplified
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
