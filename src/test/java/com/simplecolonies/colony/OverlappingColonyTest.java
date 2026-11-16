package com.simplecolonies.colony;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.culture.CultureType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Overlapping colonies must not shadow each other's protection. */
class OverlappingColonyTest {
    private static final int FIRST = -1200000700, SECOND = -1200000701;

    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @AfterEach void cleanUp() { install(null, null); }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Colony> colonies() {
        try {
            var field = ColonyManager.class.getDeclaredField("colonies");
            field.setAccessible(true);
            return (Map<Integer, Colony>) field.get(ColonyManager.getInstance());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Colony install(Integer id, Colony colony) {
        var map = colonies();
        if (id == null) {
            map.remove(FIRST);
            map.remove(SECOND);
            return null;
        }
        map.put(id, colony);
        return colony;
    }

    @Test void aProtectedBlockIsFoundInWhicheverColonyOwnsIt() {
        BlockPos shared = new BlockPos(0, 64, 0);
        // First colony only has a marker far away, so it protects nothing here.
        install(FIRST, new Colony(FIRST, CultureType.ROMAN, shared));
        var second = install(SECOND, new Colony(SECOND, CultureType.ROMAN, shared));
        BlockPos built = shared.offset(2, 0, 2);
        second.registerPlayerBuilding(new Building(BuildingType.TOWN_HALL, shared, CultureType.ROMAN, true));

        assertEquals(2, ColonyManager.getInstance().getAllAt(shared).size(), "both colonies cover the position");
        assertTrue(ColonyManager.getInstance().getAllAt(shared).stream().anyMatch(c -> c.protectsStructure(shared)),
                "a block inside a hall must be protected even when another colony is found first");
        assertTrue(second.protectsStructure(built), "the hall shell is protected");
        assertFalse(new Colony(FIRST, CultureType.ROMAN, shared.offset(500, 0, 500)).protectsStructure(built),
                "a distant colony owns nothing here");
    }
}
