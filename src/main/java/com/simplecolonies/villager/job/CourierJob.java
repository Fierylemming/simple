package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.entity.ColonyVillager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** A courier carries one real stack from a local storage container to a designated hub. */
public class CourierJob extends AbstractJob {
    private BlockPos source;
    private BlockPos hub;

    public CourierJob(ColonyVillager villager, Colony colony) { super(villager, colony, JobType.COURIER); }
    private ColonyVillager worker() { return (ColonyVillager) villager; }

    @Override protected void doWork(ServerLevel level) {
        var warehouse = getWarehouse();
        var center = warehouse.getConfiguredWarehouseCenter();
        if (center == null || !level.isLoaded(center)) { state = JobState.STUCK; ticksUntilNextWork = 100; return; }
        var storage = warehouse.courierStoragePositions(level);
        if (hub == null || !storage.contains(hub) || hub.distSqr(center) > 100) {
            hub = storage.stream().filter(p -> p.distSqr(center) <= 100)
                    .min(java.util.Comparator.comparingDouble(p -> p.distSqr(center))).orElse(null);
        }
        if (hub == null) { state = JobState.WAITING_FOR_CHEST; ticksUntilNextWork = 100; return; }
        if (!worker().getCourierCargo().isEmpty()) {
            if (!walkTo(hub)) return;
            Container destination = warehouse.courierContainer(level, hub);
            if (destination == null) { hub = null; state = JobState.STUCK; return; }
            insert(destination, worker().getCourierCargo());
            if (worker().getCourierCargo().isEmpty()) worker().setCourierCargo(ItemStack.EMPTY);
            villager.swing(InteractionHand.MAIN_HAND);
            state = worker().getCourierCargo().isEmpty() ? JobState.WORKING : JobState.WAITING_FOR_CHEST;
            ticksUntilNextWork = 40;
            return;
        }
        if (source == null || !storage.contains(source) || source.distSqr(center) <= 100) {
            source = storage.stream().filter(p -> p.distSqr(center) > 100)
                    .filter(p -> hasItems(warehouse.courierContainer(level, p)))
                    .min(java.util.Comparator.comparingDouble(p -> p.distSqr(villager.blockPosition()))).orElse(null);
        }
        if (source == null) { state = JobState.IDLE; ticksUntilNextWork = 100; return; }
        if (!walkTo(source)) return;
        Container from = warehouse.courierContainer(level, source);
        if (from == null) { source = null; state = JobState.STUCK; return; }
        for (int slot = 0; slot < from.getContainerSize(); slot++) {
            ItemStack stack = from.getItem(slot);
            if (stack.isEmpty()) continue;
            worker().setCourierCargo(from.removeItem(slot, stack.getCount()));
            from.setChanged();
            villager.swing(InteractionHand.MAIN_HAND);
            source = null;
            state = JobState.WORKING; ticksUntilNextWork = 20; return;
        }
        source = null; state = JobState.IDLE; ticksUntilNextWork = 40;
    }

    private boolean walkTo(BlockPos target) {
        if (villager.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) <= 9) return true;
        var path = villager.getNavigation().createPath(target, 1);
        if (path == null || !path.canReach()) {
            source = null; state = JobState.STUCK; ticksUntilNextWork = 60; return false;
        }
        villager.getNavigation().moveTo(path, 1.0);
        state = JobState.GOING_TO_WORK; ticksUntilNextWork = 10; return false;
    }
    private static boolean hasItems(Container c) {
        if (c == null) return false;
        for (int i = 0; i < c.getContainerSize(); i++) if (!c.getItem(i).isEmpty()) return true;
        return false;
    }
    private static void insert(Container c, ItemStack cargo) {
        for (int i = 0; i < c.getContainerSize() && !cargo.isEmpty(); i++) {
            ItemStack stack = c.getItem(i);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, cargo) || !c.canPlaceItem(i, cargo)) continue;
            int n = Math.min(cargo.getCount(), Math.min(stack.getMaxStackSize(), c.getMaxStackSize()) - stack.getCount());
            if (n > 0) { stack.grow(n); cargo.shrink(n); c.setChanged(); }
        }
        for (int i = 0; i < c.getContainerSize() && !cargo.isEmpty(); i++) {
            if (!c.getItem(i).isEmpty() || !c.canPlaceItem(i, cargo)) continue;
            int n = Math.min(cargo.getCount(), Math.min(cargo.getMaxStackSize(), c.getMaxStackSize()));
            c.setItem(i, cargo.split(n)); c.setChanged();
        }
    }
}
