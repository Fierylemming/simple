package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ColonyVillager;
import com.simplecolonies.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class AnimalApproachGameTests {
    private static final class Probe extends AbstractJob {
        Probe(ColonyVillager worker, Colony colony) { super(worker, colony, JobType.SHEPHERD); }
        protected void doWork(ServerLevel level) { }
        void approach(ServerLevel level, Animal first, Animal second) { approachPair(level, first, second); }
        int delay() { return ticksUntilNextWork; }
    }

    @GameTest(template = "empty")
    public static void livestockPairApproachRetriesAfterJump(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        var colony = new Colony(1, CultureType.ROMAN, helper.absolutePos(new BlockPos(8, 1, 8)));
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(3, 1, 8));
        var first = helper.spawn(EntityType.SHEEP, new BlockPos(8, 1, 8));
        var second = helper.spawn(EntityType.SHEEP, new BlockPos(9, 1, 8));
        worker.setOnGround(false);
        var probe = new Probe(worker, colony);
        probe.approach(helper.getLevel(), first, second);
        if (probe.getState() != AbstractJob.JobState.GOING_TO_WORK || probe.delay() != 0)
            helper.fail("Airborne worker did not retry animal path on landing");
        helper.succeed();
    }
}
