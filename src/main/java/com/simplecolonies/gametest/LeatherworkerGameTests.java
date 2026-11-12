package com.simplecolonies.gametest;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.villager.ColonyProfessions;
import com.simplecolonies.villager.job.JobType;
import com.simplecolonies.villager.job.LeatherworkerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class LeatherworkerGameTests {
    @GameTest(template = "isolated", timeoutTicks = 1400)
    public static void chestplateUsesEightLeatherAndKeepsOneSpare(GameTestHelper helper) {
        for (int x = 40; x <= 72; x++) for (int z = 40; z <= 72; z++)
            helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos site = new BlockPos(56, 1, 56), chest = new BlockPos(58, 1, 58);
        helper.setBlock(site, Blocks.CAULDRON);
        helper.setBlock(chest, Blocks.CHEST);
        Container storage = (Container) helper.getBlockEntity(chest);
        storage.setItem(0, new ItemStack(Items.LEATHER, 7));
        var level = helper.getLevel();
        var worker = helper.spawn(EntityType.VILLAGER, new BlockPos(50, 1, 56));
        worker.setVillagerData(worker.getVillagerData().setProfession(ColonyProfessions.profession(JobType.LEATHERWORKER)));
        worker.refreshBrain(level);
        worker.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(site)));
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(site));
        colony.getWarehouseManager().registerStorage(level, helper.absolutePos(chest));
        var job = new LeatherworkerJob(worker, colony);
        int[] previous = {0};
        for (int tick = 1; tick < 1400; tick++) helper.runAtTickTime(tick, () -> {
            job.tick(level);
            int count = colony.getWarehouseCount(level, "minecraft:leather_chestplate");
            if (count > previous[0] && worker.distanceToSqr(colony.getCenter().getX() + 0.5,
                    colony.getCenter().getY() + 0.5, colony.getCenter().getZ() + 0.5) > 9)
                helper.fail("Leatherworker crafted away from the workstation");
            previous[0] = count;
        });
        helper.runAtTickTime(300, () -> {
            if (colony.getWarehouseCount(level, "minecraft:leather") != 7
                    || colony.getWarehouseCount(level, "minecraft:leather_chestplate") != 0)
                helper.fail("Incomplete recipe consumed leather or created armor");
            storage.setItem(0, new ItemStack(Items.LEATHER, 16));
            storage.setChanged();
        });
        helper.runAtTickTime(850, () -> {
            if (colony.getWarehouseCount(level, "minecraft:leather") != 8
                    || colony.getWarehouseCount(level, "minecraft:leather_chestplate") != 1)
                helper.fail("Recipe or reserve cap incorrect: " + colony.getWarehouseSnapshot(level));
            var spare = colony.takeStockItem(level, helper.absolutePos(site), Items.LEATHER_CHESTPLATE);
            if (!spare.is(Items.LEATHER_CHESTPLATE)) helper.fail("Unable to withdraw the spare");
        });
        helper.runAtTickTime(1300, () -> {
            if (colony.getWarehouseCount(level, "minecraft:leather") != 0
                    || colony.getWarehouseCount(level, "minecraft:leather_chestplate") != 1)
                helper.fail("Replacement recipe incorrect: " + colony.getWarehouseSnapshot(level));
            helper.succeed();
        });
    }
}
