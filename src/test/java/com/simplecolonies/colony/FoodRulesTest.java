package com.simplecolonies.colony;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoodRulesTest {
    @Test void edibleItemsAreDistinctFromIngredients() {
        assertTrue(FoodRules.isFood("minecraft:bread"));
        assertTrue(FoodRules.isFood("minecraft:beetroot"));
        assertTrue(FoodRules.isFood("minecraft:cooked_cod"));
        assertFalse(FoodRules.isFood("minecraft:wheat"));
        assertFalse(FoodRules.isFood("minecraft:wheat_seeds"));
    }
}
