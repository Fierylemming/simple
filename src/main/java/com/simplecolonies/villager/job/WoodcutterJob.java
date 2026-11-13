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

/** Harvest short, recognizable, unbuilt trees; never use nearby leaves alone as proof. */
public class WoodcutterJob extends AbstractJob {
    private record Species(net.minecraft.world.level.block.Block log, net.minecraft.world.level.block.Block leaves,
                           net.minecraft.world.level.block.Block sapling) {}
    private static final java.util.List<Species> SPECIES = java.util.List.of(
            new Species(Blocks.OAK_LOG, Blocks.OAK_LEAVES, Blocks.OAK_SAPLING),
            new Species(Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES, Blocks.SPRUCE_SAPLING),
            new Species(Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, Blocks.BIRCH_SAPLING),
            new Species(Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES, Blocks.JUNGLE_SAPLING),
            new Species(Blocks.ACACIA_LOG, Blocks.ACACIA_LEAVES, Blocks.ACACIA_SAPLING),
            new Species(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_LEAVES, Blocks.DARK_OAK_SAPLING),
            new Species(Blocks.MANGROVE_LOG, Blocks.MANGROVE_LEAVES, Blocks.MANGROVE_PROPAGULE),
            new Species(Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES, Blocks.CHERRY_SAPLING));

    private static Species species(BlockState state) {
        return SPECIES.stream().filter(s -> state.is(s.log())).findFirst().orElse(null);
    }

    private BlockPos treeBase;
    private Species treeSpecies;
    private java.util.List<BlockPos> tree = java.util.List.of();
    private int remaining;
    private boolean restored;

    public WoodcutterJob(Villager villager, Colony colony) {
        super(villager, colony, JobType.LUMBERJACK);
    }

    @Override
    protected void doWork(ServerLevel level) {
        if (!restored) { readMemory(); restored = true; }
        if (treeBase == null) {
            BlockPos center = villager.blockPosition();
            for (BlockPos mutable : BlockPos.betweenClosed(center.offset(-16, -4, -16), center.offset(16, 8, 16))) {
                BlockPos base = mutable.immutable();
                if (!level.isLoaded(base)) continue;
                Species species = species(level.getBlockState(base));
                if (species == null) continue;
                var candidate = new TreeView(level, species);
                if (!candidate.trunk(base) || !candidate.loaded(base.below()) || !candidate.soil(base.below())) continue;
                var found = SafeOakTree.trunk(candidate, base);
                // The worker must be able to reach each block from ground level, without scaffolding.
                if (found.isEmpty() || found.size() > 4) continue;
                treeBase = base;
                treeSpecies = species;
                tree = found;
                remaining = tree.size();
                writeMemory();
                break;
            }
        }
        if (treeBase == null) { state = JobState.IDLE; ticksUntilNextWork = 100; return; }
        if (!level.isLoaded(treeBase)) { state = JobState.STUCK; ticksUntilNextWork = 100; return; }
        Species species = species(level.getBlockState(treeBase));
        if (species == null || (treeSpecies != null && treeSpecies != species)) {
            clearTree(); state = JobState.STUCK; ticksUntilNextWork = 60; return;
        }
        // Old saved partial oak trees did not have a species field.
        if (treeSpecies == null) treeSpecies = species;
        var view = new TreeView(level, species);
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
            String sapling = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(species.sapling().asItem()).toString();
            // Dark oak requires four saplings, so don't waste one on an ungrowable singleton.
            if (species.sapling() != Blocks.DARK_OAK_SAPLING && level.getBlockState(treeBase).isAir()
                    && view.soil(treeBase.below()) && colony.consumeStock(level, sapling, 1)) {
                if (!level.setBlock(treeBase, species.sapling().defaultBlockState(), 3))
                    colony.depositToWarehouse(sapling, 1);
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
        treeBase = null; treeSpecies = null; tree = java.util.List.of(); remaining = 0; writeMemory();
    }
    private void writeMemory() {
        if (!(villager instanceof ColonyVillager cv)) return;
        CompoundTag tag = new CompoundTag();
        if (treeBase != null) {
            tag.putLong("base", treeBase.asLong());
            if (treeSpecies != null) tag.putString("species", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(treeSpecies.log()).toString());
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
        String savedSpecies = tag.getString("species");
        if (!savedSpecies.isEmpty()) {
            treeSpecies = SPECIES.stream().filter(s -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.log()).toString().equals(savedSpecies)).findFirst().orElse(null);
            if (treeSpecies == null) return;
        }
        treeBase = base; tree = java.util.List.copyOf(logs); remaining = count;
    }

    private final class TreeView implements SafeOakTree.View {
        private final ServerLevel level;
        private final Species species;
        TreeView(ServerLevel level, Species species) { this.level = level; this.species = species; }
        public boolean loaded(BlockPos pos) { return level.isLoaded(pos); }
        public boolean soil(BlockPos pos) { return level.getBlockState(pos).is(BlockTags.DIRT); }
        public boolean trunk(BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            return state.is(species.log()) && state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y;
        }
        public boolean naturalLeaf(BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            return state.is(species.leaves()) && !state.getValue(LeavesBlock.PERSISTENT);
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
