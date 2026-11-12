package com.simplecolonies.villager.job;

import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Harvest only recognizable, unbuilt oak trees; never use nearby leaves alone as proof. */
public class WoodcutterJob extends AbstractJob {
    private BlockPos treeBase;
    private java.util.List<BlockPos> tree = java.util.List.of();
    private int remaining;

    public WoodcutterJob(Villager villager, Colony colony) {
        super(villager, colony, JobType.LUMBERJACK);
    }

    @Override
    protected void doWork(ServerLevel level) {
        var view = new TreeView(level);
        if (treeBase == null) {
            BlockPos center = villager.blockPosition();
            for (BlockPos mutable : BlockPos.betweenClosed(center.offset(-16, -4, -16), center.offset(16, 8, 16))) {
                BlockPos base = mutable.immutable();
                if (!view.loaded(base) || !view.trunk(base) || !view.loaded(base.below()) || !view.soil(base.below())) continue;
                var found = SafeOakTree.trunk(view, base);
                // The worker must be able to reach each block from ground level, without scaffolding.
                if (found.isEmpty() || found.size() > 4) continue;
                treeBase = base;
                tree = found;
                remaining = tree.size();
                break;
            }
        }
        if (treeBase == null) { state = JobState.IDLE; ticksUntilNextWork = 100; return; }
        if (!SafeOakTree.safePartialTrunk(view, treeBase, tree, remaining)) {
            treeBase = null; state = JobState.STUCK; ticksUntilNextWork = 60; return;
        }
        BlockPos log = tree.get(remaining - 1);
        if (villager.distanceToSqr(log.getX() + 0.5, log.getY() + 0.5, log.getZ() + 0.5) > 20.25) {
            var path = villager.getNavigation().createPath(treeBase, 1);
            if (path == null || !path.canReach()) { treeBase = null; state = JobState.STUCK; ticksUntilNextWork = 60; return; }
            villager.getNavigation().moveTo(path, 0.7);
            state = JobState.GOING_TO_WORK; ticksUntilNextWork = 10; return;
        }
        var block = level.getBlockState(log);
        var drops = net.minecraft.world.level.block.Block.getDrops(block, level, log, null, villager, villager.getMainHandItem());
        if (!level.destroyBlock(log, false)) { treeBase = null; state = JobState.STUCK; return; }
        villager.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        for (var drop : drops) if (!drop.isEmpty()) depositToColony(level,
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(drop.getItem()).toString(), drop.getCount());
        remaining--;
        if (remaining == 0) {
            // No free saplings: replant only from real colony stock, at the tree's original base.
            if (level.getBlockState(treeBase).isAir() && view.soil(treeBase.below())
                    && colony.consumeStock(level, "minecraft:oak_sapling", 1))
                level.setBlock(treeBase, Blocks.OAK_SAPLING.defaultBlockState(), 3);
            treeBase = null;
        }
        state = JobState.WORKING;
        ticksUntilNextWork = 30;
    }

    private final class TreeView implements SafeOakTree.View {
        private final ServerLevel level;
        TreeView(ServerLevel level) { this.level = level; }
        public boolean loaded(BlockPos pos) { return level.isLoaded(pos); }
        public boolean soil(BlockPos pos) { return level.getBlockState(pos).is(BlockTags.DIRT); }
        public boolean trunk(BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            return state.is(Blocks.OAK_LOG) && state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y;
        }
        public boolean naturalLeaf(BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            return state.is(Blocks.OAK_LEAVES) && !state.getValue(LeavesBlock.PERSISTENT);
        }
        public boolean clearOrNatural(BlockPos pos, boolean belowBase) {
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || level.getBlockEntity(pos) != null) return false;
            if (state.is(BlockTags.LEAVES)) return state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
            if (state.is(BlockTags.LOGS)) return false;
            return state.isAir() || state.canBeReplaced() || state.is(BlockTags.DIRT)
                    || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)
                    || (belowBase && state.is(BlockTags.BASE_STONE_OVERWORLD));
        }
        public boolean protectedPosition(BlockPos pos) {
            if (!colony.isInside(pos)) return true;
            // Use a generous horizontal buffer, including incomplete buildings and the larger starter farm.
            if (near(pos, colony.getCenter(), 9)) return true;
            for (var building : colony.getBuildings()) {
                int margin = Math.max(building.getType() == BuildingType.FARM ? 10 : 8, building.getSize() / 2 + 5);
                if (near(pos, building.getPos(), margin)) return true;
            }
            // Generated structures may not be registered as colony buildings.
            for (var structure : level.structureManager().getAllStructuresAt(pos).keySet()) {
                if (level.structureManager().getStructureAt(pos, structure).isValid()) return true;
            }
            return false;
        }
        private boolean near(BlockPos a, BlockPos b, int radius) {
            return Math.abs((long) a.getX() - b.getX()) <= radius && Math.abs((long) a.getZ() - b.getZ()) <= radius;
        }
    }
}
