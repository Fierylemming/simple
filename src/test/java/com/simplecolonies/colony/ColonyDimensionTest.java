package com.simplecolonies.colony;

import com.simplecolonies.culture.CultureType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColonyDimensionTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @SuppressWarnings("unchecked")
    @Test void matchingCoordinatesInAnotherDimensionAreNotColonyTerritory() throws Exception {
        var constructor = ColonyManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var manager = constructor.newInstance();
        var field = ColonyManager.class.getDeclaredField("colonies");
        field.setAccessible(true);
        var colonies = (java.util.Map<Integer, Colony>) field.get(manager);
        var colony = new Colony(7, CultureType.ROMAN, BlockPos.ZERO);
        colonies.put(colony.getId(), colony);
        assertSame(colony, manager.getColonyAt(Level.OVERWORLD, BlockPos.ZERO).orElseThrow());
        assertTrue(manager.getColonyAt(Level.NETHER, BlockPos.ZERO).isEmpty());
        assertTrue(manager.getColonyAt(Level.END, BlockPos.ZERO).isEmpty());
    }
}
