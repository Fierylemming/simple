package com.simplecolonies.building;

import com.simplecolonies.culture.CultureType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class WorksitePlanTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void workshopPlansSurroundButNeverReplaceTheirWorkstations() {
        var types = new BuildingType[]{BuildingType.BUILDER_HUT, BuildingType.LUMBERJACK_HUT, BuildingType.MINE, BuildingType.BLACKSMITH, BuildingType.BUTCHER_HUT, BuildingType.HARBOR, BuildingType.BAKERY, BuildingType.LEATHER_SHOP, BuildingType.WAREHOUSE, BuildingType.BARRACKS};
        var fingerprints = new java.util.HashSet<Map<BlockPos, net.minecraft.world.level.block.state.BlockState>>();
        for (var type : types) {
            Map<BlockPos, net.minecraft.world.level.block.state.BlockState> placements = new HashMap<>();
            LevelOneWorksites.generate(type, BlockPos.ZERO, CultureType.ROMAN, (pos, state) -> assertNull(placements.put(pos, state)));
            assertFalse(placements.containsKey(BlockPos.ZERO));
            assertTrue(placements.values().stream().anyMatch(s -> s.is(Blocks.OAK_DOOR)));
            assertTrue(placements.values().stream().anyMatch(s -> s.is(Blocks.TORCH)));
            assertTrue(placements.keySet().stream().allMatch(p -> Math.abs(p.getX()) <= 2 && Math.abs(p.getZ()) <= 2 && p.getY() >= 0 && p.getY() <= 3));
            assertTrue(fingerprints.add(placements), "worksites should not all have the same materials");
            Building building = new Building(type, BlockPos.ZERO, CultureType.ROMAN);
            assertTrue(building.hasSupportedPlan());
            assertEquals(placements.size(), building.getRemainingPlacements().size());
            for (int i = 0; i < 7; i++) building.markPlaced(building.getNextPlacement());
            Building loaded = Building.load(building.save());
            assertEquals(building.getNextPlacement().pos, loaded.getNextPlacement().pos);
            assertEquals(building.getNextPlacement().state, loaded.getNextPlacement().state);
            assertEquals(building.getRequiredResources(), loaded.getRequiredResources());
        }
    }
    @Test void oldUnsupportedWorksitesDoNotSuddenlyGainPlacements() {
        var tag = new Building(BuildingType.MINE, BlockPos.ZERO, CultureType.GREEK).save();
        tag.putInt("planVersion", 3);
        Building old = Building.load(tag);
        assertFalse(old.hasSupportedPlan());
        assertTrue(old.getRemainingPlacements().isEmpty());
    }
    @Test void olderSmithAndButcherPlaceholdersRemainUnsupported() {
        for (var type : new BuildingType[]{BuildingType.BLACKSMITH, BuildingType.BUTCHER_HUT}) {
            var tag = new Building(type, BlockPos.ZERO, CultureType.ROMAN).save();
            tag.putInt("planVersion", 6);
            Building old = Building.load(tag);
            assertFalse(old.hasSupportedPlan());
            assertTrue(old.getRemainingPlacements().isEmpty());
        }
    }
    @Test void olderHarborPlaceholdersRemainUnsupported() {
        for (int version = 1; version <= 7; version++) {
            var tag = new Building(BuildingType.HARBOR, BlockPos.ZERO, CultureType.ROMAN).save();
            tag.putInt("planVersion", version);
            Building old = Building.load(tag);
            assertFalse(old.hasSupportedPlan());
            assertTrue(old.getRemainingPlacements().isEmpty());
        }
    }
    @Test void olderBakeryAndLeatherPlaceholdersRemainUnsupported() {
        for (var type : new BuildingType[]{BuildingType.BAKERY, BuildingType.LEATHER_SHOP}) {
            for (int version = 1; version <= 8; version++) {
                var tag = new Building(type, BlockPos.ZERO, CultureType.ROMAN).save();
                tag.putInt("planVersion", version);
                Building old = Building.load(tag);
                assertFalse(old.hasSupportedPlan());
                assertTrue(old.getRemainingPlacements().isEmpty());
            }
        }
    }
    @Test void olderWarehouseAndBarracksPlaceholdersRemainUnsupported() {
        for (var type : new BuildingType[]{BuildingType.WAREHOUSE, BuildingType.BARRACKS}) {
            for (int version = 1; version <= 9; version++) {
                var tag = new Building(type, BlockPos.ZERO, CultureType.ROMAN).save();
                tag.putInt("planVersion", version);
                Building old = Building.load(tag);
                assertFalse(old.hasSupportedPlan());
                assertTrue(old.getRemainingPlacements().isEmpty());
            }
        }
    }
    @Test void marketsAreVersionedCulturalAndResumeWithoutChangingOlderPlaceholders() {
        var fingerprints = new java.util.HashSet<Map<BlockPos, net.minecraft.world.level.block.state.BlockState>>();
        for (var culture : CultureType.values()) {
            var building = new Building(BuildingType.MARKET, BlockPos.ZERO, culture);
            assertTrue(building.hasSupportedPlan());
            Map<BlockPos, net.minecraft.world.level.block.state.BlockState> plan = new HashMap<>();
            building.getRemainingPlacements().forEach(p -> assertNull(plan.put(p.pos, p.state)));
            assertTrue(fingerprints.add(plan), "Each culture should have a distinct meeting hall");
            for (int i = 0; i < 9; i++) building.markPlaced(building.getNextPlacement());
            var reloaded = Building.load(building.save());
            assertEquals(building.getNextPlacement().pos, reloaded.getNextPlacement().pos);
            assertEquals(building.getRequiredResources(), reloaded.getRequiredResources());
        }
        for (int version = 1; version <= 10; version++) {
            var tag = new Building(BuildingType.MARKET, BlockPos.ZERO, CultureType.ROMAN).save();
            tag.putInt("planVersion", version);
            var legacy = Building.load(tag);
            assertFalse(legacy.hasSupportedPlan());
            assertTrue(legacy.getRemainingPlacements().isEmpty());
        }
    }

    @Test void timberWorksitesResumeAndLegacyVersionRemainsOak() {
        for (var type : new BuildingType[]{BuildingType.MARKET, BuildingType.BLACKSMITH, BuildingType.WAREHOUSE}) {
            var b = new Building(type, BlockPos.ZERO, CultureType.ROMAN, HouseTimber.SPRUCE);
            assertTrue(b.getRequiredResources().containsKey("minecraft:spruce_door"));
            for (int i = 0; i < 8; i++) b.markPlaced(b.getNextPlacement());
            var restored = Building.load(b.save());
            assertEquals(b.getRequiredResources(), restored.getRequiredResources());
            assertEquals(b.getNextPlacement().state, restored.getNextPlacement().state);
            var tag = new Building(type, BlockPos.ZERO, CultureType.ROMAN, HouseTimber.SPRUCE).save();
            tag.putInt("planVersion", 11);
            var legacy = Building.load(tag);
            assertTrue(legacy.getRequiredResources().containsKey("minecraft:oak_door"));
        }
    }

    @Test void libraryPlanIsVersionedAndPreservesPartialProgress() {
        for (var culture : CultureType.values()) {
            var library = new Building(BuildingType.LIBRARY, BlockPos.ZERO, culture, HouseTimber.BIRCH);
            assertTrue(library.hasSupportedPlan());
            assertEquals(2, library.getRequiredResources().get("minecraft:bookshelf"));
            assertTrue(library.getRequiredResources().containsKey("minecraft:birch_door"));
            for (int i = 0; i < 7; i++) library.markPlaced(library.getNextPlacement());
            var restored = Building.load(library.save());
            assertEquals(library.getRequiredResources(), restored.getRequiredResources());
            assertEquals(library.getNextPlacement().pos, restored.getNextPlacement().pos);
            assertEquals(library.getNextPlacement().state, restored.getNextPlacement().state);
        }
        for (int version = 1; version <= 12; version++) {
            var tag = new Building(BuildingType.LIBRARY, BlockPos.ZERO, CultureType.ROMAN).save();
            tag.putInt("planVersion", version);
            var legacy = Building.load(tag);
            assertFalse(legacy.hasSupportedPlan());
            assertTrue(legacy.getRemainingPlacements().isEmpty());
        }
    }

    @Test void smithPlanIncludesUsableAdjacentFurnace() {
        var b = new Building(BuildingType.BLACKSMITH, BlockPos.ZERO, CultureType.ROMAN);
        assertTrue(b.getRemainingPlacements().stream().anyMatch(p -> p.pos.equals(BlockPos.ZERO.east()) && p.state.is(Blocks.FURNACE)));
    }

    @Test void smithyWingResumesAndOnlyAwardsTierAtCompletion() {
        var smithy = new Building(BuildingType.BLACKSMITH, BlockPos.ZERO, CultureType.GREEK, HouseTimber.BIRCH);
        smithy.setState(BuildingState.COMPLETE);
        var wing = BlockPos.ZERO.east(5);
        var preview = smithy.previewSmithyWing(wing);
        assertTrue(preview.stream().anyMatch(p -> p.pos.equals(wing) && p.state.is(Blocks.SMITHING_TABLE)));
        assertTrue(preview.stream().anyMatch(p -> p.pos.equals(wing.east()) && p.state.is(Blocks.FURNACE)));
        assertTrue(smithy.beginSmithyWing(wing));
        assertEquals(1, smithy.getLevel());
        smithy.finishConstruction();
        assertEquals(1, smithy.getLevel());
        for (int i = 0; i < 12; i++) smithy.markPlaced(smithy.getNextPlacement());
        var loaded = Building.load(smithy.save());
        assertEquals(smithy.getNextPlacement().pos, loaded.getNextPlacement().pos);
        assertEquals(smithy.getNextPlacement().state, loaded.getNextPlacement().state);
        assertEquals(smithy.getRequiredResources(), loaded.getRequiredResources());
        while (loaded.getNextPlacement() != null) loaded.markPlaced(loaded.getNextPlacement());
        loaded.finishConstruction();
        assertEquals(2, loaded.getLevel());
        assertEquals(BuildingState.COMPLETE, loaded.getState());
        assertEquals(wing, Building.load(loaded.save()).getUpgradeSite());
        assertFalse(loaded.canBuildSmithyWing());
        assertTrue(smithy.cancelUpgrade());
        assertEquals(BuildingState.COMPLETE, smithy.getState());
        assertEquals(1, smithy.getLevel());
        assertNull(smithy.getUpgradeSite());
    }

    @Test void unknownUpgradePlanCannotBeBuiltOrMisreadAsOriginalSmithy() {
        var smithy = new Building(BuildingType.BLACKSMITH, BlockPos.ZERO, CultureType.ROMAN);
        smithy.setState(BuildingState.COMPLETE);
        assertTrue(smithy.beginSmithyWing(BlockPos.ZERO.east(5)));
        var save = smithy.save();
        save.putInt("upgradePlanVersion", 99);
        var loaded = Building.load(save);
        assertFalse(loaded.hasSupportedPlan());
        assertTrue(loaded.getRemainingPlacements().isEmpty());
        assertEquals(99, loaded.save().getInt("upgradePlanVersion"));
    }

    @Test void generatedHousesAndWorksitesPersistPhysicalWingsThroughFiveTiers() {
        for (var type : java.util.List.of(BuildingType.HOUSE, BuildingType.BLACKSMITH, BuildingType.BAKERY,
                BuildingType.BUILDER_HUT, BuildingType.LUMBERJACK_HUT, BuildingType.MINE, BuildingType.BUTCHER_HUT,
                BuildingType.LEATHER_SHOP, BuildingType.HARBOR, BuildingType.WAREHOUSE, BuildingType.BARRACKS)) {
            var building = new Building(type, BlockPos.ZERO, CultureType.GREEK, HouseTimber.BIRCH);
            building.setState(BuildingState.COMPLETE);
            for (int target = 2; target <= 5; target++) {
                assertTrue(building.canBuildWorksiteWing(), type + " at " + target);
                BlockPos wing = BlockPos.ZERO.east(target * 8);
                var preview = building.previewWorksiteWing(wing);
                assertFalse(preview.isEmpty(), type.toString());
                assertTrue(building.beginWorksiteWing(wing));
                assertEquals(target - 1, building.getLevel());
                assertTrue(building.isOperational());
                assertEquals(target - 2, building.completedWingSites().size());
                assertEquals(preview.get(0).pos, building.getNextPlacement().pos);
                building.markPlaced(building.getNextPlacement());
                var restored = Building.load(building.save());
                assertEquals(building.getNextPlacement().pos, restored.getNextPlacement().pos);
                assertEquals(building.getNextPlacement().state, restored.getNextPlacement().state);
                assertEquals(target - 2, restored.completedWingSites().size());
                while (restored.getNextPlacement() != null) restored.markPlaced(restored.getNextPlacement());
                restored.finishConstruction();
                assertEquals(target, restored.getLevel());
                assertEquals(target - 1, restored.completedWingSites().size());
                if (type == BuildingType.HOUSE) assertEquals(target, restored.shelterSites().size());
                assertEquals(restored.completedWingSites(), Building.load(restored.save()).completedWingSites());
                building = restored;
            }
            assertFalse(building.canBuildWorksiteWing());
        }
    }

    @Test void cancellationOfLaterWingRetainsEarlierCompletedShelters() {
        var house = new Building(BuildingType.HOUSE, BlockPos.ZERO, CultureType.ROMAN);
        house.setState(BuildingState.COMPLETE);
        var first = BlockPos.ZERO.east(7);
        assertTrue(house.beginWorksiteWing(first));
        while (house.getNextPlacement() != null) house.markPlaced(house.getNextPlacement());
        house.finishConstruction();
        assertTrue(house.beginWorksiteWing(BlockPos.ZERO.west(7)));
        assertEquals(java.util.List.of(BlockPos.ZERO, first), house.shelterSites());
        assertTrue(house.cancelUpgrade());
        assertEquals(2, house.getLevel());
        assertEquals(java.util.List.of(BlockPos.ZERO, first), Building.load(house.save()).shelterSites());
    }

}
