package com.simplecolonies.villager.job;

/** Explicit drops: never turn arbitrary blocks (including containers) into cobblestone. */
final class MinerRules {
    static String dropFor(String id) {
        return switch (id) {
            case "minecraft:stone" -> "minecraft:cobblestone";
            case "minecraft:deepslate" -> "minecraft:cobbled_deepslate";
            case "minecraft:coal_ore", "minecraft:deepslate_coal_ore" -> "minecraft:coal";
            case "minecraft:iron_ore", "minecraft:deepslate_iron_ore" -> "minecraft:raw_iron";
            case "minecraft:copper_ore", "minecraft:deepslate_copper_ore" -> "minecraft:raw_copper";
            case "minecraft:gold_ore", "minecraft:deepslate_gold_ore" -> "minecraft:raw_gold";
            default -> null;
        };
    }
    static boolean allowedForTool(String id, int toolTier) {
        if (dropFor(id) == null || toolTier < 1) return false;
        return !id.equals("minecraft:gold_ore") && !id.equals("minecraft:deepslate_gold_ore") || toolTier >= 2;
    }
    static boolean farFromBuilding(int x, int z, int bx, int bz, int margin) {
        return Math.abs((long)x - bx) > margin || Math.abs((long)z - bz) > margin;
    }
    private MinerRules() {}
}
