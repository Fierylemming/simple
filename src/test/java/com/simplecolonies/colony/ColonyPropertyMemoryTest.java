package com.simplecolonies.colony;

import com.simplecolonies.culture.CultureType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColonyPropertyMemoryTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void playerBlocksAreRememberedAcrossSaveAndLoad() {
        Colony colony = new Colony(7, CultureType.ROMAN, BlockPos.ZERO);
        BlockPos wall = new BlockPos(4, 70, 4);
        BlockPos far = new BlockPos(4, 70, 40);
        assertFalse(colony.isPlayerPlaced(wall));
        colony.recordPlayerPlaced(wall);

        assertTrue(colony.isPlayerPlaced(wall));
        assertTrue(colony.isPlayerPropertyNear(wall.east(), 1), "a wall should protect its neighbours");
        assertFalse(colony.isPlayerPropertyNear(far, 1));

        Colony restored = Colony.load(colony.save());
        assertTrue(restored.isPlayerPlaced(wall), "player blocks must survive a reload");
        assertTrue(restored.isPlayerPropertyNear(wall.above(), 1));
        assertFalse(restored.isPlayerPropertyNear(far, 0));
    }

    @Test void forgettingAPlayerBlockRemovesProtection() {
        Colony colony = new Colony(8, CultureType.GREEK, BlockPos.ZERO);
        BlockPos block = new BlockPos(-3, 66, 12);
        colony.recordPlayerPlaced(block);
        assertTrue(colony.forgetPlayerPlaced(block));
        assertFalse(colony.isPlayerPlaced(block));
        assertFalse(colony.isPlayerPropertyNear(block, 1));
        assertFalse(colony.forgetPlayerPlaced(block), "forgetting twice is harmless");
    }

    @Test void legacySavesWithoutClaimsStillLoad() {
        Colony colony = new Colony(9, CultureType.EGYPTIAN, new BlockPos(32, 64, 32));
        var tag = colony.save();
        tag.remove("playerPlaced");
        Colony legacy = Colony.load(tag);
        assertFalse(legacy.isPlayerPropertyNear(new BlockPos(32, 64, 32), 2));
    }
}
