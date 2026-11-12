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
        if (target == null || !level.isLoaded(target) || !eligible(level, target)) target = findWork(level);
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

    private boolean eligible(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos.below()).is(Blocks.FARMLAND)) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir() || (state.getBlock() instanceof CropBlock crop && seedFor(state) != null && crop.isMaxAge(state));
    }

    private BlockPos findWork(ServerLevel level) {
        BlockPos center = workplacePos == null ? villager.blockPosition() : workplacePos;
        unreachableUntil.entrySet().removeIf(e -> e.getValue() <= level.getGameTime());
        BlockPos empty = null;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-6, -3, -6), center.offset(6, 3, 6))) {
            if (!level.isLoaded(p) || !colony.isInside(p) || unreachableUntil.containsKey(p) || !eligible(level, p)) continue;
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
