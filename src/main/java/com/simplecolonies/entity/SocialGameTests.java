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
