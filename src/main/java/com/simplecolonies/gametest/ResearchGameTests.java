package com.simplecolonies.gametest;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.progression.ColonyUpgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class ResearchGameTests {
    @GameTest(template = "empty")
    public static void tightHousingResearchAddsRealShelterCapacityWithoutBeds(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(8, 1, 8));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new com.simplecolonies.building.Building(com.simplecolonies.building.BuildingType.HOUSE,
                center, CultureType.ROMAN, true));
        for (int i = 0; i < 6; i++) colony.addCitizen(new com.simplecolonies.colony.CitizenData(
                java.util.UUID.randomUUID(), "Resident", com.simplecolonies.villager.job.JobType.UNEMPLOYED));
        colony.reconcileHomes();
        if (colony.getShelterCapacity() != 4 || colony.getCitizens().stream().filter(c -> c.home != null).count() != 4)
            helper.fail("Base shelter assignment incorrect");
        colony.depositToWarehouse("minecraft:cobblestone", 32);
        colony.depositToWarehouse("minecraft:oak_planks", 32);
        colony.depositToWarehouse("minecraft:coal", 4);
        colony.depositToWarehouse("minecraft:white_wool", 8);
        if (!colony.getResearch().tryBuy(ColonyUpgrade.TOOL_STONE, helper.getLevel(), null)
                || !colony.getResearch().tryBuy(ColonyUpgrade.HOUSING_EFFICIENT, helper.getLevel(), null))
            helper.fail("Could not buy housing research using actual white wool");
        colony.reconcileHomes();
        if (colony.getShelterCapacity() != 6 || colony.getCitizens().stream().filter(c -> c.home != null).count() != 6
                || colony.getWarehouseCount(helper.getLevel(), "minecraft:white_wool") != 0
                || colony.getWarehouseCount(helper.getLevel(), "minecraft:oak_planks") != 0)
            helper.fail("Research did not charge or update shelter assignments");
        var restored = Colony.load(colony.save());
        restored.reconcileHomes();
        if (restored.getShelterCapacity() != 6 || restored.getCitizens().stream().filter(c -> c.home != null).count() != 6)
            helper.fail("Researched shelter and assignments did not survive reload");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void courierResearchCostsStockButUnsupportedSchematicCannotCharge(GameTestHelper helper) {
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(8, 1, 8)));
        var tag = colony.getResearch().save();
        var unlocked = new net.minecraft.nbt.ListTag();
        for (var up : ColonyUpgrade.values()) if (up != ColonyUpgrade.SCHEMATIC_CANNON && up != ColonyUpgrade.WAREHOUSE_CAPACITY)
            unlocked.add(net.minecraft.nbt.StringTag.valueOf(up.name()));
        tag.put("unlocked", unlocked); tag.putInt("autoCooldown", 0);
        colony.getResearch().load(tag);
        colony.depositToWarehouse("minecraft:chest", 8);
        colony.depositToWarehouse("minecraft:oak_planks", 40);
        colony.depositToWarehouse("minecraft:iron_block", 2);
        colony.depositToWarehouse("minecraft:redstone", 8);
        if (!colony.getResearch().tryBuy(ColonyUpgrade.WAREHOUSE_CAPACITY, helper.getLevel(), null)
                || colony.getWarehouseCount(helper.getLevel(), "minecraft:chest") != 0
                || colony.getWarehouseCount(helper.getLevel(), "minecraft:oak_planks") != 8)
            helper.fail("Courier research did not charge real stock");
        var before = colony.getWarehouseSnapshot(helper.getLevel());
        if (colony.getResearch().tryBuy(ColonyUpgrade.SCHEMATIC_CANNON, helper.getLevel(), null))
            helper.fail("Paid for unsupported schematic research");
        colony.getResearch().tickAutoProgress(helper.getLevel());
        if (!before.equals(colony.getWarehouseSnapshot(helper.getLevel()))
                || colony.getResearch().isUnlocked(ColonyUpgrade.SCHEMATIC_CANNON)) helper.fail("Auto research paid for unsupported research");
        unlocked.add(net.minecraft.nbt.StringTag.valueOf("WAREHOUSE_CAPACITY"));
        unlocked.add(net.minecraft.nbt.StringTag.valueOf("SCHEMATIC_CANNON"));
        tag.put("unlocked", unlocked);
        colony.getResearch().load(tag);
        var restored = Colony.load(colony.save());
        if (!restored.getResearch().isUnlocked(ColonyUpgrade.WAREHOUSE_CAPACITY)
                || !restored.getResearch().isUnlocked(ColonyUpgrade.SCHEMATIC_CANNON)) helper.fail("Old unlock was lost on reload");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void completedLibraryShortensResearchIntervalAndSurvivesReload(GameTestHelper helper) {
        var plain = researchColony(helper, false);
        var library = researchColony(helper, true);
        for (var colony : new Colony[]{plain, library}) {
            var saved = colony.getResearch().save();
            saved.putInt("autoCooldown", 0);
            colony.getResearch().load(saved);
            colony.depositToWarehouse("minecraft:cobblestone", 64);
            colony.depositToWarehouse("minecraft:oak_planks", 64);
            colony.depositToWarehouse("minecraft:coal", 16);
            colony.depositToWarehouse("minecraft:wheat", 32);
            colony.getResearch().tickAutoProgress(helper.getLevel());
            if (!colony.getResearch().isUnlocked(ColonyUpgrade.TOOL_STONE)) helper.fail("First research did not unlock");
        }
        library = Colony.load(library.save());
        for (int day = 0; day < 3; day++) {
            plain.getResearch().tickAutoProgress(helper.getLevel());
            library.getResearch().tickAutoProgress(helper.getLevel());
        }
        if (plain.getResearch().isUnlocked(ColonyUpgrade.EFFICIENCY_1)
                || !library.getResearch().isUnlocked(ColonyUpgrade.EFFICIENCY_1))
            helper.fail("Completed library did not shorten research cooldown after reload");
        if (library.getWarehouseCount(helper.getLevel(), "minecraft:wheat") != 16
                || library.getWarehouseCount(helper.getLevel(), "minecraft:oak_planks") != 32
                || library.getWarehouseCount(helper.getLevel(), "minecraft:coal") != 8)
            helper.fail("Library research did not spend actual materials");
        helper.succeed();
    }

    private static Colony researchColony(GameTestHelper helper, boolean completedLibrary) {
        BlockPos center = helper.absolutePos(new BlockPos(8, 1, 8));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        var library = new com.simplecolonies.building.Building(com.simplecolonies.building.BuildingType.LIBRARY,
                center.east(5), CultureType.ROMAN, completedLibrary);
        colony.registerPlayerBuilding(library);
        return colony;
    }

    @GameTest(template = "empty")
    public static void automaticResearchPreservesItsSurplus(GameTestHelper helper) {
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(8, 1, 8)));
        var saved = colony.getResearch().save(); saved.putInt("autoCooldown", 0);
        colony.getResearch().load(saved);
        colony.depositToWarehouse("minecraft:cobblestone", 32);
        colony.depositToWarehouse("minecraft:oak_planks", 16);
        colony.depositToWarehouse("minecraft:coal", 4);
        colony.getResearch().tickAutoProgress(helper.getLevel());
        if (colony.getResearch().isUnlocked(ColonyUpgrade.TOOL_STONE)
                || colony.getWarehouseCount(helper.getLevel(), "minecraft:cobblestone") != 32)
            helper.fail("Automatic research spent the last required supplies");
        colony.depositToWarehouse("minecraft:cobblestone", 32);
        colony.depositToWarehouse("minecraft:oak_planks", 16);
        colony.depositToWarehouse("minecraft:coal", 4);
        saved = colony.getResearch().save(); saved.putInt("autoCooldown", 0);
        colony.getResearch().load(saved);
        colony.getResearch().tickAutoProgress(helper.getLevel());
        if (!colony.getResearch().isUnlocked(ColonyUpgrade.TOOL_STONE)
                || colony.getWarehouseCount(helper.getLevel(), "minecraft:cobblestone") != 32
                || colony.getWarehouseCount(helper.getLevel(), "minecraft:oak_planks") != 16
                || colony.getWarehouseCount(helper.getLevel(), "minecraft:coal") != 4)
            helper.fail("Automatic research did not retain its surplus");
        helper.succeed();
    }

    @GameTest(template = "isolated")
    public static void researchRequiresAndConsumesCompleteRealCost(GameTestHelper helper) {
        BlockPos site = new BlockPos(56, 1, 56);
        helper.setBlock(site, Blocks.CHEST);
        Container chest = (Container) helper.getBlockEntity(site);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
        chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 16));
        chest.setItem(2, new ItemStack(Items.COAL, 3));
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(site));
        colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(site));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var research = colony.getResearch();
        var level = helper.getLevel();
        if (research.tryBuy(ColonyUpgrade.TOOL_STONE, level, player) || research.isUnlocked(ColonyUpgrade.TOOL_STONE)
                || chest.getItem(0).getCount() != 32 || chest.getItem(1).getCount() != 16 || chest.getItem(2).getCount() != 3)
            helper.fail("Research with missing coal consumed items or unlocked");
        chest.setItem(2, new ItemStack(Items.COAL, 4));
        chest.setChanged();
        if (!research.tryBuy(ColonyUpgrade.TOOL_STONE, level, player) || !research.isUnlocked(ColonyUpgrade.TOOL_STONE)
                || colony.getWarehouseCount(level, "minecraft:cobblestone") != 0
                || colony.getWarehouseCount(level, "minecraft:oak_planks") != 0
                || colony.getWarehouseCount(level, "minecraft:coal") != 0)
            helper.fail("Research did not consume its full cost exactly once");
        if (research.tryBuy(ColonyUpgrade.TOOL_STONE, level, player)) helper.fail("Research was purchased twice");
        helper.succeed();
    }
}
