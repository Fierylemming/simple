package com.simplecolonies.progression;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchCostsTest {
    static class Stock implements ResearchCosts.Stock {
        final Map<String, Integer> items = new HashMap<>();
        String fail;
        public int count(String id) { return items.getOrDefault(id, 0); }
        public boolean take(String id, int amount) {
            if (id.equals(fail) || count(id) < amount) return false;
            items.put(id, count(id) - amount);
            return true;
        }
        public void refund(String id, int amount) { items.merge(id, amount, Integer::sum); }
    }
    @Test void refusesIncompleteCostWithoutConsumption() {
        var stock = new Stock(); stock.items.put("wood", 4); stock.items.put("stone", 1);
        assertFalse(ResearchCosts.pay(stock, Map.of("wood", 4, "stone", 2)));
        assertEquals(4, stock.count("wood")); assertEquals(1, stock.count("stone"));
    }
    @Test void refundsEarlierWithdrawalWhenLaterOneFails() {
        var stock = new Stock(); stock.items.put("wood", 4); stock.items.put("stone", 2); stock.fail = "stone";
        var cost = new LinkedHashMap<String, Integer>(); cost.put("wood", 4); cost.put("stone", 2);
        assertFalse(ResearchCosts.pay(stock, cost));
        assertEquals(4, stock.count("wood")); assertEquals(2, stock.count("stone"));
    }
    @Test void automaticResearchRequiresDoubleStockWithoutOverflow() {
        var stock = new Stock(); stock.items.put("wood", 7); stock.items.put("stone", 4);
        var cost = Map.of("wood", 4, "stone", 2);
        assertFalse(ResearchCosts.hasSurplus(stock, cost));
        stock.items.put("wood", 8);
        assertTrue(ResearchCosts.hasSurplus(stock, cost));
        assertTrue(ResearchCosts.pay(stock, cost));
        assertEquals(4, stock.count("wood")); assertEquals(2, stock.count("stone"));
        stock.items.put("wood", Integer.MAX_VALUE);
        assertFalse(ResearchCosts.hasSurplus(stock, Map.of("wood", Integer.MAX_VALUE)));
    }
    @Test void paysFullCostExactlyOnce() {
        var stock = new Stock(); stock.items.put("wood", 5); stock.items.put("stone", 2);
        assertTrue(ResearchCosts.pay(stock, Map.of("wood", 4, "stone", 2)));
        assertEquals(1, stock.count("wood")); assertEquals(0, stock.count("stone"));
    }
}
