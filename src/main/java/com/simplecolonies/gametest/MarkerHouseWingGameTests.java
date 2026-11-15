package com.simplecolonies.gametest;

import com.simplecolonies.block.ModBlocks;
import com.simplecolonies.block.entity.BuildingMarkerBlockEntity;
import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingState;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.building.BuildingUpgrade;
import com.simplecolonies.colony.CitizenData;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ModEntities;
import com.simplecolonies.villager.job.BuilderJob;
import com.simplecolonies.villager.job.JobType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class MarkerHouseWingGameTests {
    @GameTest(template = "isolated", timeoutTicks = 9000)
    public static void markerHouseWingUsesPhysicalMaterialsAndResumesAfterReload(GameTestHelper helper) {
        for (int x = 36; x <= 80; x++) for (int z = 36; z <= 80; z++) {
            helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
            // The isolated GameTest template has an artificial ceiling at y=5;
            // a real six-block-tall house cannot be planned beneath it.
            for (int y = 5; y <= 7; y++) {
                var p = helper.absolutePos(new BlockPos(x, y, z));
                if (helper.getLevel().getBlockState(p).is(Blocks.BARRIER))
                    helper.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        var origin = new BlockPos(56, 1, 56); var chest = origin.offset(-12, 0, -12);
        helper.setBlock(origin, ModBlocks.BUILDING_MARKER.get());
        helper.setBlock(origin.west(), Blocks.CRAFTING_TABLE);
        helper.setBlock(chest, Blocks.CHEST);
        var marker = (BuildingMarkerBlockEntity) helper.getBlockEntity(origin);
        var abs = helper.absolutePos(origin); var storage = helper.absolutePos(chest);
        Colony[] colony = {new Colony(GameTestColonyIds.next(), CultureType.ROMAN, abs)};
        colony[0].addCitizen(new CitizenData(java.util.UUID.randomUUID(), "Builder", JobType.BUILDER));
        for (int i = 0; i < 7; i++) colony[0].addCitizen(new CitizenData(java.util.UUID.randomUUID(), "Resident", JobType.UNEMPLOYED));
        if (!colony[0].updateMarkerBuilding(abs, false, BuildingType.HOUSE)) helper.fail("Could not designate the house");
        marker.setBuildingType(BuildingType.HOUSE); marker.setLinkedBuildingPos(abs);
        Building[] house = {colony[0].getBuildings().getFirst()};
        colony[0].depositToWarehouse("minecraft:spruce_log", 300);
        colony[0].depositToWarehouse("minecraft:cobblestone", 300);
        colony[0].depositToWarehouse("minecraft:coal", 32);
        if (BuildingUpgrade.tryUpgrade(colony[0], house[0], helper.getLevel(), null)) helper.fail("Virtual stock funded a physical wing");
        var warehouse = colony[0].getWarehouseManager();
        if (!warehouse.registerStorage(helper.getLevel(), storage)) helper.fail("Could not share materials");
        warehouse.deposit(helper.getLevel(), storage, "minecraft:spruce_log", 300);
        warehouse.deposit(helper.getLevel(), storage, "minecraft:cobblestone", 300);
        warehouse.deposit(helper.getLevel(), storage, "minecraft:coal", 32);
        var container = (Container) helper.getBlockEntity(chest);
        if (!BuildingUpgrade.tryUpgrade(colony[0], house[0], helper.getLevel(), null)) helper.fail("Could not queue marker wing");
        var wing = house[0].getUpgradeSite(); int total = house[0].getRemainingPlacements().size();
        if (house[0].getRemainingPlacements().stream().noneMatch(p -> p.state.is(Blocks.SPRUCE_PLANKS))
                || house[0].getRemainingPlacements().stream().anyMatch(p -> p.state.is(Blocks.OAK_PLANKS)))
            helper.fail("Marker wing did not select the available spruce family");
        if (wing == null || total == 0 || house[0].getLevel() != 1 || colony[0].getShelterCapacity() != 4
                || !house[0].isOperational() || container.countItem(net.minecraft.world.item.Items.SPRUCE_LOG) != 300)
            helper.fail("Queue prematurely consumed supplies or awarded capacity");
        if (colony[0].updateMarkerBuilding(abs, true, BuildingType.FARM)) helper.fail("Changed marker during unfinished wing");
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), origin.offset(-4, 0, -5));
        BuilderJob[] job = {new BuilderJob(worker, colony[0])};
        boolean[] reloaded = {false};
        for (int tick = 1; tick < 9000; tick++) helper.runAtTickTime(tick, () -> {
            job[0].tick(helper.getLevel());
            if (!reloaded[0] && house[0].getRemainingPlacements().size() < total
                    && house[0].getState() != BuildingState.COMPLETE) {
                var tag = new CompoundTag(); worker.addAdditionalSaveData(tag);
                var savedMaterials = new net.minecraft.world.SimpleContainer(18);
                savedMaterials.fromTag(tag.getList("builderMaterials", net.minecraft.nbt.Tag.TAG_COMPOUND), helper.getLevel().registryAccess());
                if (savedMaterials.countItem(net.minecraft.world.item.Items.SPRUCE_LOG) != worker.getBuilderMaterials().countItem(net.minecraft.world.item.Items.SPRUCE_LOG))
                    helper.fail("Carried supplies were not saved");
                int remaining = house[0].getRemainingPlacements().size();
                colony[0] = Colony.load(colony[0].save());
                house[0] = colony[0].getBuildings().getFirst();
                if (!house[0].isPlayerPlaced || !house[0].isUpgrading() || !house[0].isOperational()
                        || !wing.equals(house[0].getUpgradeSite()) || house[0].getRemainingPlacements().size() != remaining
                        || house[0].getRemainingPlacements().stream().noneMatch(p -> p.state.is(Blocks.SPRUCE_PLANKS))
                        || colony[0].getShelterCapacity() != 4) helper.fail("Marker wing or parent did not survive reload");
                job[0] = new BuilderJob(worker, colony[0]); reloaded[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            if (!reloaded[0] || house[0].getState() != BuildingState.COMPLETE || house[0].getLevel() != 2
                    || colony[0].getShelterCapacity() != 8 || !house[0].shelterSites().contains(wing)
                    || !helper.getBlockState(origin).is(ModBlocks.BUILDING_MARKER.get())
                    || container.countItem(net.minecraft.world.item.Items.SPRUCE_LOG) >= 300)
                helper.fail("Physical marker wing incomplete: " + house[0].getState() + ", " + job[0].getState()
                        + " remaining=" + house[0].getRemainingPlacements().size() + " worker=" + worker.blockPosition());
            var restored = Colony.load(colony[0].save());
            if (restored.getShelterCapacity() != 8 || !restored.getBuildings().getFirst().shelterSites().contains(wing))
                helper.fail("Completed marker wing was not saved");
        });
    }

    @GameTest(template = "empty")
    public static void oldMarkerCapacityAndCompletedWingsSurviveAnotherCancelledUpgrade(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        var old = new Building(BuildingType.HOUSE, pos, CultureType.GREEK, true);
        old.setLevel(2); // Historical capacity-only upgrades remain part of the parent.
        var legacyTag = old.save(); legacyTag.remove("planVersion");
        var house = Building.load(legacyTag);
        if (!house.beginWorksiteWing(pos.east(7))) helper.fail("Old house marker could not upgrade");
        while (house.getNextPlacement() != null) house.markPlaced(house.getNextPlacement());
        house.finishConstruction();
        if (house.getLevel() != 3 || house.getHousingCapacity() != 12 || !house.shelterSites().contains(pos.east(7)))
            helper.fail("Completed first wing did not preserve historical marker capacity");
        if (!house.beginWorksiteWing(pos.west(7))) helper.fail("Could not queue another marker wing");
        var saved = Building.load(house.save());
        if (!saved.isUpgrading() || saved.getLevel() != 3 || !saved.shelterSites().contains(pos.east(7))
                || saved.shelterSites().contains(pos.west(7)) || !saved.cancelUpgrade())
            helper.fail("In-progress marker wing failed to preserve its completed parent and wing");
        saved = Building.load(saved.save());
        if (saved.getLevel() != 3 || saved.isUpgrading() || !saved.shelterSites().contains(pos.east(7))
                || saved.shelterSites().contains(pos.west(7))) helper.fail("Cancelled marker wing did not survive reload");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void markerWingCancellationAndBreakingPreservePlacedBlocks(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(8, 1, 8));
        var colony = new Colony(GameTestColonyIds.next(), CultureType.GREEK, pos);
        colony.updateMarkerBuilding(pos, false, BuildingType.HOUSE);
        var house = colony.getBuildings().getFirst();
        var wing = pos.east(4); // Direct plan fixture stays within the small template.
        if (!house.beginWorksiteWing(wing)) helper.fail("Could not begin wing");
        var first = house.getNextPlacement();
        helper.getLevel().setBlock(first.pos, first.state, 3); house.markPlaced(first);
        var restored = Colony.load(colony.save());
        if (!restored.cancelUnfinishedBuilding(pos) || restored.getBuildings().size() != 1
                || restored.getBuildings().getFirst().getLevel() != 1 || !helper.getLevel().getBlockState(first.pos).equals(first.state))
            helper.fail("Cancellation removed the parent or a placed block");
        var retained = restored.getBuildings().getFirst();
        if (!retained.beginWorksiteWing(wing) || !restored.removeMarkerBuilding(pos, BuildingType.HOUSE)
                || !restored.getBuildings().isEmpty() || !helper.getLevel().getBlockState(first.pos).equals(first.state))
            helper.fail("Breaking a marker did not revoke an unfinished designation safely");
        helper.succeed();
    }
}
