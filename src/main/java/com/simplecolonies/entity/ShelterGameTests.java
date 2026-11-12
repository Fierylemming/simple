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
    @GameTest(template = "isolated", timeoutTicks = 1800)
    public static void villagersFindReachableConstructedHouse(GameTestHelper helper) {
        for (int x = 24; x <= 75; x++) for (int z = 25; z <= 70; z++) {
            helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
            // The GameTest structure adds a barrier ceiling at y=5; it is not a real roof.
            helper.setBlock(x, 5, z, Blocks.AIR);
        }
        var level = helper.getLevel();
        BlockPos blocked = new BlockPos(40, 1, 46), open = new BlockPos(58, 1, 46);
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(blocked));
        // A completed marker without a roof must not be treated as shelter.
        colony.registerPlayerBuilding(new Building(BuildingType.TOWN_HALL,
                helper.absolutePos(new BlockPos(40, 1, 37)), CultureType.ROMAN, true));
        var blockedHouse = placeHouse(helper, blocked);
        var openHouse = placeHouse(helper, open);
        colony.registerPlayerBuilding(blockedHouse);
        colony.registerPlayerBuilding(openHouse);
        // Seal the nearer house after construction. Its roof and floor still exist,
        // but none of its interior spots can be reached from outside.
        int doorZ = blockedHouse.getRemainingPlacements().stream()
                .filter(p -> p.state.getBlock() instanceof DoorBlock).findFirst().orElseThrow().pos.getZ();
        BlockPos door = new BlockPos(blockedHouse.getPos().getX(), blockedHouse.getPos().getY() + 1, doorZ);
        level.setBlock(door, Blocks.COBBLESTONE.defaultBlockState(), 3);
        level.setBlock(door.above(), Blocks.COBBLESTONE.defaultBlockState(), 3);
        var first = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(39, 1, 36));
        var second = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(41, 1, 36));
        for (int tick = 1; tick < 1800; tick++) helper.runAtTickTime(tick, () -> {
            first.seekShelter(level, colony);
            second.seekShelter(level, colony);
        });
        helper.succeedWhen(() -> {
            for (var villager : new ColonyVillager[]{first, second}) {
                BlockPos p = villager.blockPosition(), origin = openHouse.getPos();
                if (p.getY() != origin.getY() + 1 || Math.abs(p.getX() - origin.getX()) > 1
                        || Math.abs(p.getZ() - origin.getZ()) > 1)
                    {
                        var target = origin.above().north();
                        var path = villager.getNavigation().createPath(target, 0);
                        helper.fail("Villager did not reach house: " + p.toShortString()
                                + "; target=" + target.toShortString() + " path=" + (path == null ? "null" : path.canReach() + "/" + path.getEndNode())
                                + "; floor=" + level.getBlockState(target.below()) + "; spot=" + level.getBlockState(target)
                                + "; roof=" + level.getBlockState(target.above(3)));
                    }
            }
        });
    }

    private static Building placeHouse(GameTestHelper helper, BlockPos relative) {
        var level = helper.getLevel();
        var house = new Building(BuildingType.HOUSE, helper.absolutePos(relative), CultureType.ROMAN);
        for (var placement : house.getRemainingPlacements()) {
            level.setBlock(placement.pos, placement.state, 2);
            if (placement.state.getBlock() instanceof DoorBlock)
                level.setBlock(placement.pos.above(), placement.state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        }
        house.setState(com.simplecolonies.building.BuildingState.COMPLETE);
        return house;
    }
}
