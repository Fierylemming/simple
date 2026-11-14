package com.simplecolonies.gametest;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingState;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ModEntities;
import com.simplecolonies.villager.job.BuilderJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class PhysicalBuilderGameTests {
    @GameTest(template = "empty", timeoutTicks = 7000)
    public static void builderFetchesCraftsPlacesAndReturnsRealMaterials(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos station = new BlockPos(11, 1, 10), chest = new BlockPos(4, 1, 4), table = new BlockPos(3, 1, 4);
        helper.setBlock(station, Blocks.FURNACE); helper.setBlock(chest, Blocks.CHEST); helper.setBlock(table, Blocks.CRAFTING_TABLE);
        BlockPos origin = helper.absolutePos(station), absChest = helper.absolutePos(chest), absTable = helper.absolutePos(table);
        Colony colony = new Colony(0, CultureType.ROMAN, origin);
        colony.registerPlayerBuilding(new Building(BuildingType.TOWN_HALL, absTable, CultureType.ROMAN, true));
        var building = new Building(BuildingType.MINE, origin, CultureType.ROMAN);
        colony.registerPlayerBuilding(building);
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), absChest)) helper.fail("Could not share construction chest");
        var inventory = (Container) helper.getBlockEntity(chest);
        ItemStack special = new ItemStack(Items.OAK_LOG, 8);
        special.set(DataComponents.CUSTOM_NAME, Component.literal("Private timber"));
        inventory.setItem(0, special);
        inventory.setItem(2, new ItemStack(Items.COBBLESTONE, 64));
        inventory.setItem(3, new ItemStack(Items.COBBLESTONE, 64));
        inventory.setItem(4, new ItemStack(Items.COAL, 16));
        colony.depositToWarehouse("minecraft:oak_log", 100); // Cannot be physically collected.
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(9, 1, 7));
        var job = new BuilderJob(worker, colony);
        var first = building.getNextPlacement();
        boolean[] ready = {false}, collected = {false}, crafted = {false};
        for (int tick = 1; tick < 7000; tick++) helper.runAtTickTime(tick, () -> {
            job.tick(helper.getLevel());
            if (ready[0] && !collected[0] && worker.getBuilderMaterials().countItem(Items.OAK_LOG) > 0) {
                if (worker.distanceToSqr(absChest.getX()+.5, absChest.getY()+.5, absChest.getZ()+.5) > 9)
                    helper.fail("Builder collected timber remotely");
                CompoundTag tag = new CompoundTag(); worker.addAdditionalSaveData(tag);
                var restored = new SimpleContainer(18);
                restored.fromTag(tag.getList("builderMaterials", Tag.TAG_COMPOUND), helper.getLevel().registryAccess());
                if (restored.countItem(Items.OAK_LOG) != worker.getBuilderMaterials().countItem(Items.OAK_LOG))
                    helper.fail("Builder materials did not survive save");
                collected[0] = true;
            }
            if (!crafted[0] && (worker.getBuilderMaterials().countItem(Items.OAK_PLANKS) > 0
                    || worker.getBuilderMaterials().countItem(Items.OAK_STAIRS) > 0)) {
                if (!collected[0] || worker.distanceToSqr(absTable.getX()+.5, absTable.getY()+.5, absTable.getZ()+.5) > 9)
                    helper.fail("Builder crafted timber components away from table");
                crafted[0] = true;
            }
        });
        helper.runAtTickTime(200, () -> {
            if (building.getState() != BuildingState.NEEDS_RESOURCES || !helper.getLevel().getBlockState(first.pos).isAir()
                    || !worker.getBuilderMaterials().isEmpty() || inventory.getItem(0).getCount() != 8)
                helper.fail("Builder used virtual or customized stock before real timber arrived: " + job.getState());
            inventory.setItem(1, new ItemStack(Items.OAK_LOG, 64));
            ready[0] = true;
        });
        helper.succeedWhen(() -> {
            if (!ready[0] || !collected[0] || !crafted[0] || building.getState() != BuildingState.COMPLETE
                    || !worker.getBuilderMaterials().isEmpty())
                helper.fail("Physical build in progress: " + building.getState() + " " + job.getState()
                        + " at " + worker.blockPosition().toShortString()
                        + " next=" + (building.getNextPlacement() == null ? "none" : building.getNextPlacement().itemId));
            if (colony.getWarehouseCount("minecraft:oak_log") != 100 || inventory.getItem(0).getCount() != 8
                    || !ItemStack.isSameItemSameComponents(inventory.getItem(0), special)
                    || !helper.getBlockState(station).is(Blocks.FURNACE))
                helper.fail("Builder changed virtual stock, customized timber or the workstation");
        });
    }
    @GameTest(template = "empty", timeoutTicks = 800)
    public static void importedPlanUsesRealMaterialsAndRespectsObstructions(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos base = new BlockPos(8, 1, 8), chest = new BlockPos(6, 1, 8);
        helper.setBlock(base, Blocks.OBSIDIAN); helper.setBlock(chest, Blocks.CHEST);
        var center = helper.absolutePos(base);
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.addCitizen(new com.simplecolonies.colony.CitizenData(java.util.UUID.randomUUID(), "Builder", com.simplecolonies.villager.job.JobType.BUILDER));
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chest))) helper.fail("Could not share chest");
        var container = (Container) helper.getBlockEntity(chest);
        container.setItem(0, new ItemStack(Items.OAK_PLANKS, 3));
        var source = new CompoundTag(); source.putString("source", "import-fixture");
        var blocks = java.util.List.of(new Building.BlockPlacement(center, Blocks.OAK_PLANKS.defaultBlockState(), "minecraft:oak_planks"),
                new Building.BlockPlacement(center.east(), Blocks.OAK_PLANKS.defaultBlockState(), "minecraft:oak_planks"),
                new Building.BlockPlacement(center.east(2), Blocks.OAK_PLANKS.defaultBlockState(), "minecraft:oak_planks"));
        Building plan = Building.imported(center, CultureType.ROMAN, source, blocks);
        if (!colony.queueImportedBuilding(plan)) helper.fail("Could not queue imported plan");
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(7, 1, 9)); worker.setNoAi(true);
        var job = new BuilderJob(worker, colony);
        for (int tick = 1; tick < 800; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(100, () -> {
            if (!helper.getBlockState(base).is(Blocks.OBSIDIAN) || container.countItem(Items.OAK_PLANKS) != 3
                    || plan.getState() != BuildingState.BLOCKED) helper.fail("Imported builder altered an obstruction or consumed supplies");
            // Even replaceable vegetation could have been placed by a player.
            helper.setBlock(base, Blocks.SHORT_GRASS);
        });
        helper.runAtTickTime(200, () -> {
            if (!helper.getBlockState(base).is(Blocks.SHORT_GRASS) || container.countItem(Items.OAK_PLANKS) != 3
                    || plan.getState() != BuildingState.BLOCKED) helper.fail("Imported builder cleared a replaceable block");
            helper.setBlock(base, Blocks.AIR);
        });
        helper.succeedWhen(() -> {
            if (plan.getState() != BuildingState.COMPLETE || !helper.getBlockState(base).is(Blocks.OAK_PLANKS)
                    || !helper.getBlockState(base.east()).is(Blocks.OAK_PLANKS) || !helper.getBlockState(base.east(2)).is(Blocks.OAK_PLANKS)
                    || container.countItem(Items.OAK_PLANKS) != 0 || !plan.hasImportedSource(source))
                helper.fail("Imported physical construction incomplete: " + plan.getState() + ", " + job.getState());
        });
    }

    @GameTest(template = "empty")
    public static void cancellingImportedPlanSuppressesAutomaticRetryAcrossReload(GameTestHelper helper) {
        var center = helper.absolutePos(new BlockPos(8, 1, 8));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.addCitizen(new com.simplecolonies.colony.CitizenData(java.util.UUID.randomUUID(), "Builder", com.simplecolonies.villager.job.JobType.BUILDER));
        var source = new CompoundTag(); source.putString("source", "cancel-fixture");
        var blocks = java.util.List.of(new Building.BlockPlacement(center.east(10), Blocks.OAK_PLANKS.defaultBlockState(), "minecraft:oak_planks"));
        var plan = Building.imported(center, CultureType.ROMAN, source, blocks);
        if (!plan.importedFootprintNear(center.east(10), 0) || plan.importedFootprintNear(center, 0)
                || !colony.queueImportedBuilding(plan) || !colony.cancelUnfinishedBuilding(center))
            helper.fail("Could not cancel imported plan or calculate footprint");
        var restored = Colony.load(colony.save());
        if (!restored.isCancelledSchematic(source)) helper.fail("Cancelled blueprint would be automatically queued after reload");
        restored.clearCancelledSchematic(source);
        if (restored.isCancelledSchematic(source)) helper.fail("Explicit retry could not clear cancellation");
        helper.succeed();
    }

}
