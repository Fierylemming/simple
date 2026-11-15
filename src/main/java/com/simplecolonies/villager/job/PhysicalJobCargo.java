package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.entity.ColonyVillager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Actual, persisted worker cargo; never takes unrelated or unspawned drops. */
final class PhysicalJobCargo {
    enum Delivery { NONE, MOVING, DELIVERED, WAITING }
    private final ColonyVillager worker;
    private final Colony colony;
    private final java.util.Map<BlockPos, Long> unreachable = new java.util.HashMap<>();

    PhysicalJobCargo(ColonyVillager worker, Colony colony) { this.worker = worker; this.colony = colony; }
    boolean hasCargo() { return !worker.getCourierCargo().isEmpty() || !worker.getCourierCargoExtra().isEmpty(); }

    /** Move as much as fits into the two existing cargo slots. Any remainder
     * stays in its original item entity with all components intact. */
    void collect(ItemEntity entity) {
        ItemStack stack = entity.getItem();
        collectStack(stack);
        if (stack.isEmpty()) entity.discard();
        else entity.setItem(stack);
    }

    /** Take items from a freshly generated drop stack, leaving any excess in
     * the caller's stack so it can be spawned in the world. */
    void collectStack(ItemStack stack) {
        if (stack.isEmpty()) return;
        if (worker.getCourierCargo().isEmpty()) worker.setCourierCargo(stack.split(stack.getMaxStackSize()));
        else merge(worker.getCourierCargo(), stack);
        if (!stack.isEmpty()) {
            if (worker.getCourierCargoExtra().isEmpty()) worker.setCourierCargoExtra(stack.split(stack.getMaxStackSize()));
            else merge(worker.getCourierCargoExtra(), stack);
        }
    }

    private static void merge(ItemStack target, ItemStack source) {
        if (!ItemStack.isSameItemSameComponents(target, source)) return;
        int n = Math.min(source.getCount(), target.getMaxStackSize() - target.getCount());
        if (n > 0) { target.grow(n); source.shrink(n); }
    }

    Delivery deliver(ServerLevel level) {
        if (!hasCargo()) return Delivery.NONE;
        var warehouse = colony.getWarehouseManager();
        unreachable.entrySet().removeIf(e -> e.getValue() <= level.getGameTime());
        var positions = warehouse.courierStoragePositions(level).stream()
                .filter(p -> !unreachable.containsKey(p))
                .filter(p -> {
                    var container = warehouse.courierContainer(level, p);
                    return CourierJob.canInsert(container, worker.getCourierCargo())
                            || CourierJob.canInsert(container, worker.getCourierCargoExtra());
                }).sorted(java.util.Comparator.comparingDouble(p -> p.distSqr(worker.blockPosition()))).toList();
        for (BlockPos pos : positions) {
            if (worker.distanceToSqr(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5) > 9) {
                // Ground navigation cannot create a path mid-jump. Do not mark
                // an otherwise reachable chest unreachable for ten seconds.
                if (!worker.onGround() && !worker.isInLiquid() && !worker.isPassenger()) return Delivery.MOVING;
                var path = worker.getNavigation().createPath(pos, 1);
                if (path == null || !path.canReach()) { unreachable.put(pos, level.getGameTime()+200); continue; }
                worker.getNavigation().moveTo(path, .7);
                return Delivery.MOVING;
            }
            var container = warehouse.courierContainer(level, pos);
            if (container == null) continue;
            CourierJob.insert(container, worker.getCourierCargo());
            CourierJob.insert(container, worker.getCourierCargoExtra());
            worker.swing(InteractionHand.MAIN_HAND);
            return Delivery.DELIVERED;
        }
        return Delivery.WAITING;
    }
}
