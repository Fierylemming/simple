package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.entity.ColonyVillager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Carries real stacks from a local shared container to a designated hub. */
public class CourierJob extends AbstractJob {
    private BlockPos source;
    private BlockPos hub;
    private final java.util.Map<BlockPos, Long> unreachableHubs = new java.util.HashMap<>();

    public CourierJob(ColonyVillager villager, Colony colony) { super(villager, colony, JobType.COURIER); }
    private ColonyVillager worker() { return (ColonyVillager) villager; }

    @Override protected void doWork(ServerLevel level) {
        var warehouse = getWarehouse();
        var center = warehouse.getConfiguredWarehouseCenter();
        if (center == null || !level.isLoaded(center)) { state = JobState.STUCK; ticksUntilNextWork = 100; return; }
        var storage = warehouse.courierStoragePositions(level);
        unreachableHubs.entrySet().removeIf(e -> e.getValue() <= level.getGameTime());
        if (!worker().getCourierCargo().isEmpty() || !worker().getCourierCargoExtra().isEmpty()) {
            // Pick an accepting hub rather than permanently waiting at the
            // closest full or incompatible chest. Never fall back to unshared
            // containers or lose the cargo if none is available.
            if (hub == null || !storage.contains(hub) || hub.distSqr(center) > 100
                    || unreachableHubs.containsKey(hub)
                    || !(canInsert(warehouse.courierContainer(level, hub), worker().getCourierCargo())
                        || canInsert(warehouse.courierContainer(level, hub), worker().getCourierCargoExtra()))) {
                hub = storage.stream().filter(p -> p.distSqr(center) <= 100 && !unreachableHubs.containsKey(p))
                        .filter(p -> canInsert(warehouse.courierContainer(level, p), worker().getCourierCargo())
                                || canInsert(warehouse.courierContainer(level, p), worker().getCourierCargoExtra()))
                        .min(java.util.Comparator.comparingDouble(p -> p.distSqr(villager.blockPosition()))).orElse(null);
            }
            if (hub == null) { state = JobState.WAITING_FOR_CHEST; ticksUntilNextWork = 40; return; }
            if (!walkTo(hub, level, true)) return;
            Container destination = warehouse.courierContainer(level, hub);
            if (destination == null) { hub = null; state = JobState.STUCK; return; }
            insert(destination, worker().getCourierCargo());
            insert(destination, worker().getCourierCargoExtra());
            if (worker().getCourierCargo().isEmpty()) worker().setCourierCargo(ItemStack.EMPTY);
            if (worker().getCourierCargoExtra().isEmpty()) worker().setCourierCargoExtra(ItemStack.EMPTY);
            villager.swing(InteractionHand.MAIN_HAND);
            state = worker().getCourierCargo().isEmpty() && worker().getCourierCargoExtra().isEmpty()
                    ? JobState.WORKING : JobState.WAITING_FOR_CHEST;
            ticksUntilNextWork = 40;
            return;
        }
        if (storage.stream().noneMatch(p -> p.distSqr(center) <= 100)) {
            state = JobState.WAITING_FOR_CHEST; ticksUntilNextWork = 100; return;
        }
        if (source == null || !storage.contains(source) || source.distSqr(center) <= 100) {
            source = storage.stream().filter(p -> p.distSqr(center) > 100)
                    .filter(p -> hasItems(warehouse.courierContainer(level, p)))
                    .min(java.util.Comparator.comparingDouble(p -> p.distSqr(villager.blockPosition()))).orElse(null);
        }
        if (source == null) { state = JobState.IDLE; ticksUntilNextWork = 100; return; }
        if (!walkTo(source, level, false)) return;
        Container from = warehouse.courierContainer(level, source);
        if (from == null) { source = null; state = JobState.STUCK; return; }
        for (int slot = 0; slot < from.getContainerSize(); slot++) {
            ItemStack stack = from.getItem(slot);
            if (stack.isEmpty()) continue;
            worker().setCourierCargo(from.removeItem(slot, stack.getCount()));
            // Researched couriers can carry one additional actual stack. Do not
            // discard a second saved stack even if an old unlock was later removed.
            if (colony.getResearch().isUnlocked(com.simplecolonies.progression.ColonyUpgrade.WAREHOUSE_CAPACITY)) {
                for (int second = 0; second < from.getContainerSize(); second++) {
                    ItemStack next = from.getItem(second);
                    if (next.isEmpty()) continue;
                    worker().setCourierCargoExtra(from.removeItem(second, next.getCount()));
                    break;
                }
            }
            from.setChanged();
            villager.swing(InteractionHand.MAIN_HAND);
            source = null;
            state = JobState.WORKING; ticksUntilNextWork = 20; return;
        }
        source = null; state = JobState.IDLE; ticksUntilNextWork = 40;
    }

    private boolean walkTo(BlockPos target, ServerLevel level, boolean destination) {
        if (villager.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) <= 9) return true;
        if (!villager.onGround() && !villager.isInLiquid() && !villager.isPassenger()) {
            state = JobState.GOING_TO_WORK; ticksUntilNextWork = 0; return false;
        }
        var path = villager.getNavigation().createPath(target, 1);
        if (path == null || !path.canReach()) {
            if (destination) { unreachableHubs.put(target, level.getGameTime() + 200); hub = null; }
            else source = null;
            state = JobState.STUCK; ticksUntilNextWork = 10; return false;
        }
        villager.getNavigation().moveTo(path, 1.0);
        state = JobState.GOING_TO_WORK; ticksUntilNextWork = 10; return false;
    }
    private static boolean hasItems(Container c) {
        if (c == null) return false;
        for (int i = 0; i < c.getContainerSize(); i++) if (!c.getItem(i).isEmpty()) return true;
        return false;
    }
    static boolean canInsert(Container c, ItemStack cargo) {
        if (c == null || cargo.isEmpty()) return false;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack existing = c.getItem(i);
            if (!com.simplecolonies.warehouse.WarehouseManager.accepts(c, i, cargo)) continue;
            if (existing.isEmpty() || (ItemStack.isSameItemSameComponents(existing, cargo)
                    && existing.getCount() < Math.min(existing.getMaxStackSize(), c.getMaxStackSize()))) return true;
        }
        return false;
    }
    static void insert(Container c, ItemStack cargo) {
        for (int i = 0; i < c.getContainerSize() && !cargo.isEmpty(); i++) {
            ItemStack stack = c.getItem(i);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, cargo) || !com.simplecolonies.warehouse.WarehouseManager.accepts(c, i, cargo)) continue;
            int n = Math.min(cargo.getCount(), Math.min(stack.getMaxStackSize(), c.getMaxStackSize()) - stack.getCount());
            if (n > 0) { stack.grow(n); cargo.shrink(n); c.setChanged(); }
        }
        for (int i = 0; i < c.getContainerSize() && !cargo.isEmpty(); i++) {
            if (!c.getItem(i).isEmpty() || !com.simplecolonies.warehouse.WarehouseManager.accepts(c, i, cargo)) continue;
            int n = Math.min(cargo.getCount(), Math.min(cargo.getMaxStackSize(), c.getMaxStackSize()));
            c.setItem(i, cargo.split(n)); c.setChanged();
        }
    }
}
