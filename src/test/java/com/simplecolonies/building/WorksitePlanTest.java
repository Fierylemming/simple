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
        var types = new BuildingType[]{BuildingType.BUILDER_HUT, BuildingType.LUMBERJACK_HUT, BuildingType.MINE};
        var fingerprints = new java.util.HashSet<Map<BlockPos, net.minecraft.world.level.block.state.BlockState>>();
        for (var type : types) {
            Map<BlockPos, net.minecraft.world.level.block.state.BlockState> placements = new HashMap<>();
            LevelOneWorksites.generate(type, BlockPos.ZERO, (pos, state) -> assertNull(placements.put(pos, state)));
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
}
