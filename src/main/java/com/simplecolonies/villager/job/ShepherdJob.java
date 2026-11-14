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
        var center = pasture.getPos();
        var sheep = level.getEntitiesOfClass(Sheep.class, new AABB(center).inflate(6, 3, 6),
                animal -> animal.isAlive() && colony.isInside(animal.blockPosition()));
        // Breeding already-sheared sheep does not require a pair of shears.
        if (sheep.stream().noneMatch(a -> !com.simplecolonies.entity.AnimalProtection.protectedFromWorkers(a) && !a.isBaby() && !a.isSheared())) {
            breedSheep(level, sheep, storage); return;
        }
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
                // Gathering and shearing are distinct physical actions, even when
                // a sheep happens to stand beside the storage chest.
                villager.swing(InteractionHand.MAIN_HAND);
                state = JobState.WORKING; ticksUntilNextWork = 20; return;
            }
        }
        var shears = villager.getMainHandItem();
        var target = sheep.stream().filter(animal -> !com.simplecolonies.entity.AnimalProtection.protectedFromWorkers(animal)
                        && animal.isShearable(null, shears, level, animal.blockPosition()))
                .min(java.util.Comparator.comparingDouble(villager::distanceToSqr)).orElse(null);
        if (target == null) { breedSheep(level, sheep, storage); return; }
        if (villager.distanceToSqr(target) > 6.25 || !villager.hasLineOfSight(target)) {
            var path = villager.getNavigation().createPath(target, 1);
            if (path == null || !path.canReach()) { state = JobState.STUCK; ticksUntilNextWork = 60; return; }
            villager.getNavigation().moveTo(path, 0.7); state = JobState.GOING_TO_WORK; ticksUntilNextWork = 20; return;
        }
        if (com.simplecolonies.entity.AnimalProtection.protectedFromWorkers(target)
                || !target.isShearable(null, shears, level, target.blockPosition())) return;
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

    /** Feed a small flock only from real, plain wheat in shared storage.
     * The worker walks to collect each item and to each sheep; vanilla animal
     * AI, not this job, creates offspring. Named sheep count toward the cap
     * but are never fed. Keep at least two wheat in storage as a reserve. */
    private void breedSheep(ServerLevel level, java.util.List<Sheep> flock, java.util.List<BlockPos> storage) {
        if (flock.size() >= 4) { state = JobState.IDLE; ticksUntilNextWork = 100; return; }
        var adults = flock.stream().filter(a -> !com.simplecolonies.entity.AnimalProtection.protectedFromWorkers(a) && a.getAge() == 0).toList();
        var target = adults.stream().filter(a -> !a.isInLove() && a.canFallInLove())
                // Do not spend two wheat on sheep that are too far apart to
                // find each other before vanilla's love timer expires. The
                // shepherd does not teleport or force animal navigation.
                .filter(a -> adults.stream().anyMatch(other -> other != a && a.distanceToSqr(other) <= 36
                        && (other.isInLove() || other.canFallInLove())))
                .min(java.util.Comparator.comparingDouble(villager::distanceToSqr)).orElse(null);
        if (target == null) { state = JobState.IDLE; ticksUntilNextWork = 100; return; }
        boolean partnerWaiting = adults.stream().anyMatch(a -> a != target && a.distanceToSqr(target) <= 36 && a.isInLove());
        // Old saves may have shepherd wheat in vanilla inventory. Move only the
        // actual plain wheat needed for this pair into the persistent work slot.
        var supplies = worker().getWorkSupplies();
        if (!supplies.isEmpty() && !plainWheat(supplies)) { state = JobState.STUCK; ticksUntilNextWork = 100; return; }
        int needed = partnerWaiting ? 1 : 2;
        var inventory = villager.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && (supplies.isEmpty() || supplies.getCount() < needed); i++) {
            if (!plainWheat(inventory.getItem(i))) continue;
            int carried = supplies.isEmpty() ? 0 : supplies.getCount();
            var moved = inventory.removeItem(i, needed - carried);
            if (supplies.isEmpty()) { worker().setWorkSupplies(moved); supplies = worker().getWorkSupplies(); }
            else supplies.grow(moved.getCount());
        }
        int carried = supplies.isEmpty() ? 0 : supplies.getCount();
        // Secure food for both parents before feeding either, otherwise a
        // long trip back to storage can let the first parent's love expire.
        if (carried < needed) {
            var warehouse = colony.getWarehouseManager();
            int amount = 0;
            for (var pos : storage) {
                var container = warehouse.courierContainer(level, pos);
                if (container == null) continue;
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    var stack = container.getItem(slot);
                    if (plainWheat(stack)) amount += stack.getCount();
                }
            }
            if (amount < needed - carried + 2) { state = JobState.NEEDS_RESOURCE; ticksUntilNextWork = 100; return; }
            var source = storage.stream().filter(pos -> {
                var c = warehouse.courierContainer(level, pos);
                if (c == null) return false;
                for (int i = 0; i < c.getContainerSize(); i++) if (plainWheat(c.getItem(i))) return true;
                return false;
            }).min(java.util.Comparator.comparingDouble(p -> p.distSqr(villager.blockPosition()))).orElse(null);
            if (source == null) { state = JobState.NEEDS_RESOURCE; ticksUntilNextWork = 100; return; }
            if (!walkTo(source)) return;
            var container = warehouse.courierContainer(level, source);
            if (container == null) { state = JobState.STUCK; return; }
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (!plainWheat(container.getItem(i))) continue;
                var taken = container.removeItem(i, 1);
                container.setChanged();
                if (supplies.isEmpty()) worker().setWorkSupplies(taken);
                else supplies.grow(taken.getCount());
                state = JobState.WORKING; ticksUntilNextWork = 10; return;
            }
            state = JobState.NEEDS_RESOURCE; return;
        }
        if (partnerWaiting) {
            if (villager.distanceToSqr(target) > 6.25 || !villager.hasLineOfSight(target)) {
                var path = villager.getNavigation().createPath(target, 1);
                if (path == null || !path.canReach()) { state = JobState.STUCK; ticksUntilNextWork = 60; return; }
                villager.getNavigation().moveTo(path, 0.7); state = JobState.GOING_TO_WORK; ticksUntilNextWork = 10; return;
            }
        } else {
            var partner = adults.stream().filter(a -> a != target && a.canFallInLove() && a.distanceToSqr(target) <= 36)
                    .min(java.util.Comparator.comparingDouble(villager::distanceToSqr)).orElse(null);
            if (partner == null) return;
            if (villager.distanceToSqr(target) > 6.25 || !villager.hasLineOfSight(target)
                    || villager.distanceToSqr(partner) > 6.25 || !villager.hasLineOfSight(partner)) {
                approachPair(level, target, partner);
                return;
            }
        }
        if (!target.isAlive() || com.simplecolonies.entity.AnimalProtection.protectedFromWorkers(target) || target.getAge() != 0 || target.isInLove() || !target.canFallInLove()) return;
        supplies.shrink(1);
        if (supplies.isEmpty()) worker().setWorkSupplies(ItemStack.EMPTY);
        target.setInLove(null);
        villager.getLookControl().setLookAt(target); villager.swing(InteractionHand.MAIN_HAND);
        // Approach the second parent promptly, while the first is still in love.
        state = JobState.WORKING; ticksUntilNextWork = 5;
    }

    private static boolean plainWheat(ItemStack stack) {
        return stack.is(Items.WHEAT) && ItemStack.isSameItemSameComponents(stack, Items.WHEAT.getDefaultInstance());
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
