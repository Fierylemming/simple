package com.simplecolonies.villager.job;

import java.util.LinkedHashMap;
import java.util.Map;

/** Vanilla stone/iron tool recipes, with oak logs and planks as a source of sticks. */
final class ToolCrafting {
    interface Stock {
        int count(String id);
        boolean consume(String id, int amount);
        void add(String id, int amount);
    }
    private static final String STICK = "minecraft:stick", PLANKS = "minecraft:oak_planks", LOG = "minecraft:oak_log";

    static boolean make(Stock stock, String material, String result) {
        if (!(material.equals("minecraft:cobblestone") && (result.equals("minecraft:stone_pickaxe") || result.equals("minecraft:stone_axe")))
                && !(material.equals("minecraft:iron_ingot") && (result.equals("minecraft:iron_pickaxe") || result.equals("minecraft:iron_axe")))) return false;
        if (stock.count(material) < 3) return false;
        int existingSticks = Math.min(2, stock.count(STICK));
        boolean makeSticks = existingSticks < 2;
        int planks = makeSticks ? Math.min(2, stock.count(PLANKS)) : 0;
        int logs = makeSticks && planks < 2 ? 1 : 0;
        if (stock.count(LOG) < logs) return false;

        // Plan first, consume once, and refund already removed inputs on failure.
        var inputs = new LinkedHashMap<String, Integer>();
        inputs.put(material, 3);
        if (existingSticks > 0) inputs.put(STICK, existingSticks);
        if (planks > 0) inputs.put(PLANKS, planks);
        if (logs > 0) inputs.put(LOG, logs);
        var taken = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Integer> input : inputs.entrySet()) {
            if (!stock.consume(input.getKey(), input.getValue())) {
                taken.forEach(stock::add);
                return false;
            }
            taken.put(input.getKey(), input.getValue());
        }
        if (makeSticks) {
            int sparePlanks = planks + logs * 4 - 2;
            if (sparePlanks > 0) stock.add(PLANKS, sparePlanks);
            int spareSticks = 4 - (2 - existingSticks);
            if (spareSticks > 0) stock.add(STICK, spareSticks);
        }
        stock.add(result, 1);
        return true;
    }
    private ToolCrafting() {}
}
