package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.warehouse.WarehouseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * Base job AI. All villagers now deposit to nearest chest in village radius via WarehouseManager,
 * and pull from chests + Warehouse hub. This answers "villagers should just interact with chests round the village".
 *
 * Vanilla AI note: vanilla Minecraft villagers have a schedule (work/bed) but do not produce items — they just
 * pathfind to job site POI and play animations. ColonyVillager completely replaces that with real logic:
 * Farmer actually plants/harvests, Lumberjack chops/plants, Miner mines, Builder places blocks, etc.
 * See ColonyVillager.tick() -> jobInstance.tick().
 */
public abstract class AbstractJob {
    protected final Villager villager;
    protected final Colony colony;
    protected final JobType type;
    protected BlockPos workplacePos;
    private BlockPos claimedSite;
    protected int ticksUntilNextWork = 0;
    protected JobState state = JobState.IDLE;

    public AbstractJob(Villager villager, Colony colony, JobType type) {
        this.villager = villager;
        this.colony = colony;
        this.type = type;
    }

    public JobType getType() { return type; }
    public JobState getState() { return state; }

    public final void tick(ServerLevel level) {
        onTick(level);
        if (ticksUntilNextWork > 0) { ticksUntilNextWork--; return; }
        if (!colony.isInside(villager.blockPosition())) {
            state = JobState.GOING_TO_WORK;
            // Path to colony center — courier is faster
            double speed = (type == JobType.COURIER) ? 1.2 : 0.6;
            villager.getNavigation().moveTo(colony.getCenter().getX()+0.5, colony.getCenter().getY(), colony.getCenter().getZ()+0.5, speed);
            ticksUntilNextWork = 40;
            return;
        }
        // Ensure workplace is set — for player-designated buildings via Building Marker, workplacePos will be that marker pos
        var remembered = villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
        BlockPos currentSite = remembered.filter(p -> p.dimension().equals(level.dimension())).map(p -> p.pos()).orElse(null);
        if (!java.util.Objects.equals(currentSite, claimedSite) || workplacePos == null) {
            claimedSite = currentSite;
            workplacePos = findWorkplace(level);
        }
        ticksUntilNextWork = getWorkInterval();
        doWork(level);
    }

    protected BlockPos findWorkplace(ServerLevel level) {
        if (claimedSite == null) return colony.getCenter();
        // A farm's registered center can be several blocks from its composter.
        // Prefer the nearest matching completed building to this villager's own POI.
        return colony.getBuildings().stream()
                .filter(b -> b.getType().job == type && b.getState() == com.simplecolonies.building.BuildingState.COMPLETE)
                .filter(b -> b.getPos().distSqr(claimedSite) <= 144)
                .min(java.util.Comparator.comparingDouble(b -> b.getPos().distSqr(claimedSite)))
                .map(com.simplecolonies.building.Building::getPos).orElse(claimedSite);
    }

    /** Move to the villager's claimed POI before workstation-based production. */
    protected boolean atClaimedWorkstation(ServerLevel level) {
        var site = villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
        if (site.isEmpty() || !site.get().dimension().equals(level.dimension()) || !level.isLoaded(site.get().pos())) {
            state = JobState.STUCK;
            ticksUntilNextWork = 40;
            return false;
        }
        BlockPos target = site.get().pos();
        if (villager.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) <= 9) return true;
        var path = villager.getNavigation().createPath(target, 1);
        if (path == null || !path.canReach()) {
            state = JobState.STUCK;
            ticksUntilNextWork = 60;
            return false;
        }
        villager.getNavigation().moveTo(path, 0.7);
        state = JobState.GOING_TO_WORK;
        ticksUntilNextWork = 20;
        return false;
    }

    /** Called even between work actions, for behaviours that must not be deferred. */
    protected void onTick(ServerLevel level) {}

    protected abstract void doWork(ServerLevel level);

    protected int getWorkInterval() {
        int base = 20;
        double mult = colony.getResearch().getWorkSpeedMultiplier();
        // Faster speed = lower interval
        return Math.max(5, (int)(base / mult));
    }

    // --- Warehouse / chest helpers ---

    protected void depositToColony(ServerLevel level, String itemId, int count) {
        WarehouseManager wm = colony.getWarehouseManager();
        if (level != null && wm.getChestCount() > 0) {
            int deposited = wm.deposit(level, villager.blockPosition(), itemId, count);
            if (deposited < count) {
                // Remainder goes to abstract stockpile (overflow if chests full)
                colony.depositToWarehouse(level, itemId, count - deposited);
            }
        } else {
            colony.depositToWarehouse(itemId, count);
        }
    }

    // Legacy overload for jobs that haven't been updated to pass level (fallback)
    protected void depositToColony(String itemId, int count) {
        colony.depositToWarehouse(itemId, count);
    }

    protected boolean requestFromColony(ServerLevel level, String itemId, int count) {
        return colony.getWarehouseCount(level, itemId) >= count;
    }

    // Legacy overload
    protected boolean requestFromColony(String itemId, int count) {
        return requestFromColony(null, itemId, count);
    }

    protected WarehouseManager getWarehouse() { return colony.getWarehouseManager(); }

    public enum JobState { IDLE, GOING_TO_WORK, WORKING, STUCK, NEEDS_TOOL, NEEDS_RESOURCE, WAITING_FOR_CHEST }
}
