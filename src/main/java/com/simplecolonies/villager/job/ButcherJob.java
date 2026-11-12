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
        if (target == null) { state = JobState.NEEDS_RESOURCE; ticksUntilNextWork = 100; return; }
        if (villager.distanceToSqr(target) > 6.25) {
            var path = villager.getNavigation().createPath(target, 1);
            if (path == null || !path.canReach()) { state = JobState.STUCK; ticksUntilNextWork = 60; return; }
            villager.getNavigation().moveTo(path, 0.7);
            state = JobState.GOING_TO_WORK; ticksUntilNextWork = 20; return;
        }
        villager.getLookControl().setLookAt(target);
        villager.swing(InteractionHand.MAIN_HAND);
        target.hurt(level.damageSources().mobAttack(villager), 4.0f);
        // Minecraft produces the actual loot on death. Automatic collection is not implemented yet.
        state = JobState.WORKING;
        ticksUntilNextWork = 40;
    }
}
