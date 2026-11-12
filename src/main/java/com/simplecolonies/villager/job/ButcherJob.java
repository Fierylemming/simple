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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

/** Butchers actual livestock near a designated butcher hut, leaving a breeding pair. */
public class ButcherJob extends AbstractJob {
    public ButcherJob(Villager villager, Colony colony) { super(villager, colony, JobType.BUTCHER); }

    @Override protected void doWork(ServerLevel level) {
        var hut = colony.getBuildings().stream()
                .filter(b -> b.getType() == BuildingType.BUTCHER_HUT && b.getState() == BuildingState.COMPLETE)
                .filter(b -> level.isLoaded(b.getPos()))
                .min(java.util.Comparator.comparingDouble(b -> b.getPos().distSqr(villager.blockPosition())))
                .orElse(null);
        if (hut == null) { state = JobState.STUCK; ticksUntilNextWork = 100; return; }
        BlockPos center = hut.getPos();
        var animals = level.getEntitiesOfClass(Animal.class, new AABB(center).inflate(8, 4, 8),
                a -> a.isAlive() && !a.isBaby() && !a.hasCustomName() && (a instanceof Cow || a instanceof Pig));
        // Never slaughter the last two eligible adults of either species.
        Animal target = animals.stream().filter(a -> animals.stream().filter(other -> other.getType() == a.getType()).count() > 2)
                .min(java.util.Comparator.comparingDouble(villager::distanceToSqr)).orElse(null);
        if (target == null) { breedLivestock(level, center); return; }
        if (villager.distanceToSqr(target) > 6.25) {
            var path = villager.getNavigation().createPath(target, 1);
            if (path == null || !path.canReach()) { state = JobState.STUCK; ticksUntilNextWork = 60; return; }
            villager.getNavigation().moveTo(path, 0.7);
            state = JobState.GOING_TO_WORK; ticksUntilNextWork = 20; return;
        }
        villager.getLookControl().setLookAt(target);
        villager.swing(InteractionHand.MAIN_HAND);
        var drops = ButcherDrops.attack(villager, target, 4.0f);
        // Transfer only actual, unmodified vanilla livestock drops from this attack,
        // only after the server has spawned them and while they are within reach.
        // Existing nearby items, cancelled drops and component-bearing modded loot
        // are left alone. Other professions still use the provisional stock network.
        for (var drop : drops) {
            if (drop.isRemoved() || level.getEntity(drop.getUUID()) != drop || villager.distanceToSqr(drop) > 6.25) continue;
            var stack = drop.getItem();
            var item = stack.getItem();
            if (!(stack.is(net.minecraft.world.item.Items.BEEF) || stack.is(net.minecraft.world.item.Items.PORKCHOP)
                    || stack.is(net.minecraft.world.item.Items.COOKED_BEEF) || stack.is(net.minecraft.world.item.Items.COOKED_PORKCHOP)
                    || stack.is(net.minecraft.world.item.Items.LEATHER))
                    || !net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack, item.getDefaultInstance())) continue;
            int count = stack.getCount();
            if (count <= 0) continue;
            depositToColony(level, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString(), count);
            drop.discard();
        }
        state = JobState.WORKING;
        ticksUntilNextWork = 40;
    }

    /** Feed an eligible pair with real stock; vanilla animal AI creates the offspring. */
    private void breedLivestock(ServerLevel level, BlockPos center) {
        var all = level.getEntitiesOfClass(Animal.class, new AABB(center).inflate(8, 4, 8),
                a -> a.isAlive() && (a instanceof Cow || a instanceof Pig));
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
            if (villager.distanceToSqr(candidate) > 6.25) {
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
