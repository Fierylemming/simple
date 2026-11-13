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

    @GameTest(template = "empty", timeoutTicks = 1200)
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
        colony.registerPlayerBuilding(new Building(BuildingType.FARM, colony.getCenter(), CultureType.ROMAN, true));
        FarmerJob job = new FarmerJob(farmer, colony);
        for (int tick = 1; tick < 1200; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.succeedWhen(() -> {
            if (colony.getWarehouseCount(helper.getLevel(), "minecraft:bread") < 1)
                helper.fail("Farmer did not craft bread: " + job.getState() + " at " + farmer.blockPosition().toShortString());
            for (int x = 6; x <= 8; x++) {
                if (!helper.getBlockState(new BlockPos(x, 1, 9)).is(Blocks.WHEAT)) helper.fail("Harvested crop was not replanted");
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

}
