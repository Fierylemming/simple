package com.simplecolonies.building;

/** A deliberately small, deterministic subset of vanilla crafting for level-one builds. */
public final class ConstructionCrafting {
    public interface Stock {
        int count(String id);
        boolean consume(String id, int amount);
        void add(String id, int amount);
    }
    private record Recipe(int planks, int output) {}
    private static final String LOG = "minecraft:oak_log", PLANKS = "minecraft:oak_planks";
    private static Recipe recipe(String id) {
        return switch (id) {
            case "minecraft:oak_stairs" -> new Recipe(6, 4);
            case "minecraft:oak_slab" -> new Recipe(3, 6);
            case "minecraft:oak_door" -> new Recipe(6, 3);
            default -> null;
        };
    }

    /** Consumes one output, crafting whole recipes and retaining surplus. */
    public static boolean take(Stock stock, String id) {
        if (id == null || id.isEmpty()) return true; // second half of a multiblock
        if (stock.count(id) > 0) return stock.consume(id, 1);
        if (id.equals(PLANKS)) {
            if (stock.count(LOG) < 1 || !stock.consume(LOG, 1)) return false;
            stock.add(PLANKS, 4);
            return stock.consume(PLANKS, 1);
        }
        if (id.equals("minecraft:furnace")) {
            if (stock.count("minecraft:cobblestone") < 8 || !stock.consume("minecraft:cobblestone", 8)) return false;
            stock.add(id, 1);
            return stock.consume(id, 1);
        }
        if (id.equals("minecraft:torch")) {
            int missingPlanks = stock.count("minecraft:stick") > 0 ? 0 : Math.max(0, 2 - stock.count(PLANKS));
            int neededLogs = (missingPlanks + 3) / 4;
            if (stock.count("minecraft:coal") < 1 || stock.count(LOG) < neededLogs) return false;
            if (neededLogs > 0) {
                if (!stock.consume(LOG, neededLogs)) return false;
                stock.add(PLANKS, 4 * neededLogs);
            }
            if (stock.count("minecraft:stick") == 0) {
                if (!stock.consume(PLANKS, 2)) return false;
                stock.add("minecraft:stick", 4);
            }
            if (!stock.consume("minecraft:stick", 1) || !stock.consume("minecraft:coal", 1)) return false;
            stock.add(id, 4);
            return stock.consume(id, 1);
        }
        Recipe recipe = recipe(id);
        if (recipe == null) return false;
        int missing = Math.max(0, recipe.planks - stock.count(PLANKS));
        int logs = (missing + 3) / 4;
        if (stock.count(LOG) < logs) return false;
        if (logs > 0) {
            if (!stock.consume(LOG, logs)) return false;
            stock.add(PLANKS, logs * 4);
        }
        if (!stock.consume(PLANKS, recipe.planks)) return false;
        stock.add(id, recipe.output);
        return stock.consume(id, 1);
    }
    /** Check a whole plan against a copy so expansion never spends stock just to test it. */
    public static boolean canBuild(java.util.Map<String, Integer> stock, java.util.List<Building.BlockPlacement> placements) {
        var copy = new java.util.HashMap<>(stock);
        Stock simulation = new Stock() {
            public int count(String id) { return copy.getOrDefault(id, 0); }
            public boolean consume(String id, int amount) {
                if (count(id) < amount) return false;
                copy.put(id, count(id) - amount);
                return true;
            }
            public void add(String id, int amount) { copy.merge(id, amount, Integer::sum); }
        };
        for (var placement : placements) if (!take(simulation, placement.itemId)) return false;
        return true;
    }
    private ConstructionCrafting() {}
}
