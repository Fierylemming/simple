package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Harvests real drops and replants only when seed or produce is available. */
public class FarmerJob extends AbstractJob {
    private BlockPos target;
    private final java.util.Map<BlockPos, Long> unreachableUntil = new java.util.HashMap<>();
    public FarmerJob(Villager villager, Colony colony) { super(villager, colony, JobType.FARMER); }

    @Override protected void doWork(ServerLevel level) {
        var site = villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
        if (site.isEmpty() || !site.get().dimension().equals(level.dimension()) || !level.isLoaded(site.get().pos())
                || !level.getBlockState(site.get().pos()).is(Blocks.COMPOSTER)) {
            state = JobState.STUCK; ticksUntilNextWork = 100; return;
        }
        // A composter alone is not permission to harvest nearby player fields.
        // Only a completed, explicitly registered farm near this POI is worked.
        var farm = colony.getBuildings().stream()
                .filter(b -> b.getType() == com.simplecolonies.building.BuildingType.FARM
                        && b.getState() == com.simplecolonies.building.BuildingState.COMPLETE
                        && level.isLoaded(b.getPos()) && b.getPos().distSqr(site.get().pos()) <= 144)
                .min(java.util.Comparator.comparingDouble(b -> b.getPos().distSqr(site.get().pos()))).orElse(null);
        if (farm == null) { state = JobState.STUCK; ticksUntilNextWork = 100; return; }
        // Vanilla farmers make bread. This keeps the four-founder starter settlement
        // viable without requiring a separately assigned baker.
        int targetBread = Math.max(16, colony.citizenCount() * 4);
        if (colony.getWarehouseCount(level, "minecraft:bread") < targetBread
                && colony.getWarehouseCount(level, "minecraft:wheat") >= 3) {
            if (!atClaimedWorkstation(level)) return;
            if (colony.consumeStock(level, "minecraft:wheat", 3)) {
                depositToColony(level, "minecraft:bread", 1);
                villager.swing(InteractionHand.MAIN_HAND);
                state = JobState.WORKING;
                ticksUntilNextWork = 40;
                return;
            }
        }
        if (target == null || !level.isLoaded(target) || !eligible(level, target, farm.getPos())) target = findWork(level, farm.getPos());
        if (target == null) { state = JobState.IDLE; ticksUntilNextWork = 100; return; }
        if (villager.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) > 9.0) {
            var path = villager.getNavigation().createPath(target, 2);
            if (path == null || !path.canReach()) {
                unreachableUntil.put(target, level.getGameTime() + 400);
                state = JobState.STUCK; target = null; ticksUntilNextWork = 40; return;
            }
            villager.getNavigation().moveTo(path, 0.7);
            state = JobState.GOING_TO_WORK; ticksUntilNextWork = 10; return;
        }
        BlockState cropState = level.getBlockState(target);
        if (cropState.getBlock() instanceof CropBlock crop && seedFor(cropState) != null && crop.isMaxAge(cropState)) {
            var drops = Block.getDrops(cropState, level, target, null, villager, ItemStack.EMPTY);
            if (level.destroyBlock(target, false)) {
                villager.swing(InteractionHand.MAIN_HAND);
                Item seed = seedFor(cropState);
                boolean replanted = false;
                for (ItemStack drop : drops) {
                    int count = drop.getCount();
                    if (!replanted && drop.is(seed) && count > 0 && level.getBlockState(target).isAir()
                            && level.getBlockState(target.below()).is(Blocks.FARMLAND)) {
                        if (level.setBlock(target, crop.getStateForAge(0), 3)) {
                            count--; replanted = true;
                        }
                    }
                    if (count > 0) depositToColony(level, BuiltInRegistries.ITEM.getKey(drop.getItem()).toString(), count);
                }
                // Wheat sometimes drops no seeds. Use an actual stored seed as a
                // fallback so a successful harvest doesn't permanently shrink the farm.
                if (!replanted) replantFromStock(level, target, crop, seed);
                state = JobState.WORKING;
            }
        } else if (cropState.isAir() && level.getBlockState(target.below()).is(Blocks.FARMLAND)) {
            for (Item seed : new Item[]{Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.BEETROOT_SEEDS}) {
                String id = BuiltInRegistries.ITEM.getKey(seed).toString();
                Block crop = seed == Items.WHEAT_SEEDS ? Blocks.WHEAT : seed == Items.CARROT ? Blocks.CARROTS
                        : seed == Items.POTATO ? Blocks.POTATOES : Blocks.BEETROOTS;
                if (colony.consumeStock(level, id, 1)) {
                    if (level.setBlock(target, crop.defaultBlockState(), 3)) {
                        villager.swing(InteractionHand.MAIN_HAND); state = JobState.WORKING;
                    } else {
                        colony.depositToWarehouse(id, 1);
                        state = JobState.STUCK;
                    }
                    break;
                }
            }
        }
        target = null;
        ticksUntilNextWork = 40;
    }

    boolean replantFromStock(ServerLevel level, BlockPos pos, CropBlock crop, Item seed) {
        if (!level.isLoaded(pos) || !level.isLoaded(pos.below()) || !colony.isInside(pos)
                || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).is(Blocks.FARMLAND)) return false;
        String id = BuiltInRegistries.ITEM.getKey(seed).toString();
        if (!colony.consumeStock(level, id, 1)) return false;
        if (level.setBlock(pos, crop.getStateForAge(0), 3)) return true;
        colony.depositToWarehouse(id, 1);
        return false;
    }

    private boolean eligible(ServerLevel level, BlockPos pos, BlockPos farm) {
        if (Math.abs(pos.getX() - farm.getX()) > 5 || Math.abs(pos.getZ() - farm.getZ()) > 5
                || Math.abs(pos.getY() - farm.getY()) > 3) return false;
        if (!level.getBlockState(pos.below()).is(Blocks.FARMLAND)) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir() || (state.getBlock() instanceof CropBlock crop && seedFor(state) != null && crop.isMaxAge(state));
    }

    private BlockPos findWork(ServerLevel level, BlockPos center) {
        unreachableUntil.entrySet().removeIf(e -> e.getValue() <= level.getGameTime());
        BlockPos empty = null;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-5, -3, -5), center.offset(5, 3, 5))) {
            if (!level.isLoaded(p) || !colony.isInside(p) || unreachableUntil.containsKey(p) || !eligible(level, p, center)) continue;
            if (!level.getBlockState(p).isAir()) return p.immutable();
            if (empty == null) empty = p.immutable();
        }
        return empty;
    }

    private Item seedFor(BlockState state) {
        if (state.is(Blocks.WHEAT)) return Items.WHEAT_SEEDS;
        if (state.is(Blocks.CARROTS)) return Items.CARROT;
        if (state.is(Blocks.POTATOES)) return Items.POTATO;
        if (state.is(Blocks.BEETROOTS)) return Items.BEETROOT_SEEDS;
        return null;
    }
}
