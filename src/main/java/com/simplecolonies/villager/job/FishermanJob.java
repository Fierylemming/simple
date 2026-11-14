package com.simplecolonies.villager.job;

import com.simplecolonies.building.BuildingState;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.AbstractFish;
import com.simplecolonies.entity.ColonyVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Preliminary shore gathering: takes actual excess wild fish, not simulated rod loot. */
public class FishermanJob extends AbstractJob {
    private static final int MIN_SCHOOL = 2;
    private static final double REACH_SQ = 6.25;
    private net.minecraft.world.level.pathfinder.Path jobPath;
    private String lastSearch = "not searched";
    public String diagnostic() { return lastSearch; }
    private final PhysicalJobCargo cargo;
    public FishermanJob(ColonyVillager villager, Colony colony) {
        super(villager, colony, JobType.FISHERMAN);
        cargo = new PhysicalJobCargo(villager, colony);
    }

    private boolean inDanger() {
        var brain = villager.getBrain();
        return brain.isActive(net.minecraft.world.entity.schedule.Activity.PANIC)
                || brain.hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.IS_PANICKING)
                || brain.hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_HOSTILE)
                || brain.hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.HURT_BY);
    }

    @Override protected void onTick(ServerLevel level) {
        var brain = villager.getBrain();
        var pathMemory = net.minecraft.world.entity.ai.memory.MemoryModuleType.PATH;
        boolean panic = inDanger();
        if (jobPath != null && !panic && state == JobState.GOING_TO_WORK
                && villager.getNavigation().getPath() == jobPath && !jobPath.isDone()) {
            // Villager door AI consults PATH memory, not PathNavigation directly.
            // Exposing the actual job path lets vanilla open and close doors.
            brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
            brain.setMemory(pathMemory, jobPath);
        } else if (jobPath != null) {
            if (brain.getMemory(pathMemory).orElse(null) == jobPath) brain.eraseMemory(pathMemory);
            jobPath = null;
        }
    }

    private void clearJobPath() {
        if (jobPath == null) return;
        if (villager.getNavigation().getPath() == jobPath) villager.getNavigation().stop();
        var memory = net.minecraft.world.entity.ai.memory.MemoryModuleType.PATH;
        if (villager.getBrain().getMemory(memory).orElse(null) == jobPath) villager.getBrain().eraseMemory(memory);
        jobPath = null;
    }

    private void followJobPath(net.minecraft.world.level.pathfinder.Path path) {
        if (villager.getNavigation().moveTo(path, 0.7)) {
            jobPath = path;
            villager.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.PATH, path);
        }
    }

    @Override protected void doWork(ServerLevel level) {
        if (inDanger()) {
            lastSearch = "panicking";
            state = JobState.IDLE; ticksUntilNextWork = 20; return;
        }
        // Deliver a real catch even if the harbor or claimed barrel disappeared.
        var delivery = cargo.deliver(level);
        if (delivery != PhysicalJobCargo.Delivery.NONE) {
            if (delivery == PhysicalJobCargo.Delivery.MOVING) {
                jobPath = villager.getNavigation().getPath();
                if (jobPath != null) villager.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.PATH, jobPath);
                state = JobState.GOING_TO_WORK; ticksUntilNextWork = 10;
            } else {
                clearJobPath();
                state = delivery == PhysicalJobCargo.Delivery.DELIVERED ? JobState.WORKING : JobState.WAITING_FOR_CHEST;
                ticksUntilNextWork = delivery == PhysicalJobCargo.Delivery.DELIVERED ? 40 : 100;
            }
            return;
        }
        var site = villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
        if (site.isEmpty() || !site.get().dimension().equals(level.dimension()) || !level.isLoaded(site.get().pos())
                || !colony.isInside(site.get().pos()) || !level.getBlockState(site.get().pos()).is(Blocks.BARREL)) {
            lastSearch = "invalid or missing job site: " + site;
            clearJobPath(); state = JobState.STUCK; ticksUntilNextWork = 100; return;
        }
        var harbor = colony.getBuildings().stream()
                .filter(b -> b.getType() == BuildingType.HARBOR && b.isOperational())
                .filter(b -> level.isLoaded(b.getPos()) && b.getPos().distSqr(site.get().pos()) <= 144)
                .min(Comparator.comparingDouble(b -> b.getPos().distSqr(site.get().pos()))).orElse(null);
        if (harbor == null) { lastSearch = "no loaded completed harbor near " + site.get().pos().toShortString(); clearJobPath(); state = JobState.STUCK; ticksUntilNextWork = 100; return; }
        if (colony.getWarehouseCount(level, "minecraft:cod") + colony.getWarehouseCount(level, "minecraft:salmon") >= 8) {
            lastSearch = "fish reserve full";
            clearJobPath(); state = JobState.IDLE; ticksUntilNextWork = 200; return;
        }
        var fish = level.getEntitiesOfClass(AbstractFish.class, new AABB(harbor.getPos()).inflate(10, 4, 10),
                f -> f.isAlive() && f.isInWater() && colony.isInside(f.blockPosition())
                        && (f.getType() == EntityType.COD || f.getType() == EntityType.SALMON)
                        && !f.hasCustomName() && !f.fromBucket() && !f.isPersistenceRequired() && !f.requiresCustomPersistence());
        var candidates = fish.stream().filter(f -> fish.stream().filter(other -> other.getType() == f.getType()).count() > MIN_SCHOOL)
                .sorted(Comparator.comparingDouble(villager::distanceToSqr)).limit(4).toList();
        // PathNavigation.createPath mutates the active target. Replanning every
        // work interval can interrupt a valid route before the worker reaches
        // the door or shore. Let an owned dry path finish unless a fish is
        // already in reach, the school disappears, or the route becomes unsafe.
        if (jobPath != null && villager.getNavigation().getPath() == jobPath && !jobPath.isDone()) {
            boolean catchable = candidates.stream().anyMatch(f -> villager.distanceToSqr(f) <= REACH_SQ
                    && !villager.isInWater() && villager.hasLineOfSight(f));
            if (!candidates.isEmpty() && !catchable && dryPath(level, jobPath)) {
                state = JobState.GOING_TO_WORK; ticksUntilNextWork = 10; return;
            }
            clearJobPath();
        }
        // GroundPathNavigation refuses all path requests while airborne. Retry
        // next tick, rather than synchronizing retries with repeated jumps.
        if (!candidates.isEmpty() && !villager.onGround() && !villager.isInLiquid() && !villager.isPassenger()) {
            state = JobState.GOING_TO_WORK; ticksUntilNextWork = 0; return;
        }
        net.minecraft.world.level.pathfinder.Path partial = null;
        int standCount = 0, nullPaths = 0, wetPaths = 0, partialPaths = 0, reachablePaths = 0;
        double bestProgress = 0;
        for (var target : candidates) {
            if (villager.distanceToSqr(target) <= REACH_SQ && !villager.isInWater()
                    && villager.hasLineOfSight(target)) {
                villager.getLookControl().setLookAt(target);
                villager.swing(InteractionHand.MAIN_HAND);
                var drops = WorkerDrops.attack(villager, target, 4.0f);
                for (var drop : drops) {
                    if (drop.isRemoved() || level.getEntity(drop.getUUID()) != drop || villager.distanceToSqr(drop) > REACH_SQ) continue;
                    ItemStack stack = drop.getItem();
                    if (!(stack.is(Items.COD) || stack.is(Items.SALMON)) || stack.isEmpty()
                            || !ItemStack.isSameItemSameComponents(stack, stack.getItem().getDefaultInstance())) continue;
                    cargo.collect(drop);
                }
                if (cargo.hasCargo()) clearJobPath();
                state = JobState.WORKING; ticksUntilNextWork = 100; return;
            }
            // Navigate to a dry, solid shore close enough to reach this fish. Never
            // path into water or attack through an opaque block.
            var stands = new java.util.ArrayList<BlockPos>();
            BlockPos center = target.blockPosition();
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = -1; y <= 1; y++) {
                BlockPos p = center.offset(x, y, z);
                if (!colony.isInside(p) || !level.isLoaded(p) || !level.isLoaded(p.above()) || !level.isLoaded(p.below())) continue;
                if (p.distToCenterSqr(target.position()) > 4 || !level.getBlockState(p).isAir()
                        || !level.getBlockState(p.above()).isAir() || !level.getFluidState(p).isEmpty()
                        || !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)
                        || level.getBlockState(p.below()).is(Blocks.MAGMA_BLOCK)) continue;
                stands.add(p);
            }
            standCount += stands.size();
            stands.sort(Comparator.comparingDouble(p -> p.distSqr(villager.blockPosition())));
            int attempts = 0;
            for (BlockPos stand : stands) {
                if (stand.equals(villager.blockPosition())) continue;
                if (++attempts > 8) break;
                // Ask the pathfinder for a dry route, not merely reject its
                // preferred shortcut through water when dry land goes around it.
                float oldWaterMalus = villager.getPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER);
                net.minecraft.world.level.pathfinder.Path path;
                villager.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, -1.0f);
                try {
                    path = villager.getNavigation().createPath(stand, 0);
                } finally {
                    villager.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, oldWaterMalus);
                }
                if (path == null) { nullPaths++; continue; }
                if (!dryPath(level, path)) { wetPaths++; continue; }
                if (path.canReach()) {
                    reachablePaths++;
                    lastSearch = "stands=" + standCount + " null=" + nullPaths + " wet=" + wetPaths + " partial=" + partialPaths + " reachable=" + reachablePaths;
                    followJobPath(path);
                    state = JobState.GOING_TO_WORK; ticksUntilNextWork = 20; return;
                }
                // Villager pathfinding has a finite range. A safe partial path
                // can approach a distant shore without loading extra chunks.
                partialPaths++;
                var end = path.getEndNode();
                if (end != null && colony.isInside(end.asBlockPos())
                        && villager.blockPosition().distSqr(end.asBlockPos()) >= 4) {
                    double progress = stand.distSqr(villager.blockPosition()) - stand.distSqr(end.asBlockPos());
                    if (progress > bestProgress + 4) { bestProgress = progress; partial = path; }
                }
            }
        }
        lastSearch = "candidates=" + candidates.size() + " stands=" + standCount + " null=" + nullPaths
                + " wet=" + wetPaths + " partial=" + partialPaths + " reachable=" + reachablePaths + " progress=" + bestProgress;
        if (partial != null) {
            followJobPath(partial);
            state = JobState.GOING_TO_WORK; ticksUntilNextWork = 20; return;
        }
        state = candidates.isEmpty() ? JobState.NEEDS_RESOURCE : JobState.STUCK;
        // Retry unreachable candidates sooner than an empty fishing area.
        ticksUntilNextWork = candidates.isEmpty() ? 100 : 20;
    }

    private static boolean dryPath(ServerLevel level, net.minecraft.world.level.pathfinder.Path path) {
        for (int i = 0; i < path.getNodeCount(); i++) {
            BlockPos pos = path.getNode(i).asBlockPos();
            if (!level.isLoaded(pos) || !level.getFluidState(pos).isEmpty()) return false;
        }
        return true;
    }
}
