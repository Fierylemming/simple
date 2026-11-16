package com.simplecolonies.building;

import com.simplecolonies.culture.CultureType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingProtectionTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void plannedBlocksAreProtected() {
        BlockPos origin = new BlockPos(0, 64, 0);
        Building house = new Building(BuildingType.HOUSE, origin, CultureType.ROMAN);
        BlockPos planned = house.getRemainingPlacements().getFirst().pos;
        assertTrue(house.protects(planned), "a planned shell block must be protected");
        assertFalse(house.protects(origin.offset(40, 0, 40)), "a distant block is not part of the house");
    }

    @Test void savedBuildingsKeepTheirBoxWithoutThePlan() {
        Building house = new Building(BuildingType.HOUSE, new BlockPos(10, 64, 10), CultureType.ROMAN);
        BlockPos planned = house.getRemainingPlacements().getFirst().pos;
        while (house.getNextPlacement() != null) house.markPlaced(house.getNextPlacement());
        house.finishConstruction();
        Building loaded = Building.load(house.save());
        assertTrue(loaded.protects(planned), "a finished building must stay protected after reload");
        assertFalse(loaded.getRemainingPlacements().iterator().hasNext());
    }

    @Test void playerMarkerProtectsASmallCoreOnly() {
        BlockPos marker = new BlockPos(-40, 70, 12);
        Building hall = new Building(BuildingType.TOWN_HALL, marker, CultureType.GREEK, true);
        assertTrue(hall.protects(marker));
        assertTrue(hall.protects(marker.offset(3, 2, -3)));
        assertFalse(hall.protects(marker.offset(9, 0, 0)), "a marker must not claim the whole plot");
    }

    @Test void terrainMarkersOwnNoBlocks() {
        BlockPos farm = new BlockPos(80, 64, -8);
        Building marker = new Building(BuildingType.FARM, farm, CultureType.PERSIAN, true);
        assertFalse(marker.protects(farm), "farmland and crops are not a structure");
        assertFalse(new Building(BuildingType.PROTECTED_PLOT, farm, CultureType.PERSIAN, true).protects(farm));
    }

    @Test void wingsExtendTheProtectedBox() {
        BlockPos origin = new BlockPos(-64, 64, 48);
        Building blacksmith = new Building(BuildingType.BLACKSMITH, origin, CultureType.MESOPOTAMIAN);
        while (blacksmith.getNextPlacement() != null) blacksmith.markPlaced(blacksmith.getNextPlacement());
        blacksmith.finishConstruction();
        BlockPos wingSite = origin.offset(6, 0, 0);
        assertTrue(blacksmith.beginWorksiteWing(wingSite), "a supported worksite should queue a wing");
        BlockPos planned = blacksmith.getRemainingPlacements().getFirst().pos;
        assertTrue(blacksmith.protects(planned), "an unfinished wing is still colony property");
    }
}
