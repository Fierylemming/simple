package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;

/** Crafts modest tool reserves and operates a real designated furnace. */
public class BlacksmithJob extends AbstractJob {
    private static final String IRON = "minecraft:iron_ingot", COBBLE = "minecraft:cobblestone";
    private final PhysicalBuilderMaterials materials;
    private final PhysicalJobCargo cargo;
    private final PhysicalSmithFurnace furnace;
    public BlacksmithJob(Villager villager, Colony colony) {
        super(villager, colony, JobType.BLACKSMITH);
        var worker = villager instanceof com.simplecolonies.entity.ColonyVillager cv ? cv : null;
        materials = worker == null ? null : new PhysicalBuilderMaterials(worker, colony);
        cargo = worker == null ? null : new PhysicalJobCargo(worker, colony);
        furnace = worker == null ? null : new PhysicalSmithFurnace(worker, colony, materials, cargo);
    }

    @Override protected void doWork(ServerLevel level) {
        if (materials != null) { physicalTools(level); return; }
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
    private void physicalTools(ServerLevel level) {
        var delivery = cargo.deliver(level);
        if (delivery != PhysicalJobCargo.Delivery.NONE) {
            state = delivery == PhysicalJobCargo.Delivery.MOVING ? JobState.GOING_TO_WORK
                    : delivery == PhysicalJobCargo.Delivery.DELIVERED ? JobState.WORKING : JobState.WAITING_FOR_CHEST;
            ticksUntilNextWork = delivery == PhysicalJobCargo.Delivery.MOVING ? 10 : 40; return;
        }
        // Finish carrying completed tools before checking the workstation: a
        // broken workstation must not erase output already made.
        for (String item : new String[]{"stone_pickaxe", "iron_pickaxe", "stone_axe", "iron_axe", "stone_sword", "iron_sword", "shears"}) {
            if (materials.countPrepared("minecraft:" + item) > 0) { returnMaterials(level); return; }
        }
        var memory = villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
        if (memory.isEmpty() || !memory.get().dimension().equals(level.dimension()) || !level.isLoaded(memory.get().pos())
                || !colony.isInside(memory.get().pos()) || !level.getBlockState(memory.get().pos()).is(net.minecraft.world.level.block.Blocks.SMITHING_TABLE)) {
            state = JobState.STUCK; ticksUntilNextWork = 100; return;
        }
        if (furnace.hasInputs()) { runPhysicalFurnace(level, memory.get().pos()); return; }
        var warehouse = colony.getWarehouseManager();
        ToolCrafting.Stock shared = new ToolCrafting.Stock() {
            public int count(String id) {
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.ResourceLocation.parse(id));
                if (item.isEmpty()) return 0;
                int count = 0;
                for (var pos : warehouse.courierStoragePositions(level)) {
                    var c = warehouse.courierContainer(level, pos);
                    for (int i = 0; c != null && i < c.getContainerSize(); i++)
                        if (c.getItem(i).is(item.get())) count += c.getItem(i).getCount();
                }
                return count;
            }
            public boolean consume(String id, int n) { throw new UnsupportedOperationException(); }
            public void add(String id, int n) { throw new UnsupportedOperationException(); }
        };
        var kinds = new java.util.ArrayList<String>();
        if (colony.getCitizens().stream().anyMatch(c -> !c.isChild && c.job == JobType.MINER) && noSpare(shared, "pickaxe")) kinds.add("pickaxe");
        if (colony.getCitizens().stream().anyMatch(c -> !c.isChild && c.job == JobType.LUMBERJACK) && noSpare(shared, "axe")) kinds.add("axe");
        if (colony.getCitizens().stream().anyMatch(c -> !c.isChild && c.job.isCombat) && noSpare(shared, "sword")) kinds.add("sword");
        String wanted = null;
        for (String kind : kinds) {
            if (materials.canPrepare(level, "minecraft:iron_" + kind)) { wanted = "minecraft:iron_" + kind; break; }
            if (materials.canPrepare(level, "minecraft:stone_" + kind)) { wanted = "minecraft:stone_" + kind; break; }
        }
        if (wanted == null && colony.getCitizens().stream().anyMatch(c -> !c.isChild && c.job == JobType.SHEPHERD)
                && shared.count("minecraft:shears") == 0 && materials.canPrepare(level, "minecraft:shears")) wanted = "minecraft:shears";
        if (wanted != null) {
            var result = materials.prepare(level, wanted, memory.get().pos());
            state = result == PhysicalBuilderMaterials.Result.MOVING ? JobState.GOING_TO_WORK
                    : result == PhysicalBuilderMaterials.Result.WORKING || result == PhysicalBuilderMaterials.Result.READY ? JobState.WORKING
                    : result == PhysicalBuilderMaterials.Result.MISSING ? JobState.NEEDS_RESOURCE : JobState.STUCK;
            ticksUntilNextWork = result == PhysicalBuilderMaterials.Result.MOVING ? 10 : 40; return;
        }
        var returned = materials.returnSurplus(level);
        if (returned != PhysicalBuilderMaterials.Result.READY) { reportReturn(returned); return; }
        runPhysicalFurnace(level, memory.get().pos());
    }

    private void runPhysicalFurnace(ServerLevel level, net.minecraft.core.BlockPos station) {
        var result = furnace.work(level, station);
        if (result == PhysicalSmithFurnace.Result.RETURN_INPUTS) { returnMaterials(level); return; }
        state = switch (result) {
            case MOVING -> JobState.GOING_TO_WORK;
            case WAITING, WORKED -> JobState.WORKING;
            default -> JobState.NEEDS_RESOURCE;
        };
        if (result == PhysicalSmithFurnace.Result.WORKED) villager.swing(InteractionHand.MAIN_HAND);
        ticksUntilNextWork = result == PhysicalSmithFurnace.Result.MOVING ? 10 : 40;
    }

    private void returnMaterials(ServerLevel level) { reportReturn(materials.returnSurplus(level)); }
    private void reportReturn(PhysicalBuilderMaterials.Result result) {
        state = result == PhysicalBuilderMaterials.Result.MOVING ? JobState.GOING_TO_WORK
                : result == PhysicalBuilderMaterials.Result.WORKING ? JobState.WORKING : JobState.WAITING_FOR_CHEST;
        ticksUntilNextWork = result == PhysicalBuilderMaterials.Result.MOVING ? 10 : 40;
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
