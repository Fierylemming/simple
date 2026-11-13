package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;

/** Crafts modest tool reserves and operates a real designated furnace. */
public class BlacksmithJob extends AbstractJob {
    private static final String IRON = "minecraft:iron_ingot", COBBLE = "minecraft:cobblestone";
    public BlacksmithJob(Villager villager, Colony colony) { super(villager, colony, JobType.BLACKSMITH); }

    @Override protected void doWork(ServerLevel level) {
        if (!atClaimedWorkstation(level)) return;
        var stock = new ToolCrafting.Stock() {
            public int count(String id) { return colony.getWarehouseCount(level, id); }
            public boolean consume(String id, int amount) { return colony.consumeStock(level, id, amount); }
            public void add(String id, int amount) { depositToColony(level, id, amount); }
        };
        // One spare per active profession; tools already in workers' hands are
        // deliberately not counted so there is a replacement ready when one breaks.
        boolean miners = colony.getCitizens().stream().anyMatch(c -> !c.isChild && c.job == JobType.MINER);
        boolean woodcutters = colony.getCitizens().stream().anyMatch(c -> !c.isChild && c.job == JobType.LUMBERJACK);
        boolean defenders = colony.getCitizens().stream().anyMatch(c -> !c.isChild && c.job.isCombat);
        if (miners && noSpare(stock, "pickaxe") && makeTool(stock, "pickaxe")
                || woodcutters && noSpare(stock, "axe") && makeTool(stock, "axe")
                || defenders && noSpare(stock, "sword") && makeTool(stock, "sword")) {
            villager.swing(InteractionHand.MAIN_HAND);
            state = JobState.WORKING; ticksUntilNextWork = 100; return;
        }
        // Shepherds require physical shears. Craft one spare only when an
        // actual shared container has room; virtual-only output could never be
        // collected by the shepherd. Inputs still use the provisional stock network.
        boolean shepherds = colony.getCitizens().stream().anyMatch(c -> !c.isChild && c.job == JobType.SHEPHERD);
        var warehouse = colony.getWarehouseManager();
        if (shepherds && stock.count("minecraft:shears") == 0 && stock.count(IRON) >= 2
                && warehouse.courierStoragePositions(level).stream().anyMatch(p -> CourierJob.canInsert(
                        warehouse.courierContainer(level, p), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHEARS)))
                && stock.consume(IRON, 2)) {
            stock.add("minecraft:shears", 1);
            villager.swing(InteractionHand.MAIN_HAND);
            state = JobState.WORKING; ticksUntilNextWork = 100; return;
        }
        var site = villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
        var result = site.isEmpty() ? SmithFurnace.Result.UNAVAILABLE
                : SmithFurnace.work(level, colony, villager, site.get().pos());
        state = switch (result) {
            case MOVING -> JobState.GOING_TO_WORK;
            case WAITING, WORKED -> JobState.WORKING;
            case UNAVAILABLE -> JobState.NEEDS_RESOURCE;
        };
        if (result == SmithFurnace.Result.WORKED) villager.swing(InteractionHand.MAIN_HAND);
        ticksUntilNextWork = result == SmithFurnace.Result.MOVING ? 10 : 40;
    }
    private static boolean noSpare(ToolCrafting.Stock stock, String kind) {
        for (String tier : new String[]{"stone", "iron", "diamond", "netherite"})
            if (stock.count("minecraft:" + tier + "_" + kind) > 0) return false;
        // Woodcutters can use these too; miners cannot use them in the quarry.
        return !kind.equals("axe") || (stock.count("minecraft:wooden_axe") == 0 && stock.count("minecraft:golden_axe") == 0);
    }
    private static boolean makeTool(ToolCrafting.Stock stock, String kind) {
        if (ToolCrafting.make(stock, IRON, "minecraft:iron_" + kind)) return true;
        return ToolCrafting.make(stock, COBBLE, "minecraft:stone_" + kind);
    }
}
