package com.simplecolonies.progression;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The schematic cannon research is real: it doubles the blueprint a builder accepts. */
class SchematicCannonTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @SuppressWarnings("unchecked")
    private static void unlock(Colony colony, ColonyUpgrade upgrade) {
        try {
            var field = ResearchManager.class.getDeclaredField("unlocked");
            field.setAccessible(true);
            ((java.util.Set<ColonyUpgrade>) field.get(colony.getResearch())).add(upgrade);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test void theResearchCanBeQueuedOncePrerequisitesAreMet() {
        Colony colony = new Colony(-1200000800, CultureType.ROMAN, new BlockPos(0, 64, 0));
        assertFalse(colony.getResearch().canUnlock(ColonyUpgrade.SCHEMATIC_CANNON),
                "the cannon needs a builder and iron tools first");
        unlock(colony, ColonyUpgrade.UNLOCK_BUILDER);
        unlock(colony, ColonyUpgrade.TOOL_IRON);
        assertTrue(colony.getResearch().canUnlock(ColonyUpgrade.SCHEMATIC_CANNON),
                "with its prerequisites the cannon is researchable like any other upgrade");
    }

    @Test void theCannonDoublesTheAcceptedBlueprint() {
        Colony colony = new Colony(-1200000801, CultureType.ROMAN, new BlockPos(0, 64, 0));
        assertEquals(32768, colony.schematicVolumeLimit());
        assertEquals(16384, colony.schematicBlockLimit());
        unlock(colony, ColonyUpgrade.SCHEMATIC_CANNON);
        assertEquals(65536, colony.schematicVolumeLimit(), "a researched cannon accepts twice the volume");
        assertEquals(32768, colony.schematicBlockLimit(), "a researched cannon accepts twice the blocks");
    }
}
