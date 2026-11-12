package com.simplecolonies.building;

import com.simplecolonies.culture.CultureType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CulturalHousePlansTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void allFifteenPlansAreUniqueClosedRoofedAndMadeFromAvailableMaterials() {
        Set<Map<BlockPos, BlockState>> distinct = new HashSet<>();
        var allowed = Set.of(Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.COBBLESTONE,
                Blocks.OAK_STAIRS, Blocks.OAK_SLAB, Blocks.OAK_DOOR, Blocks.TORCH);
        for (CultureType culture : CultureType.values()) for (int variant = 0; variant < 3; variant++) {
            int v = variant;
            Map<BlockPos, BlockState> plan = new HashMap<>();
            CulturalHousePlans.generate(BlockPos.ZERO, culture, variant, (p, s) -> assertNull(plan.put(p, s), "duplicate at " + p));
            assertTrue(distinct.add(plan), culture + " variant " + variant + " is not distinct");
            assertTrue(plan.values().stream().allMatch(s -> allowed.contains(s.getBlock())));
            int doorZ = variant == 2 ? -3 : -2;
            assertTrue(plan.get(new BlockPos(0, 1, doorZ)).is(Blocks.OAK_DOOR));
            assertFalse(plan.containsKey(new BlockPos(0, 2, doorZ)), "upper door half is placed atomically");
            assertTrue(plan.get(new BlockPos(1, 1, 1)).is(Blocks.TORCH));
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                if (!CulturalHousePlans.footprint(v, x, z)) continue;
                assertTrue(plan.containsKey(new BlockPos(x, 0, z)), "missing floor");
                assertTrue(plan.containsKey(new BlockPos(x, 4, z)), "missing roof");
                if (CulturalHousePlans.perimeter(v, x, z)) for (int y = 1; y <= 3; y++) {
                    if (x == 0 && z == doorZ && y <= 2) continue;
                    assertTrue(plan.containsKey(new BlockPos(x, y, z)), "missing wall");
                }
            }
        }
        assertEquals(15, distinct.size());
    }
    @Test void inProgressOlderPlansRetainTheirExactPrefix() {
        for (int version : new int[]{3, 4, 5}) {
            var tag = new Building(BuildingType.HOUSE, new BlockPos(42, 80, -23), CultureType.PERSIAN).save();
            tag.putInt("planVersion", version);
            var old = Building.load(tag);
            for (int i = 0; i < 30; i++) old.markPlaced(old.getNextPlacement());
            var restored = Building.load(old.save());
            assertEquals(version, restored.save().getInt("planVersion"));
            assertEquals(old.getRemainingPlacements().size(), restored.getRemainingPlacements().size());
            assertEquals(old.getRequiredResources(), restored.getRequiredResources());
            for (int i = 0; i < old.getRemainingPlacements().size(); i++) {
                assertEquals(old.getRemainingPlacements().get(i).pos, restored.getRemainingPlacements().get(i).pos);
                assertEquals(old.getRemainingPlacements().get(i).state, restored.getRemainingPlacements().get(i).state);
            }
        }
    }
}
