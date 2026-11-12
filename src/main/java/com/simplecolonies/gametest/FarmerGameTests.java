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
        var farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 1, 8));
        farmer.setVillagerData(farmer.getVillagerData().setProfession(ColonyProfessions.profession(JobType.FARMER)));
        farmer.refreshBrain(helper.getLevel());
        farmer.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(site)));
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(site));
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
        var farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 1, 8));
        farmer.setVillagerData(farmer.getVillagerData().setProfession(ColonyProfessions.profession(JobType.FARMER)));
        farmer.refreshBrain(helper.getLevel());
        farmer.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(workstation)));
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(8, 1, 9)));
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
}
