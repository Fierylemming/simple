package com.simplecolonies.gametest;

import com.simplecolonies.colony.CitizenData;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.villager.ColonyProfessions;
import com.simplecolonies.villager.job.BlacksmithJob;
import com.simplecolonies.villager.job.JobType;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class BlacksmithGameTests {
    private static final BlockPos SITE = new BlockPos(56, 1, 56);
    private static Colony colony(GameTestHelper helper) {
        for (int x = 40; x <= 72; x++) for (int z = 40; z <= 72; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        helper.setBlock(SITE, Blocks.SMITHING_TABLE);
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(SITE));
        colony.addCitizen(new CitizenData(UUID.randomUUID(), "Miner", JobType.MINER));
        colony.addCitizen(new CitizenData(UUID.randomUUID(), "Woodcutter", JobType.LUMBERJACK));
        return colony;
    }
    private static net.minecraft.world.entity.npc.Villager worker(GameTestHelper helper, BlockPos pos) {
        var worker = helper.spawn(EntityType.VILLAGER, pos);
        worker.setVillagerData(worker.getVillagerData().setProfession(ColonyProfessions.profession(JobType.BLACKSMITH)));
        worker.refreshBrain(helper.getLevel());
        worker.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(SITE)));
        return worker;
    }
    @GameTest(template = "isolated", timeoutTicks = 1200)
    public static void makesLimitedReservesAtWorkstation(GameTestHelper helper) {
        Colony colony = colony(helper);
        colony.depositToWarehouse("minecraft:cobblestone", 9);
        colony.depositToWarehouse("minecraft:oak_log", 1);
        var worker = worker(helper, new BlockPos(50, 1, 56));
        var job = new BlacksmithJob(worker, colony);
        int[] previous = {0};
        for (int tick = 1; tick < 1200; tick++) helper.runAtTickTime(tick, () -> {
            job.tick(helper.getLevel());
            int count = colony.getWarehouseCount(helper.getLevel(), "minecraft:stone_axe")
                    + colony.getWarehouseCount(helper.getLevel(), "minecraft:stone_pickaxe");
            if (count > previous[0] && worker.distanceToSqr(helper.absolutePos(SITE).getX() + 0.5,
                    helper.absolutePos(SITE).getY() + 0.5, helper.absolutePos(SITE).getZ() + 0.5) > 9)
                helper.fail("Smith crafted from too far away");
            previous[0] = count;
        });
        helper.runAtTickTime(1100, () -> {
            if (colony.getWarehouseCount(helper.getLevel(), "minecraft:stone_pickaxe") != 1
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:stone_axe") != 1
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:cobblestone") != 3
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:oak_log") != 0
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:oak_planks") != 2
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:stick") != 0)
                helper.fail("Tools, ingredients or reserve cap incorrect: " + colony.getWarehouseSnapshot(helper.getLevel())
                        + " state=" + job.getState() + " worker=" + worker.blockPosition().toShortString());
            helper.succeed();
        });
    }
    @GameTest(template = "isolated", timeoutTicks = 1600)
    public static void smithSuppliesGuardAndReplacesSword(GameTestHelper helper) {
        Colony colony = colony(helper);
        colony.getCitizens().forEach(c -> c.job = JobType.UNEMPLOYED);
        colony.addCitizen(new CitizenData(UUID.randomUUID(), "Guard", JobType.GUARD));
        BlockPos chest = new BlockPos(60, 1, 56);
        helper.setBlock(chest, Blocks.CHEST);
        colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chest));
        var storage = (net.minecraft.world.Container) helper.getBlockEntity(chest);
        storage.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 4));
        storage.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK, 2));
        var smith = worker(helper, new BlockPos(54, 1, 56));
        var guard = helper.spawn(EntityType.VILLAGER, new BlockPos(50, 1, 56));
        var crafting = new BlacksmithJob(smith, colony);
        var defense = new com.simplecolonies.villager.job.SoldierJob(guard, colony);
        for (int tick = 1; tick < 1600; tick++) helper.runAtTickTime(tick, () -> {
            crafting.tick(helper.getLevel()); defense.tick(helper.getLevel());
        });
        helper.runAtTickTime(1400, () -> {
            if (!guard.getMainHandItem().is(net.minecraft.world.item.Items.IRON_SWORD)
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:iron_sword") != 1
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:iron_ingot") != 0
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:stick") != 0)
                helper.fail("Smith-to-guard supply chain failed: " + colony.getWarehouseSnapshot(helper.getLevel())
                        + " guard=" + guard.getMainHandItem() + " status=" + defense.getState());
            helper.succeed();
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 600)
    public static void makesOneShearsFromRealIronForShepherd(GameTestHelper helper) {
        Colony colony = colony(helper);
        colony.getCitizens().forEach(c -> c.job = JobType.UNEMPLOYED);
        colony.addCitizen(new CitizenData(UUID.randomUUID(), "Shepherd", JobType.SHEPHERD));
        BlockPos chest = new BlockPos(60, 1, 56);
        helper.setBlock(chest, Blocks.CHEST);
        var storage = (net.minecraft.world.Container) helper.getBlockEntity(chest);
        storage.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 4));
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chest))) helper.fail("Could not share chest");
        var worker = worker(helper, new BlockPos(54, 1, 56));
        var job = new BlacksmithJob(worker, colony);
        for (int tick = 1; tick < 600; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(500, () -> {
            if (colony.getWarehouseCount(helper.getLevel(), "minecraft:iron_ingot") != 2
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:shears") != 1
                    || !storage.getItem(1).is(net.minecraft.world.item.Items.SHEARS))
                helper.fail("Smith did not produce one physical shears from exactly two iron: " + colony.getWarehouseSnapshot(helper.getLevel()));
            helper.succeed();
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 300)
    public static void doesNotCraftUncollectableShears(GameTestHelper helper) {
        Colony colony = colony(helper);
        colony.getCitizens().forEach(c -> c.job = JobType.UNEMPLOYED);
        colony.addCitizen(new CitizenData(UUID.randomUUID(), "Shepherd", JobType.SHEPHERD));
        colony.depositToWarehouse("minecraft:iron_ingot", 2);
        var worker = worker(helper, new BlockPos(54, 1, 56));
        var job = new BlacksmithJob(worker, colony);
        for (int tick = 1; tick < 300; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(250, () -> {
            if (colony.getWarehouseCount(helper.getLevel(), "minecraft:iron_ingot") != 2
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:shears") != 0)
                helper.fail("Smith created shears without accessible shared storage");
            helper.succeed();
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 500)
    public static void missingSticksDoNotConsumeStone(GameTestHelper helper) {
        Colony colony = colony(helper);
        colony.depositToWarehouse("minecraft:cobblestone", 3);
        var worker = worker(helper, new BlockPos(54, 1, 56));
        var job = new BlacksmithJob(worker, colony);
        for (int tick = 1; tick < 500; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(400, () -> {
            if (colony.getWarehouseCount(helper.getLevel(), "minecraft:cobblestone") != 3
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:stone_pickaxe") != 0
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:stone_axe") != 0)
                helper.fail("Smith spent or created items without a complete recipe");
            helper.succeed();
        });
    }
    @GameTest(template = "isolated", timeoutTicks = 700)
    public static void replacesWithdrawnIronToolWithoutFreeMaterials(GameTestHelper helper) {
        Colony colony = colony(helper);
        colony.depositToWarehouse("minecraft:iron_ingot", 6);
        colony.depositToWarehouse("minecraft:stick", 4);
        var worker = worker(helper, new BlockPos(54, 1, 56));
        var job = new BlacksmithJob(worker, colony);
        for (int tick = 1; tick < 700; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(15, () -> {
            var taken = colony.takeStockItem(helper.getLevel(), helper.absolutePos(SITE), net.minecraft.world.item.Items.IRON_PICKAXE);
            if (!taken.is(net.minecraft.world.item.Items.IRON_PICKAXE)) helper.fail("Smith did not provide the first iron pickaxe");
        });
        helper.runAtTickTime(600, () -> {
            if (colony.getWarehouseCount(helper.getLevel(), "minecraft:iron_pickaxe") != 1
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:iron_axe") != 0
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:iron_ingot") != 0
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:stick") != 0)
                helper.fail("Replacement recipe or inputs incorrect: " + colony.getWarehouseSnapshot(helper.getLevel()));
            helper.succeed();
        });
    }

}
