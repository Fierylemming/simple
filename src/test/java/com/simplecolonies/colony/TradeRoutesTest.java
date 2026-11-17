package com.simplecolonies.colony;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeRoutesTest {
    @Test void harborSellsTheLargestSurplusAboveItsReserve() {
        var stock = Map.of("minecraft:cod", 40, "minecraft:oak_log", 30, "minecraft:oak_planks", 20);
        var visit = TradeRoutes.harborVisit(stock, 60, 8).orElseThrow();
        assertEquals("minecraft:cod", visit.giveItem());
        assertEquals(8, visit.giveCount());
        assertEquals("minecraft:emerald", visit.takeItem());
        assertEquals(1, visit.takeCount());
    }

    @Test void harborSellsWhicheverSpeciesTheColonyHas() {
        // A birch forest has no oak at all; the visit must still find a surplus.
        var mixed = TradeRoutes.harborVisit(
                Map.of("minecraft:birch_log", 60, "minecraft:birch_planks", 48), 40, 8).orElseThrow();
        assertEquals("minecraft:birch_log", mixed.giveItem(), "the largest spare surplus is sold");
        assertEquals(12, mixed.giveCount());
        assertEquals("minecraft:emerald", mixed.takeItem());

        var planksOnly = TradeRoutes.harborVisit(Map.of("minecraft:birch_planks", 60), 40, 8).orElseThrow();
        assertEquals("minecraft:birch_planks", planksOnly.giveItem());
        assertEquals(16, planksOnly.giveCount());
    }

    @Test void marketOrdersTheSpeciesAPlanActuallyNeeds() {
        var visit = TradeRoutes.marketVisit(Map.of("minecraft:emerald", 4),
                List.of("minecraft:birch_planks", "minecraft:cobblestone")).orElseThrow();
        assertEquals("minecraft:emerald", visit.giveItem());
        assertEquals("minecraft:birch_planks", visit.takeItem());
        assertEquals(16, visit.takeCount());
    }

    @Test void harborKeepsReservesAndSkipsSmallSurpluses() {
        assertTrue(TradeRoutes.harborVisit(Map.of("minecraft:cod", 12), 40, 0).isEmpty(),
                "a full reserve leaves nothing to sell");
        assertTrue(TradeRoutes.harborVisit(Map.of("minecraft:oak_log", 40), 40, 0).isPresent());
    }

    @Test void harborNeverSellsTheColonysOwnFood() {
        var stock = Map.of("minecraft:cod", 40);
        assertTrue(TradeRoutes.harborVisit(stock, 10, 12).isEmpty(), "food below the need is untouchable");
        assertTrue(TradeRoutes.harborVisit(stock, 24, 12).isPresent());
    }

    @Test void marketOnlyOrdersNeededGoodsItCanAfford() {
        var stock = Map.of("minecraft:emerald", 5);
        var order = TradeRoutes.marketVisit(stock, List.of("minecraft:iron_ingot")).orElseThrow();
        assertEquals("minecraft:emerald", order.giveItem());
        assertEquals(3, order.giveCount());
        assertEquals("minecraft:iron_ingot", order.takeItem());
        assertEquals(4, order.takeCount());
        assertTrue(TradeRoutes.marketVisit(stock, List.of("minecraft:diamond")).isEmpty());
    }

    @Test void marketSkipsItemsItCannotAfford() {
        var stock = Map.of("minecraft:emerald", 2);
        assertTrue(TradeRoutes.marketVisit(stock, List.of("minecraft:quartz_block")).isEmpty());
        assertTrue(TradeRoutes.marketVisit(Map.of(), List.of("minecraft:oak_log")).isEmpty());
    }
}
