package com.simplecolonies.villager.job;

import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.entity.ColonyVillager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Items;
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
    private boolean restored;

    public WoodcutterJob(Villager villager, Colony colony) {
        super(villager, colony, JobType.LUMBERJACK);
    }

    @Override
    protected void doWork(ServerLevel level) {
        var view = new TreeView(level);
        if (!restored) { readMemory(); restored = true; }
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
                writeMemory();
                break;
            }
        }
        if (treeBase == null) { state = JobState.IDLE; ticksUntilNextWork = 100; return; }
        // A chunk unload is not a safety violation: retain the partially cut tree
        // and retry without loading chunks. Actual construction in the area aborts it.
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
            for (int dy = -1; dy <= tree.size() + 2; dy++) {
                if (!view.loaded(treeBase.offset(dx, dy, dz))) {
                    state = JobState.STUCK; ticksUntilNextWork = 100; return;
                }
            }
        if (!SafeOakTree.safePartialTrunk(view, treeBase, tree, remaining)) {
            clearTree(); state = JobState.STUCK; ticksUntilNextWork = 60; return;
        }
        BlockPos log = tree.get(remaining - 1);
        if (villager.distanceToSqr(log.getX() + 0.5, log.getY() + 0.5, log.getZ() + 0.5) > 20.25) {
            net.minecraft.world.level.pathfinder.Path path = null;
            var candidates = new java.util.ArrayList<BlockPos>();
            for (Direction d : Direction.Plane.HORIZONTAL) candidates.add(treeBase.relative(d));
            candidates.sort(java.util.Comparator.comparingDouble(p -> villager.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5)));
            for (BlockPos candidate : candidates) {
                if (!level.isLoaded(candidate) || !level.isLoaded(candidate.above()) || !level.isLoaded(candidate.below())) continue;
                if (!level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                        || !level.getBlockState(candidate.above()).getCollisionShape(level, candidate.above()).isEmpty()
                        || !level.getBlockState(candidate.below()).isFaceSturdy(level, candidate.below(), Direction.UP)) continue;
                var attempt = villager.getNavigation().createPath(candidate, 0);
                if (attempt != null && attempt.canReach()) { path = attempt; break; }
            }
            if (path == null) { state = JobState.STUCK; ticksUntilNextWork = 60; return; }
            villager.getNavigation().moveTo(path, 0.7);
            state = JobState.GOING_TO_WORK; ticksUntilNextWork = 10; return;
        }
        equipAxeIfAvailable(level);
        var block = level.getBlockState(log);
        var drops = net.minecraft.world.level.block.Block.getDrops(block, level, log, null, villager, villager.getMainHandItem());
        if (!level.destroyBlock(log, false)) { state = JobState.STUCK; ticksUntilNextWork = 60; return; }
        if (villager.getMainHandItem().getItem() instanceof AxeItem)
            villager.getMainHandItem().hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);
        villager.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        for (var drop : drops) if (!drop.isEmpty()) depositToColony(level,
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(drop.getItem()).toString(), drop.getCount());
        remaining--;
        if (remaining == 0) {
            // No free saplings: replant only from real colony stock, at the tree's original base.
            if (level.getBlockState(treeBase).isAir() && view.soil(treeBase.below())
                    && colony.consumeStock(level, "minecraft:oak_sapling", 1)) {
                if (!level.setBlock(treeBase, Blocks.OAK_SAPLING.defaultBlockState(), 3))
                    colony.depositToWarehouse("minecraft:oak_sapling", 1);
            }
            clearTree();
        } else writeMemory();
        state = JobState.WORKING;
        ticksUntilNextWork = 30;
    }

    /** Use an actual stock axe when available. Bare-handed work remains possible in
     * older colonies without one, so upgrading a save does not stop its wood supply. */
    private void equipAxeIfAvailable(ServerLevel level) {
        if (!villager.getMainHandItem().isEmpty()) return;
        for (var item : new net.minecraft.world.item.Item[]{Items.NETHERITE_AXE, Items.DIAMOND_AXE,
                Items.IRON_AXE, Items.STONE_AXE, Items.WOODEN_AXE, Items.GOLDEN_AXE}) {
            var tool = takeTool(level, item);
            if (!tool.isEmpty()) { villager.setItemSlot(EquipmentSlot.MAINHAND, tool); return; }
        }
    }

    private void clearTree() {
        treeBase = null; tree = java.util.List.of(); remaining = 0; writeMemory();
    }
    private void writeMemory() {
        if (!(villager instanceof ColonyVillager cv)) return;
        CompoundTag tag = new CompoundTag();
        if (treeBase != null) {
            tag.putLong("base", treeBase.asLong());
            tag.putInt("height", tree.size());
            tag.putInt("remaining", remaining);
        }
        cv.setWoodcutterMemory(tag);
    }
    private void readMemory() {
        if (!(villager instanceof ColonyVillager cv)) return;
        CompoundTag tag = cv.getWoodcutterMemory();
        int height = tag.getInt("height"), count = tag.getInt("remaining");
        if (!tag.contains("base") || height < 3 || height > 4 || count < 1 || count > height) return;
        BlockPos base = BlockPos.of(tag.getLong("base"));
        if (!colony.isInside(base)) return;
        var logs = new java.util.ArrayList<BlockPos>();
        for (int y = 0; y < height; y++) logs.add(base.above(y));
        treeBase = base; tree = java.util.List.copyOf(logs); remaining = count;
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
