package com.simplecolonies.villager.job;

import com.simplecolonies.block.ModBlocks;
import com.simplecolonies.colony.Colony;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Miner: collects stone and ores for building and blacksmithing.
 * Creates a vertical shaft / strip mine under the colony that expands over time.
 * Simplified vs real branch mining: it mines a designated quarry area.
 */
public class MinerJob extends AbstractJob {
    private BlockPos quarryPos;
    private int depth = 0;
    private static final int QUARRY_RADIUS = 6;

    public MinerJob(Villager villager, Colony colony) {
        super(villager, colony, JobType.MINER);
    }

    @Override
    protected void doWork(ServerLevel level) {
        if (workplacePos == null) {
            workplacePos = colony.getCenter().offset(24, 0, 0);
            quarryPos = workplacePos.below(4);
        }
        state = JobState.WORKING;

        // Ensure pickaxe tool - request from blacksmith/warehouse
        if (!hasPickaxe()) {
            if (!requestFromColony("minecraft:iron_pickaxe", 1)) {
                state = JobState.NEEDS_TOOL;
                ticksUntilNextWork = 200;
                return;
            }
        }

        // Mine one layer at a time
        int mined = 0;
        for (BlockPos pos : BlockPos.betweenClosed(quarryPos.offset(-QUARRY_RADIUS, 0, -QUARRY_RADIUS), quarryPos.offset(QUARRY_RADIUS, 0, QUARRY_RADIUS))) {
            BlockPos target = pos.atY(quarryPos.getY() - depth);
            BlockState bs = level.getBlockState(target);
            if (bs.isAir() || bs.is(Blocks.BEDROCK)) continue;
            // Mine
            level.destroyBlock(target, false);
            // Convert to drops
            if (bs.is(Blocks.STONE)) depositToColony("minecraft:cobblestone", 1);
            else if (bs.is(Blocks.COAL_ORE) || bs.is(Blocks.DEEPSLATE_COAL_ORE)) depositToColony("minecraft:coal", 1);
            else if (bs.is(Blocks.IRON_ORE) || bs.is(Blocks.DEEPSLATE_IRON_ORE)) depositToColony("minecraft:raw_iron", 1);
            else if (bs.is(Blocks.COPPER_ORE)) depositToColony("minecraft:raw_copper", 1);
            else if (bs.is(Blocks.GOLD_ORE)) depositToColony("minecraft:raw_gold", 1);
            else depositToColony("minecraft:cobblestone", 1);

            mined++;
            if (mined >= 3) break;
        }

        if (mined == 0) {
            depth++;
            if (depth > 50) depth = 0; // reset quarry to top and widen
        }
        ticksUntilNextWork = 25;
    }

    private boolean hasPickaxe() {
        // Check villager inventory simplification - assume needs colony supply
        return colony.getWarehouseCount("minecraft:iron_pickaxe") > 0 || colony.getWarehouseCount("minecraft:stone_pickaxe") > 0;
    }
}
