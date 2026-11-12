package com.simplecolonies.colony;

import java.util.List;

/** Foods counted by the colony; wheat itself must first be crafted into bread. */
final class FoodRules {
    static final List<String> FOOD_IDS = List.of(
            "minecraft:bread", "minecraft:cooked_beef", "minecraft:cooked_porkchop",
            "minecraft:cooked_mutton", "minecraft:cooked_chicken", "minecraft:cooked_rabbit",
            "minecraft:cooked_cod", "minecraft:cooked_salmon", "minecraft:baked_potato",
            "minecraft:beef", "minecraft:porkchop", "minecraft:mutton", "minecraft:chicken",
            "minecraft:rabbit", "minecraft:cod", "minecraft:salmon", "minecraft:carrot",
            "minecraft:potato", "minecraft:beetroot", "minecraft:apple");
    static boolean isFood(String id) { return FOOD_IDS.contains(id); }
    private FoodRules() {}
}
