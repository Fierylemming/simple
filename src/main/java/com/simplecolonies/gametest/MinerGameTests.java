package com.simplecolonies.gametest;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ModEntities;
import com.simplecolonies.villager.job.MinerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class MinerGameTests {
    @GameTest(template = "quarry", timeoutTicks = 6500)
    public static void minerWalksAndExcavatesWithRealTool(GameTestHelper helper) {
        // GameTest surrounds structures with a barrier ceiling, which otherwise becomes
        // the heightmap surface. Remove only the artificial roof, not the side walls.
        for (int x = 1; x < 63; x++) for (int z = 1; z < 63; z++) for (int y = 24; y <= 27; y++) {
            BlockPos roof = helper.absolutePos(new BlockPos(x, y, z));
            if (helper.getLevel().getBlockState(roof).is(Blocks.BARRIER))
                helper.getLevel().setBlock(roof, Blocks.AIR.defaultBlockState(), 3);
        }
        for (int x = 0; x < 64; x++) for (int z = 0; z < 64; z++) helper.setBlock(x, 11, z, Blocks.GRASS_BLOCK);
        for (int x = 37; x <= 51; x++) for (int z = 30; z <= 34; z++) for (int y = 0; y <= 10; y++)
            helper.setBlock(x, y, z, Blocks.STONE);
        BlockPos center = helper.absolutePos(new BlockPos(15, 12, 32));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.depositToWarehouse("minecraft:stone_pickaxe", 1); // Legacy ledger must not be used.
        BlockPos chest = new BlockPos(22, 12, 32), absChest = helper.absolutePos(chest);
        helper.setBlock(chest, Blocks.CHEST);
        var container = (Container) helper.getBlockEntity(chest);
        var pickaxe = new ItemStack(Items.STONE_PICKAXE);
        pickaxe.setDamageValue(12);
        pickaxe.set(DataComponents.CUSTOM_NAME, Component.literal("Quarry pick"));
        container.setItem(0, pickaxe.copy());
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), absChest)) helper.fail("Could not share chest");
        var villager = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(16, 12, 32));
        MinerJob job = new MinerJob(villager, colony);
        BlockPos stone = new BlockPos(41, 10, 32);
        boolean[] sawCut = {false}, sawTool = {false}, sawCargo = {false};
        for (int tick = 1; tick < 6500; tick++) helper.runAtTickTime(tick, () -> {
            boolean wasStone = helper.getBlockState(stone).is(Blocks.STONE);
            job.tick(helper.getLevel());
            if (!sawTool[0] && villager.getMainHandItem().is(Items.STONE_PICKAXE)) {
                if (villager.distanceToSqr(absChest.getX()+.5, absChest.getY()+.5, absChest.getZ()+.5) > 9
                        || villager.getMainHandItem().getDamageValue() != 12
                        || !ItemStack.isSameItemSameComponents(villager.getMainHandItem(), pickaxe)
                        || !container.getItem(0).isEmpty()) helper.fail("Miner collected a remote or altered pickaxe");
                sawTool[0] = true;
            }
            if (!sawCargo[0] && (villager.getCourierCargo().is(Items.COBBLESTONE)
                    || villager.getCourierCargoExtra().is(Items.COBBLESTONE))) {
                if (villager.distanceToSqr(absChest.getX()+.5, absChest.getY()+.5, absChest.getZ()+.5) <= 9)
                    helper.fail("Miner did not carry its stone from the quarry");
                CompoundTag tag = new CompoundTag(); villager.addAdditionalSaveData(tag);
                ItemStack restored = ItemStack.parseOptional(helper.getLevel().registryAccess(), tag.getCompound("courierCargo"));
                ItemStack extra = ItemStack.parseOptional(helper.getLevel().registryAccess(), tag.getCompound("courierCargoExtra"));
                if (!restored.is(Items.COBBLESTONE) && !extra.is(Items.COBBLESTONE)) helper.fail("Mined cargo was not saved");
                sawCargo[0] = true;
            }
            if (wasStone && !helper.getBlockState(stone).is(Blocks.STONE)) {
                BlockPos cut = helper.absolutePos(stone);
                if (villager.distanceToSqr(cut.getX() + 0.5, cut.getY() + 0.5, cut.getZ() + 0.5) > 16)
                    helper.fail("Miner cut stone from too far away");
                sawCut[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            int stored = 0;
            for (int i = 0; i < container.getContainerSize(); i++) if (container.getItem(i).is(Items.COBBLESTONE)) stored += container.getItem(i).getCount();
            if (!sawCut[0] || !sawTool[0] || !sawCargo[0] || stored < 1
                    || !villager.getMainHandItem().is(Items.STONE_PICKAXE) || colony.getWarehouseCount("minecraft:stone_pickaxe") != 1)
                helper.fail("Miner " + job.getState() + " at " + villager.blockPosition().toShortString()
                        + " " + job.diagnostic() + " tool=" + villager.getMainHandItem()
                        + " stone=" + helper.getBlockState(stone) + " cobble=" + colony.getWarehouseCount(helper.getLevel(), "minecraft:cobblestone"));
            if (!villager.getMinerMemory().contains("entrance") || villager.getMinerMemory().getInt("step") < 2)
                helper.fail("Miner has not advanced into its persisted quarry: " + job.diagnostic());
        });
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void minerCraftsPickaxeFromCarriedRealIngredients(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos table = new BlockPos(4, 1, 4), chest = new BlockPos(12, 1, 4);
        helper.setBlock(table, Blocks.CRAFTING_TABLE); helper.setBlock(chest, Blocks.CHEST);
        BlockPos center = helper.absolutePos(table), absChest = helper.absolutePos(chest);
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.TOWN_HALL, center, CultureType.ROMAN, true));
        colony.depositToWarehouse("minecraft:stone_pickaxe", 1);
        var container = (Container) helper.getBlockEntity(chest);
        container.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        container.setItem(1, new ItemStack(Items.BIRCH_LOG, 1));
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), absChest)) helper.fail("Could not share chest");
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(6, 1, 4));
        var job = new MinerJob(worker, colony);
        boolean[] carried = {false}, made = {false};
        for (int tick = 1; tick < 2000; tick++) helper.runAtTickTime(tick, () -> {
            job.tick(helper.getLevel());
            if (!carried[0] && (worker.getBuilderMaterials().countItem(Items.BIRCH_LOG) > 0
                    || worker.getBuilderMaterials().countItem(Items.COBBLESTONE) > 0)) {
                if (worker.distanceToSqr(absChest.getX()+.5, absChest.getY()+.5, absChest.getZ()+.5) > 9)
                    helper.fail("Miner took crafting ingredients remotely");
                carried[0] = true;
            }
            if (!made[0] && worker.getMainHandItem().is(Items.STONE_PICKAXE)) {
                if (!carried[0] || worker.distanceToSqr(center.getX()+.5, center.getY()+.5, center.getZ()+.5) > 9
                        || colony.getWarehouseCount("minecraft:stone_pickaxe") != 1)
                    helper.fail("Miner used virtual tool or crafted away from the table");
                made[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            if (!made[0] || !worker.getBuilderMaterials().isEmpty())
                helper.fail("Miner did not craft tool and return its leftovers: " + job.getState() + " " + job.diagnostic());
            int sticks = 0, planks = 0;
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (container.getItem(i).is(Items.STICK)) sticks += container.getItem(i).getCount();
                if (container.getItem(i).is(Items.BIRCH_PLANKS)) planks += container.getItem(i).getCount();
            }
            if (sticks != 2 || planks != 2) helper.fail("Crafting leftovers were lost: " + sticks + " sticks " + planks + " planks");
        });
    }
    @GameTest(template = "empty", timeoutTicks = 500)
    public static void minerRetainsCargoWhenSharedStorageIsFullOrRemoved(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos chest = new BlockPos(8, 1, 8), center = helper.absolutePos(chest);
        helper.setBlock(chest, Blocks.CHEST);
        var container = (Container) helper.getBlockEntity(chest);
        for (int i = 0; i < container.getContainerSize(); i++) container.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), center)) helper.fail("Could not share chest");
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(7, 1, 8)); worker.setNoAi(true);
        var drop = new ItemStack(Items.RAW_IRON, 3);
        drop.set(DataComponents.CUSTOM_NAME, Component.literal("Distinct ore"));
        worker.setCourierCargo(drop.copy());
        var job = new MinerJob(worker, colony);
        for (int tick = 1; tick < 500; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(60, () -> {
            if (job.getState() != com.simplecolonies.villager.job.AbstractJob.JobState.WAITING_FOR_CHEST
                    || !ItemStack.isSameItemSameComponents(worker.getCourierCargo(), drop) || worker.getCourierCargo().getCount() != 3)
                helper.fail("Miner lost cargo or continued work while storage was full");
            if (!colony.getWarehouseManager().unregisterStorage(helper.getLevel(), center)) helper.fail("Could not unshare chest");
            container.setItem(0, ItemStack.EMPTY);
        });
        helper.runAtTickTime(160, () -> {
            if (!container.getItem(0).isEmpty() || !ItemStack.isSameItemSameComponents(worker.getCourierCargo(), drop))
                helper.fail("Miner delivered to an unshared chest");
            if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), center)) helper.fail("Could not re-share chest");
        });
        helper.succeedWhen(() -> {
            if (!worker.getCourierCargo().isEmpty() || !ItemStack.isSameItemSameComponents(container.getItem(0), drop)
                    || container.getItem(0).getCount() != 3) helper.fail("Miner has not delivered retained ore");
        });
    }

}
