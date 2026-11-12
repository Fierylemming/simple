package com.simplecolonies.gametest;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingState;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.villager.job.BuilderJob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises actual tick-driven builder movement and world placement in a flat loaded plot. */
@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class ConstructionGameTests {
    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void houseRoman0(GameTestHelper helper) { runBuilder(helper, BuildingType.HOUSE, null, CultureType.ROMAN, 0); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void houseRoman1(GameTestHelper helper) { runBuilder(helper, BuildingType.HOUSE, null, CultureType.ROMAN, 1); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void houseRoman2(GameTestHelper helper) { runBuilder(helper, BuildingType.HOUSE, null, CultureType.ROMAN, 2); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void houseGreek0(GameTestHelper helper) { runBuilder(helper, BuildingType.HOUSE, null, CultureType.GREEK, 0); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void houseGreek1(GameTestHelper helper) { runBuilder(helper, BuildingType.HOUSE, null, CultureType.GREEK, 1); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void houseGreek2(GameTestHelper helper) { runBuilder(helper, BuildingType.HOUSE, null, CultureType.GREEK, 2); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void houseEgyptian0(GameTestHelper helper) { runBuilder(helper, BuildingType.HOUSE, null, CultureType.EGYPTIAN, 0); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void houseEgyptian1(GameTestHelper helper) { runBuilder(helper, BuildingType.HOUSE, null, CultureType.EGYPTIAN, 1); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void houseEgyptian2(GameTestHelper helper) { runBuilder(helper, BuildingType.HOUSE, null, CultureType.EGYPTIAN, 2); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void housePersian0(GameTestHelper helper) { runBuilder(helper, BuildingType.HOUSE, null, CultureType.PERSIAN, 0); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void housePersian1(GameTestHelper helper) { runBuilder(helper, BuildingType.HOUSE, null, CultureType.PERSIAN, 1); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void housePersian2(GameTestHelper helper) { runBuilder(helper, BuildingType.HOUSE, null, CultureType.PERSIAN, 2); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void houseMesopotamian0(GameTestHelper helper) { runBuilder(helper, BuildingType.HOUSE, null, CultureType.MESOPOTAMIAN, 0); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void houseMesopotamian1(GameTestHelper helper) { runBuilder(helper, BuildingType.HOUSE, null, CultureType.MESOPOTAMIAN, 1); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void houseMesopotamian2(GameTestHelper helper) { runBuilder(helper, BuildingType.HOUSE, null, CultureType.MESOPOTAMIAN, 2); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void builderHut(GameTestHelper helper) { runBuilder(helper, BuildingType.BUILDER_HUT, Blocks.ANVIL, CultureType.ROMAN, -1); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void foresterHut(GameTestHelper helper) { runBuilder(helper, BuildingType.LUMBERJACK_HUT, Blocks.CAMPFIRE, CultureType.ROMAN, -1); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void mine(GameTestHelper helper) { runBuilder(helper, BuildingType.MINE, Blocks.FURNACE, CultureType.ROMAN, -1); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void smithy(GameTestHelper helper) { runBuilder(helper, BuildingType.BLACKSMITH, Blocks.SMITHING_TABLE, CultureType.ROMAN, -1); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void butcherHut(GameTestHelper helper) { runBuilder(helper, BuildingType.BUTCHER_HUT, Blocks.SMOKER, CultureType.ROMAN, -1); }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void blockedPlacementDoesNotConsume(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos relative = new BlockPos(8, 1, 9);
        BlockPos origin = helper.absolutePos(relative);
        helper.setBlock(relative, Blocks.FURNACE);
        Colony colony = new Colony(0, CultureType.ROMAN, origin);
        colony.depositToWarehouse("minecraft:oak_log", 200);
        Building building = new Building(BuildingType.MINE, origin, CultureType.ROMAN);
        colony.registerPlayerBuilding(building);
        var next = building.getNextPlacement();
        helper.getLevel().setBlock(next.pos, Blocks.OBSIDIAN.defaultBlockState(), 3);
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 1, 6));
        BuilderJob job = new BuilderJob(villager, colony);
        for (int tick = 1; tick < 200; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.succeedWhen(() -> {
            if (building.getState() != BuildingState.BLOCKED) helper.fail("Expected blocked placement");
            if (!helper.getLevel().getBlockState(next.pos).is(Blocks.OBSIDIAN)) helper.fail("Obstruction was changed");
            if (colony.getWarehouseCount("minecraft:oak_log") != 200) helper.fail("Blocked construction consumed materials");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void missingMaterialsLeaveWorldUnchanged(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 9));
        Colony colony = new Colony(0, CultureType.GREEK, origin);
        Building building = new Building(BuildingType.HOUSE, origin, CultureType.GREEK);
        colony.registerPlayerBuilding(building);
        var next = building.getNextPlacement();
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 1, 6));
        BuilderJob job = new BuilderJob(villager, colony);
        for (int tick = 1; tick < 200; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.succeedWhen(() -> {
            if (building.getState() != BuildingState.NEEDS_RESOURCES) helper.fail("Expected missing materials");
            if (!helper.getLevel().getBlockState(next.pos).isAir()) helper.fail("Placed without materials");
        });
    }

    private static void runBuilder(GameTestHelper helper, BuildingType type, Block workstation, CultureType culture, int variant) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos relative = new BlockPos(8, 1, 9);
        if (variant >= 0) {
            for (int dx = -1; dx <= 1; dx++) {
                BlockPos absolute = helper.absolutePos(relative.offset(dx, 0, 0));
                if (Math.floorMod(absolute.getX() * 31 + absolute.getZ() * 17, 3) == variant) {
                    relative = relative.offset(dx, 0, 0); break;
                }
            }
        }
        BlockPos origin = helper.absolutePos(relative);
        if (workstation != null) helper.setBlock(relative, workstation);
        final BlockPos stationRelative = relative;
        Colony colony = new Colony(0, culture, origin);
        colony.depositToWarehouse("minecraft:oak_log", 300);
        colony.depositToWarehouse("minecraft:cobblestone", 300);
        colony.depositToWarehouse("minecraft:coal", 10);
        Building blueprint = new Building(type, origin, culture);
        if (variant >= 0 && blueprint.save().getInt("houseVariant") != variant) helper.fail("Wrong house variant");
        if (!colony.registerPlayerBuilding(blueprint)) helper.fail("Failed to queue test plan");
        BlockPos door = blueprint.getRemainingPlacements().stream()
                .filter(p -> p.state.is(Blocks.OAK_DOOR)).findFirst().orElseThrow().pos;
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 1, 2));
        BuilderJob job = new BuilderJob(villager, colony);
        for (int tick = 1; tick < 2400; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.succeedWhen(() -> {
            if (blueprint.getState() != BuildingState.COMPLETE) {
                var next = blueprint.getNextPlacement();
                helper.fail("Builder " + job.getState() + " building " + blueprint.getState()
                        + " at " + villager.blockPosition().toShortString()
                        + " next " + (next == null ? "none" : next.pos.toShortString())
                        + " distance " + (next == null ? 0 : villager.distanceToSqr(next.pos.getX()+0.5, next.pos.getY()+0.5, next.pos.getZ()+0.5)));
            }
            if (workstation != null) helper.assertBlockPresent(workstation, stationRelative);
            if (type == BuildingType.BLACKSMITH) helper.assertBlockPresent(Blocks.FURNACE, stationRelative.east());
            var lower = helper.getLevel().getBlockState(door);
            var upper = helper.getLevel().getBlockState(door.above());
            if (!lower.is(Blocks.OAK_DOOR) || !upper.is(Blocks.OAK_DOOR)
                    || lower.getValue(net.minecraft.world.level.block.DoorBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                    || upper.getValue(net.minecraft.world.level.block.DoorBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)
                helper.fail("Completed shelter missing its two-part door");
        });
    }
}
