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
        boolean[] sawTrackedDoor = {false};
        for (int tick = 1; tick < 1200; tick++) helper.runAtTickTime(tick, () -> {
            villager.seekShelter(helper.getLevel(), colony);
            var remembered = villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.DOORS_TO_CLOSE);
            if (remembered.isPresent() && remembered.get().contains(net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), absDoor)))
                sawTrackedDoor[0] = true;
        });
        helper.succeedWhen(() -> {
            BlockPos p = villager.blockPosition();
            if (Math.abs(p.getX() - colony.getCenter().getX()) > 2 || Math.abs(p.getZ() - colony.getCenter().getZ()) > 2)
                helper.fail("Villager has not entered hall: " + p.toShortString()
                        + "; alive=" + villager.isAlive() + "; sleeping=" + villager.isSleeping()
                        + "; navigationDone=" + villager.getNavigation().isDone()
                        + "; walkTarget=" + villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
                        + "; panic=" + villager.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.IS_PANICKING)
                        + "; hostile=" + villager.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_HOSTILE));
            var state = helper.getLevel().getBlockState(absDoor);
            if (!state.is(Blocks.OAK_DOOR) || state.getValue(DoorBlock.OPEN)) helper.fail("Hall door is not closed: " + state
                    + "; villager=" + p.toShortString() + "; navigationDone=" + villager.getNavigation().isDone()
                    + "; walkTarget=" + villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
                            .map(target -> target.getTarget().currentBlockPosition().toShortString())
                    + "; path=" + villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.PATH)
                    + "; doorsToClose=" + villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.DOORS_TO_CLOSE)
                    + "; sawTrackedDoor=" + sawTrackedDoor[0] + "; center=" + colony.getCenter().toShortString()
                    + "; door=" + absDoor.toShortString() + "; nearbyLiving="
                    + helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                            new net.minecraft.world.phys.AABB(absDoor).inflate(2)).size()
                    + "; powered=" + helper.getLevel().hasNeighborSignal(absDoor));
        });
    }
    // Independent fixtures exercise vanilla door/path behavior under different
    // random navigation choices; one lucky path is not sufficient evidence.
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void bedlessHallDoorRepeatOne(GameTestHelper helper) {
        bedlessVillagerEntersHallAndDoorCloses(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void bedlessHallDoorRepeatTwo(GameTestHelper helper) {
        bedlessVillagerEntersHallAndDoorCloses(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void shelterWalkRedirectsVanillaWanderingOutdoors(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos hall = new BlockPos(8, 1, 9), door = hall.north(3);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            for (int y = 0; y <= 2; y++) if ((Math.abs(x) == 3 || Math.abs(z) == 3) && !(x == 0 && z == -3 && y < 2))
                helper.setBlock(hall.offset(x, y, z), Blocks.COBBLESTONE);
            helper.setBlock(hall.offset(x, 3, z), Blocks.OAK_PLANKS);
        }
        var absoluteDoor = helper.absolutePos(door);
        var low = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        helper.getLevel().setBlock(absoluteDoor, low, 2);
        helper.getLevel().setBlock(absoluteDoor.above(), low.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(hall));
        colony.registerPlayerBuilding(new Building(BuildingType.TOWN_HALL, colony.getCenter(), CultureType.ROMAN, true));
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), hall.west());
        helper.runAtTickTime(20, () -> {
            var inside = helper.absolutePos(hall.west());
            worker.moveTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
            worker.setOnGround(true);
            var outside = helper.absolutePos(new BlockPos(8, 1, 2));
            worker.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                    new net.minecraft.world.entity.ai.memory.WalkTarget(outside, 0.7f, 0));
            var before = worker.blockPosition();
            worker.seekShelter(helper.getLevel(), colony);
            var walk = worker.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
            if (!worker.blockPosition().equals(before) || walk.isEmpty()
                    || walk.get().getTarget().currentBlockPosition().distSqr(colony.getCenter()) > 2)
                helper.fail("Did not redirect outdoor wandering to shelter without teleporting: " + walk);
            // Vanilla may assign another outdoor walk immediately, well before
            // the next expensive shelter search. It must still be redirected.
            worker.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                    new net.minecraft.world.entity.ai.memory.WalkTarget(outside, 0.7f, 0));
            worker.seekShelter(helper.getLevel(), colony);
            walk = worker.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
            if (walk.isEmpty() || walk.get().getTarget().currentBlockPosition().distSqr(colony.getCenter()) > 2)
                helper.fail("Outdoor wander was accepted during the shelter search cooldown");
            // Simulate a door vanilla recorded as opened by this villager. Do
            // not shut it while another living entity occupies the doorway.
            var opened = low.setValue(DoorBlock.OPEN, true);
            helper.getLevel().setBlock(absoluteDoor, opened, 2);
            helper.getLevel().setBlock(absoluteDoor.above(), opened.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
            worker.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.DOORS_TO_CLOSE,
                    new java.util.HashSet<>(java.util.Set.of(net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), absoluteDoor))));
            var blocker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), door);
            worker.seekShelter(helper.getLevel(), colony);
            if (!helper.getLevel().getBlockState(absoluteDoor).getValue(DoorBlock.OPEN)) helper.fail("Closed an occupied doorway");
            blocker.discard();
            worker.seekShelter(helper.getLevel(), colony);
            if (helper.getLevel().getBlockState(absoluteDoor).getValue(DoorBlock.OPEN)) helper.fail("Did not close the remembered empty door");
            // A cached interior cannot hold a villager in a newly breached hall.
            helper.setBlock(hall.east(3), Blocks.AIR);
            helper.setBlock(hall.east(3).above(), Blocks.AIR);
            worker.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                    new net.minecraft.world.entity.ai.memory.WalkTarget(outside, 0.7f, 0));
            worker.seekShelter(helper.getLevel(), colony);
            walk = worker.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
            if (walk.isEmpty() || !walk.get().getTarget().currentBlockPosition().equals(outside))
                helper.fail("Cached shelter overrode a walk after the hall was breached");
            helper.setBlock(hall.east(3), Blocks.COBBLESTONE);
            helper.setBlock(hall.east(3).above(), Blocks.COBBLESTONE);
            worker.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.IS_PANICKING, true);
            if (!worker.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.IS_PANICKING))
                helper.fail("Panic test memory is not registered on this villager brain");
            worker.seekShelter(helper.getLevel(), colony);
            walk = worker.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
            if (walk.isEmpty() || !walk.get().getTarget().currentBlockPosition().equals(outside))
                helper.fail("Shelter redirected a panicking villager");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void shelterSearchRetriesAfterLanding(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos hall = new BlockPos(8, 1, 9), door = hall.north(3);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            for (int y = 0; y <= 2; y++) if ((Math.abs(x) == 3 || Math.abs(z) == 3) && !(x == 0 && z == -3 && y < 2))
                helper.setBlock(hall.offset(x, y, z), Blocks.COBBLESTONE);
            helper.setBlock(hall.offset(x, 3, z), Blocks.OAK_PLANKS);
        }
        var absDoor = helper.absolutePos(door);
        var low = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        helper.getLevel().setBlock(absDoor, low, 2);
        helper.getLevel().setBlock(absDoor.above(), low.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(hall));
        colony.registerPlayerBuilding(new Building(BuildingType.TOWN_HALL, colony.getCenter(), CultureType.ROMAN, true));
        var villager = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(8, 1, 3));
        helper.runAtTickTime(20, () -> {
            villager.setOnGround(false);
            villager.seekShelter(helper.getLevel(), colony);
            villager.setOnGround(true);
            villager.seekShelter(helper.getLevel(), colony);
            var walk = villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
            if (walk.isEmpty() || walk.get().getTarget().currentBlockPosition().distSqr(colony.getCenter()) > 2)
                helper.fail("Shelter search did not retry when the villager landed: " + walk);
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void rejectsRoofedShelterWithWallOpening(GameTestHelper helper) {
        BlockPos hall = new BlockPos(8, 1, 9);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            helper.setBlock(hall.offset(x, -1, z), Blocks.GRASS_BLOCK);
            for (int y = 0; y <= 2; y++) if (Math.abs(x) == 3 || Math.abs(z) == 3)
                helper.setBlock(hall.offset(x, y, z), Blocks.COBBLESTONE);
            helper.setBlock(hall.offset(x, 3, z), Blocks.OAK_PLANKS);
        }
        BlockPos door = helper.absolutePos(hall.north(3));
        var low = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        helper.getLevel().setBlock(door, low, 2);
        helper.getLevel().setBlock(door.above(), low.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        BlockPos inside = helper.absolutePos(hall);
        if (!ShelterSafety.enclosed(helper.getLevel(), inside)) helper.fail("Closed hall rejected");
        helper.setBlock(hall.east(3), Blocks.AIR);
        helper.setBlock(hall.east(3).above(), Blocks.AIR);
        if (ShelterSafety.enclosed(helper.getLevel(), inside)) helper.fail("Roofed shelter with a wall opening was accepted");
        helper.setBlock(hall.east(3), Blocks.OAK_FENCE);
        if (ShelterSafety.enclosed(helper.getLevel(), inside)) helper.fail("Fence was treated as a solid wall");
        helper.succeed();
    }

    @GameTest(template = "isolated", timeoutTicks = 1800)
    public static void villagersFindReachableConstructedHouse(GameTestHelper helper) {
        for (int x = 24; x <= 75; x++) for (int z = 25; z <= 70; z++) {
            helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
            // Clear the artificial barrier ceiling and terrain immediately above it.
            // Otherwise a roofless marker appears sheltered in the underground test world.
            for (int y = 5; y <= 8; y++) helper.setBlock(x, y, z, Blocks.AIR);
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
