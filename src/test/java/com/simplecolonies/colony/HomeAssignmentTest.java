package com.simplecolonies.colony;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.villager.job.JobType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HomeAssignmentTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void sharedHomesRespectCapacityPersistAndReassignOnlyWhenNecessary() {
        Colony colony = new Colony(42, CultureType.ROMAN, BlockPos.ZERO);
        BlockPos first = new BlockPos(15, 0, 0), second = new BlockPos(0, 0, 15);
        colony.registerPlayerBuilding(new Building(BuildingType.TOWN_HALL, BlockPos.ZERO, CultureType.ROMAN, true));
        colony.registerPlayerBuilding(new Building(BuildingType.HOUSE, first, CultureType.ROMAN, true));
        for (int i = 1; i <= 5; i++) colony.addCitizen(new CitizenData(new UUID(0, i), "Citizen " + i, JobType.MINER));
        colony.reconcileHomes();
        assertEquals(8, colony.getShelterCapacity()); // bedless hall + completed house
        assertEquals(4, colony.getCitizens().stream().filter(c -> first.equals(c.home)).count());
        assertEquals(1, colony.getCitizens().stream().filter(c -> c.home == null).count());
        colony.registerPlayerBuilding(new Building(BuildingType.HOUSE, second, CultureType.ROMAN, true));
        colony.reconcileHomes();
        assertEquals(4, colony.getCitizens().stream().filter(c -> first.equals(c.home)).count());
        assertEquals(1, colony.getCitizens().stream().filter(c -> second.equals(c.home)).count());
        Colony restored = Colony.load(colony.save());
        restored.reconcileHomes();
        for (var citizen : colony.getCitizens())
            assertEquals(citizen.home, restored.getCitizens().stream().filter(c -> c.uuid.equals(citizen.uuid)).findFirst().orElseThrow().home);
        assertTrue(restored.updateMarkerBuilding(first, true, null));
        restored.reconcileHomes();
        assertEquals(4, restored.getCitizens().stream().filter(c -> second.equals(c.home)).count());
        assertEquals(1, restored.getCitizens().stream().filter(c -> c.home == null).count());
    }

    @Test void markerRemovalOnlyTouchesMatchingPlayerBuildings() {
        Colony colony = new Colony(44, CultureType.ROMAN, BlockPos.ZERO);
        BlockPos housePos = new BlockPos(6, 0, 0), generatedPos = new BlockPos(9, 0, 0);
        colony.registerPlayerBuilding(new Building(BuildingType.HOUSE, housePos, CultureType.ROMAN, true));
        var generated = new Building(BuildingType.HOUSE, generatedPos, CultureType.ROMAN);
        generated.setState(com.simplecolonies.building.BuildingState.COMPLETE);
        colony.registerPlayerBuilding(generated);
        assertFalse(colony.removeMarkerBuilding(housePos, BuildingType.WAREHOUSE));
        assertFalse(colony.removeMarkerBuilding(generatedPos, BuildingType.HOUSE));
        assertFalse(colony.updateMarkerBuilding(generatedPos, true, BuildingType.FARM));
        assertEquals(2, colony.getBuildings().size());
        assertTrue(colony.removeMarkerBuilding(housePos, BuildingType.HOUSE));
        assertEquals(1, colony.getBuildings().size());
        assertSame(generated, colony.getBuildings().getFirst());
    }

    @Test void legacyCitizensHaveNoInventedHomeAndUnfinishedHousesAreIgnored() {
        var legacy = new CitizenData(UUID.randomUUID(), "Legacy", JobType.UNEMPLOYED).save();
        legacy.remove("home");
        var citizen = CitizenData.load(legacy);
        assertNull(citizen.home);
        Colony colony = new Colony(43, CultureType.ROMAN, BlockPos.ZERO);
        colony.addCitizen(citizen);
        colony.registerPlayerBuilding(new Building(BuildingType.HOUSE, new BlockPos(15, 0, 0), CultureType.ROMAN));
        colony.reconcileHomes();
        assertEquals(0, colony.getShelterCapacity()); // unfinished house is not shelter
        assertNull(citizen.home);
    }
}
