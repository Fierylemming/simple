package com.simplecolonies.gametest;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.villager.ColonyProfessions;
import com.simplecolonies.villager.job.BlacksmithJob;
import com.simplecolonies.villager.job.JobType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class SmithFurnaceGameTests {
    private static final BlockPos SITE = new BlockPos(56, 1, 56), FURNACE = new BlockPos(57, 1, 56);
    private static Colony setup(GameTestHelper helper, boolean designated) {
        for (int x = 40; x <= 72; x++) for (int z = 40; z <= 72; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        helper.setBlock(SITE, Blocks.SMITHING_TABLE);
        helper.setBlock(FURNACE, Blocks.FURNACE);
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(SITE));
        if (designated) colony.registerPlayerBuilding(new Building(BuildingType.BLACKSMITH, colony.getCenter(), CultureType.ROMAN, true));
        colony.depositToWarehouse("minecraft:raw_iron", 3);
        colony.depositToWarehouse("minecraft:coal", 1);
        return colony;
    }
    private static BlacksmithJob smith(GameTestHelper helper, Colony colony) {
        var worker = helper.spawn(EntityType.VILLAGER, new BlockPos(55, 1, 56));
        worker.setVillagerData(worker.getVillagerData().setProfession(ColonyProfessions.profession(JobType.BLACKSMITH)));
        worker.refreshBrain(helper.getLevel());
        worker.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(SITE)));
        return new BlacksmithJob(worker, colony);
    }
    @GameTest(template = "isolated", timeoutTicks = 1300)
    public static void smeltsInActualFurnaceOverTime(GameTestHelper helper) {
        Colony colony = setup(helper, true);
        var job = smith(helper, colony);
        for (int tick = 1; tick < 1300; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(30, () -> {
            Container furnace = (Container) helper.getBlockEntity(FURNACE);
            if (!furnace.getItem(0).is(Items.RAW_IRON) || furnace.getItem(0).getCount() != 3
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:iron_ingot") != 0)
                helper.fail("Input was not placed in a real furnace, or ingots appeared instantly");
        });
        helper.runAtTickTime(100, () -> {
            if (!helper.getBlockState(FURNACE).getValue(AbstractFurnaceBlock.LIT)) helper.fail("Furnace did not burn real fuel");
            var be = helper.getBlockEntity(FURNACE);
            var saved = be.saveWithFullMetadata(helper.getLevel().registryAccess());
            if (!saved.getCompound("NeoForgeData").contains("simplecolonies_smith_batch")) helper.fail("Furnace batch ownership was not persisted");
            // Reload the block entity from NBT while it is actively smelting.
            be.loadWithComponents(saved, helper.getLevel().registryAccess());
            if (!be.getPersistentData().contains("simplecolonies_smith_batch")) helper.fail("Furnace lost ownership on reload");
        });
        helper.runAtTickTime(1200, () -> {
            if (colony.getWarehouseCount(helper.getLevel(), "minecraft:iron_ingot") != 3
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:raw_iron") != 0
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:coal") != 0)
                helper.fail("Wrong final smelting stock: " + colony.getWarehouseSnapshot(helper.getLevel()) + " state=" + job.getState());
            if (helper.getBlockEntity(FURNACE).getPersistentData().contains("simplecolonies_smith_batch"))
                helper.fail("Completed batch remained claimed");
            helper.succeed();
        });
    }
    @GameTest(template = "isolated", timeoutTicks = 450)
    public static void ignoresOccupiedFurnaces(GameTestHelper helper) {
        Colony colony = setup(helper, true);
        Container furnace = (Container) helper.getBlockEntity(FURNACE);
        furnace.setItem(0, new ItemStack(Items.GOLD_ORE, 2));
        var job = smith(helper, colony);
        for (int tick = 1; tick < 450; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(400, () -> {
            if (!furnace.getItem(0).is(Items.GOLD_ORE) || furnace.getItem(0).getCount() != 2
                    || !furnace.getItem(1).isEmpty() || colony.getWarehouseCount(helper.getLevel(), "minecraft:raw_iron") != 3
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:coal") != 1
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:iron_ingot") != 0)
                helper.fail("Smith used an unrelated furnace or smelted remotely");
            helper.succeed();
        });
    }
    @GameTest(template = "isolated", timeoutTicks = 450)
    public static void relinquishesBatchWhenPlayerChangesInput(GameTestHelper helper) {
        Colony colony = setup(helper, true);
        var job = smith(helper, colony);
        Container furnace = (Container) helper.getBlockEntity(FURNACE);
        for (int tick = 1; tick < 450; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(10, () -> {
            if (!furnace.getItem(0).is(Items.RAW_IRON)) helper.fail("Batch was not started");
            furnace.setItem(0, new ItemStack(Items.GOLD_ORE, 2));
        });
        helper.runAtTickTime(170, () -> {
            if (!furnace.getItem(0).is(Items.GOLD_ORE) || furnace.getItem(0).getCount() != 2
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:iron_ingot") != 0
                    || helper.getBlockEntity(FURNACE).getPersistentData().contains("simplecolonies_smith_batch"))
                helper.fail("Smith modified a changed furnace or produced unearned iron");
            helper.succeed();
        });
    }
    @GameTest(template = "isolated", timeoutTicks = 350)
    public static void finishesClaimedBatchBeforeStartingAnother(GameTestHelper helper) {
        Colony colony = setup(helper, true);
        colony.depositToWarehouse("minecraft:raw_iron", 3);
        colony.depositToWarehouse("minecraft:coal", 1);
        var job = smith(helper, colony);
        for (int tick = 1; tick < 350; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        BlockPos extra = new BlockPos(56, 1, 55);
        helper.runAtTickTime(10, () -> helper.setBlock(extra, Blocks.FURNACE));
        helper.runAtTickTime(160, () -> {
            var other = (Container) helper.getBlockEntity(extra);
            if (!other.isEmpty() || colony.getWarehouseCount(helper.getLevel(), "minecraft:raw_iron") != 3
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:coal") != 1)
                helper.fail("Smith started a second batch before finishing the first");
            helper.succeed();
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 450)
    public static void leavesEmptyUndesignatedFurnaceAlone(GameTestHelper helper) {
        Colony colony = setup(helper, false);
        Container furnace = (Container) helper.getBlockEntity(FURNACE);
        var job = smith(helper, colony);
        for (int tick = 1; tick < 450; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(400, () -> {
            if (!furnace.isEmpty() || colony.getWarehouseCount(helper.getLevel(), "minecraft:raw_iron") != 3
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:coal") != 1
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:iron_ingot") != 0)
                helper.fail("Smith used an undesignated furnace or smelted remotely");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 3500)
    public static void colonySmithCarriesOreFuelAndSmeltedIron(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos site = new BlockPos(4, 1, 8), furnacePos = site.east(), chest = new BlockPos(13, 1, 8);
        helper.setBlock(site, Blocks.SMITHING_TABLE); helper.setBlock(furnacePos, Blocks.FURNACE); helper.setBlock(chest, Blocks.CHEST);
        var station = helper.absolutePos(site); var absFurnace = helper.absolutePos(furnacePos); var storage = helper.absolutePos(chest);
        Colony colony = new Colony(0, CultureType.ROMAN, station);
        colony.registerPlayerBuilding(new Building(BuildingType.BLACKSMITH, station, CultureType.ROMAN, true));
        colony.depositToWarehouse("minecraft:raw_iron", 3); colony.depositToWarehouse("minecraft:coal", 1);
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), storage)) helper.fail("Could not share furnace supplies");
        var container = (Container) helper.getBlockEntity(chest); var furnace = (Container) helper.getBlockEntity(furnacePos);
        var special = new ItemStack(Items.RAW_IRON, 3);
        special.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Reserved ore"));
        container.setItem(0, special.copy());
        var worker = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(7, 1, 8));
        worker.setVillagerData(worker.getVillagerData().setProfession(ColonyProfessions.profession(JobType.BLACKSMITH)));
        worker.refreshBrain(helper.getLevel());
        worker.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), station));
        var job = new BlacksmithJob(worker, colony);
        boolean[] supplied = {false}, picked = {false}, loaded = {false}, carried = {false};
        for (int tick = 1; tick < 3500; tick++) helper.runAtTickTime(tick, () -> {
            job.tick(helper.getLevel());
            if (!picked[0] && worker.getBuilderMaterials().countItem(Items.RAW_IRON) == 3) {
                if (!supplied[0] || worker.distanceToSqr(storage.getX()+.5, storage.getY()+.5, storage.getZ()+.5) > 9)
                    helper.fail("Smith obtained ore remotely");
                var tag = new net.minecraft.nbt.CompoundTag(); worker.addAdditionalSaveData(tag);
                var saved = new net.minecraft.world.SimpleContainer(18);
                saved.fromTag(tag.getList("builderMaterials", net.minecraft.nbt.Tag.TAG_COMPOUND), helper.getLevel().registryAccess());
                if (saved.countItem(Items.RAW_IRON) != 3) helper.fail("Smelting supplies did not persist");
                picked[0] = true;
            }
            if (!loaded[0] && furnace.getItem(0).is(Items.RAW_IRON)) {
                if (!picked[0] || worker.distanceToSqr(absFurnace.getX()+.5, absFurnace.getY()+.5, absFurnace.getZ()+.5) > 9
                        || !helper.getBlockEntity(furnacePos).getPersistentData().contains("simplecolonies_smith_batch"))
                    helper.fail("Smith loaded the furnace remotely or without a claim");
                loaded[0] = true;
            }
            if (!carried[0] && worker.getCourierCargo().is(Items.IRON_INGOT)) {
                if (!loaded[0] || worker.distanceToSqr(absFurnace.getX()+.5, absFurnace.getY()+.5, absFurnace.getZ()+.5) > 9)
                    helper.fail("Smith took furnace output remotely");
                var tag = new net.minecraft.nbt.CompoundTag(); worker.addAdditionalSaveData(tag);
                if (!ItemStack.parseOptional(helper.getLevel().registryAccess(), tag.getCompound("courierCargo")).is(Items.IRON_INGOT))
                    helper.fail("Smelted iron cargo did not persist");
                carried[0] = true;
            }
        });
        helper.runAtTickTime(150, () -> {
            if (!furnace.isEmpty() || !worker.getBuilderMaterials().isEmpty()) helper.fail("Smith used virtual or customized smelting stock");
            container.setItem(1, new ItemStack(Items.RAW_IRON, 3)); container.setItem(2, new ItemStack(Items.COAL, 1)); supplied[0] = true;
        });
        helper.succeedWhen(() -> {
            if (!supplied[0] || !picked[0] || !loaded[0] || !carried[0] || container.countItem(Items.IRON_INGOT) != 3
                    || !worker.getCourierCargo().isEmpty() || helper.getBlockEntity(furnacePos).getPersistentData().contains("simplecolonies_smith_batch"))
                helper.fail("Physical smelting incomplete: " + job.getState() + " furnace=" + furnace.getItem(0) + "," + furnace.getItem(2));
            if (colony.getWarehouseCount("minecraft:raw_iron") != 3 || colony.getWarehouseCount("minecraft:coal") != 1
                    || !ItemStack.isSameItemSameComponents(container.getItem(0), special) || container.getItem(0).getCount() != 3)
                helper.fail("Smelting used virtual or customized supplies");
        });
    }

}
