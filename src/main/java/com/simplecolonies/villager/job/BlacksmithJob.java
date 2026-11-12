package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;

/** Supplies modest reserves of real tools; existing smelting remains provisional. */
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
        if (miners && noSpare(stock, "pickaxe") && makeTool(stock, "pickaxe")
                || woodcutters && noSpare(stock, "axe") && makeTool(stock, "axe")) {
            villager.swing(InteractionHand.MAIN_HAND);
            state = JobState.WORKING; ticksUntilNextWork = 100; return;
        }
        int iron = colony.getWarehouseCount(level, "minecraft:raw_iron");
        int coal = colony.getWarehouseCount(level, "minecraft:coal");
        if (iron >= 3 && coal >= 1 && colony.getWarehouseCount(level, IRON) < 12
                && colony.consumeStock(level, "minecraft:raw_iron", 3)) {
            if (colony.consumeStock(level, "minecraft:coal", 1)) {
                depositToColony(level, IRON, 3);
                villager.swing(InteractionHand.MAIN_HAND);
                state = JobState.WORKING;
            } else {
                colony.depositToWarehouse("minecraft:raw_iron", 3);
                state = JobState.NEEDS_RESOURCE;
            }
        } else state = JobState.NEEDS_RESOURCE;
        ticksUntilNextWork = 180;
    }
    private static boolean noSpare(ToolCrafting.Stock stock, String kind) {
        for (String tier : new String[]{"stone", "iron", "diamond", "netherite"})
            if (stock.count("minecraft:" + tier + "_" + kind) > 0) return false;
        return true;
    }
    private static boolean makeTool(ToolCrafting.Stock stock, String kind) {
        if (ToolCrafting.make(stock, IRON, "minecraft:iron_" + kind)) return true;
        return ToolCrafting.make(stock, COBBLE, "minecraft:stone_" + kind);
    }
}
