package com.simplecolonies.progression;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Progression tree for Town Hall upgrades.
 * Spec: initially villagers can only use wooden tools, player must upgrade or village self-upgrades when enough resources.
 * Default professions: Farmer + Woodcutter. Everything else locked behind research.
 *
 * Tree:
 *  Tier 0 (free, unlocked): TOOL_WOOD, PROF_FARMER, PROF_LUMBERJACK
 *  Tier 1 (stone): TOOL_STONE, PROF_MINER, PROF_BUILDER
 *  Tier 2 (iron): TOOL_IRON, PROF_BLACKSMITH, PROF_BUTCHER, PROF_COURIER
 *  Tier 3 (advanced): TOOL_DIAMOND, PROF_LEATHERWORKER, PROF_BAKER, PROF_SOLDIER, etc.
 *
 * Also includes building efficiency upgrades like WAREHOUSE_CAPACITY, FASTER_CRAFTING etc.
 */
public enum ColonyUpgrade {
    // === Tool tiers — boost all villagers ===
    TOOL_WOOD("Wooden Tools", "Villagers use wood (1.0× speed)", 0,
            Map.of(), Set.of(), UpgradeCategory.TOOLS,
            "Starting tech — all new villages have this"),

    TOOL_STONE("Stone Tools", "Stone tools, 1.3× speed, miners can mine iron", 1,
            Map.of("minecraft:cobblestone", 32, "minecraft:oak_planks", 16, "minecraft:coal", 4),
            Set.of(TOOL_WOOD), UpgradeCategory.TOOLS,
            "First upgrade — village must gather cobble/planks"),

    TOOL_IRON("Iron Tools", "Iron tools, 1.6× speed, unlocks Nether-ready", 2,
            Map.of("minecraft:raw_iron", 16, "minecraft:coal", 8, "minecraft:oak_planks", 8),
            Set.of(TOOL_STONE, UNLOCK_MINER), UpgradeCategory.TOOLS,
            "Needs miner to have produced raw iron"),

    TOOL_DIAMOND("Diamond Tools", "Diamond tools, 2.0× speed, fastest", 3,
            Map.of("minecraft:diamond", 4, "minecraft:iron_ingot", 8),
            Set.of(TOOL_IRON), UpgradeCategory.TOOLS,
            "Late game — trade or mine diamonds"),

    // === Profession unlocks ===
    PROF_FARMER("Farmer", "Unlocks Farmer job", 0,
            Map.of(), Set.of(), UpgradeCategory.PROFESSION,
            "Default — feeds village"),
    PROF_LUMBERJACK("Woodcutter", "Unlocks Lumberjack (plants trees)", 0,
            Map.of(), Set.of(), UpgradeCategory.PROFESSION,
            "Default — supplies wood"),
    UNLOCK_MINER("Miner", "Unlocks Miner (quarry)", 1,
            Map.of("minecraft:cobblestone", 16, "minecraft:coal", 8),
            Set.of(TOOL_STONE), UpgradeCategory.PROFESSION,
            "Needs stone tools to quarry"),
    UNLOCK_BUILDER("Builder", "Unlocks Builder (constructs blueprints)", 1,
            Map.of("minecraft:oak_planks", 32, "minecraft:cobblestone", 16),
            Set.of(TOOL_STONE), UpgradeCategory.PROFESSION,
            "Essential for expansion"),
    UNLOCK_COURIER("Courier", "Unlocks Courier (bulk chest logistics)", 2,
            Map.of("minecraft:leather", 8, "minecraft:oak_planks", 16),
            Set.of(UNLOCK_BUILDER), UpgradeCategory.PROFESSION,
            "Moves 9 stacks/trip — use with Warehouse"),
    UNLOCK_BUTCHER("Butcher", "Unlocks Butcher (meat + leather chain)", 2,
            Map.of("minecraft:wheat", 16, "minecraft:oak_fence", 8),
            Set.of(PROF_FARMER), UpgradeCategory.PROFESSION,
            "Supplies leather to leatherworker"),
    UNLOCK_LEATHERWORKER("Leatherworker", "Unlocks Leatherworker (armor)", 2,
            Map.of("minecraft:leather", 12),
            Set.of(UNLOCK_BUTCHER), UpgradeCategory.PROFESSION,
            "Makes armor for soldiers"),
    UNLOCK_BLACKSMITH("Blacksmith", "Unlocks Blacksmith (smelts + tools)", 2,
            Map.of("minecraft:furnace", 2, "minecraft:coal", 8, "minecraft:cobblestone", 16),
            Set.of(UNLOCK_MINER, TOOL_IRON), UpgradeCategory.PROFESSION,
            "Makes iron tools for all"),
    UNLOCK_SOLDIER("Soldiers", "Unlocks Barracks & Soldier/Guard", 2,
            Map.of("minecraft:cobblestone", 64, "minecraft:iron_ingot", 4),
            Set.of(UNLOCK_BUILDER, TOOL_IRON), UpgradeCategory.PROFESSION,
            "War + defense"),
    UNLOCK_BAKER("Baker", "Unlocks Bakery (wheat→bread 2× efficient)", 1,
            Map.of("minecraft:wheat", 32, "minecraft:furnace", 1),
            Set.of(PROF_FARMER), UpgradeCategory.PROFESSION,
            "Doubles food value"),
    UNLOCK_FISHERMAN("Fisherman", "Unlocks Fisherman (coastal food)", 1,
            Map.of("minecraft:string", 8, "minecraft:oak_planks", 8),
            Set.of(PROF_FARMER), UpgradeCategory.PROFESSION,
            "For Greek/Mesopotamian"),

    // === Village-wide buffs ===
    EFFICIENCY_1("Efficiency I", "+15% work speed (all jobs)", 1,
            Map.of("minecraft:emerald", 4, "minecraft:oak_planks", 16),
            Set.of(TOOL_STONE), UpgradeCategory.BUFF,
            ""),
    EFFICIENCY_2("Efficiency II", "+25% work speed", 2,
            Map.of("minecraft:emerald", 8, "minecraft:diamond", 1),
            Set.of(EFFICIENCY_1, TOOL_IRON), UpgradeCategory.BUFF,
            ""),
    WAREHOUSE_CAPACITY("Warehouse Network", "Courier carries +50% (Warehouse hub more chests)", 1,
            Map.of("minecraft:chest", 8, "minecraft:oak_planks", 32),
            Set.of(UNLOCK_COURIER), UpgradeCategory.BUFF,
            "Build warehouse with many chests first"),
    HOUSING_EFFICIENT("Tight Housing", "+2 beds per House building", 1,
            Map.of("minecraft:wool", 8, "minecraft:oak_planks", 16),
            Set.of(UNLOCK_BUILDER), UpgradeCategory.BUFF,
            ""),
    // === Create integration ===
    SCHEMATIC_CANNON("Schematic Cannon", "Builder can paste Create schematics for player", 2,
            Map.of("minecraft:iron_block", 2, "minecraft:redstone", 8),
            Set.of(UNLOCK_BUILDER), UpgradeCategory.BUFF,
            "Give builder a Create schematic item + mats, it builds it");

    public final String displayName;
    public final String description;
    public final int tier;
    public final Map<String, Integer> cost; // itemId -> count
    public final Set<ColonyUpgrade> prerequisites;
    public final UpgradeCategory category;
    public final String flavor;

    ColonyUpgrade(String displayName, String description, int tier, Map<String, Integer> cost, Set<ColonyUpgrade> prerequisites, UpgradeCategory category, String flavor) {
        this.displayName = displayName;
        this.description = description;
        this.tier = tier;
        this.cost = cost;
        this.prerequisites = prerequisites;
        this.category = category;
        this.flavor = flavor;
    }

    public boolean isProfession() { return category == UpgradeCategory.PROFESSION; }
    public boolean isToolTier() { return category == UpgradeCategory.TOOLS; }

    /** Highest tool tier unlocked = current tool power. */
    public int toolSpeedPercent() {
        return switch (this) {
            case TOOL_WOOD -> 100;
            case TOOL_STONE -> 130;
            case TOOL_IRON -> 160;
            case TOOL_DIAMOND -> 200;
            default -> 100;
        };
    }

    public enum UpgradeCategory { TOOLS, PROFESSION, BUFF }

    public static List<ColonyUpgrade> tier(int tier) {
        return java.util.Arrays.stream(values()).filter(u -> u.tier == tier).toList();
    }

    public static Set<ColonyUpgrade> defaultsUnlocked() {
        return Set.of(TOOL_WOOD, PROF_FARMER, PROF_LUMBERJACK);
    }
}
