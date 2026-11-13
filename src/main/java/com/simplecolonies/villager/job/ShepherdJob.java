package com.simplecolonies.villager.job;

import com.simplecolonies.block.entity.BuildingMarkerBlockEntity;
import com.simplecolonies.building.BuildingState;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.entity.ColonyVillager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

/** Shears real sheep in a player-designated pasture and carries actual drops to shared storage. */
public final class ShepherdJob extends AbstractJob {
    public ShepherdJob(ColonyVillager villager, Colony colony) { super(villager, colony, JobType.SHEPHERD); }
    private ColonyVillager worker() { return (ColonyVillager) villager; }

    @Override protected void doWork(ServerLevel level) {
        var warehouse = colony.getWarehouseManager();
        var storage = warehouse.courierStoragePositions(level);
        // Cargo survives unload and is dropped if this worker dies or changes jobs.
        // Finish an existing delivery even when the pasture or loom was removed.
        if (!worker().getCourierCargo().isEmpty() || !worker().getCourierCargoExtra().isEmpty()) {
            var destination = storage.stream().filter(p -> {
                var c = warehouse.courierContainer(level, p);
                return CourierJob.canInsert(c, worker().getCourierCargo()) || CourierJob.canInsert(c, worker().getCourierCargoExtra());
            }).min(java.util.Comparator.comparingDouble(p -> p.distSqr(villager.blockPosition()))).orElse(null);
            if (destination == null) { state = JobState.WAITING_FOR_CHEST; ticksUntilNextWork = 100; return; }
            if (!walkTo(destination)) return;
            var container = warehouse.courierContainer(level, destination);
            if (container == null) { state = JobState.WAITING_FOR_CHEST; ticksUntilNextWork = 40; return; }
            CourierJob.insert(container, worker().getCourierCargo());
            CourierJob.insert(container, worker().getCourierCargoExtra());
            boolean delivered = worker().getCourierCargo().isEmpty() && worker().getCourierCargoExtra().isEmpty();
            villager.swing(InteractionHand.MAIN_HAND);
            state = delivered ? JobState.WORKING : JobState.WAITING_FOR_CHEST;
            ticksUntilNextWork = 40;
            return;
        }
        var site = villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
        if (site.isEmpty() || !site.get().dimension().equals(level.dimension()) || !level.isLoaded(site.get().pos())
                || !colony.isInside(site.get().pos()) || !level.getBlockState(site.get().pos()).is(Blocks.LOOM)) {
            state = JobState.STUCK; ticksUntilNextWork = 100; return;
        }
        var pasture = colony.getBuildings().stream().filter(b -> b.isPlayerPlaced && b.getType() == BuildingType.PASTURE
                && b.getState() == BuildingState.COMPLETE && level.isLoaded(b.getPos()) && colony.isInside(b.getPos())
                && b.getPos().distSqr(site.get().pos()) <= 144)
                .filter(b -> level.getBlockEntity(b.getPos()) instanceof BuildingMarkerBlockEntity marker
                        && marker.getBuildingType() == BuildingType.PASTURE && b.getPos().equals(marker.getLinkedBuildingPos()))
                .min(java.util.Comparator.comparingDouble(b -> b.getPos().distSqr(villager.blockPosition()))).orElse(null);
        if (pasture == null || storage.isEmpty()) { state = JobState.STUCK; ticksUntilNextWork = 100; return; }
        if (!villager.getMainHandItem().is(Items.SHEARS)) {
            if (!villager.getMainHandItem().isEmpty()) { state = JobState.NEEDS_TOOL; ticksUntilNextWork = 100; return; }
            var inventory = villager.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (inventory.getItem(slot).is(Items.SHEARS)) {
                    villager.setItemSlot(EquipmentSlot.MAINHAND, inventory.removeItem(slot, 1));
                    break;
                }
            }
            if (!villager.getMainHandItem().is(Items.SHEARS)) {
                BlockPos source = storage.stream().filter(p -> {
                    var c = warehouse.courierContainer(level, p);
                    if (c == null) return false;
                    for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(Items.SHEARS)) return true;
                    return false;
                }).min(java.util.Comparator.comparingDouble(p -> p.distSqr(villager.blockPosition()))).orElse(null);
                if (source == null) { state = JobState.NEEDS_TOOL; ticksUntilNextWork = 100; return; }
                if (!walkTo(source)) return;
                var container = warehouse.courierContainer(level, source);
                if (container == null) { state = JobState.STUCK; return; }
                for (int slot = 0; slot < container.getContainerSize(); slot++) if (container.getItem(slot).is(Items.SHEARS)) {
                    villager.setItemSlot(EquipmentSlot.MAINHAND, container.removeItem(slot, 1));
                    container.setChanged(); break;
                }
                if (!villager.getMainHandItem().is(Items.SHEARS)) { state = JobState.NEEDS_TOOL; return; }
            }
        }
        var center = pasture.getPos();
        var shears = villager.getMainHandItem();
        var sheep = level.getEntitiesOfClass(Sheep.class, new AABB(center).inflate(6, 3, 6),
                animal -> animal.isAlive() && !animal.hasCustomName() && colony.isInside(animal.blockPosition())
                        && animal.isShearable(null, shears, level, animal.blockPosition()));
        var target = sheep.stream().min(java.util.Comparator.comparingDouble(villager::distanceToSqr)).orElse(null);
        if (target == null) { state = JobState.IDLE; ticksUntilNextWork = 100; return; }
        if (villager.distanceToSqr(target) > 6.25 || !villager.hasLineOfSight(target)) {
            var path = villager.getNavigation().createPath(target, 1);
            if (path == null || !path.canReach()) { state = JobState.STUCK; ticksUntilNextWork = 60; return; }
            villager.getNavigation().moveTo(path, 0.7); state = JobState.GOING_TO_WORK; ticksUntilNextWork = 20; return;
        }
        if (!target.isShearable(null, shears, level, target.blockPosition())) return;
        var drops = target.onSheared(null, shears, level, target.blockPosition());
        // Preserve all returned stack components. If an unusual modded sheep
        // returns more than two stacks, leave the excess as real world drops.
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            ItemStack remaining = drop.copy();
            if (worker().getCourierCargo().isEmpty()) worker().setCourierCargo(remaining.split(remaining.getMaxStackSize()));
            else merge(worker().getCourierCargo(), remaining);
            if (!remaining.isEmpty()) {
                if (worker().getCourierCargoExtra().isEmpty()) worker().setCourierCargoExtra(remaining.split(remaining.getMaxStackSize()));
                else merge(worker().getCourierCargoExtra(), remaining);
            }
            if (!remaining.isEmpty()) target.spawnShearedDrop(level, target.blockPosition(), remaining);
        }
        target.gameEvent(GameEvent.SHEAR, villager);
        shears.hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);
        villager.getLookControl().setLookAt(target);
        villager.swing(InteractionHand.MAIN_HAND);
        state = JobState.WORKING; ticksUntilNextWork = 20;
    }

    private static void merge(ItemStack cargo, ItemStack incoming) {
        if (!ItemStack.isSameItemSameComponents(cargo, incoming)) return;
        int count = Math.min(incoming.getCount(), cargo.getMaxStackSize() - cargo.getCount());
        if (count > 0) { cargo.grow(count); incoming.shrink(count); }
    }

    private boolean walkTo(BlockPos pos) {
        if (villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 9) return true;
        var path = villager.getNavigation().createPath(pos, 1);
        if (path == null || !path.canReach()) { state = JobState.STUCK; ticksUntilNextWork = 60; return false; }
        villager.getNavigation().moveTo(path, 0.7); state = JobState.GOING_TO_WORK; ticksUntilNextWork = 20; return false;
    }
}
