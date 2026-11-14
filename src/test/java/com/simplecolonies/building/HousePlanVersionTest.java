package com.simplecolonies.building;

import com.simplecolonies.culture.CultureType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HousePlanVersionTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void newHouseSavesVariantAndCraftableMaterials() {
        Building b = new Building(BuildingType.HOUSE, BlockPos.ZERO, CultureType.GREEK);
        assertTrue(b.getRequiredResources().containsKey("minecraft:cobblestone"));
        assertTrue(b.getRequiredResources().containsKey("minecraft:oak_door"));
        assertTrue(b.getRequiredResources().containsKey("minecraft:oak_stairs"));
        assertTrue(b.getRequiredResources().containsKey("minecraft:oak_slab"));
        var tag = b.save();
        assertEquals(13, tag.getInt("planVersion"));
        Building restored = Building.load(tag);
        assertEquals(b.getRequiredResources(), restored.getRequiredResources());
        assertEquals(tag.getInt("houseVariant"), restored.save().getInt("houseVariant"));
    }
    @Test void nonOakPlansKeepTimberAndProgressAcrossReload() {
        for (var wood : HouseTimber.values()) {
            Building b = new Building(BuildingType.HOUSE, new BlockPos(4, 64, 8), CultureType.GREEK, wood);
            assertTrue(b.getRequiredResources().containsKey("minecraft:" + wood.id + "_door"));
            if (wood != HouseTimber.OAK) assertFalse(b.getRequiredResources().containsKey("minecraft:oak_planks"));
            for (int i = 0; i < 5; i++) b.markPlaced(b.getNextPlacement());
            Building restored = Building.load(b.save());
            assertEquals(wood.id, restored.save().getString("houseTimber"));
            assertEquals(b.getRequiredResources(), restored.getRequiredResources());
            assertEquals(b.getNextPlacement().pos, restored.getNextPlacement().pos);
            assertEquals(b.getNextPlacement().state, restored.getNextPlacement().state);
        }
    }
    @Test void versionElevenAlwaysRestoresOriginalOakPlan() {
        var tag = new Building(BuildingType.HOUSE, BlockPos.ZERO, CultureType.GREEK, HouseTimber.BIRCH).save();
        tag.putInt("planVersion", 11);
        Building legacy = Building.load(tag);
        assertTrue(legacy.getRequiredResources().containsKey("minecraft:oak_door"));
        assertFalse(legacy.getRequiredResources().containsKey("minecraft:birch_door"));
    }
    @Test void allCultureVariantsHaveDoorsRoofsAndNoBeds() {
        for (CultureType culture : CultureType.values()) for (int variant = 0; variant < 3; variant++) {
            Map<BlockPos, net.minecraft.world.level.block.state.BlockState> plan = new HashMap<>();
            LevelOneHouses.generate(BlockPos.ZERO, culture, variant, (pos, state) -> assertNull(plan.put(pos, state)));
            assertTrue(plan.values().stream().anyMatch(s -> s.is(Blocks.OAK_DOOR)));
            assertTrue(plan.values().stream().anyMatch(s -> s.is(Blocks.OAK_STAIRS)));
            assertTrue(plan.values().stream().anyMatch(s -> s.is(Blocks.OAK_SLAB)));
            assertFalse(plan.values().stream().anyMatch(s -> s.is(net.minecraft.tags.BlockTags.BEDS)));
        }
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
    @Test void versionTwoContinuesWithOriginalStoneAndLogCosts() {
        var tag = new Building(BuildingType.HOUSE, BlockPos.ZERO, CultureType.ROMAN).save();
        tag.putInt("planVersion", 2);
        Building old = Building.load(tag);
        assertEquals(15, old.getRequiredResources().get("minecraft:cobblestone"));
        assertEquals(56, old.getRequiredResources().get("minecraft:oak_log"));
    }
}
