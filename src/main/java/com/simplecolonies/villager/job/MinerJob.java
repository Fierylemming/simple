package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Miner — mines quarry under colony, needs pickaxe (from blacksmith chest), deposits to chests.
 * Vanilla has no miner; this replaces fake work with real quarry.
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

        if (!hasPickaxe(level)) {
            if (!requestFromColony(level, "minecraft:iron_pickaxe", 1)) {
                state = JobState.NEEDS_TOOL;
                ticksUntilNextWork = 200;
                return;
            }
        }

        int mined = 0;
        for (BlockPos pos : BlockPos.betweenClosed(quarryPos.offset(-QUARRY_RADIUS, 0, -QUARRY_RADIUS), quarryPos.offset(QUARRY_RADIUS, 0, QUARRY_RADIUS))) {
            BlockPos target = pos.atY(quarryPos.getY() - depth);
            BlockState bs = level.getBlockState(target);
            if (bs.isAir() || bs.is(Blocks.BEDROCK)) continue;
            level.destroyBlock(target, false);
            if (bs.is(Blocks.STONE)) depositToColony(level, "minecraft:cobblestone", 1);
            else if (bs.is(Blocks.COAL_ORE) || bs.is(Blocks.DEEPSLATE_COAL_ORE)) depositToColony(level, "minecraft:coal", 1);
            else if (bs.is(Blocks.IRON_ORE) || bs.is(Blocks.DEEPSLATE_IRON_ORE)) depositToColony(level, "minecraft:raw_iron", 1);
            else if (bs.is(Blocks.COPPER_ORE)) depositToColony(level, "minecraft:raw_copper", 1);
            else if (bs.is(Blocks.GOLD_ORE)) depositToColony(level, "minecraft:raw_gold", 1);
            else depositToColony(level, "minecraft:cobblestone", 1);
            mined++;
            if (mined >= 3) break;
        }

        if (mined == 0) {
            depth++;
            if (depth > 50) depth = 0;
        }
        ticksUntilNextWork = 25;
    }

    private boolean hasPickaxe(ServerLevel level) {
        return colony.getWarehouseManager().has("minecraft:iron_pickaxe", 1) || colony.getWarehouseManager().has("minecraft:stone_pickaxe", 1)
                || colony.getWarehouseCount("minecraft:iron_pickaxe") > 0;
    }
}
