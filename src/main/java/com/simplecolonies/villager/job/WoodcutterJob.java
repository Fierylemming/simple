package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Woodcutter (Lumberjack): plants trees nearby and harvests wood.
 * Plants saplings in a managed zone to be self-renewing.
 */
public class WoodcutterJob extends AbstractJob {
    private BlockPos managedForestCenter;
    private int plantCooldown = 0;

    public WoodcutterJob(Villager villager, Colony colony) {
        super(villager, colony, JobType.LUMBERJACK);
    }

    @Override
    protected void doWork(ServerLevel level) {
        if (workplacePos == null) workplacePos = colony.getCenter();
        if (managedForestCenter == null) managedForestCenter = workplacePos.offset(16, 0, 16);
        state = JobState.WORKING;

        // 1. Try harvest a log nearby (max 3 per cycle to not grief)
        int harvested = 0;
        for (BlockPos pos : BlockPos.betweenClosed(villager.blockPosition().offset(-10, -4, -10), villager.blockPosition().offset(10, 8, 10))) {
            BlockState bs = level.getBlockState(pos);
            if (bs.is(BlockTags.LOGS)) {
                level.destroyBlock(pos, false);
                // Chop leaves too simplified
                depositToColony("minecraft:oak_log", 1);
                harvested++;
                if (harvested >= 2) break;
            }
        }

        // 2. Replant phase - maintain 10 trees in managed area
        if (plantCooldown-- <= 0) {
            plantCooldown = 200;
            int treeCount = countLogsNearby(level, managedForestCenter, 20);
            if (treeCount < 10) {
                BlockPos plantPos = findPlantSpot(level, managedForestCenter);
                if (plantPos != null) {
                    // Take sapling from colony stock or use oak sapling
                    level.setBlock(plantPos, Blocks.OAK_SAPLING.defaultBlockState(), 3);
                }
            }
        }

        ticksUntilNextWork = harvested > 0 ? 30 : 60;
    }

    private int countLogsNearby(ServerLevel level, BlockPos center, int radius) {
        int c = 0;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -5, -radius), center.offset(radius, 10, radius))) {
            if (level.getBlockState(p).is(BlockTags.LOGS)) c++;
        }
        return c;
    }

    private BlockPos findPlantSpot(ServerLevel level, BlockPos center) {
        for (int i = 0; i < 10; i++) {
            BlockPos p = center.offset(level.random.nextInt(20)-10, 0, level.random.nextInt(20)-10);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isSolid()) return p;
        }
        return null;
    }
}
