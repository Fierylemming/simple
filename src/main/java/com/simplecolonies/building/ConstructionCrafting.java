package com.simplecolonies.building;

/** A deliberately small, deterministic subset of vanilla crafting for level-one builds. */
public final class ConstructionCrafting {
    public interface Stock {
        int count(String id);
        boolean consume(String id, int amount);
        void add(String id, int amount);
    }
    private record Recipe(int planks, int output) {}
    private static final String STICK = "minecraft:stick";
    private static String woodId(HouseTimber wood, String suffix) { return "minecraft:" + wood.id + "_" + suffix; }
    private static Recipe recipe(HouseTimber wood, String id) {
        if (id.equals(woodId(wood, "stairs"))) return new Recipe(6, 4);
        if (id.equals(woodId(wood, "slab"))) return new Recipe(3, 6);
        if (id.equals(woodId(wood, "door"))) return new Recipe(6, 3);
        return null;
    }

    /** Consumes one output, crafting whole recipes and retaining surplus. */
    public static boolean take(Stock stock, String id) {
        if (id == null || id.isEmpty()) return true;
        if (stock.count(id) > 0) return stock.consume(id, 1);
        if (id.equals("minecraft:furnace")) {
            if (stock.count("minecraft:cobblestone") < 8 || !stock.consume("minecraft:cobblestone", 8)) return false;
            stock.add(id, 1);
            return stock.consume(id, 1);
        }
        if (id.equals("minecraft:torch")) {
            if (stock.count("minecraft:coal") < 1) return false;
            if (stock.count(STICK) == 0) {
                HouseTimber source = null;
                for (var wood : HouseTimber.all()) {
                    if (stock.count(woodId(wood, "planks")) >= 2 || stock.count(woodId(wood, "log")) > 0) {
                        source = wood; break;
                    }
                }
                if (source == null) return false;
                String planks = woodId(source, "planks");
                if (stock.count(planks) < 2) {
                    if (!stock.consume(woodId(source, "log"), 1)) return false;
                    stock.add(planks, 4);
                }
                if (!stock.consume(planks, 2)) return false;
                stock.add(STICK, 4);
            }
            if (!stock.consume(STICK, 1) || !stock.consume("minecraft:coal", 1)) return false;
            stock.add(id, 4);
            return stock.consume(id, 1);
        }
        for (var wood : HouseTimber.all()) {
            String planks = woodId(wood, "planks"), log = woodId(wood, "log");
            if (id.equals(planks)) {
                if (stock.count(log) < 1 || !stock.consume(log, 1)) return false;
                stock.add(planks, 4);
                return stock.consume(planks, 1);
            }
            Recipe recipe = recipe(wood, id);
            if (recipe == null) continue;
            int missing = Math.max(0, recipe.planks - stock.count(planks));
            int logs = (missing + 3) / 4;
            if (stock.count(log) < logs) return false;
            if (logs > 0) {
                if (!stock.consume(log, logs)) return false;
                stock.add(planks, logs * 4);
            }
            if (!stock.consume(planks, recipe.planks)) return false;
            stock.add(id, recipe.output);
            return stock.consume(id, 1);
        }
        return false;
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
