package com.simplecolonies.gametest;

import com.simplecolonies.block.ModBlocks;
import com.simplecolonies.block.entity.BuildingMarkerBlockEntity;
import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ModEntities;
import com.simplecolonies.villager.ColonyProfessions;
import com.simplecolonies.villager.job.JobType;
import com.simplecolonies.villager.job.ShepherdJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
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
public class ShepherdGameTests {
    @GameTest(template = "isolated", timeoutTicks = 1200)
    public static void shearsOnlyDesignatedSheepAndCarriesWoolToSharedChest(GameTestHelper helper) {
        for (int x = 44; x <= 70; x++) for (int z = 50; z <= 62; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos marker = new BlockPos(56, 1, 56), loom = new BlockPos(55, 1, 55), chest = new BlockPos(65, 1, 56);
        helper.setBlock(marker, ModBlocks.BUILDING_MARKER.get());
        helper.setBlock(loom, Blocks.LOOM);
        helper.setBlock(chest, Blocks.CHEST);
        var absMarker = helper.absolutePos(marker);
        Colony colony = new Colony(0, CultureType.ROMAN, absMarker);
        colony.registerPlayerBuilding(new Building(BuildingType.PASTURE, absMarker, CultureType.ROMAN, true));
        var markerEntity = (BuildingMarkerBlockEntity) helper.getBlockEntity(marker);
        markerEntity.setBuildingType(BuildingType.PASTURE); markerEntity.setLinkedBuildingPos(absMarker);
        var items = (Container) helper.getBlockEntity(chest);
        var absChest = helper.absolutePos(chest);
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), absChest)) helper.fail("Could not share chest");
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(54, 1, 56));
        worker.setVillagerData(worker.getVillagerData().setProfession(ColonyProfessions.profession(JobType.SHEPHERD)));
        worker.refreshBrain(helper.getLevel());
        worker.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(loom)));
        worker.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.SHEARS));
        var sheep = helper.spawn(EntityType.SHEEP, new BlockPos(55, 1, 56));
        sheep.setNoAi(true); sheep.setColor(net.minecraft.world.item.DyeColor.WHITE);
        var named = helper.spawn(EntityType.SHEEP, new BlockPos(56, 1, 57));
        named.setCustomName(net.minecraft.network.chat.Component.literal("Pet")); named.setNoAi(true);
        var child = helper.spawn(EntityType.SHEEP, new BlockPos(55, 1, 57));
        child.setBaby(true); child.setNoAi(true);
        var outside = helper.spawn(EntityType.SHEEP, new BlockPos(64, 1, 59));
        outside.setNoAi(true);
        var job = new ShepherdJob(worker, colony);
        boolean[] sawCargo = {false};
        for (int tick = 1; tick < 1200; tick++) helper.runAtTickTime(tick, () -> {
            job.tick(helper.getLevel());
            if (!worker.getCourierCargo().isEmpty() && !sawCargo[0]) {
                if (!sheep.isSheared() || !worker.getCourierCargo().is(Items.WHITE_WOOL)
                        || worker.getMainHandItem().getDamageValue() != 1) helper.fail("Shearing did not use real tool and drops");
                CompoundTag saved = new CompoundTag(); worker.addAdditionalSaveData(saved);
                ItemStack restored = ItemStack.parseOptional(helper.getLevel().registryAccess(), saved.getCompound("courierCargo"));
                if (!ItemStack.isSameItemSameComponents(restored, worker.getCourierCargo())
                        || restored.getCount() != worker.getCourierCargo().getCount()) helper.fail("Wool cargo did not persist");
                if (worker.distanceToSqr(absChest.getX() + .5, absChest.getY() + .5, absChest.getZ() + .5) <= 9)
                    helper.fail("Did not carry wool from pasture");
                sawCargo[0] = true;
            }
            if (!items.getItem(0).isEmpty() && worker.distanceToSqr(absChest.getX() + .5, absChest.getY() + .5, absChest.getZ() + .5) > 9)
                helper.fail("Delivered wool remotely");
        });
        helper.succeedWhen(() -> {
            if (!sawCargo[0] || !worker.getCourierCargo().isEmpty() || !items.getItem(0).is(Items.WHITE_WOOL)
                    || items.getItem(0).getCount() < 1 || items.getItem(0).getCount() > 3)
                helper.fail("Wool not physically delivered: " + job.getState() + "; position=" + worker.blockPosition().toShortString());
            if (named.isSheared() || child.isSheared() || outside.isSheared()) helper.fail("Shepherd sheared protected sheep");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void walksToSharedChestForShears(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos pasture = new BlockPos(7, 1, 8), loom = new BlockPos(6, 1, 7), chest = new BlockPos(13, 1, 8);
        helper.setBlock(pasture, ModBlocks.BUILDING_MARKER.get()); helper.setBlock(loom, Blocks.LOOM); helper.setBlock(chest, Blocks.CHEST);
        BlockPos center = helper.absolutePos(pasture), absChest = helper.absolutePos(chest);
        var marker = (BuildingMarkerBlockEntity) helper.getBlockEntity(pasture);
        marker.setBuildingType(BuildingType.PASTURE); marker.setLinkedBuildingPos(center);
        Colony colony = new Colony(0, CultureType.ROMAN, center);
        colony.registerPlayerBuilding(new Building(BuildingType.PASTURE, center, CultureType.ROMAN, true));
        var container = (Container) helper.getBlockEntity(chest);
        ItemStack shears = new ItemStack(Items.SHEARS); shears.setDamageValue(12); container.setItem(0, shears);
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), absChest)) helper.fail("Could not share chest");
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(4, 1, 8));
        worker.setVillagerData(worker.getVillagerData().setProfession(ColonyProfessions.profession(JobType.SHEPHERD)));
        worker.refreshBrain(helper.getLevel());
        worker.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(loom)));
        var sheep = helper.spawn(EntityType.SHEEP, new BlockPos(8, 1, 8)); sheep.setNoAi(true);
        var job = new ShepherdJob(worker, colony);
        boolean[] tookShears = {false};
        for (int tick = 1; tick < 1200; tick++) helper.runAtTickTime(tick, () -> {
            job.tick(helper.getLevel());
            if (!tookShears[0] && worker.getMainHandItem().is(Items.SHEARS)) {
                if (worker.distanceToSqr(absChest.getX() + .5, absChest.getY() + .5, absChest.getZ() + .5) > 9
                        || worker.getMainHandItem().getDamageValue() != 12 || !container.getItem(0).isEmpty())
                    helper.fail("Shears were duplicated, altered, or collected remotely");
                tookShears[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            if (!tookShears[0] || !sheep.isSheared() || worker.getMainHandItem().getDamageValue() != 13)
                helper.fail("Shepherd did not fetch and use the shears: " + job.getState());
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void refusesStalePastureAndUnsharedTool(GameTestHelper helper) {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        BlockPos pasture = new BlockPos(8, 1, 8), loom = new BlockPos(7, 1, 8), chest = new BlockPos(5, 1, 8);
        helper.setBlock(loom, Blocks.LOOM); helper.setBlock(chest, Blocks.CHEST);
        ((Container) helper.getBlockEntity(chest)).setItem(0, new ItemStack(Items.SHEARS));
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(pasture));
        // A stale building record is not enough: its marker has been removed.
        if (!colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chest))) helper.fail("Could not share test chest");
        colony.registerPlayerBuilding(new Building(BuildingType.PASTURE, colony.getCenter(), CultureType.ROMAN, true));
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(6, 1, 8));
        worker.setVillagerData(worker.getVillagerData().setProfession(ColonyProfessions.profession(JobType.SHEPHERD)));
        worker.refreshBrain(helper.getLevel());
        worker.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(loom)));
        var sheep = helper.spawn(EntityType.SHEEP, new BlockPos(9, 1, 8)); sheep.setNoAi(true);
        var job = new ShepherdJob(worker, colony);
        for (int tick = 1; tick < 300; tick++) helper.runAtTickTime(tick, () -> job.tick(helper.getLevel()));
        helper.runAtTickTime(120, () -> {
            if (sheep.isSheared() || !worker.getMainHandItem().isEmpty()) helper.fail("Shepherd used a stale pasture");
            colony.getWarehouseManager().unregisterStorage(helper.getLevel(), helper.absolutePos(chest));
            helper.setBlock(pasture, ModBlocks.BUILDING_MARKER.get());
            var entity = (BuildingMarkerBlockEntity) helper.getBlockEntity(pasture);
            entity.setBuildingType(BuildingType.PASTURE); entity.setLinkedBuildingPos(helper.absolutePos(pasture));
        });
        helper.runAtTickTime(250, () -> {
            if (sheep.isSheared() || !worker.getMainHandItem().isEmpty()
                    || !((Container) helper.getBlockEntity(chest)).getItem(0).is(Items.SHEARS))
                helper.fail("Shepherd used an unshared chest or a stale pasture");
            helper.succeed();
        });
    }
}
