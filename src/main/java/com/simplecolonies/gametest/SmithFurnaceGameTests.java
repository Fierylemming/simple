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

}
