package com.simplecolonies.gametest;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ModEntities;
import com.simplecolonies.villager.ColonyProfessions;
import com.simplecolonies.villager.job.AbstractJob;
import com.simplecolonies.villager.job.BakerJob;
import com.simplecolonies.villager.job.JobType;
import com.simplecolonies.villager.job.LeatherworkerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class PhysicalProductionGameTests {
    @GameTest(template = "empty", timeoutTicks = 2200)
    public static void bakerCarriesWheatAndBread(GameTestHelper helper) { recipe(helper, false); }
    @GameTest(template = "empty", timeoutTicks = 2200)
    public static void leatherworkerCarriesLeatherAndArmor(GameTestHelper helper) { recipe(helper, true); }

    private static void recipe(GameTestHelper helper, boolean leather) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos site = new BlockPos(4, 1, 8), chest = new BlockPos(13, 1, 8);
        helper.setBlock(site, leather ? Blocks.CAULDRON : Blocks.SMOKER); helper.setBlock(chest, Blocks.CHEST);
        BlockPos station = helper.absolutePos(site), storage = helper.absolutePos(chest);
        var colony = new Colony(0, CultureType.ROMAN, station);
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), storage)) helper.fail("Could not share production storage");
        Item input = leather ? Items.LEATHER : Items.WHEAT, output = leather ? Items.LEATHER_CHESTPLATE : Items.BREAD;
        int amount = leather ? 8 : 3;
        colony.depositToWarehouse(leather ? "minecraft:leather" : "minecraft:wheat", amount);
        var container = (Container) helper.getBlockEntity(chest);
        var special = new ItemStack(input, amount);
        special.set(DataComponents.CUSTOM_NAME, Component.literal("Reserved ingredient"));
        container.setItem(0, special.copy());
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(7, 1, 8));
        var profession = leather ? JobType.LEATHERWORKER : JobType.BAKER;
        worker.setVillagerData(worker.getVillagerData().setProfession(ColonyProfessions.profession(profession)));
        worker.refreshBrain(helper.getLevel());
        worker.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), station));
        AbstractJob job = leather ? new LeatherworkerJob(worker, colony) : new BakerJob(worker, colony);
        boolean[] supplied = {false}, picked = {false}, crafted = {false};
        for (int tick = 1; tick < 2200; tick++) helper.runAtTickTime(tick, () -> {
            job.tick(helper.getLevel());
            if (!picked[0] && worker.getBuilderMaterials().countItem(input) == amount) {
                if (!supplied[0] || worker.distanceToSqr(storage.getX()+.5, storage.getY()+.5, storage.getZ()+.5) > 9)
                    helper.fail("Ingredients were collected remotely or from virtual stock");
                CompoundTag tag = new CompoundTag(); worker.addAdditionalSaveData(tag);
                var saved = new SimpleContainer(18);
                saved.fromTag(tag.getList("builderMaterials", Tag.TAG_COMPOUND), helper.getLevel().registryAccess());
                if (saved.countItem(input) != amount) helper.fail("Production inputs not saved");
                picked[0] = true;
            }
            if (!crafted[0] && worker.getCourierCargo().is(output)) {
                if (!picked[0] || worker.distanceToSqr(station.getX()+.5, station.getY()+.5, station.getZ()+.5) > 9)
                    helper.fail("Production happened away from the workstation");
                CompoundTag tag = new CompoundTag(); worker.addAdditionalSaveData(tag);
                if (!ItemStack.parseOptional(helper.getLevel().registryAccess(), tag.getCompound("courierCargo")).is(output))
                    helper.fail("Finished output not saved on worker");
                crafted[0] = true;
            }
        });
        helper.runAtTickTime(150, () -> {
            if (!worker.getBuilderMaterials().isEmpty() || !worker.getCourierCargo().isEmpty()
                    || !ItemStack.isSameItemSameComponents(container.getItem(0), special) || container.getItem(0).getCount() != amount)
                helper.fail("Crafted with virtual or customized ingredients");
            container.setItem(1, new ItemStack(input, leather ? 16 : 3)); supplied[0] = true;
        });
        helper.succeedWhen(() -> {
            if (!supplied[0] || !picked[0] || !crafted[0] || !worker.getCourierCargo().isEmpty()
                    || container.countItem(output) != 1) helper.fail("Physical production incomplete: " + job.getState());
            if (!ItemStack.isSameItemSameComponents(container.getItem(0), special) || container.getItem(0).getCount() != amount
                    || colony.getWarehouseCount(leather ? "minecraft:leather" : "minecraft:wheat") != amount
                    || (leather && container.getItem(1).getCount() != 8))
                helper.fail("Recipe consumed customized or virtual ingredients, or ignored spare policy");
        });
    }
}
