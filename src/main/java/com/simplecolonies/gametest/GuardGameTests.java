package com.simplecolonies.gametest;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.villager.job.SoldierJob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class GuardGameTests {
    @GameTest(template = "isolated", timeoutTicks = 1200)
    public static void guardApproachesAndAttacksHostile(GameTestHelper helper) {
        for (int x = 45; x <= 67; x++) for (int z = 50; z <= 62; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        var guard = helper.spawn(EntityType.VILLAGER, new BlockPos(51, 1, 56));
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(60, 1, 56));
        zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(56, 1, 56)));
        SoldierJob job = new SoldierJob(guard, colony);
        for (int tick = 1; tick < 1200; tick++) helper.runAtTickTime(tick, () -> {
            float health = zombie.getHealth();
            job.tick(helper.getLevel());
            if (zombie.getHealth() < health && guard.distanceToSqr(zombie) > 4)
                helper.fail("Guard attacked a hostile remotely");
        });
        helper.succeedWhen(() -> {
            if (zombie.isAlive()) helper.fail("Guard " + job.getState() + " at " + guard.blockPosition().toShortString()
                    + ", hostile at " + zombie.blockPosition().toShortString() + ", distance " + guard.distanceToSqr(zombie)
                    + ", health " + zombie.getHealth());
        });
    }
    @GameTest(template = "isolated", timeoutTicks = 400)
    public static void guardWearsRealSwordInCombat(GameTestHelper helper) {
        for (int x = 48; x <= 64; x++) for (int z = 50; z <= 62; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        var guard = helper.spawn(EntityType.VILLAGER, new BlockPos(54, 1, 56));
        var sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(8);
        guard.setItemSlot(EquipmentSlot.MAINHAND, sword);
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(55, 1, 56));
        zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(56, 1, 56)));
        SoldierJob job = new SoldierJob(guard, colony);
        float initial = zombie.getHealth();
        for (int tick = 1; tick < 400; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.succeedWhen(() -> {
            if (zombie.getHealth() >= initial || guard.getMainHandItem().getDamageValue() <= 8)
                helper.fail("Sword did not deal damage and lose durability");
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 1600)
    public static void soldierFollowsBeyondColonyAndReturnsAfterDismissal(GameTestHelper helper) {
        for (int x = 12; x <= 100; x++) for (int z = 50; z <= 62; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        var guard = helper.spawn(EntityType.VILLAGER, new BlockPos(68, 1, 56));
        var leader = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var position = helper.absolutePos(new BlockPos(84, 1, 56));
        leader.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(18, 1, 56)));
        colony.setLeader(leader);
        if (colony.isInside(guard.blockPosition()) || colony.isInside(leader.blockPosition()))
            helper.fail("Fixture must begin outside the colony");
        SoldierJob job = new SoldierJob(guard, colony);
        job.setCommander(leader, true);
        boolean[] reachedLeader = {false};
        double[] dismissedDistance = {0};
        for (int tick = 1; tick < 1600; tick++) helper.runAtTickTime(tick, () -> {
            job.tick(helper.getLevel());
            if (!reachedLeader[0] && guard.distanceToSqr(leader) <= 36) {
                reachedLeader[0] = true;
                dismissedDistance[0] = colony.getCenter().distSqr(guard.blockPosition());
                job.setCommander(leader, false);
            }
        });
        helper.succeedWhen(() -> {
            if (!reachedLeader[0] || job.isFollowing()
                    || colony.getCenter().distSqr(guard.blockPosition()) >= dismissedDistance[0] - 25)
                helper.fail("Follow/return failed: followed=" + reachedLeader[0] + " state=" + job.getState()
                        + " guard=" + guard.blockPosition().toShortString());
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void followOrderSurvivesReloadAndRevokesOnLeadershipChange(GameTestHelper helper) {
        var worker = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(8, 1, 8));
        worker.setJob(com.simplecolonies.villager.job.JobType.GUARD);
        var leader = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(8, 1, 8)));
        colony.setLeader(leader);
        var job = new SoldierJob(worker, colony);
        job.setCommander(leader, true);
        var saved = new net.minecraft.nbt.CompoundTag();
        worker.saveWithoutId(saved);
        var restored = com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get().create(helper.getLevel());
        if (restored == null) { helper.fail("Could not create restored defender"); return; }
        restored.load(saved);
        var resumed = new SoldierJob(restored, colony);
        if (!resumed.isFollowing() || !leader.getUUID().equals(restored.getFollowCommanderId()))
            helper.fail("Follow order did not survive entity reload");
        // No leader in this colony represents a revoked/changed leadership.
        var other = new Colony(1, CultureType.ROMAN, colony.getCenter());
        var revoked = new SoldierJob(restored, other);
        if (revoked.hasValidCommander(helper.getLevel()) || revoked.isFollowing() || restored.getFollowCommanderId() != null)
            helper.fail("Former leader retained a follow order");
        job.setCommander(leader, true);
        worker.setJob(com.simplecolonies.villager.job.JobType.FARMER);
        if (worker.getFollowCommanderId() != null) helper.fail("Profession change retained a military order");
        helper.succeed();
    }

    @GameTest(template = "isolated", timeoutTicks = 1000)
    public static void soldierFollowsLeaderAndStopsWhenReleased(GameTestHelper helper) {
        for (int x = 40; x <= 72; x++) for (int z = 40; z <= 72; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        var guard = helper.spawn(EntityType.VILLAGER, new BlockPos(50, 1, 56));
        var leader = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var position = helper.absolutePos(new BlockPos(62, 1, 56));
        leader.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(56, 1, 56)));
        colony.setLeader(leader);
        SoldierJob job = new SoldierJob(guard, colony);
        job.setCommander(leader, true);
        for (int tick = 1; tick < 1000; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.succeedWhen(() -> {
            if (!job.isFollowing() || guard.distanceToSqr(leader) > 36)
                helper.fail("Soldier has not followed leader: " + job.getState() + " at " + guard.position());
            job.setCommander(leader, false);
            if (job.isFollowing() || !guard.getNavigation().isDone()) helper.fail("Follow command did not stop navigation");
        });
    }
}
