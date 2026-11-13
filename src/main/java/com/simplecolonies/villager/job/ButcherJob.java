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

/** Butchers actual livestock near a designated butcher hut, leaving a breeding pair. */
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
                .filter(b -> b.getType() == BuildingType.BUTCHER_HUT && b.getState() == BuildingState.COMPLETE)
                .filter(b -> level.isLoaded(b.getPos()))
                .min(java.util.Comparator.comparingDouble(b -> b.getPos().distSqr(villager.blockPosition())))
                .orElse(null);
        if (hut == null) { state = JobState.STUCK; ticksUntilNextWork = 100; return; }
        BlockPos center = hut.getPos();
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

    /** Feed an eligible pair with real stock; vanilla animal AI creates the offspring. */
    private void breedLivestock(ServerLevel level, BlockPos center) {
        var all = level.getEntitiesOfClass(Animal.class, new AABB(center).inflate(8, 4, 8),
                a -> a.isAlive() && colony.isInside(a.blockPosition()) && (a instanceof Cow || a instanceof Pig));
        for (Class<? extends Animal> species : java.util.List.of(Cow.class, Pig.class)) {
            var herd = all.stream().filter(a -> a.getClass() == species).toList();
            if (herd.size() >= 4) continue; // Includes calves, piglets and named animals.
            var adults = herd.stream().filter(a -> !a.hasCustomName() && a.getAge() == 0).toList();
            var candidate = adults.stream().filter(a -> !a.isInLove() && a.canFallInLove())
                    .filter(a -> adults.stream().anyMatch(other -> other != a && (other.isInLove() || other.canFallInLove())))
                    .min(java.util.Comparator.comparingDouble(villager::distanceToSqr)).orElse(null);
            if (candidate == null) continue;
            boolean partnerWaiting = adults.stream().anyMatch(a -> a != candidate && a.isInLove());
            String[] foods = species == Cow.class ? new String[]{"minecraft:wheat"}
                    : new String[]{"minecraft:carrot", "minecraft:potato", "minecraft:beetroot"};
            String food = null;
            for (String item : foods) {
                // Preserve two items for other colony needs and enough for both parents.
                if (colony.getWarehouseCount(level, item) >= (partnerWaiting ? 3 : 4)) { food = item; break; }
            }
            if (food == null) continue;
            if (villager.distanceToSqr(candidate) > 6.25 || !villager.hasLineOfSight(candidate)) {
                var path = villager.getNavigation().createPath(candidate, 1);
                if (path == null || !path.canReach()) { state = JobState.STUCK; ticksUntilNextWork = 60; return; }
                villager.getNavigation().moveTo(path, 0.7);
                state = JobState.GOING_TO_WORK; ticksUntilNextWork = 20; return;
            }
            if (!candidate.isAlive() || candidate.getAge() != 0 || candidate.isInLove() || !candidate.canFallInLove()) continue;
            if (!colony.consumeStock(level, food, 1)) continue;
            candidate.setInLove(null);
            villager.getLookControl().setLookAt(candidate);
            villager.swing(InteractionHand.MAIN_HAND);
            state = JobState.WORKING; ticksUntilNextWork = 20; return;
        }
        state = JobState.NEEDS_RESOURCE; ticksUntilNextWork = 100;
    }
}
