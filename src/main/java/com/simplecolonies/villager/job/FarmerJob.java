package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Farmer: tends 9x9 farm around farmland, harvests mature crops, replants, deposits food.
 * Food is critical - without farmer colony starves.
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
        // Scan farm
        int harvested = 0;
        for (BlockPos pos : BlockPos.betweenClosed(workplacePos.offset(-FARM_RADIUS, 0, -FARM_RADIUS), workplacePos.offset(FARM_RADIUS, 0, FARM_RADIUS))) {
            BlockState state = level.getBlockState(pos.above());
            if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                // Harvest
                level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
                // Replant - use seeds from colony stock or dropped
                level.setBlock(pos.above(), crop.getStateForAge(0), 3);
                harvested++;
                // Deposit: 1 wheat + 1 seeds approx
                depositToColony("minecraft:wheat", 1);
                if (level.random.nextFloat() < 0.3f) depositToColony("minecraft:bread", 1); // simplified baker step
                if (harvested >= 3) break; // throttle per tick
            }
        }
        if (harvested == 0) {
            // No mature crops - maybe till/plant if has seeds
            ticksUntilNextWork = 100;
        }
    }

    private BlockPos findFarmlandNear(ServerLevel level, BlockPos from) {
        for (BlockPos p : BlockPos.betweenClosed(from.offset(-16, -4, -16), from.offset(16, 4, 16))) {
            if (level.getBlockState(p).is(Blocks.FARMLAND)) return p;
        }
        return null;
    }

    @Override
    protected int getWorkInterval() { return 40; }
}
