package com.simplecolonies.villager.job;

import com.simplecolonies.building.BuildingState;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import com.simplecolonies.entity.ColonyVillager;
import net.minecraft.world.phys.AABB;

/** Works only in explicitly designated pastures, leaving a breeding pair. */
public class ButcherJob extends AbstractJob {
    private final PhysicalJobCargo cargo;
    public ButcherJob(ColonyVillager villager, Colony colony) {
        super(villager, colony, JobType.BUTCHER);
        this.cargo = new PhysicalJobCargo(villager, colony);
    }
    private ColonyVillager worker() { return (ColonyVillager) villager; }

    @Override protected void doWork(ServerLevel level) {
        // Finish a physical delivery even if the hut or workstation disappears.
        var delivery = cargo.deliver(level);
        if (delivery != PhysicalJobCargo.Delivery.NONE) {
            state = switch (delivery) {
                case MOVING -> JobState.GOING_TO_WORK;
                case DELIVERED -> JobState.WORKING;
                default -> JobState.WAITING_FOR_CHEST;
            };
            ticksUntilNextWork = delivery == PhysicalJobCargo.Delivery.MOVING ? 10
                    : delivery == PhysicalJobCargo.Delivery.DELIVERED ? 40 : 100;
            return;
        }
        var hut = colony.getBuildings().stream()
                .filter(b -> b.getType() == BuildingType.BUTCHER_HUT && b.isOperational())
                .filter(b -> level.isLoaded(b.getPos()))
                .min(java.util.Comparator.comparingDouble(b -> b.getPos().distSqr(villager.blockPosition())))
                .orElse(null);
        // A generated hut is not permission to slaughter nearby player animals.
        // Require a still-linked player-designated pasture near the hut; removing
        // or changing the marker revokes permission immediately on the next action.
        var pasture = hut == null ? null : colony.getBuildings().stream()
                .filter(b -> b.isPlayerPlaced && b.getType() == BuildingType.PASTURE
                        && b.getState() == BuildingState.COMPLETE && level.isLoaded(b.getPos())
                        && colony.isInside(b.getPos()) && hut.distanceToOperationalSite(b.getPos()) <= 20 * 20)
                .filter(b -> level.getBlockEntity(b.getPos()) instanceof com.simplecolonies.block.entity.BuildingMarkerBlockEntity marker
                        && marker.getBuildingType() == BuildingType.PASTURE && b.getPos().equals(marker.getLinkedBuildingPos()))
                .min(java.util.Comparator.comparingDouble(b -> b.getPos().distSqr(villager.blockPosition()))).orElse(null);
        if (pasture == null) {
            // Do not lose food already collected if the player revokes permission.
            if (!worker().getWorkSupplies().isEmpty()) {
                worker().setCourierCargo(worker().getWorkSupplies());
                worker().setWorkSupplies(net.minecraft.world.item.ItemStack.EMPTY);
                state = JobState.WORKING; ticksUntilNextWork = 10;
            } else { state = JobState.STUCK; ticksUntilNextWork = 100; }
            return;
        }
        BlockPos center = pasture.getPos();
        var animals = level.getEntitiesOfClass(Animal.class, new AABB(center).inflate(8, 4, 8),
                a -> a.isAlive() && colony.isInside(a.blockPosition()) && !a.isBaby() && !a.hasCustomName()
                        && (a instanceof Cow || a instanceof Pig));
        // Never slaughter the last two eligible adults of either species.
        Animal target = animals.stream().filter(a -> !a.isInLove())
                .filter(a -> animals.stream().filter(other -> other.getType() == a.getType()).count() > 2)
                .min(java.util.Comparator.comparingDouble(villager::distanceToSqr)).orElse(null);
        if (target == null) { breedLivestock(level, center); return; }
        if (villager.distanceToSqr(target) > 6.25 || !villager.hasLineOfSight(target)) {
            var path = villager.getNavigation().createPath(target, 1);
            if (path == null || !path.canReach()) { state = JobState.STUCK; ticksUntilNextWork = 60; return; }
            villager.getNavigation().moveTo(path, 0.7);
            state = JobState.GOING_TO_WORK; ticksUntilNextWork = 20; return;
        }
        villager.getLookControl().setLookAt(target);
        villager.swing(InteractionHand.MAIN_HAND);
        var drops = WorkerDrops.attack(villager, target, 4.0f);
        // Pick up only spawned plain livestock drops caused by this attack and
        // still in reach. Other drops, including unrelated or customized items,
        // remain in the world. Delivery requires a trip to shared storage.
        for (var drop : drops) {
            if (drop.isRemoved() || level.getEntity(drop.getUUID()) != drop || villager.distanceToSqr(drop) > 6.25) continue;
            var stack = drop.getItem();
            var item = stack.getItem();
            if (!(stack.is(net.minecraft.world.item.Items.BEEF) || stack.is(net.minecraft.world.item.Items.PORKCHOP)
                    || stack.is(net.minecraft.world.item.Items.COOKED_BEEF) || stack.is(net.minecraft.world.item.Items.COOKED_PORKCHOP)
                    || stack.is(net.minecraft.world.item.Items.LEATHER))
                    || !net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack, item.getDefaultInstance())) continue;
            if (stack.getCount() <= 0) continue;
            cargo.collect(drop);
        }
        state = JobState.WORKING;
        ticksUntilNextWork = 40;
    }

    /** Collect real plain food before feeding; vanilla animal AI makes offspring. */
    private void breedLivestock(ServerLevel level, BlockPos center) {
        var all = level.getEntitiesOfClass(Animal.class, new AABB(center).inflate(8, 4, 8),
                a -> a.isAlive() && colony.isInside(a.blockPosition()) && (a instanceof Cow || a instanceof Pig));
        var supplies = worker().getWorkSupplies();
        for (Class<? extends Animal> species : java.util.List.of(Cow.class, Pig.class)) {
            var herd = all.stream().filter(a -> a.getClass() == species).toList();
            if (herd.size() >= 4) continue;
            var adults = herd.stream().filter(a -> !a.hasCustomName() && a.getAge() == 0).toList();
            var candidate = adults.stream().filter(a -> !a.isInLove() && a.canFallInLove())
                    .filter(a -> adults.stream().anyMatch(other -> other != a && a.distanceToSqr(other) <= 36
                            && (other.isInLove() || other.canFallInLove())))
                    .min(java.util.Comparator.comparingDouble(villager::distanceToSqr)).orElse(null);
            if (candidate == null) continue;
            boolean partnerWaiting = adults.stream().anyMatch(a -> a != candidate && a.distanceToSqr(candidate) <= 36 && a.isInLove());
            var foods = species == Cow.class ? java.util.List.of(net.minecraft.world.item.Items.WHEAT)
                    : java.util.List.of(net.minecraft.world.item.Items.CARROT, net.minecraft.world.item.Items.POTATO, net.minecraft.world.item.Items.BEETROOT);
            if (!supplies.isEmpty() && (!foods.contains(supplies.getItem()) || !plain(supplies))) continue;
            int carried = supplies.isEmpty() ? 0 : supplies.getCount();
            int needed = partnerWaiting ? 1 : 2;
            if (carried < needed) {
                // Select one food type, and recheck the two-item reserve before
                // every removal to handle other workers or players changing stock.
                var storage = colony.getWarehouseManager().courierStoragePositions(level);
                net.minecraft.world.item.Item food = supplies.isEmpty() ? null : supplies.getItem();
                if (food == null) for (var item : foods) {
                    if (countFood(level, storage, item) >= needed - carried + 2) { food = item; break; }
                }
                if (food == null || countFood(level, storage, food) < needed - carried + 2) continue;
                if (collectFood(level, storage, food, needed - carried)) return;
                continue;
            }
            if (villager.distanceToSqr(candidate) > 6.25 || !villager.hasLineOfSight(candidate)) {
                var path = villager.getNavigation().createPath(candidate, 1);
                if (path == null || !path.canReach()) { state = JobState.STUCK; ticksUntilNextWork = 60; return; }
                villager.getNavigation().moveTo(path, 0.7);
                state = JobState.GOING_TO_WORK; ticksUntilNextWork = 20; return;
            }
            if (!partnerWaiting) {
                // Do not start the short love timer until both parents can be
                // reached promptly from the same position. This avoids wasting
                // the first item on a pair separated by a fence or long walk.
                var partner = adults.stream().filter(a -> a != candidate && a.canFallInLove()
                                && a.distanceToSqr(candidate) <= 36)
                        .min(java.util.Comparator.comparingDouble(villager::distanceToSqr)).orElse(null);
                if (partner == null) continue;
                if (villager.distanceToSqr(partner) > 6.25 || !villager.hasLineOfSight(partner)) {
                    var path = villager.getNavigation().createPath(partner, 1);
                    if (path == null || !path.canReach()) { state = JobState.STUCK; ticksUntilNextWork = 60; return; }
                    villager.getNavigation().moveTo(path, .7); state = JobState.GOING_TO_WORK; ticksUntilNextWork = 10; return;
                }
            }
            if (!candidate.isAlive() || candidate.hasCustomName() || candidate.getAge() != 0
                    || candidate.isInLove() || !candidate.canFallInLove()) continue;
            supplies.shrink(1);
            if (supplies.isEmpty()) worker().setWorkSupplies(net.minecraft.world.item.ItemStack.EMPTY);
            candidate.setInLove(null);
            villager.getLookControl().setLookAt(candidate);
            villager.swing(InteractionHand.MAIN_HAND);
            state = JobState.WORKING; ticksUntilNextWork = 5; return;
        }
        // If there is no longer a suitable pair, return unused carried food to
        // shared storage rather than getting stuck on a different species.
        if (!worker().getWorkSupplies().isEmpty()) {
            // A recently fed animal may have wandered out of range. Keep the
            // second item while its love timer is active instead of starting a
            // long storage trip and abandoning the pair.
            var carriedFood = worker().getWorkSupplies();
            boolean waiting = all.stream().anyMatch(a -> !a.hasCustomName() && a.getAge() == 0 && a.isInLove()
                    && ((a instanceof Cow && carriedFood.is(net.minecraft.world.item.Items.WHEAT))
                        || (a instanceof Pig && (carriedFood.is(net.minecraft.world.item.Items.CARROT)
                            || carriedFood.is(net.minecraft.world.item.Items.POTATO) || carriedFood.is(net.minecraft.world.item.Items.BEETROOT)))));
            if (waiting) { state = JobState.WORKING; ticksUntilNextWork = 10; return; }
            worker().setCourierCargo(worker().getWorkSupplies());
            worker().setWorkSupplies(net.minecraft.world.item.ItemStack.EMPTY);
            state = JobState.WORKING; ticksUntilNextWork = 10; return;
        }
        state = JobState.NEEDS_RESOURCE; ticksUntilNextWork = 100;
    }

    private static boolean plain(net.minecraft.world.item.ItemStack stack) {
        return !stack.isEmpty() && net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack, stack.getItem().getDefaultInstance());
    }

    private int countFood(ServerLevel level, java.util.List<BlockPos> storage, net.minecraft.world.item.Item item) {
        int count = 0;
        for (var pos : storage) {
            var c = colony.getWarehouseManager().courierContainer(level, pos);
            if (c == null) continue;
            for (int i = 0; i < c.getContainerSize(); i++)
                if (c.getItem(i).is(item) && plain(c.getItem(i))) count += c.getItem(i).getCount();
        }
        return count;
    }

    private boolean collectFood(ServerLevel level, java.util.List<BlockPos> storage, net.minecraft.world.item.Item food, int missing) {
        var warehouse = colony.getWarehouseManager();
        var positions = storage.stream().filter(p -> {
            var c = warehouse.courierContainer(level, p);
            if (c == null) return false;
            for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(food) && plain(c.getItem(i))) return true;
            return false;
        }).sorted(java.util.Comparator.comparingDouble(p -> p.distSqr(villager.blockPosition()))).toList();
        for (var pos : positions) {
            if (villager.distanceToSqr(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5) > 9) {
                var path = villager.getNavigation().createPath(pos, 1);
                if (path == null || !path.canReach()) continue;
                villager.getNavigation().moveTo(path, .7);
                state = JobState.GOING_TO_WORK; ticksUntilNextWork = 10; return true;
            }
            var c = warehouse.courierContainer(level, pos);
            int allowed = Math.min(missing, Math.max(0, countFood(level, storage, food) - 2));
            if (c == null || allowed <= 0) return false;
            for (int i = 0; i < c.getContainerSize(); i++) {
                if (!c.getItem(i).is(food) || !plain(c.getItem(i))) continue;
                var taken = c.removeItem(i, allowed);
                if (taken.isEmpty()) continue;
                c.setChanged();
                if (worker().getWorkSupplies().isEmpty()) worker().setWorkSupplies(taken);
                else worker().getWorkSupplies().grow(taken.getCount());
                villager.swing(InteractionHand.MAIN_HAND);
                state = JobState.WORKING; ticksUntilNextWork = 10; return true;
            }
        }
        return false;
    }
}
