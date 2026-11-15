package com.simplecolonies.gametest;

import com.simplecolonies.block.ModBlocks;
import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import com.simplecolonies.culture.CultureType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class TownHallRepairProtectionGameTests {
    @GameTest(template = "empty")
    public static void hallRepairDoesNotReplacePropertyOrBuildAFoundation(GameTestHelper helper) throws Exception {
        var relative = new BlockPos(8, 1, 8); var center = helper.absolutePos(relative);
        var colony = new Colony(GameTestColonyIds.next(), CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.TOWN_HALL, center, CultureType.ROMAN, true));
        var method = ColonyManager.class.getDeclaredMethod("placeTownHallIfNeeded", ServerLevel.class, Colony.class);
        method.setAccessible(true);
        var manager = ColonyManager.getInstance();
        helper.setBlock(relative.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(relative, Blocks.DANDELION);
        method.invoke(manager, helper.getLevel(), colony);
        if (!helper.getBlockState(relative).is(Blocks.DANDELION)) helper.fail("Hall repair cleared a flower");
        helper.setBlock(relative, Blocks.AIR); helper.setBlock(relative.below(), Blocks.AIR);
        method.invoke(manager, helper.getLevel(), colony);
        if (!helper.getBlockState(relative).isAir() || !helper.getBlockState(relative.below()).isAir())
            helper.fail("Hall repair created a foundation in a hole");
        helper.setBlock(relative.below(), Blocks.GRASS_BLOCK);
        colony.updateMarkerBuilding(center.east(7), false, BuildingType.PROTECTED_PLOT);
        method.invoke(manager, helper.getLevel(), colony);
        if (!helper.getBlockState(relative).isAir()) helper.fail("Hall repair ignored protected plot");
        colony.updateMarkerBuilding(center.east(7), true, null);
        method.invoke(manager, helper.getLevel(), colony);
        if (!helper.getBlockState(relative).is(ModBlocks.TOWN_HALL.get())) helper.fail("Safe hall was not repaired");
        helper.succeed();
    }
}
