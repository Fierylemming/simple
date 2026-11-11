package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

/**
 * Base job AI. Each villager ticks its job once per second if inside colony radius.
 * Jobs are deliberately simple state machines to stay performant vs MineColonies' complexity.
 */
public abstract class AbstractJob {
    protected final Villager villager;
    protected final Colony colony;
    protected final JobType type;
    protected BlockPos workplacePos;
    protected int ticksUntilNextWork = 0;
    protected JobState state = JobState.IDLE;

    public AbstractJob(Villager villager, Colony colony, JobType type) {
        this.villager = villager;
        this.colony = colony;
        this.type = type;
    }

    public JobType getType() { return type; }
    public JobState getState() { return state; }

    /** Called every tick while villager is loaded */
    public final void tick(ServerLevel level) {
        if (ticksUntilNextWork > 0) { ticksUntilNextWork--; return; }
        if (!colony.isInside(villager.blockPosition())) {
            state = JobState.GOING_TO_WORK;
            // Pathfind to colony center
            return;
        }
        doWork(level);
        ticksUntilNextWork = getWorkInterval();
    }

    protected abstract void doWork(ServerLevel level);

    protected int getWorkInterval() { return 20; } // default 1 sec

    protected void depositToColony(String itemId, int count) {
        colony.depositToWarehouse(itemId, count);
    }

    protected boolean requestFromColony(String itemId, int count) {
        int have = colony.getWarehouseCount(itemId);
        if (have >= count) {
            // Would decrement warehouse; simplified here - real would use colony.tryTake
            return true;
        }
        // Post request to supply chain board
        colony.broadcast(net.minecraft.network.chat.Component.literal(
                type.displayName + " at " + workplacePos + " needs " + count + "x " + itemId));
        return false;
    }

    public enum JobState { IDLE, GOING_TO_WORK, WORKING, STUCK, NEEDS_TOOL, NEEDS_RESOURCE }
}
