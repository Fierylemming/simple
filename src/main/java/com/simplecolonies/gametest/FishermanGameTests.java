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
        for (int x = 40; x <= 72; x++) for (int z = 40; z <= 72; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
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

    @GameTest(template = "isolated", timeoutTicks = 900)
    public static void requiresHarborAndRespectsStockLimit(GameTestHelper helper) {
        Colony colony = setup(helper);
        Cod a = wildCod(helper, new BlockPos(58, 0, 56));
        Cod b = wildCod(helper, new BlockPos(58, 0, 57));
        Cod c = wildCod(helper, new BlockPos(58, 0, 58));
        var worker = worker(helper);
        var job = new FishermanJob(worker, colony);
        for (int tick = 1; tick < 900; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(300, () -> {
            if (!a.isAlive() || !b.isAlive() || !c.isAlive() || colony.getWarehouseCount(helper.getLevel(), "minecraft:cod") != 0)
                helper.fail("Worked without a designated harbor");
            colony.registerPlayerBuilding(new Building(BuildingType.HARBOR, colony.getCenter(), CultureType.ROMAN, true));
            colony.depositToWarehouse("minecraft:salmon", 8);
        });
        helper.runAtTickTime(800, () -> {
            if (!a.isAlive() || !b.isAlive() || !c.isAlive() || colony.getWarehouseCount(helper.getLevel(), "minecraft:cod") != 0)
                helper.fail("Caught fish despite a full reserve");
            helper.succeed();
        });
    }
}
