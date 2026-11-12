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
    public static void house(GameTestHelper helper) { runBuilder(helper, BuildingType.HOUSE, null); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void builderHut(GameTestHelper helper) { runBuilder(helper, BuildingType.BUILDER_HUT, Blocks.ANVIL); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void foresterHut(GameTestHelper helper) { runBuilder(helper, BuildingType.LUMBERJACK_HUT, Blocks.CAMPFIRE); }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void mine(GameTestHelper helper) { runBuilder(helper, BuildingType.MINE, Blocks.FURNACE); }

    private static void runBuilder(GameTestHelper helper, BuildingType type, Block workstation) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos relative = new BlockPos(8, 1, 9);
        BlockPos origin = helper.absolutePos(relative);
        if (workstation != null) helper.setBlock(relative, workstation);
        Colony colony = new Colony(0, CultureType.ROMAN, origin);
        colony.depositToWarehouse("minecraft:oak_log", 300);
        colony.depositToWarehouse("minecraft:cobblestone", 300);
        colony.depositToWarehouse("minecraft:coal", 10);
        Building blueprint = new Building(type, origin, CultureType.ROMAN);
        if (!colony.registerPlayerBuilding(blueprint)) helper.fail("Failed to queue test plan");
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
            if (workstation != null) helper.assertBlockPresent(workstation, relative);
        });
    }
}
