package com.simplecolonies.client.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProfessionAppearanceTest {
    @Test void colonyRolesUseVisibleVanillaOutfits() {
        assertEquals("farmer", ProfessionAppearance.vanillaOverlay("farmer"));
        assertEquals("mason", ProfessionAppearance.vanillaOverlay("builder"));
        assertEquals("fletcher", ProfessionAppearance.vanillaOverlay("lumberjack"));
        assertNull(ProfessionAppearance.vanillaOverlay("unemployed"));
    }
}
