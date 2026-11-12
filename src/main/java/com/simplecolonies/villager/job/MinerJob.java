package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Small remote quarry, restricted to buried natural stone and supported ores. */
public class MinerJob extends AbstractJob {
    private static final int QUARRY_RADIUS = 6;
    private static final int[][] DIRECTIONS = {{24,0},{17,17},{0,24},{-17,17},{-24,0},{-17,-17},{0,-24},{17,-17}};
    private int quarryIndex;
    private int depth;

    public MinerJob(Villager villager, Colony colony) { super(villager, colony, JobType.MINER); }

    @Override protected void doWork(ServerLevel level) {
        int toolTier = pickaxeTier(level);
        if (toolTier == 0) {
            state = JobState.NEEDS_TOOL;
            ticksUntilNextWork = 200;
            return;
        }
        BlockPos center = colony.getCenter();
        int[] direction = DIRECTIONS[quarryIndex];
        BlockPos quarry = center.offset(direction[0], -8 - depth, direction[1]);
        int mined = 0;
        for (BlockPos mutable : BlockPos.betweenClosed(quarry.offset(-QUARRY_RADIUS, 0, -QUARRY_RADIUS), quarry.offset(QUARRY_RADIUS, 0, QUARRY_RADIUS))) {
            BlockPos pos = mutable.immutable();
            if (pos.getY() <= level.getMinBuildHeight() || !level.isLoaded(pos) || !colony.isInside(pos)) continue;
            BlockState state = level.getBlockState(pos);
            String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            String output = MinerRules.dropFor(id);
            if (output == null || !MinerRules.allowedForTool(id, toolTier) || !safeToMine(level, pos)) continue;
            if (level.destroyBlock(pos, false)) {
                depositToColony(level, output, 1);
                if (++mined >= 3) break;
            }
        }
        if (mined == 0) {
            quarryIndex = (quarryIndex + 1) % DIRECTIONS.length;
            if (quarryIndex == 0) depth = (depth + 1) % 51;
        }
        state = mined > 0 ? JobState.WORKING : JobState.STUCK;
        ticksUntilNextWork = 25;
    }

    private int pickaxeTier(ServerLevel level) {
        // Counts include both chests and the colony's abstract stockpile.
        if (colony.getWarehouseCount(level, "minecraft:iron_pickaxe") > 0
                || colony.getWarehouseCount(level, "minecraft:diamond_pickaxe") > 0
                || colony.getWarehouseCount(level, "minecraft:netherite_pickaxe") > 0) return 2;
        return colony.getWarehouseCount(level, "minecraft:stone_pickaxe") > 0 ? 1 : 0;
    }

    private boolean safeToMine(ServerLevel level, BlockPos pos) {
        if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - pos.getY() < 8) return false;
        if (!MinerRules.farFromBuilding(pos.getX(), pos.getZ(), colony.getCenter().getX(), colony.getCenter().getZ(), 12)) return false;
        for (var building : colony.getBuildings()) {
            int margin = Math.max(12, building.getSize() / 2 + 8);
            if (!MinerRules.farFromBuilding(pos.getX(), pos.getZ(), building.getPos().getX(), building.getPos().getZ(), margin)) return false;
        }
        for (var structure : level.structureManager().getAllStructuresAt(pos).keySet()) {
            if (level.structureManager().getStructureAt(pos, structure).isValid()) return false;
        }
        // Require an intact natural surround: don't mine into a cave, player tunnel or building.
        for (BlockPos neighbor : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (!level.isLoaded(neighbor) || level.getBlockEntity(neighbor) != null) return false;
            BlockState block = level.getBlockState(neighbor);
            if (!block.getFluidState().isEmpty()) return false;
            if (!(block.is(BlockTags.BASE_STONE_OVERWORLD) || block.is(BlockTags.DIRT) || block.is(Blocks.GRAVEL)
                    || block.is(Blocks.SAND) || block.is(Blocks.RED_SAND) || block.is(Blocks.TUFF)
                    || block.is(Blocks.ANDESITE) || block.is(Blocks.DIORITE) || block.is(Blocks.GRANITE)
                    || MinerRules.dropFor(BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString()) != null)) return false;
        }
        return true;
    }
}
