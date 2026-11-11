package com.simplecolonies.building;

import java.util.Map;

/**
 * Building definitions. Each type has:
 * - levels (1-3) for upgrades
 * - housing capacity
 * - workplace for job type
 * - material cost preview
 */
public enum BuildingType {
    TOWN_HALL("Town Hall", "Center of colony, manages citizens and diplomacy", 0, null, Map.of("minecraft:oak_log", 32, "minecraft:cobblestone", 64)),
    HOUSE("House", "Housing for 4 villagers + beds", 4, null, Map.of("minecraft:oak_planks", 40, "minecraft:cobblestone", 20, "minecraft:wool", 8)),
    FARM("Farm", "Workplace for Farmer", 0, com.simplecolonies.villager.job.JobType.FARMER, Map.of("minecraft:farmland", 20, "minecraft:wheat_seeds", 16, "minecraft:oak_fence", 12)),
    LUMBERJACK_HUT("Lumberjack Hut", "Workplace for Woodcutter", 0, com.simplecolonies.villager.job.JobType.LUMBERJACK, Map.of("minecraft:oak_log", 24, "minecraft:iron_axe", 1)),
    MINE("Mine", "Entrance to quarry for Miner", 0, com.simplecolonies.villager.job.JobType.MINER, Map.of("minecraft:cobblestone", 48, "minecraft:ladder", 16, "minecraft:torch", 12)),
    WAREHOUSE("Warehouse", "Shared stockpile - expands storage", 0, null, Map.of("minecraft:chest", 8, "minecraft:oak_planks", 32)),
    BAKERY("Bakery", "Turns wheat into bread efficiently", 0, com.simplecolonies.villager.job.JobType.BAKER, Map.of("minecraft:furnace", 2, "minecraft:wheat", 16)),
    BLACKSMITH("Blacksmith", "Workplace for Blacksmith", 0, com.simplecolonies.villager.job.JobType.BLACKSMITH, Map.of("minecraft:furnace", 2, "minecraft:anvil", 1, "minecraft:cobblestone", 32)),
    BARRACKS("Barracks", "Houses & trains soldiers", 6, com.simplecolonies.villager.job.JobType.SOLDIER, Map.of("minecraft:cobblestone", 64, "minecraft:iron_block", 4)),
    MARKET("Market", "Villager trading hall", 0, null, Map.of("minecraft:emerald", 8, "minecraft:oak_planks", 24)),
    LIBRARY("Library", "Unlocks upgrades", 0, null, Map.of("minecraft:bookshelf", 12, "minecraft:oak_planks", 16)),
    TEMPLE("Temple", "Culture-specific, boosts reputation", 0, null, Map.of("minecraft:quartz_block", 32, "minecraft:gold_block", 2)),
    CASTLE("Castle", "Final upgrade for large city defense", 12, com.simplecolonies.villager.job.JobType.GUARD, Map.of("minecraft:stone_bricks", 128, "minecraft:iron_block", 8)),
    WALLS("Walls", "Defensive perimeter", 0, com.simplecolonies.villager.job.JobType.GUARD, Map.of("minecraft:cobblestone", 96, "minecraft:stone_bricks", 32)),
    HARBOR("Harbor", "Fishing & trade for coastal cultures", 0, com.simplecolonies.villager.job.JobType.FISHERMAN, Map.of("minecraft:oak_planks", 48, "minecraft:boat", 4)),
    BUTCHER_HUT("Butcher Hut", "Workplace for Butcher", 0, com.simplecolonies.villager.job.JobType.BUTCHER, Map.of("minecraft:cobblestone", 24, "minecraft:furnace", 1)),
    LEATHER_SHOP("Leather Shop", "Workplace for Leatherworker", 0, com.simplecolonies.villager.job.JobType.LEATHERWORKER, Map.of("minecraft:leather", 8, "minecraft:oak_planks", 16));

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
