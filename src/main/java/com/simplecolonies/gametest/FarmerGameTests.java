package com.simplecolonies.gametest;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.villager.ColonyProfessions;
import com.simplecolonies.villager.job.FarmerJob;
import com.simplecolonies.villager.job.JobType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class FarmerGameTests {
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void harvestsOnlyDesignatedFarmBounds(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos site = new BlockPos(8, 1, 8), own = new BlockPos(7, 1, 9), privateCrop = new BlockPos(14, 1, 9);
        helper.setBlock(site, Blocks.COMPOSTER);
        for (BlockPos p : new BlockPos[]{own, privateCrop}) {
            helper.setBlock(p.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            helper.setBlock(p, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
            helper.setBlock(p.north().below(), Blocks.WATER);
        }
        var farmer = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(6, 1, 8));
        farmer.setVillagerData(farmer.getVillagerData().setProfession(ColonyProfessions.profession(JobType.FARMER)));
        farmer.refreshBrain(helper.getLevel());
        farmer.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(site)));
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(site));
        BlockPos chest = new BlockPos(12, 1, 7);
        helper.setBlock(chest, Blocks.CHEST);
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chest))) helper.fail("Could not share farm storage");
        var job = new FarmerJob(farmer, colony);
        helper.runAtTickTime(5, () -> {
            job.tick(helper.getLevel());
            if (helper.getBlockState(own).getValue(CropBlock.AGE) != 7) helper.fail("Harvested without a designated farm");
            colony.registerPlayerBuilding(new Building(BuildingType.FARM, colony.getCenter(), CultureType.ROMAN, true));
        });
        for (int tick = 6; tick < 1200; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(1000, () -> {
            if (!helper.getBlockState(privateCrop).is(Blocks.WHEAT) || helper.getBlockState(privateCrop).getValue(CropBlock.AGE) != 7)
                helper.fail("Farmer harvested outside designated farm bounds");
            if (colony.getWarehouseCount(helper.getLevel(), "minecraft:bread") < 1
                    && colony.getWarehouseCount(helper.getLevel(), "minecraft:wheat") < 1)
                helper.fail("Farmer did not harvest inside designated farm");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void harvestReplantAndCraftBread(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        helper.setBlock(8, 0, 8, Blocks.WATER);
        for (int x = 6; x <= 8; x++) {
            helper.setBlock(x, 0, 9, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            helper.setBlock(x, 1, 9, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        }
        BlockPos workstation = new BlockPos(9, 1, 9);
        helper.setBlock(workstation, Blocks.COMPOSTER);
        var farmer = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(5, 1, 8));
        farmer.setVillagerData(farmer.getVillagerData().setProfession(ColonyProfessions.profession(JobType.FARMER)));
        farmer.refreshBrain(helper.getLevel());
        farmer.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(workstation)));
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(8, 1, 9)));
        BlockPos chest = new BlockPos(13, 1, 7);
        helper.setBlock(chest, Blocks.CHEST);
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chest))) helper.fail("Could not share farm storage");
        // Wheat may drop no seed. Supply a real fallback so this integration
        // checks replanting rather than depending on random loot.
        ((net.minecraft.world.Container) helper.getBlockEntity(chest)).setItem(0,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT_SEEDS, 6));
        colony.registerPlayerBuilding(new Building(BuildingType.FARM, colony.getCenter(), CultureType.ROMAN, true));
        FarmerJob job = new FarmerJob(farmer, colony);
        for (int tick = 1; tick < 2000; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.succeedWhen(() -> {
            if (colony.getWarehouseCount(helper.getLevel(), "minecraft:bread") < 1)
                helper.fail("Farmer did not craft bread: " + job.getState() + " at " + farmer.blockPosition().toShortString());
            for (int x = 6; x <= 8; x++) {
                if (!helper.getBlockState(new BlockPos(x, 1, 9)).is(Blocks.WHEAT)) helper.fail("Harvested crop was not replanted at x=" + x + "; block=" + helper.getBlockState(new BlockPos(x, 1, 9)) + "; ground=" + helper.getBlockState(new BlockPos(x, 0, 9)) + "; worker=" + job.getState());
            }
        });
    }
    @GameTest(template = "empty", timeoutTicks = 1600)
    public static void harvestCargoSurvivesAndIsDeliveredPhysically(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos site = new BlockPos(5, 1, 8), crop = new BlockPos(6, 1, 8), chest = new BlockPos(14, 1, 8);
        helper.setBlock(site, Blocks.COMPOSTER);
        helper.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        helper.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        helper.setBlock(crop.south().below(), Blocks.WATER);
        var worker = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(4, 1, 8));
        worker.setVillagerData(worker.getVillagerData().setProfession(ColonyProfessions.profession(JobType.FARMER)));
        worker.refreshBrain(helper.getLevel());
        worker.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(site)));
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(site));
        colony.registerPlayerBuilding(new Building(BuildingType.FARM, colony.getCenter(), CultureType.ROMAN, true));
        FarmerJob job = new FarmerJob(worker, colony);
        boolean[] carried = {false}, removedSite = {false};
        for (int tick = 1; tick < 1600; tick++) helper.runAtTickTime(tick, () -> {
            job.tick(helper.getLevel());
            if (!carried[0] && (!worker.getCourierCargo().isEmpty() || !worker.getCourierCargoExtra().isEmpty())) {
                var tag = new net.minecraft.nbt.CompoundTag();
                worker.addAdditionalSaveData(tag);
                var restored = net.minecraft.world.item.ItemStack.parseOptional(helper.getLevel().registryAccess(), tag.getCompound("courierCargo"));
                if (restored.isEmpty() || !net.minecraft.world.item.ItemStack.isSameItemSameComponents(restored, worker.getCourierCargo())
                        || restored.getCount() != worker.getCourierCargo().getCount()
                        || colony.getWarehouseCount(helper.getLevel(), "minecraft:wheat") != 0)
                    helper.fail("Harvest was not persisted as physical cargo");
                carried[0] = true;
            }
        });
        helper.runAtTickTime(200, () -> {
            if (!carried[0]) helper.fail("Farmer did not harvest without storage");
            helper.setBlock(site, Blocks.AIR);
            worker.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
            helper.setBlock(chest, Blocks.CHEST);
            if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chest))) helper.fail("Could not share chest");
            removedSite[0] = true;
        });
        helper.succeedWhen(() -> {
            if (!removedSite[0]) helper.fail("Waiting to remove workstation");
            var container = (net.minecraft.world.Container) helper.getBlockEntity(chest);
            if (container.countItem(net.minecraft.world.item.Items.WHEAT) < 1
                    || !worker.getCourierCargo().isEmpty() || !worker.getCourierCargoExtra().isEmpty())
                helper.fail("Harvest still in transit: " + job.getState() + " at " + worker.blockPosition().toShortString());
            BlockPos dest = helper.absolutePos(chest);
            if (worker.distanceToSqr(dest.getX()+.5, dest.getY()+.5, dest.getZ()+.5) > 9)
                helper.fail("Harvest deposited without visiting storage");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1500)
    public static void carriesWheatToWorkstationAndDeliversBread(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos site = new BlockPos(4, 1, 8), chest = new BlockPos(13, 1, 8);
        helper.setBlock(site, Blocks.COMPOSTER); helper.setBlock(chest, Blocks.CHEST);
        BlockPos absSite = helper.absolutePos(site), absChest = helper.absolutePos(chest);
        var worker = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), new BlockPos(4, 1, 9));
        worker.setVillagerData(worker.getVillagerData().setProfession(ColonyProfessions.profession(JobType.FARMER)));
        worker.refreshBrain(helper.getLevel());
        worker.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), absSite));
        Colony colony = new Colony(0, CultureType.ROMAN, absSite);
        colony.registerPlayerBuilding(new Building(BuildingType.FARM, absSite, CultureType.ROMAN, true));
        var storage = (net.minecraft.world.Container) helper.getBlockEntity(chest);
        storage.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT, 3));
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), absChest)) helper.fail("Could not share chest");
        var job = new FarmerJob(worker, colony);
        boolean[] gathered = {false}, made = {false}, delivered = {false};
        for (int tick = 1; tick < 1500; tick++) helper.runAtTickTime(tick, () -> {
            job.tick(helper.getLevel());
            if (!gathered[0] && storage.countItem(net.minecraft.world.item.Items.WHEAT) == 0) {
                if (worker.distanceToSqr(absChest.getX()+.5, absChest.getY()+.5, absChest.getZ()+.5) > 9
                        || worker.getInventory().countItem(net.minecraft.world.item.Items.WHEAT) != 3)
                    helper.fail("Wheat was not physically collected");
                gathered[0] = true;
            }
            if (!made[0] && worker.getCourierCargo().is(net.minecraft.world.item.Items.BREAD)) {
                if (!gathered[0] || worker.distanceToSqr(absSite.getX()+.5, absSite.getY()+.5, absSite.getZ()+.5) > 9
                        || worker.getInventory().countItem(net.minecraft.world.item.Items.WHEAT) != 0)
                    helper.fail("Bread was made remotely or without consuming wheat");
                made[0] = true;
            }
            if (!delivered[0] && storage.countItem(net.minecraft.world.item.Items.BREAD) > 0) {
                if (!made[0] || worker.distanceToSqr(absChest.getX()+.5, absChest.getY()+.5, absChest.getZ()+.5) > 9)
                    helper.fail("Bread was deposited remotely");
                delivered[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            if (!gathered[0] || !made[0] || !delivered[0])
                helper.fail("Bread in progress: " + job.getState() + "; at " + worker.blockPosition().toShortString());
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void colonyVillagerDoesNotTrampleCropsWhenFalling(GameTestHelper helper) {
        BlockPos plot = new BlockPos(8, 0, 8), crop = plot.above();
        helper.setBlock(plot, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        helper.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        var worker = helper.spawn(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(), crop);
        var abs = helper.absolutePos(plot);
        Blocks.FARMLAND.fallOn(helper.getLevel(), helper.getLevel().getBlockState(abs), abs, worker, 10);
        if (!helper.getBlockState(plot).is(Blocks.FARMLAND) || !helper.getBlockState(crop).is(Blocks.WHEAT))
            helper.fail("Colony villager trampled farmland and destroyed crop");
        helper.succeed();
    }

}
