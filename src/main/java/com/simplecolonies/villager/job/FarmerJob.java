package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Farmer — now deposits to nearest chest in village radius, not abstract void.
 * If no chest found near farm, deposits to abstract warehouse and courier will consolidate later.
 * Real vanilla farmer AI just plants pathfinds; this one actually harvests and feeds colony.
 */
public class FarmerJob extends AbstractJob {
    private static final int FARM_RADIUS = 4;

    public FarmerJob(Villager villager, Colony colony) {
        super(villager, colony, JobType.FARMER);
    }

    @Override
    protected void doWork(ServerLevel level) {
        if (workplacePos == null) {
            workplacePos = findFarmlandNear(level, villager.blockPosition());
            if (workplacePos == null) { state = JobState.STUCK; return; }
        }
        state = JobState.WORKING;
        int harvested = 0;
        for (BlockPos pos : BlockPos.betweenClosed(workplacePos.offset(-FARM_RADIUS, 0, -FARM_RADIUS), workplacePos.offset(FARM_RADIUS, 0, FARM_RADIUS))) {
            BlockState state = level.getBlockState(pos.above());
            if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(pos.above(), crop.getStateForAge(0), 3);
                harvested++;
                depositToColony(level, "minecraft:wheat", 1);
                if (level.random.nextFloat() < 0.3f) depositToColony(level, "minecraft:bread", 1);
                if (harvested >= 3) break;
            }
        }
        if (harvested == 0) {
            // No crops ready — forage surface cobble/coal so Stone tier is reachable without Miner (fixes chicken-egg)
            ForagerJob.tryForageSurfaceCobble(villager, colony, level);
            ticksUntilNextWork = 100;
        }
    }

    private BlockPos findFarmlandNear(ServerLevel level, BlockPos from) {
        // Prefer building-marker farm area if player designated one
        for (BlockPos p : BlockPos.betweenClosed(from.offset(-16, -4, -16), from.offset(16, 4, 16))) {
            if (level.getBlockState(p).is(Blocks.FARMLAND)) return p;
        }
        return null;
    }

    @Override
    protected int getWorkInterval() { return 40; }
}
