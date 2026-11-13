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
    public static void completedLibraryShortensResearchIntervalAndSurvivesReload(GameTestHelper helper) {
        var plain = researchColony(helper, false);
        var library = researchColony(helper, true);
        for (var colony : new Colony[]{plain, library}) {
            var saved = colony.getResearch().save();
            saved.putInt("autoCooldown", 0);
            colony.getResearch().load(saved);
            colony.depositToWarehouse("minecraft:cobblestone", 32);
            colony.depositToWarehouse("minecraft:oak_planks", 32);
            colony.depositToWarehouse("minecraft:coal", 8);
            colony.depositToWarehouse("minecraft:wheat", 16);
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
        if (library.getWarehouseCount(helper.getLevel(), "minecraft:wheat") != 0
                || library.getWarehouseCount(helper.getLevel(), "minecraft:oak_planks") != 0
                || library.getWarehouseCount(helper.getLevel(), "minecraft:coal") != 0)
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
