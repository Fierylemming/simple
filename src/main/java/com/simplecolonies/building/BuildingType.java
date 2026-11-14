package com.simplecolonies.building;

import java.util.Map;

/**
 * Building definitions — REWORKED for self-sufficient progression.
 * Each building's baseCost now only uses mats gatherable in previous tier,
 * so a village can walk the whole chain without player.
 *
 * Tier 0-1 (wood/stone): Town Hall, House, Farm, Lumber Hut, Mine, Warehouse, Butcher, Walls, Harbor
 * Tier 2 (iron): Blacksmith (now no anvil), Barracks, Castle, Leather Shop (needs leather from Butcher)
 * Tier 3: Market/Library/Temple (need trading/quartz, late)
 */
public enum BuildingType {
    TOWN_HALL("Town Hall", "Center of colony, manages citizens and diplomacy", 0, null, Map.of("minecraft:oak_log", 32, "minecraft:cobblestone", 64)),
    HOUSE("House", "Shared shelter for 4 villagers; beds are not included", 4, null, Map.of("minecraft:oak_planks", 40, "minecraft:cobblestone", 20)),
    FARM("Farm", "Workplace for Farmer (needs only wood/wheat)", 0, com.simplecolonies.villager.job.JobType.FARMER, Map.of("minecraft:farmland", 20, "minecraft:wheat_seeds", 16, "minecraft:oak_fence", 12)),
    BUILDER_HUT("Builder Hut", "Shelter around the builder's workstation", 0, com.simplecolonies.villager.job.JobType.BUILDER, Map.of("minecraft:oak_planks", 30, "minecraft:cobblestone", 16)),
    LUMBERJACK_HUT("Lumberjack Hut", "Workplace for Woodcutter (now stone axe, not iron, so self-sufficient)", 0, com.simplecolonies.villager.job.JobType.LUMBERJACK, Map.of("minecraft:oak_log", 24, "minecraft:stone_axe", 1)),
    MINE("Mine", "Entrance to quarry for Miner (stone only)", 0, com.simplecolonies.villager.job.JobType.MINER, Map.of("minecraft:cobblestone", 48, "minecraft:ladder", 16, "minecraft:torch", 12)),
    WAREHOUSE("Warehouse", "Shared stockpile — many chests, courier hub", 0, null, Map.of("minecraft:chest", 8, "minecraft:oak_planks", 32)),
    BAKERY("Bakery", "Workplace for a baker who crafts bread from real wheat", 0, com.simplecolonies.villager.job.JobType.BAKER, Map.of("minecraft:furnace", 2, "minecraft:wheat", 16)),
    // Blacksmith now costs furnace+cobble+planks — no anvil, so Tier1 village can build it then start smelting iron for Tier2
    BLACKSMITH("Blacksmith", "Workplace for Blacksmith (smelts ores, makes iron tools)", 0, com.simplecolonies.villager.job.JobType.BLACKSMITH, Map.of("minecraft:furnace", 2, "minecraft:cobblestone", 32, "minecraft:oak_planks", 16)),
    BARRACKS("Barracks", "Defender worksite shell; does not supply beds or training", 6, com.simplecolonies.villager.job.JobType.SOLDIER, Map.of("minecraft:cobblestone", 64, "minecraft:iron_ingot", 8)),
    MARKET("Market", "Civic meeting hall; trading is not yet implemented", 0, null, Map.of("minecraft:gold_ingot", 8, "minecraft:oak_planks", 24)),
    LIBRARY("Library", "Research is faster with completed physical library wings", 0, null, Map.of("minecraft:bookshelf", 12, "minecraft:oak_planks", 16)),
    TEMPLE("Temple", "Culture-specific, boosts reputation (late, needs quartz/gold)", 0, null, Map.of("minecraft:quartz_block", 16, "minecraft:sandstone", 32)),
    CASTLE("Castle", "Final upgrade for large city defense (iron+ stone)", 12, com.simplecolonies.villager.job.JobType.GUARD, Map.of("minecraft:stone_bricks", 128, "minecraft:iron_ingot", 16)),
    WALLS("Walls", "Defensive perimeter (stone, Tier1)", 0, com.simplecolonies.villager.job.JobType.GUARD, Map.of("minecraft:cobblestone", 96, "minecraft:stone_bricks", 32)),
    HARBOR("Harbor", "Shore gathering worksite; trading is not implemented", 0, com.simplecolonies.villager.job.JobType.FISHERMAN, Map.of("minecraft:oak_planks", 48, "minecraft:oak_boat", 2)),
    BUTCHER_HUT("Butcher Hut", "Workplace for Butcher (wheat+furnace, Tier1)", 0, com.simplecolonies.villager.job.JobType.BUTCHER, Map.of("minecraft:cobblestone", 24, "minecraft:furnace", 1)),
    LEATHER_SHOP("Leather Shop", "Workplace for Leatherworker (leather+planks, Tier2 from Butcher)", 0, com.simplecolonies.villager.job.JobType.LEATHERWORKER, Map.of("minecraft:leather", 8, "minecraft:oak_planks", 16)),
    PASTURE("Pasture", "Player-designated livestock work area for shepherds and butchers", 0, com.simplecolonies.villager.job.JobType.SHEPHERD, Map.of()),
    SCHEMATIC("Schematic", "Imported player-supplied construction plan", 0, null, Map.of());

    public final String displayName;
    public final String description;
    public final int housingCapacity;
    public final com.simplecolonies.villager.job.JobType job;
    public final Map<String, Integer> baseCost;

    BuildingType(String displayName, String description, int housingCapacity, com.simplecolonies.villager.job.JobType job, Map<String, Integer> baseCost) {
        this.displayName = displayName;
        this.description = description;
        this.housingCapacity = housingCapacity;
        this.job = job;
        this.baseCost = baseCost;
    }

    public static BuildingType byId(String id) {
        for (BuildingType b : values()) if (b.name().equalsIgnoreCase(id) || b.displayName.equalsIgnoreCase(id)) return b;
        return HOUSE;
    }
}
