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
            for (int y = 5; y <= 8; y++) helper.setBlock(x, y, z, Blocks.AIR);
        }
        BlockPos center = helper.absolutePos(new BlockPos(56, 1, 56));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.TOWN_HALL, center, CultureType.ROMAN, true));
        BlockPos storage = center.offset(2, 0, 2);
        helper.getLevel().setBlock(storage, Blocks.CHEST.defaultBlockState(), 3);
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), storage)) helper.fail("Could not share construction stock");
        return colony;
    }
    private static void supply(GameTestHelper helper, Colony colony, String id, int amount) {
        if (colony.getWarehouseManager().deposit(helper.getLevel(), colony.getCenter(), id, amount) != amount)
            helper.fail("Could not store fixture supply " + id);
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
            supply(helper, colony, "minecraft:oak_log", 300);
            supply(helper, colony, "minecraft:cobblestone", 300);
            supply(helper, colony, "minecraft:coal", 10);
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

    @GameTest(template = "isolated", timeoutTicks = 400)
    public static void queuesHouseFromBirchWithoutSpendingToPlan(GameTestHelper helper) {
        Colony colony = setup(helper);
        citizen(colony, JobType.BUILDER);
        for (int i = 0; i < 3; i++) citizen(colony, JobType.UNEMPLOYED);
        supply(helper, colony, "minecraft:birch_log", 300);
        supply(helper, colony, "minecraft:cobblestone", 300);
        supply(helper, colony, "minecraft:coal", 10);
        var stock = colony.getWarehouseSnapshot(helper.getLevel());
        helper.runAtTickTime(200, () -> {
            colony.tick(helper.getLevel());
            var house = colony.getBuildings().stream().filter(b -> b.getType() == BuildingType.HOUSE && b.hasSupportedPlan()).findFirst().orElse(null);
            if (house == null || !house.save().getString("houseTimber").equals("birch")
                    || !house.getRequiredResources().containsKey("minecraft:birch_door")
                    || !stock.equals(colony.getWarehouseSnapshot(helper.getLevel())))
                helper.fail("Non-oak house was not queued without spending stock: " + colony.getExpansionStatus());
            var restored = Colony.load(colony.save()).getBuildings().stream().filter(b -> b.getType() == BuildingType.HOUSE).findFirst().orElseThrow();
            if (!restored.getRequiredResources().equals(house.getRequiredResources())) helper.fail("Birch plan changed after reload");
            helper.succeed();
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 400)
    public static void grownSettlementQueuesAffordableCivicHall(GameTestHelper helper) {
        Colony original = setup(helper);
        citizen(original, JobType.BUILDER);
        var legacyTag = new Building(BuildingType.MARKET, original.getCenter().east(16), CultureType.ROMAN).save();
        legacyTag.putInt("planVersion", 10);
        original.registerPlayerBuilding(Building.load(legacyTag));
        var tag = original.save();
        tag.putInt("level", 2);
        tag.putInt("radius", 64);
        Colony colony = Colony.load(tag);
        supply(helper, colony, "minecraft:oak_log", 200);
        supply(helper, colony, "minecraft:cobblestone", 200);
        supply(helper, colony, "minecraft:coal", 10);
        var before = colony.getWarehouseSnapshot(helper.getLevel());
        helper.runAtTickTime(200, () -> {
            colony.tick(helper.getLevel());
            var market = colony.getBuildings().stream().filter(b -> b.getType() == BuildingType.MARKET && b.hasSupportedPlan()).findFirst().orElse(null);
            if (market == null || !market.hasSupportedPlan() || market.getState() != BuildingState.BLUEPRINT)
                helper.fail("Grown settlement did not queue a civic hall: " + colony.getExpansionStatus());
            if (!before.equals(colony.getWarehouseSnapshot(helper.getLevel()))) helper.fail("Planning spent building materials");
            var restored = Colony.load(colony.save()).getBuildings().stream().filter(b -> b.getType() == BuildingType.MARKET && b.hasSupportedPlan()).findFirst().orElse(null);
            if (restored == null || !restored.hasSupportedPlan() || !restored.getPos().equals(market.getPos()))
                helper.fail("Civic plan did not survive reload");
            helper.succeed();
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 400)
    public static void civicHallCanUseSpruce(GameTestHelper helper) {
        Colony original = setup(helper);
        citizen(original, JobType.BUILDER);
        var tag = original.save();
        tag.putInt("level", 2); tag.putInt("radius", 64);
        Colony colony = Colony.load(tag);
        supply(helper, colony, "minecraft:spruce_log", 200);
        supply(helper, colony, "minecraft:cobblestone", 200);
        supply(helper, colony, "minecraft:coal", 10);
        helper.runAtTickTime(200, () -> {
            colony.tick(helper.getLevel());
            var hall = colony.getBuildings().stream().filter(b -> b.getType() == BuildingType.MARKET && b.hasSupportedPlan()).findFirst().orElse(null);
            if (hall == null || !hall.getRequiredResources().containsKey("minecraft:spruce_door"))
                helper.fail("Civic hall did not use available spruce: " + colony.getExpansionStatus());
            var restored = Colony.load(colony.save()).getBuildings().stream().filter(b -> b.getType() == BuildingType.MARKET).findFirst().orElseThrow();
            if (!restored.getRequiredResources().equals(hall.getRequiredResources())) helper.fail("Civic timber plan changed on reload");
            helper.succeed();
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 650)
    public static void grownColonyQueuesLibraryOnlyWithRealBookshelves(GameTestHelper helper) {
        Colony original = setup(helper);
        citizen(original, JobType.BUILDER);
        original.registerPlayerBuilding(new Building(BuildingType.MARKET, original.getCenter().east(16), CultureType.ROMAN, true));
        var legacy = new Building(BuildingType.LIBRARY, original.getCenter().west(16), CultureType.ROMAN).save();
        legacy.putInt("planVersion", 12);
        original.registerPlayerBuilding(Building.load(legacy));
        var saved = original.save(); saved.putInt("level", 3); saved.putInt("radius", 80);
        Colony colony = Colony.load(saved);
        supply(helper, colony, "minecraft:birch_log", 200);
        supply(helper, colony, "minecraft:cobblestone", 200);
        supply(helper, colony, "minecraft:coal", 10);
        helper.runAtTickTime(200, () -> {
            colony.tick(helper.getLevel());
            if (colony.getBuildings().stream().anyMatch(b -> b.getType() == BuildingType.LIBRARY && b.hasSupportedPlan()))
                helper.fail("Queued library without bookshelves");
            supply(helper, colony, "minecraft:bookshelf", 2);
        });
        helper.runAtTickTime(400, () -> {
            var before = colony.getWarehouseSnapshot(helper.getLevel());
            colony.tick(helper.getLevel());
            var library = colony.getBuildings().stream().filter(b -> b.getType() == BuildingType.LIBRARY && b.hasSupportedPlan()).findFirst().orElse(null);
            if (library == null || library.getState() != BuildingState.BLUEPRINT
                    || !library.getRequiredResources().containsKey("minecraft:birch_door")
                    || !before.equals(colony.getWarehouseSnapshot(helper.getLevel())))
                helper.fail("Library did not queue safely: " + colony.getExpansionStatus());
            var restored = Colony.load(colony.save()).getBuildings().stream().filter(b -> b.getType() == BuildingType.LIBRARY && b.hasSupportedPlan()).findFirst().orElse(null);
            if (restored == null || !restored.getRemainingPlacements().getFirst().state.equals(library.getRemainingPlacements().getFirst().state))
                helper.fail("Library plan changed after reload");
            helper.succeed();
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 300)
    public static void compactHouseLeavesOutsideFootprintUntouched(GameTestHelper helper) {
        Colony colony = setup(helper);
        BlockPos site = null;
        for (int x = 70; x <= 76; x++) {
            var candidate = helper.absolutePos(new BlockPos(x, 1, 56));
            if (Building.houseVariantAt(candidate) == 0) { site = candidate; break; }
        }
        if (site == null) { helper.fail("Could not find compact house variant"); return; }
        BlockPos obstacle = site.east(3);
        helper.getLevel().setBlock(obstacle, Blocks.OBSIDIAN.defaultBlockState(), 3);
        if (!colony.isClearBuildSite(helper.getLevel(), site)) helper.fail("Unused edge blocked compact house site");
        var blueprint = new Building(BuildingType.HOUSE, site, CultureType.ROMAN);
        if (blueprint.getRemainingPlacements().stream().anyMatch(p -> p.pos.equals(obstacle))
                || !helper.getLevel().getBlockState(obstacle).is(Blocks.OBSIDIAN))
            helper.fail("Compact plan altered property outside its footprint");
        helper.getLevel().setBlock(site.east(), Blocks.OBSIDIAN.defaultBlockState(), 3);
        if (colony.isClearBuildSite(helper.getLevel(), site)) helper.fail("Obstacle inside footprint was ignored");
        helper.succeed();
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
        stock.forEach((id, amount) -> supply(helper, colony, id, amount));
        helper.runAtTickTime(200, () -> {
            colony.tick(helper.getLevel());
            var house = colony.getBuildings().stream().filter(b -> b.getType() == BuildingType.HOUSE && b.hasSupportedPlan()).findFirst().orElse(null);
            if (house == null || house.getHouseVariant() != 0)
                helper.fail("Did not find an affordable variant: " + colony.getExpansionStatus()
                        + " sample height=" + helper.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX() + 12, center.getZ())
                        + " clear=" + colony.isClearBuildSite(helper.getLevel(), center.east(12)));
            helper.succeed();
        });
    }
    @GameTest(template = "isolated", timeoutTicks = 400)
    public static void virtualSuppliesDoNotQueuePhysicalConstruction(GameTestHelper helper) {
        Colony colony = setup(helper);
        citizen(colony, JobType.BUILDER);
        for (int i = 0; i < 3; i++) citizen(colony, JobType.UNEMPLOYED);
        colony.depositToWarehouse("minecraft:oak_log", 300);
        colony.depositToWarehouse("minecraft:cobblestone", 300);
        colony.depositToWarehouse("minecraft:coal", 10);
        helper.runAtTickTime(200, () -> {
            colony.tick(helper.getLevel());
            if (plans(colony) != 0 || !colony.getExpansionStatus().contains("Not enough materials"))
                helper.fail("Queued physical house from inaccessible virtual stock");
            helper.succeed();
        });
    }

}
