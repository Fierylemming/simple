package com.simplecolonies.entity;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class SocialGameTests {
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void idleAdultsApproachSameColonyNeighbors(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        var first = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(4, 1, 8));
        var second = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(10, 1, 8));
        first.setNoAi(true); second.setNoAi(true);
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(8, 1, 8)));
        first.setColony(colony); second.setColony(colony);
        helper.runAtTickTime(5, () -> {
            first.trySocialize(helper.getLevel());
            var walk = first.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
            if (walk.isEmpty() || !walk.get().getTarget().currentBlockPosition().equals(second.blockPosition()))
                helper.fail("Idle villager did not approach its same-colony neighbor");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void civicMeetingRequiresEnclosedReachableHall(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos hall = new BlockPos(10, 1, 9);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            for (int y = 0; y <= 2; y++) if (Math.abs(x) == 2 || Math.abs(z) == 2)
                helper.setBlock(hall.offset(x, y, z), Blocks.COBBLESTONE);
            helper.setBlock(hall.offset(x, 3, z), Blocks.OAK_PLANKS);
        }
        var door = helper.absolutePos(hall.north(2));
        var state = Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.NORTH);
        helper.getLevel().setBlock(door, state, 2);
        helper.getLevel().setBlock(door.above(), state.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(8, 1, 8)));
        colony.registerPlayerBuilding(new com.simplecolonies.building.Building(com.simplecolonies.building.BuildingType.MARKET,
                helper.absolutePos(hall), CultureType.ROMAN, true));
        var villager = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(5, 1, 5));
        villager.setColony(colony); villager.setNoAi(true);
        helper.runAtTickTime(20, () -> {
            villager.setOnGround(true);
            villager.seekCivicMeeting(helper.getLevel(), colony);
            var walk = villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
            if (walk.isEmpty() || !walk.get().getTarget().currentBlockPosition().equals(helper.absolutePos(hall)))
                helper.fail("Idle citizen did not seek the reachable civic hall");
            villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
            // A removed wall makes the hall unsafe, even though its marker remains.
            helper.setBlock(hall.east(2), Blocks.AIR);
            helper.setBlock(hall.east(2).above(), Blocks.AIR);
            villager.seekCivicMeeting(helper.getLevel(), colony);
            if (villager.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET))
                helper.fail("Citizen sought an unenclosed civic hall");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1500)
    public static void idleCitizensShareVanillaGossip(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        var first = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(7, 1, 8));
        var second = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(9, 1, 8));
        first.setNoAi(true); second.setNoAi(true);
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(8, 1, 8)));
        first.setColony(colony); second.setColony(colony);
        UUID topic = UUID.randomUUID();
        second.getGossips().add(topic, GossipType.MINOR_POSITIVE, 50);
        helper.runAtTickTime(1300, () -> {
            first.trySocialize(helper.getLevel());
            if (first.getGossips().getReputation(topic, type -> true) <= 0) helper.fail("Villagers did not exchange gossip");
            helper.succeed();
        });
    }
}
