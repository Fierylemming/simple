package com.simplecolonies.entity;

import com.simplecolonies.block.ModBlocks;
import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class ShelterGameTests {
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void bedlessVillagerEntersHallAndDoorCloses(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos hall = new BlockPos(8, 1, 9), door = hall.offset(0, 0, -3);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            for (int y = 0; y <= 2; y++) if ((Math.abs(x) == 3 || Math.abs(z) == 3) && !(x == 0 && z == -3 && y < 2))
                helper.setBlock(hall.offset(x, y, z), Blocks.COBBLESTONE);
            helper.setBlock(hall.offset(x, 3, z), Blocks.OAK_PLANKS);
        }
        helper.setBlock(hall, ModBlocks.TOWN_HALL.get());
        var low = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        var absDoor = helper.absolutePos(door);
        helper.getLevel().setBlock(absDoor, low, 2);
        helper.getLevel().setBlock(absDoor.above(), low.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(hall));
        colony.registerPlayerBuilding(new Building(BuildingType.TOWN_HALL, colony.getCenter(), CultureType.ROMAN, true));
        ColonyVillager villager = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(8, 1, 3));
        for (int tick = 1; tick < 1200; tick++) helper.runAtTickTime(tick, () -> villager.seekShelter(helper.getLevel(), colony));
        helper.succeedWhen(() -> {
            BlockPos p = villager.blockPosition();
            if (Math.abs(p.getX() - colony.getCenter().getX()) > 2 || Math.abs(p.getZ() - colony.getCenter().getZ()) > 2)
                helper.fail("Villager has not entered hall: " + p.toShortString());
            var state = helper.getLevel().getBlockState(absDoor);
            if (!state.is(Blocks.OAK_DOOR) || state.getValue(DoorBlock.OPEN)) helper.fail("Hall door is not closed");
        });
    }
}
