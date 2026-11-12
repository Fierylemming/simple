package com.simplecolonies.gametest;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingState;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.building.ConstructionCrafting;
import com.simplecolonies.colony.CitizenData;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.villager.job.JobType;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class HousingGrowthGameTests {
    private static Colony setup(GameTestHelper helper) {
        for (int x = 30; x <= 82; x++) for (int z = 30; z <= 82; z++) {
            helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
            // Remove the GameTest boundary ceiling from the heightmap.
            helper.setBlock(x, 5, z, Blocks.AIR);
        }
        BlockPos center = helper.absolutePos(new BlockPos(56, 1, 56));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.TOWN_HALL, center, CultureType.ROMAN, true));
        return colony;
    }
    private static void citizen(Colony colony, JobType job) {
        colony.addCitizen(new CitizenData(UUID.randomUUID(), "Test citizen", job));
    }
    private static long plans(Colony colony) {
        return colony.getBuildings().stream().filter(b -> b.getType() == BuildingType.HOUSE && b.hasSupportedPlan()).count();
    }

    @GameTest(template = "isolated", timeoutTicks = 1100)
    public static void housingNeedsBuilderAndMaterialsWithoutSpendingToPlan(GameTestHelper helper) {
        Colony colony = setup(helper);
        for (int i = 0; i < 4; i++) citizen(colony, JobType.UNEMPLOYED);
        helper.runAtTickTime(200, () -> {
            colony.tick(helper.getLevel());
            if (plans(colony) != 0 || !colony.getExpansionStatus().equals("No builder assigned"))
                helper.fail("Housing queued without a builder");
            citizen(colony, JobType.BUILDER);
        });
        helper.runAtTickTime(400, () -> {
            colony.tick(helper.getLevel());
            if (plans(colony) != 0 || !colony.getExpansionStatus().contains("Not enough materials"))
                helper.fail("Housing queued without materials: " + colony.getExpansionStatus());
            colony.depositToWarehouse("minecraft:oak_log", 300);
            colony.depositToWarehouse("minecraft:cobblestone", 300);
            colony.depositToWarehouse("minecraft:coal", 10);
        });
        helper.runAtTickTime(600, () -> {
            var before = colony.getWarehouseSnapshot(helper.getLevel());
            colony.tick(helper.getLevel());
            var house = colony.getBuildings().stream().filter(b -> b.getType() == BuildingType.HOUSE && b.hasSupportedPlan()).findFirst().orElse(null);
            if (house == null || house.getState() != BuildingState.BLUEPRINT || !before.equals(colony.getWarehouseSnapshot(helper.getLevel())))
                helper.fail("Affordable house was not queued without spending stock: " + colony.getExpansionStatus());
            Colony restored = Colony.load(colony.save());
            var restoredHouse = restored.getBuildings().stream().filter(b -> b.getType() == BuildingType.HOUSE && b.hasSupportedPlan()).findFirst().orElse(null);
            if (restoredHouse == null || !restoredHouse.getPos().equals(house.getPos())
                    || restoredHouse.getHouseVariant() != house.getHouseVariant()
                    || restoredHouse.getRemainingPlacements().size() != house.getRemainingPlacements().size())
                helper.fail("Queued house was not preserved across reload");
        });
        helper.runAtTickTime(800, () -> {
            colony.tick(helper.getLevel());
            if (plans(colony) != 1) helper.fail("Queued a second house while construction was unfinished");
            helper.succeed();
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 500)
    public static void selectsAffordableHouseVariant(GameTestHelper helper) {
        Colony colony = setup(helper);
        citizen(colony, JobType.BUILDER);
        for (int i = 0; i < 3; i++) citizen(colony, JobType.UNEMPLOYED);
        BlockPos center = colony.getCenter();
        Building compact = null;
        for (int i = 0; i < 3; i++) {
            Building sample = new Building(BuildingType.HOUSE, center.east(i), CultureType.ROMAN);
            if (sample.getHouseVariant() == 0) compact = sample;
        }
        if (compact == null) throw new IllegalStateException("Missing compact house");
        var stock = compact.getRequiredResources();
        for (int i = 0; i < 3; i++) {
            Building sample = new Building(BuildingType.HOUSE, center.east(i), CultureType.ROMAN);
            if (sample.getHouseVariant() != 0 && ConstructionCrafting.canBuild(stock, sample.getRemainingPlacements()))
                helper.fail("Fixture must only afford the compact variant");
        }
        stock.forEach(colony::depositToWarehouse);
        helper.runAtTickTime(200, () -> {
            colony.tick(helper.getLevel());
            var house = colony.getBuildings().stream().filter(b -> b.getType() == BuildingType.HOUSE && b.hasSupportedPlan()).findFirst().orElse(null);
            if (house == null || house.getHouseVariant() != 0)
                helper.fail("Did not find an affordable variant: " + colony.getExpansionStatus());
            helper.succeed();
        });
    }
}
