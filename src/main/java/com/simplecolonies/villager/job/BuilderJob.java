package com.simplecolonies.villager.job;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingState;
import com.simplecolonies.building.ConstructionCrafting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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
                .filter(b -> b.getState() == BuildingState.BLUEPRINT || b.getState() == BuildingState.UNDER_CONSTRUCTION || b.getState() == BuildingState.NEEDS_RESOURCES || b.getState() == BuildingState.BLOCKED)
                .findFirst().orElse(null);
        }
        if (currentJob == null) { state = JobState.IDLE; return; }
        state = JobState.WORKING;
        ConstructionCrafting.Stock stock = new ConstructionCrafting.Stock() {
            public int count(String id) { return colony.getWarehouseCount(level, id); }
            public boolean consume(String id, int amount) { return colony.consumeStock(level, id, amount); }
            public void add(String id, int amount) { colony.depositToWarehouse(id, amount); }
        };
        for (int i = 0; i < com.simplecolonies.config.ColonyConfig.MAX_BUILDER_BLOCKS_PER_TICK.get(); i++) {
            Building.BlockPlacement next = currentJob.getNextPlacement();
            if (next == null) {
                currentJob.setState(BuildingState.COMPLETE);
                colony.broadcast(net.minecraft.network.chat.Component.literal("Builder completed " + currentJob.getType().displayName));
                ColonyManager.getInstance().markDirty(colony);
                currentJob = null;
                return;
            }
            if (!level.isLoaded(next.pos)) { state = JobState.STUCK; return; }
            if (villager.distanceToSqr(next.pos.getX() + 0.5, next.pos.getY() + 0.5, next.pos.getZ() + 0.5) > 25.0) {
                BlockPos groundTarget = next.pos.atY(currentJob.getPos().getY());
                var path = villager.getNavigation().createPath(groundTarget, 3);
                if (path == null) { state = JobState.STUCK; ticksUntilNextWork = 40; return; }
                villager.getNavigation().moveTo(path, 0.7);
                state = JobState.GOING_TO_WORK;
                ticksUntilNextWork = 10;
                return;
            }
            boolean door = next.state.getBlock() instanceof DoorBlock && next.state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
            if (door) {
                BlockPos upper = next.pos.above();
                if (!level.isLoaded(upper)) { state = JobState.STUCK; return; }
                var above = level.getBlockState(upper);
                var expected = next.state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
                if (!above.equals(expected) && !above.isAir() && !above.canBeReplaced()) {
                    currentJob.setState(BuildingState.BLOCKED); state = JobState.STUCK;
                    ColonyManager.getInstance().markDirty(colony); return;
                }
            }
            var existing = level.getBlockState(next.pos);
            if (existing.equals(next.state)) {
                if (door && !level.getBlockState(next.pos.above()).equals(next.state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER))) {
                    level.setBlock(next.pos.above(), next.state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
                }
                currentJob.markPlaced(next); // already present, do not charge twice
            } else {
                // Never demolish somebody's building or a solid obstruction.
                if (!existing.isAir() && !existing.canBeReplaced()) {
                    currentJob.setState(BuildingState.BLOCKED);
                    ColonyManager.getInstance().markDirty(colony);
                    state = JobState.STUCK;
                    return;
                }
                if (!ConstructionCrafting.take(stock, next.itemId)) {
                    currentJob.setState(BuildingState.NEEDS_RESOURCES);
                    ColonyManager.getInstance().markDirty(colony);
                    state = JobState.NEEDS_RESOURCE;
                    return;
                }
                if (door) {
                    level.setBlock(next.pos, next.state, 2);
                    level.setBlock(next.pos.above(), next.state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
                } else level.setBlock(next.pos, next.state, 3);
                currentJob.markPlaced(next);
            }
            currentJob.setState(BuildingState.UNDER_CONSTRUCTION);
            ColonyManager.getInstance().markDirty(colony);
        }
    }
}
