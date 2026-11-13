package com.simplecolonies.gametest;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.CitizenData;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ModEntities;
import com.simplecolonies.villager.job.JobType;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class WorksiteExpansionGameTests {
    @GameTest(template = "isolated", timeoutTicks = 600)
    public static void smithyExpansionUsesAssignedStationAndPreservesLegacy(GameTestHelper helper) {
        testExpansion(helper, JobType.BLACKSMITH, BuildingType.BLACKSMITH, Blocks.SMITHING_TABLE);
    }
    @GameTest(template = "isolated", timeoutTicks = 600)
    public static void butcherExpansionUsesAssignedStation(GameTestHelper helper) {
        testExpansion(helper, JobType.BUTCHER, BuildingType.BUTCHER_HUT, Blocks.SMOKER);
    }
    @GameTest(template = "isolated", timeoutTicks = 600)
    public static void bakeryExpansionUsesAssignedStation(GameTestHelper helper) {
        testExpansion(helper, JobType.BAKER, BuildingType.BAKERY, Blocks.SMOKER);
    }
    @GameTest(template = "isolated", timeoutTicks = 600)
    public static void leatherShopExpansionUsesAssignedStation(GameTestHelper helper) {
        testExpansion(helper, JobType.LEATHERWORKER, BuildingType.LEATHER_SHOP, Blocks.CAULDRON);
    }
    @GameTest(template = "isolated", timeoutTicks = 800)
    public static void warehouseExpansionUsesRegisteredCourierBarrel(GameTestHelper helper) {
        testExpansion(helper, JobType.COURIER, BuildingType.WAREHOUSE, Blocks.BARREL);
    }
    @GameTest(template = "isolated", timeoutTicks = 600)
    public static void barracksExpansionUsesGuardStation(GameTestHelper helper) {
        testExpansion(helper, JobType.GUARD, BuildingType.BARRACKS, Blocks.GRINDSTONE);
    }
    @GameTest(template = "isolated", timeoutTicks = 1300)
    public static void harborExpansionNeedsDeepWaterAndClearPlot(GameTestHelper helper) {
        for (int x = 20; x <= 75; x++) for (int z = 35; z <= 75; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos center = helper.absolutePos(new BlockPos(36, 1, 56));
        BlockPos site = new BlockPos(56, 1, 56), absSite = helper.absolutePos(site);
        helper.setBlock(site, Blocks.BARREL);
        var barrel = (net.minecraft.world.Container) helper.getBlockEntity(site);
        barrel.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.TOWN_HALL, center, CultureType.ROMAN, true));
        colony.registerPlayerBuilding(new Building(BuildingType.HOUSE, center.north(16), CultureType.ROMAN, true));
        colony.addCitizen(new CitizenData(UUID.randomUUID(), "Builder", JobType.BUILDER));
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), site.offset(0, 0, 2));
        worker.setNoAi(true);
        worker.setColony(colony);
        worker.setJob(JobType.FISHERMAN);
        colony.getCitizens().stream().filter(c -> c.uuid.equals(worker.getUUID())).findFirst().orElseThrow().job = JobType.FISHERMAN;
        worker.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), absSite));
        colony.depositToWarehouse("minecraft:oak_log", 300);
        colony.depositToWarehouse("minecraft:cobblestone", 300);
        colony.depositToWarehouse("minecraft:coal", 10);
        var tag = new Building(BuildingType.HARBOR, center.south(14), CultureType.ROMAN).save();
        tag.putInt("planVersion", 7);
        var legacy = Building.load(tag);
        colony.registerPlayerBuilding(legacy);
        helper.runAtTickTime(200, () -> {
            colony.tick(helper.getLevel());
            if (hasHarborPlan(colony)) helper.fail("Harbor queued on dry land");
            // A shallow pool outside the footprint should not suffice either.
            for (int x = 60; x <= 61; x++) for (int z = 55; z <= 56; z++) helper.setBlock(x, 0, z, Blocks.WATER);
        });
        helper.runAtTickTime(400, () -> {
            colony.tick(helper.getLevel());
            if (hasHarborPlan(colony)) helper.fail("Harbor queued beside shallow water");
            for (int x = 60; x <= 61; x++) for (int z = 55; z <= 56; z++) {
                helper.setBlock(x, -1, z, Blocks.WATER);
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        });
        helper.runAtTickTime(600, () -> {
            colony.tick(helper.getLevel());
            if (hasHarborPlan(colony)) helper.fail("Harbor queued beside buried water");
            for (int x = 60; x <= 61; x++) for (int z = 55; z <= 56; z++) helper.setBlock(x, 1, z, Blocks.AIR);
            helper.setBlock(site.offset(2, 0, 0), Blocks.OBSIDIAN);
        });
        helper.runAtTickTime(800, () -> {
            colony.tick(helper.getLevel());
            if (hasHarborPlan(colony)) helper.fail("Harbor queued over an obstruction");
            helper.setBlock(site.offset(2, 0, 0), Blocks.AIR);
        });
        helper.runAtTickTime(1000, () -> {
            colony.tick(helper.getLevel());
            var harbor = colony.getBuildings().stream().filter(b -> b.getType() == BuildingType.HARBOR && b.hasSupportedPlan()).findFirst().orElse(null);
            if (harbor == null || !harbor.getPos().equals(absSite) || legacy.hasSupportedPlan() || !legacy.getRemainingPlacements().isEmpty())
                helper.fail("No new plan at shoreline or old placeholder changed: " + colony.getExpansionStatus());
            if (!helper.getBlockState(site).is(Blocks.BARREL) || barrel.getItem(0).getCount() != 3
                    || !helper.getBlockState(new BlockPos(60, 0, 55)).is(Blocks.WATER))
                helper.fail("Planning altered the station, its inventory or water");
            helper.succeed();
        });
    }
    private static boolean hasHarborPlan(Colony colony) {
        return colony.getBuildings().stream().anyMatch(b -> b.getType() == BuildingType.HARBOR && b.hasSupportedPlan());
    }

    @GameTest(template = "isolated", timeoutTicks = 100)
    public static void stoneSupportsHouseAndWorksiteWithoutReplacingObstacles(GameTestHelper helper) {
        for (int x = 48; x <= 64; x++) for (int z = 48; z <= 64; z++) {
            helper.setBlock(x, 0, z, Blocks.STONE);
            for (int y = 5; y <= 8; y++) {
                BlockPos p = helper.absolutePos(new BlockPos(x, y, z));
                if (helper.getLevel().getBlockState(p).is(Blocks.BARRIER)) helper.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        BlockPos site = new BlockPos(56, 1, 56);
        var colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(36, 1, 56)));
        BlockPos absolute = helper.absolutePos(site);
        boolean house = colony.isClearBuildSite(helper.getLevel(), absolute);
        boolean workshop = colony.isClearWorksite(helper.getLevel(), absolute, false);
        if (!house || !workshop) helper.fail("Flat stone should support construction: house=" + house + " workshop=" + workshop);
        helper.setBlock(site.offset(1, 0, 1), Blocks.OBSIDIAN);
        if (colony.isClearBuildSite(helper.getLevel(), absolute) || colony.isClearWorksite(helper.getLevel(), absolute, false))
            helper.fail("Stone plot planning ignored an existing obstacle");
        if (!helper.getBlockState(site.offset(1, 0, 1)).is(Blocks.OBSIDIAN)) helper.fail("Planning altered obstacle");
        helper.succeed();
    }

    private static void testExpansion(GameTestHelper helper, JobType type, BuildingType buildingType, Block stationBlock) {
        for (int x = 20; x <= 75; x++) for (int z = 35; z <= 75; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos center = helper.absolutePos(new BlockPos(36, 1, 56));
        BlockPos site = new BlockPos(56, 1, 56), absSite = helper.absolutePos(site);
        helper.setBlock(site, stationBlock);
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        if (buildingType == BuildingType.WAREHOUSE) colony.getWarehouseManager().registerStorage(helper.getLevel(), absSite);
        colony.registerPlayerBuilding(new Building(BuildingType.TOWN_HALL, center, CultureType.ROMAN, true));
        colony.registerPlayerBuilding(new Building(BuildingType.HOUSE, center.north(16), CultureType.ROMAN, true));
        colony.addCitizen(new CitizenData(UUID.randomUUID(), "Builder", JobType.BUILDER));
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), site.offset(0, 0, 2));
        worker.setNoAi(true);
        worker.setColony(colony);
        worker.setJob(type);
        colony.getCitizens().stream().filter(c -> c.uuid.equals(worker.getUUID())).findFirst().orElseThrow().job = type;
        worker.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), absSite));
        colony.depositToWarehouse("minecraft:oak_log", 300);
        colony.depositToWarehouse("minecraft:cobblestone", 300);
        colony.depositToWarehouse("minecraft:coal", 10);
        // An existing worksite elsewhere must not suppress a second worker's site.
        colony.registerPlayerBuilding(new Building(buildingType, center.south(18), CultureType.ROMAN, true));
        // Unsupported old placeholders must remain untouched and not prevent new work.
        var legacyTag = new Building(buildingType, center.south(14), CultureType.ROMAN).save();
        legacyTag.putInt("planVersion", 6);
        var legacy = Building.load(legacyTag);
        colony.registerPlayerBuilding(legacy);
        helper.setBlock(site.north(3), Blocks.OBSIDIAN);
        helper.runAtTickTime(200, () -> {
            colony.tick(helper.getLevel());
            if (colony.getBuildings().stream().anyMatch(b -> b.getType() == buildingType && b.hasSupportedPlan()))
                helper.fail("Worksite queued with a blocked entrance");
            helper.setBlock(site.north(3), Blocks.AIR);
            if (buildingType == BuildingType.WAREHOUSE) colony.getWarehouseManager().unregisterStorage(helper.getLevel(), absSite);
        });
        helper.runAtTickTime(400, () -> {
            colony.tick(helper.getLevel());
            if (buildingType == BuildingType.WAREHOUSE) {
                if (colony.getBuildings().stream().anyMatch(b -> b.getType() == buildingType && b.hasSupportedPlan()))
                    helper.fail("Warehouse queued around an unshared barrel");
                colony.getWarehouseManager().registerStorage(helper.getLevel(), absSite);
            } else {
                verifyExpansion(helper, colony, buildingType, legacy, absSite);
            }
        });
        if (buildingType == BuildingType.WAREHOUSE) helper.runAtTickTime(600, () -> {
            colony.tick(helper.getLevel());
            verifyExpansion(helper, colony, buildingType, legacy, absSite);
        });
    }

    private static void verifyExpansion(GameTestHelper helper, Colony colony, BuildingType type, Building legacy, BlockPos station) {
        var plan = colony.getBuildings().stream().filter(b -> b.getType() == type && b.hasSupportedPlan()).findFirst().orElse(null);
        if (plan == null || !plan.getPos().equals(station) || legacy.hasSupportedPlan() || !legacy.getRemainingPlacements().isEmpty())
            helper.fail("No supported plan at claimed station, or legacy placeholder changed: " + colony.getExpansionStatus());
        helper.succeed();
    }
}
