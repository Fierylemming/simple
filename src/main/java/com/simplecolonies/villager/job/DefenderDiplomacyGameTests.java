package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class DefenderDiplomacyGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void defendersFightOnlyEligibleEnemiesDuringMutualWar(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        var center = helper.absolutePos(new BlockPos(8, 1, 8));
        var a = new Colony(20001, CultureType.ROMAN, center);
        var b = new Colony(20002, CultureType.GREEK, center.east(20));
        var guard = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 1, 8)); guard.setNoAi(true);
        var enemy = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(9, 1, 8)); enemy.setNoAi(true);
        enemy.setColony(b); enemy.setJob(JobType.GUARD);
        long day = Math.floorDiv(helper.getLevel().getDayTime(), 24000L);
        float initial = enemy.getHealth();
        java.util.function.Supplier<SoldierJob> job = () -> new SoldierJob(guard, a, id -> id == b.getId() ? b : null);
        job.get().tick(helper.getLevel());
        if (enemy.getHealth() != initial) helper.fail("Guard attacked a neutral defender");
        a.getDiplomacy().declareWar(a, b, day);
        enemy.setJob(JobType.FARMER);
        job.get().tick(helper.getLevel());
        if (enemy.getHealth() != initial) helper.fail("Guard attacked a civilian");
        enemy.setJob(JobType.GUARD); enemy.setBaby(true);
        job.get().tick(helper.getLevel());
        if (enemy.getHealth() != initial) helper.fail("Guard attacked a child");
        enemy.setBaby(false);
        job.get().tick(helper.getLevel());
        if (enemy.getHealth() >= initial || guard.distanceToSqr(enemy) > 4)
            helper.fail("Guard did not attack a nearby enemy defender during war");
        enemy.invulnerableTime = 0; float after = enemy.getHealth();
        a.getDiplomacy().makePeace(a, b, day);
        job.get().tick(helper.getLevel());
        if (enemy.getHealth() != after) helper.fail("Guard attacked during a peace treaty");
        a.getDiplomacy().proposeAlliance(a, b);
        job.get().tick(helper.getLevel());
        if (enemy.getHealth() != after) helper.fail("Guard attacked an ally");
        helper.succeed();
    }
}
