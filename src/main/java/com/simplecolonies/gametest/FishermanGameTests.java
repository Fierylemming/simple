package com.simplecolonies.gametest;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.villager.ColonyProfessions;
import com.simplecolonies.villager.job.FishermanJob;
import com.simplecolonies.villager.job.JobType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class FishermanGameTests {
    private static final BlockPos SITE = new BlockPos(54, 1, 54);

    private static Colony setup(GameTestHelper helper) {
        // Broad ground prevents ordinary villager wandering off an artificial test platform.
        for (int x = 24; x <= 87; x++) for (int z = 24; z <= 87; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        for (int x = 58; x <= 61; x++) for (int z = 54; z <= 60; z++) {
            helper.setBlock(x, -1, z, Blocks.STONE);
            helper.setBlock(x, 0, z, Blocks.WATER);
        }
        helper.setBlock(SITE, Blocks.BARREL);
        return new Colony(0, CultureType.ROMAN, helper.absolutePos(SITE));
    }

    // GameTestHelper.spawn marks mobs persistent; natural fish are not persistent.
    private static Cod wildCod(GameTestHelper helper, BlockPos relative) {
        Cod fish = EntityType.COD.create(helper.getLevel());
        if (fish == null) throw new IllegalStateException("Could not create cod");
        BlockPos p = helper.absolutePos(relative);
        fish.moveTo(p.getX() + 0.5, p.getY() + 0.15, p.getZ() + 0.5);
        fish.setNoAi(true);
        fish.setNoGravity(true);
        helper.getLevel().addFreshEntity(fish);
        return fish;
    }

    private static net.minecraft.world.entity.npc.Villager worker(GameTestHelper helper) {
        var worker = helper.spawn(EntityType.VILLAGER, new BlockPos(50, 1, 56));
        worker.setVillagerData(worker.getVillagerData().setProfession(ColonyProfessions.profession(JobType.FISHERMAN)));
        worker.refreshBrain(helper.getLevel());
        worker.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(SITE)));
        return worker;
    }

    @GameTest(template = "isolated", timeoutTicks = 3600)
    public static void constructedHarborLetsFishermanReachShore(GameTestHelper helper) {
        Colony colony = setup(helper);
        colony.depositToWarehouse("minecraft:oak_log", 200);
        colony.depositToWarehouse("minecraft:cobblestone", 100);
        colony.depositToWarehouse("minecraft:coal", 10);
        var harbor = new Building(BuildingType.HARBOR, helper.absolutePos(SITE), CultureType.ROMAN);
        colony.registerPlayerBuilding(harbor);
        Cod first = wildCod(helper, new BlockPos(58, 0, 56));
        Cod second = wildCod(helper, new BlockPos(58, 0, 57));
        Cod third = wildCod(helper, new BlockPos(58, 0, 58));
        var builder = helper.spawn(EntityType.VILLAGER, new BlockPos(50, 1, 50));
        var builderJob = new com.simplecolonies.villager.job.BuilderJob(builder, colony);
        FishermanJob[] job = {null};
        net.minecraft.world.entity.npc.Villager[] fisher = {null};
        StringBuilder trace = new StringBuilder();
        int[] workTicks = {0};
        for (int tick = 1; tick < 3600; tick++) helper.runAtTickTime(tick, () -> {
            if (harbor.getState() != com.simplecolonies.building.BuildingState.COMPLETE) {
                builderJob.tick(helper.getLevel());
            } else {
                if (job[0] == null) {
                    fisher[0] = helper.spawn(EntityType.VILLAGER, SITE.east());
                    fisher[0].setVillagerData(fisher[0].getVillagerData().setProfession(ColonyProfessions.profession(JobType.FISHERMAN)));
                    fisher[0].refreshBrain(helper.getLevel());
                    fisher[0].getBrain().setMemory(MemoryModuleType.JOB_SITE,
                            GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(SITE)));
                    job[0] = new FishermanJob(fisher[0], colony);
                }
                job[0].tick(helper.getLevel());
                if (++workTicks[0] % 80 == 0 && workTicks[0] <= 640) {
                    var path = fisher[0].getNavigation().createPath(helper.absolutePos(new BlockPos(57, 1, 56)), 0);
                    trace.append(workTicks[0]).append(":").append(job[0].getState()).append("/")
                            .append(fisher[0].blockPosition().toShortString()).append("/")
                            .append(path == null ? "null" : path.canReach() + ":" + path.getEndNode()).append(";");
                }
            }
        });
        helper.runAtTickTime(3400, () -> {
            int alive = (first.isAlive() ? 1 : 0) + (second.isAlive() ? 1 : 0) + (third.isAlive() ? 1 : 0);
            if (harbor.getState() != com.simplecolonies.building.BuildingState.COMPLETE || job[0] == null
                    || alive != 2 || colony.getWarehouseCount(helper.getLevel(), "minecraft:cod") != 1)
                helper.fail("Constructed harbor-to-shore flow incomplete: building=" + harbor.getState()
                        + " builder=" + builderJob.getState() + " fisher=" + (job[0] == null ? "not started" : job[0].getState())
                        + " fisher pos=" + (fisher[0] == null ? "none" : fisher[0].position()) + " alive=" + alive
                        + " fish=" + first.position() + "/" + second.position() + "/" + third.position()
                        + " water=" + first.isInWater() + "/" + second.isInWater() + "/" + third.isInWater()
                        + " door=" + helper.getBlockState(SITE.north(2))
                        + " site=" + helper.getBlockState(SITE) + " stock=" + colony.getWarehouseSnapshot(helper.getLevel())
                        + " trace=" + trace);
            helper.assertBlockPresent(Blocks.BARREL, SITE);
            helper.succeed();
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 1400)
    public static void gathersOnlyExcessWildFishAtShore(GameTestHelper helper) {
        Colony colony = setup(helper);
        colony.registerPlayerBuilding(new Building(BuildingType.HARBOR, colony.getCenter(), CultureType.ROMAN, true));
        Cod first = wildCod(helper, new BlockPos(58, 0, 56));
        Cod second = wildCod(helper, new BlockPos(58, 0, 57));
        Cod third = wildCod(helper, new BlockPos(58, 0, 58));
        Cod named = wildCod(helper, new BlockPos(60, 0, 55));
        named.setCustomName(Component.literal("Pet"));
        Cod bucket = wildCod(helper, new BlockPos(60, 0, 57));
        bucket.setFromBucket(true);
        Cod persistent = wildCod(helper, new BlockPos(60, 0, 59));
        persistent.setPersistenceRequired();
        var existing = new ItemEntity(helper.getLevel(), colony.getCenter().getX() + 1.5, colony.getCenter().getY(),
                colony.getCenter().getZ() + 0.5, new ItemStack(Items.COD, 7));
        existing.setUnlimitedLifetime();
        existing.setNeverPickUp();
        helper.getLevel().addFreshEntity(existing);
        var worker = worker(helper);
        var job = new FishermanJob(worker, colony);
        for (int tick = 1; tick < 1400; tick++) helper.runAtTickTime(tick, () -> {
            float h1 = first.getHealth(), h2 = second.getHealth(), h3 = third.getHealth();
            job.tick(helper.getLevel());
            for (var fish : new Cod[]{first, second, third}) {
                float before = fish == first ? h1 : fish == second ? h2 : h3;
                if (fish.getHealth() < before && (worker.distanceToSqr(fish) > 6.25 || worker.isInWater()))
                    helper.fail("Fisherman attacked from too far away or in water");
            }
        });
        helper.runAtTickTime(300, () -> {
            if (colony.getWarehouseCount(helper.getLevel(), "minecraft:cod") == 0) {
                var stand = helper.absolutePos(new BlockPos(57, 1, 56));
                var path = worker.getNavigation().createPath(stand, 0);
                helper.fail("No catch: state=" + job.getState() + " worker=" + worker.position()
                        + " fish=" + first.position() + "/" + second.position() + "/" + third.position()
                        + " water=" + first.isInWater() + "/" + second.isInWater() + "/" + third.isInWater()
                        + " path=" + (path == null ? "null" : path.canReach() + "/" + path.getEndNode())
                        + " site=" + helper.getBlockState(SITE));
            }
        });
        helper.runAtTickTime(1200, () -> {
            int alive = (first.isAlive() ? 1 : 0) + (second.isAlive() ? 1 : 0) + (third.isAlive() ? 1 : 0);
            if (alive != 2 || !named.isAlive() || !bucket.isAlive() || !persistent.isAlive()
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:cod") != 1)
                helper.fail("Fish conservation or real loot incorrect: alive=" + alive + " stock="
                        + colony.getWarehouseSnapshot(helper.getLevel()) + " state=" + job.getState()
                        + " worker=" + worker.blockPosition().toShortString());
            if (!existing.isAlive() || existing.getItem().getCount() != 7) helper.fail("Collected unrelated player drop");
            helper.succeed();
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 1400)
    public static void requiresHarborAndRespectsStockLimit(GameTestHelper helper) {
        Colony colony = setup(helper);
        Cod a = wildCod(helper, new BlockPos(58, 0, 56));
        Cod b = wildCod(helper, new BlockPos(58, 0, 57));
        Cod c = wildCod(helper, new BlockPos(58, 0, 58));
        var worker = worker(helper);
        var job = new FishermanJob(worker, colony);
        for (int tick = 1; tick < 1400; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(300, () -> {
            if (!a.isAlive() || !b.isAlive() || !c.isAlive() || colony.getWarehouseCount(helper.getLevel(), "minecraft:cod") != 0)
                helper.fail("Worked without a designated harbor");
            colony.registerPlayerBuilding(new Building(BuildingType.HARBOR, colony.getCenter(), CultureType.ROMAN, true));
            colony.depositToWarehouse("minecraft:salmon", 8);
        });
        helper.runAtTickTime(800, () -> {
            if (!a.isAlive() || !b.isAlive() || !c.isAlive() || colony.getWarehouseCount(helper.getLevel(), "minecraft:cod") != 0)
                helper.fail("Caught fish despite a full reserve");
            colony.depositToWarehouse("minecraft:salmon", -8);
            helper.setBlock(SITE, Blocks.AIR);
        });
        helper.runAtTickTime(1300, () -> {
            if (!a.isAlive() || !b.isAlive() || !c.isAlive() || colony.getWarehouseCount(helper.getLevel(), "minecraft:cod") != 0)
                helper.fail("Worked after the barrel was removed");
            helper.succeed();
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 1300)
    public static void catchesSalmonWithoutTakingTheLastCod(GameTestHelper helper) {
        Colony colony = setup(helper);
        colony.registerPlayerBuilding(new Building(BuildingType.HARBOR, colony.getCenter(), CultureType.ROMAN, true));
        Cod a = wildCod(helper, new BlockPos(60, 0, 56));
        Cod b = wildCod(helper, new BlockPos(60, 0, 59));
        var salmon = new net.minecraft.world.entity.animal.Salmon[3];
        for (int i = 0; i < salmon.length; i++) {
            salmon[i] = EntityType.SALMON.create(helper.getLevel());
            if (salmon[i] == null) throw new IllegalStateException("Could not create salmon");
            BlockPos p = helper.absolutePos(new BlockPos(58, 0, 56 + i));
            salmon[i].moveTo(p.getX() + 0.5, p.getY() + 0.15, p.getZ() + 0.5);
            salmon[i].setNoAi(true);
            salmon[i].setNoGravity(true);
            helper.getLevel().addFreshEntity(salmon[i]);
        }
        var worker = worker(helper);
        var job = new FishermanJob(worker, colony);
        for (int tick = 1; tick < 1300; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(1100, () -> {
            int alive = 0;
            for (var fish : salmon) if (fish.isAlive()) alive++;
            if (!a.isAlive() || !b.isAlive() || alive != 2
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:cod") != 0
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:salmon") != 1)
                helper.fail("Wrong species, school conservation or salmon loot: " + colony.getWarehouseSnapshot(helper.getLevel())
                        + " salmon=" + alive + " state=" + job.getState());
            helper.succeed();
        });
    }
}
