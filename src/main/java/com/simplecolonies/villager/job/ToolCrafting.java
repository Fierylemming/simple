package com.simplecolonies.villager.job;

import java.util.LinkedHashMap;
import java.util.Map;

/** Vanilla stone/iron tool recipes, using real wooden planks or logs for sticks. */
final class ToolCrafting {
    interface Stock {
        int count(String id);
        boolean consume(String id, int amount);
        void add(String id, int amount);
    }
    private static final String STICK = "minecraft:stick";
    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry"};

    static boolean make(Stock stock, String material, String result) {
        boolean stone = material.equals("minecraft:cobblestone") && (result.equals("minecraft:stone_sword")
                || result.equals("minecraft:stone_pickaxe") || result.equals("minecraft:stone_axe"));
        boolean iron = material.equals("minecraft:iron_ingot") && (result.equals("minecraft:iron_sword")
                || result.equals("minecraft:iron_pickaxe") || result.equals("minecraft:iron_axe"));
        if (!(stone || iron)) return false;
        boolean sword = result.endsWith("_sword");
        int materials = sword ? 2 : 3;
        int neededSticks = sword ? 1 : 2;
        if (stock.count(material) < materials) return false;
        int existingSticks = Math.min(neededSticks, stock.count(STICK));
        boolean makeSticks = existingSticks < neededSticks;
        // A vanilla stick recipe accepts two planks from any wood species.
        // Prefer existing planks, then turn one real log into its matching
        // four planks; do not convert non-oak timber into oak.
        var inputs = new LinkedHashMap<String, Integer>();
        inputs.put(material, materials);
        if (existingSticks > 0) inputs.put(STICK, existingSticks);
        int missingPlanks = makeSticks ? 2 : 0;
        for (String wood : WOODS) {
            String id = "minecraft:" + wood + "_planks";
            int take = Math.min(missingPlanks, stock.count(id));
            if (take > 0) { inputs.put(id, take); missingPlanks -= take; }
        }
        String craftedPlanks = null;
        if (missingPlanks > 0) {
            for (String wood : WOODS) {
                String id = "minecraft:" + wood + "_log";
                if (stock.count(id) > 0) {
                    inputs.put(id, 1);
                    craftedPlanks = "minecraft:" + wood + "_planks";
                    break;
                }
            }
            if (craftedPlanks == null) return false;
        }

        // Plan first, consume once, and refund already removed inputs on failure.
        var taken = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Integer> input : inputs.entrySet()) {
            if (!stock.consume(input.getKey(), input.getValue())) {
                taken.forEach(stock::add);
                return false;
            }
            taken.put(input.getKey(), input.getValue());
        }
        if (makeSticks) {
            if (craftedPlanks != null) stock.add(craftedPlanks, 4 - missingPlanks);
            int spareSticks = 4 - (neededSticks - existingSticks);
            if (spareSticks > 0) stock.add(STICK, spareSticks);
        }
        stock.add(result, 1);
        return true;
    }
    private ToolCrafting() {}
}
