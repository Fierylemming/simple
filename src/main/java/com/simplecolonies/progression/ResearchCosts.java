package com.simplecolonies.progression;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pay a complete research cost or refund earlier inputs if a later withdrawal fails. */
final class ResearchCosts {
    interface Counts { int count(String id); }
    interface Stock extends Counts {
        boolean take(String id, int amount);
        void refund(String id, int amount);
    }
    static boolean affordable(Counts stock, Map<String, Integer> cost) {
        return cost.entrySet().stream().allMatch(e -> e.getValue() >= 0 && stock.count(e.getKey()) >= e.getValue());
    }
    /** Auto research leaves at least one complete cost in stock for other needs. */
    static boolean hasSurplus(Counts stock, Map<String, Integer> cost) {
        return cost.entrySet().stream().allMatch(e -> e.getValue() >= 0
                && (long) stock.count(e.getKey()) >= 2L * e.getValue());
    }
    static boolean pay(Stock stock, Map<String, Integer> cost) {
        if (!affordable(stock, cost)) return false;
        var taken = new LinkedHashMap<String, Integer>();
        for (var e : cost.entrySet()) {
            if (e.getValue() == 0) continue;
            if (!stock.take(e.getKey(), e.getValue())) {
                taken.forEach(stock::refund);
                return false;
            }
            taken.put(e.getKey(), e.getValue());
        }
        return true;
    }
    private ResearchCosts() {}
}
