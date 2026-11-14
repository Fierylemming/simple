package com.simplecolonies.gametest;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ModEntities;
import com.simplecolonies.villager.job.MinerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class ToolReplacementGameTests {
    @GameTest(template = "isolated", timeoutTicks = 1000)
    public static void minerCraftsReplacementFromRealInputs(GameTestHelper helper) {
        BlockPos hall = new BlockPos(56, 1, 56), table = hall.east(2);
        for (int x = 50; x <= 62; x++) for (int z = 50; z <= 62; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        helper.setBlock(table, Blocks.CRAFTING_TABLE);
        BlockPos chest = hall.east().south();
        helper.setBlock(chest, Blocks.CHEST);
        var container = (Container) helper.getBlockEntity(chest);
        var colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(hall));
        colony.registerPlayerBuilding(new Building(BuildingType.TOWN_HALL, colony.getCenter(), CultureType.ROMAN, true));
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chest))) helper.fail("Could not share tool ingredients");
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), hall.east());
        worker.setNoAi(true);
        var job = new MinerJob(worker, colony);
        for (int tick = 1; tick < 1000; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(10, () -> {
            if (!worker.getMainHandItem().isEmpty() || job.getState() != com.simplecolonies.villager.job.AbstractJob.JobState.NEEDS_TOOL)
                helper.fail("Miner got a free tool without ingredients");
            container.setItem(0, new ItemStack(Items.COBBLESTONE, 6));
            container.setItem(1, new ItemStack(Items.STICK, 4));
        });
        helper.runAtTickTime(300, () -> {
            if (!worker.getMainHandItem().is(Items.STONE_PICKAXE) || colony.getWarehouseCount(helper.getLevel(), "minecraft:cobblestone") != 3
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:stick") != 2)
                helper.fail("Miner failed to craft first pickaxe with real inputs: " + colony.getWarehouseSnapshot(helper.getLevel()));
            worker.getMainHandItem().setDamageValue(worker.getMainHandItem().getMaxDamage() - 1);
            worker.getMainHandItem().hurtAndBreak(1, worker, EquipmentSlot.MAINHAND);
            if (!worker.getMainHandItem().isEmpty()) helper.fail("Fixture failed to break pickaxe");
        });
        helper.runAtTickTime(850, () -> {
            if (!worker.getMainHandItem().is(Items.STONE_PICKAXE) || colony.getWarehouseCount(helper.getLevel(), "minecraft:cobblestone") != 0
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:stick") != 0)
                helper.fail("Broken pickaxe not replaced from stock: " + colony.getWarehouseSnapshot(helper.getLevel()) + " " + job.getState());
            helper.succeed();
        });
    }
}
