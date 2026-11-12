package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Physically collect stored equipment; never replace existing equipment. */
final class GuardEquipment {
    enum Result { NONE, MOVING, EQUIPPED }
    private static final double REACH_SQ = 6.25;

    static Result collectChestplate(ServerLevel level, Colony colony, Villager worker) {
        return collect(level, colony, worker, EquipmentSlot.CHEST, stack -> stack.is(Items.LEATHER_CHESTPLATE));
    }

    static Result collectSword(ServerLevel level, Colony colony, Villager worker) {
        return collect(level, colony, worker, EquipmentSlot.MAINHAND,
                stack -> stack.is(Items.IRON_SWORD) || stack.is(Items.STONE_SWORD));
    }

    private static Result collect(ServerLevel level, Colony colony, Villager worker, EquipmentSlot equipmentSlot,
                                  java.util.function.Predicate<net.minecraft.world.item.ItemStack> accepts) {
        if (!worker.getItemBySlot(equipmentSlot).isEmpty()) return Result.NONE;
        var warehouse = colony.getWarehouseManager();
        var positions = warehouse.courierStoragePositions(level).stream()
                .sorted(Comparator.comparingDouble(p -> p.distSqr(worker.blockPosition()))).toList();
        for (BlockPos pos : positions) {
            Container chest = warehouse.courierContainer(level, pos);
            if (chest == null || !hasItem(chest, accepts)) continue;
            Vec3 center = Vec3.atCenterOf(pos);
            if (worker.distanceToSqr(center) <= REACH_SQ && visible(level, worker, worker.getEyePosition(), pos)) {
                // Recheck after arrival: containers may have been emptied or replaced.
                chest = warehouse.courierContainer(level, pos);
                if (chest == null || !worker.getItemBySlot(equipmentSlot).isEmpty()) continue;
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    if (!accepts.test(chest.getItem(slot))) continue;
                    var item = chest.removeItem(slot, 1);
                    if (item.isEmpty()) continue;
                    chest.setChanged();
                    worker.setItemSlot(equipmentSlot, item);
                    worker.setGuaranteedDrop(equipmentSlot);
                    worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    return Result.EQUIPPED;
                }
            }
            var stands = new java.util.ArrayList<BlockPos>();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos stand = pos.relative(direction);
                if (!colony.isInside(stand) || !level.isLoaded(stand) || !level.isLoaded(stand.above()) || !level.isLoaded(stand.below())) continue;
                if (!level.getBlockState(stand).isAir() || !level.getBlockState(stand.above()).isAir()
                        || !level.getBlockState(stand.below()).isFaceSturdy(level, stand.below(), Direction.UP)
                        || level.getBlockState(stand.below()).is(Blocks.MAGMA_BLOCK)) continue;
                Vec3 eye = new Vec3(stand.getX() + 0.5, stand.getY() + worker.getEyeHeight(), stand.getZ() + 0.5);
                if (visible(level, worker, eye, pos)) stands.add(stand);
            }
            stands.sort(Comparator.comparingDouble(p -> p.distSqr(worker.blockPosition())));
            for (BlockPos stand : stands) {
                if (stand.equals(worker.blockPosition())) continue;
                var path = worker.getNavigation().createPath(stand, 0);
                if (path != null && path.canReach()) {
                    worker.getNavigation().moveTo(path, 0.7);
                    return Result.MOVING;
                }
            }
        }
        return Result.NONE;
    }

    private static boolean hasItem(Container container, java.util.function.Predicate<net.minecraft.world.item.ItemStack> accepts) {
        for (int i = 0; i < container.getContainerSize(); i++)
            if (accepts.test(container.getItem(i))) return true;
        return false;
    }

    private static boolean visible(ServerLevel level, Villager worker, Vec3 from, BlockPos target) {
        var hit = level.clip(new ClipContext(from, Vec3.atCenterOf(target), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, worker));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }

    private GuardEquipment() {}
}
