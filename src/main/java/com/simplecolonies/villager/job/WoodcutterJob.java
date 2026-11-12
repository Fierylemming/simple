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
import net.minecraft.world.level.levelgen.Heightmap;

/** Harvest only recognizable, unbuilt oak trees; never use nearby leaves alone as proof. */
public class WoodcutterJob extends AbstractJob {
    private int plantCooldown;

    public WoodcutterJob(Villager villager, Colony colony) {
        super(villager, colony, JobType.LUMBERJACK);
    }

    @Override
    protected void doWork(ServerLevel level) {
        var view = new TreeView(level);
        boolean harvested = false;
        BlockPos center = villager.blockPosition();
        for (BlockPos mutable : BlockPos.betweenClosed(center.offset(-16, -4, -16), center.offset(16, 8, 16))) {
            BlockPos base = mutable.immutable();
            if (!view.loaded(base) || !view.trunk(base) || !view.loaded(base.below()) || !view.soil(base.below())) continue;
            var tree = SafeOakTree.trunk(view, base);
            if (tree.isEmpty()) continue;
            // Recheck the full tree immediately before changing the world.
            if (!tree.equals(SafeOakTree.trunk(view, base))) continue;
            int removed = 0;
            for (BlockPos log : tree) {
                if (level.destroyBlock(log, false)) removed++;
            }
            if (removed > 0) {
                depositToColony(level, "minecraft:oak_log", removed);
                harvested = true;
                // Only replant if all trunk blocks were removed and the base is still free.
                if (removed == tree.size() && level.getBlockState(base).isAir() && view.soil(base.below())) {
                    level.setBlock(base, Blocks.OAK_SAPLING.defaultBlockState(), 3);
                }
            }
            break;
        }

        if (--plantCooldown <= 0) {
            plantCooldown = 10;
            if (countNearbyOakAndSaplings(level, center) < 10) plantSapling(level, view, center);
        }
        state = harvested ? JobState.WORKING : JobState.IDLE;
        ticksUntilNextWork = harvested ? 30 : 60;
    }

    private int countNearbyOakAndSaplings(ServerLevel level, BlockPos center) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-10, -4, -10), center.offset(10, 8, 10))) {
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.OAK_SAPLING) || state.is(Blocks.OAK_LOG)) {
                if (++count >= 10) break;
            }
        }
        return count;
    }

    private void plantSapling(ServerLevel level, TreeView view, BlockPos center) {
        for (int i = 0; i < 20; i++) {
            int x = center.getX() + level.random.nextInt(33) - 16;
            int z = center.getZ() + level.random.nextInt(33) - 16;
            if (!level.isLoaded(new BlockPos(x, center.getY(), z))) continue;
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (colony.isInside(pos) && level.getBlockState(pos).isAir() && SafeOakTree.safePlantingArea(view, pos)) {
                level.setBlock(pos, Blocks.OAK_SAPLING.defaultBlockState(), 3);
                return;
            }
        }
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
