package com.simplecolonies.building;

import com.simplecolonies.culture.CultureType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HousePlanVersionTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }
    @Test void newHouseUsesStoneAndSavesItsPlan() {
        Building b = new Building(BuildingType.HOUSE, BlockPos.ZERO, CultureType.ROMAN);
        assertEquals(15, b.getRequiredResources().get("minecraft:cobblestone"));
        assertEquals(56, b.getRequiredResources().get("minecraft:oak_log"));
        var tag = b.save();
        assertEquals(2, tag.getInt("planVersion"));
        Building restored = Building.load(tag);
        assertEquals(b.getRequiredResources(), restored.getRequiredResources());
    }
    @Test void oldInProgressHouseKeepsOldPlanAcrossSaveAndReload() {
        var tag = new Building(BuildingType.HOUSE, BlockPos.ZERO, CultureType.ROMAN).save();
        tag.remove("planVersion");
        Building legacy = Building.load(tag);
        assertFalse(legacy.getRequiredResources().containsKey("minecraft:cobblestone"));
        assertEquals(71, legacy.getRequiredResources().get("minecraft:oak_log"));
        for (int i = 0; i < 3; i++) legacy.markPlaced(legacy.getNextPlacement());
        Building restored = Building.load(legacy.save());
        assertEquals(1, restored.save().getInt("planVersion"));
        assertEquals(68, restored.getRequiredResources().get("minecraft:oak_log"));
        assertEquals(legacy.getNextPlacement().pos, restored.getNextPlacement().pos);
        assertEquals(legacy.getNextPlacement().state, restored.getNextPlacement().state);
    }
}
