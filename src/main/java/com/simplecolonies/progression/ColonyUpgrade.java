package com.simplecolonies.progression;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Progression tree — REWORKED for self-sufficiency.
 *
 * Design rule (your feedback): every upgrade must be craftable from resources gathered in previous tier,
 * so a village left alone can walk the whole tree miner→builder→iron without player hand-holding.
 *
 * Tier 0 (free): wood-tier only, villagers use wooden tools (1.0×)
 *  - TOOL_WOOD, PROF_FARMER, PROF_LUMBERJACK, UNLOCK_BUILDER, UNLOCK_MINER
 *
 * Tier 1 — Stone Age (needs only wood/cobble/coal/wheat/string/wool — all from Tier0 farmer/lumberjack):
 *  - TOOL_STONE [32 cobble,16 planks,4 coal] → 1.3×, lets miner actually mine iron/stone tier
 *  - UNLOCK_BUTCHER [16 wheat,8 fence] → provides leather/meat (from farmer wheat)
 *  - UNLOCK_BAKER [32 wheat,1 furnace] → doubles food
 *  - UNLOCK_FISHERMAN [8 string,8 planks] → coastal food
 *  - Then after TOOL_STONE:
 *     - EFFICIENCY_1 [4 wheat*8→emeralds? but village can trade: cost 16 wheat,8 planks,4 coal] → +15%
 *     - HOUSING_EFFICIENT [8 wool,16 planks] → +2 beds/house
 *
 * Tier 2 — Iron Age (needs raw_iron from Tier1 Miner, leather from Tier1 Butcher, planks/wheat):
 *  - UNLOCK_BLACKSMITH [2 furnace,8 coal,16 cobble] → smelts iron, makes tools (prereq: Miner, so Miner exists to supply it)
 *  - TOOL_IRON [16 raw_iron,8 coal,8 planks] → 1.6× — needs Miner (raw iron) + Blacksmith (to smelt) so prereq both
 *  - UNLOCK_COURIER [8 leather,16 planks,8 string] → needs Builder + Butcher (leather), bulk logistics
 *  - UNLOCK_LEATHERWORKER [12 leather] → leather armor, prereq Butcher
 *  - WAREHOUSE_CAPACITY [8 chest,32 planks] → courier +50%, prereq Courier
 *  - UNLOCK_SOLDIER [64 cobble,4 iron_ingot] → needs Builder + Iron tools (iron ingots via Blacksmith)
 *
 * Tier 3 — Diamond/Advanced (needs diamonds via Miner at iron tier, emeralds via trading, redstone via iron pick):
 *  - TOOL_DIAMOND [4 diamond,8 iron_ingot] → 2.0×
 *  - EFFICIENCY_2 [8 emerald,1 diamond] → +25%, prereq Eff1 + Iron
 *  - SCHEMATIC_CANNON [2 iron_block,8 redstone] → needs Iron tier to mine redstone+iron
 */
public enum ColonyUpgrade {
    // === Tool tiers ===
    TOOL_WOOD("Wooden Tools", "Villagers use wood (1.0× speed)", 0,
            Map.of(), Set.of(), UpgradeCategory.TOOLS,
            "Starting tech — all new villages have this"),

    TOOL_STONE("Stone Tools", "Stone tools, 1.3× speed, miners can mine iron & redstone", 1,
            Map.of("minecraft:cobblestone", 32, "minecraft:oak_planks", 16, "minecraft:coal", 4),
            Set.of(TOOL_WOOD), UpgradeCategory.TOOLS,
            "First upgrade — gather cobble from the starter miner"),

    // === Profession unlocks — Tier 0 (free) ===
    PROF_FARMER("Farmer", "Unlocks Farmer job", 0,
            Map.of(), Set.of(), UpgradeCategory.PROFESSION,
            "Default — feeds village"),
    PROF_LUMBERJACK("Woodcutter", "Unlocks Lumberjack (plants trees)", 0,
            Map.of(), Set.of(), UpgradeCategory.PROFESSION,
            "Default — supplies wood"),

    // Tier 1 — needs only Tier0 resources (wheat from farmer, wood, cobble, coal)
    UNLOCK_BUTCHER("Butcher", "Unlocks Butcher (meat + leather chain)", 1,
            Map.of("minecraft:wheat", 16, "minecraft:oak_fence", 8),
            Set.of(PROF_FARMER), UpgradeCategory.PROFESSION,
            "From farmer wheat, provides leather for Tier2"),
    UNLOCK_BAKER("Baker", "Unlocks Bakery (wheat→bread 2× efficient)", 1,
            Map.of("minecraft:wheat", 32, "minecraft:furnace", 1),
            Set.of(PROF_FARMER), UpgradeCategory.PROFESSION,
            "Doubles food value, uses only wheat"),
    UNLOCK_FISHERMAN("Fisherman", "Unlocks Fisherman (coastal food)", 1,
            Map.of("minecraft:string", 8, "minecraft:oak_planks", 8),
            Set.of(PROF_FARMER), UpgradeCategory.PROFESSION,
            "For Greek/Mesopotamian, needs string from spiders/looms"),

    UNLOCK_MINER("Miner", "Unlocks Miner (quarry)", 0,
            Map.of(), Set.of(), UpgradeCategory.PROFESSION,
            "Available from the start; needs a pickaxe and furnace"),
    UNLOCK_BUILDER("Builder", "Constructs house blueprints", 0,
            Map.of(), Set.of(), UpgradeCategory.PROFESSION,
            "Available from the start"),
    EFFICIENCY_1("Efficiency I", "+15% work speed (all jobs)", 1,
            Map.of("minecraft:wheat", 16, "minecraft:oak_planks", 16, "minecraft:coal", 4),
            Set.of(TOOL_STONE), UpgradeCategory.BUFF,
            "No emerald — so village can self-buy without trading"),
    HOUSING_EFFICIENT("Tight Housing", "+2 beds per House building", 1,
            Map.of("minecraft:wool", 8, "minecraft:oak_planks", 16),
            Set.of(TOOL_STONE), UpgradeCategory.BUFF,
            "Uses only wood/wool from lumberjack/farmer"),

    // Tier 2 — needs Tier1 resources (raw_iron from miner, leather from butcher, planks)
    UNLOCK_BLACKSMITH("Blacksmith", "Unlocks Blacksmith (smelts ores, makes iron tools)", 2,
            Map.of("minecraft:furnace", 2, "minecraft:coal", 8, "minecraft:cobblestone", 16),
            Set.of(UNLOCK_MINER), UpgradeCategory.PROFESSION,
            "Needs Miner to exist (not yet iron), provides smelting for Iron tier"),
    TOOL_IRON("Iron Tools", "Iron tools, 1.6× speed, unlocks Nether-ready & soldier gear", 2,
            Map.of("minecraft:raw_iron", 16, "minecraft:coal", 8, "minecraft:oak_planks", 8),
            Set.of(TOOL_STONE, UNLOCK_MINER, UNLOCK_BLACKSMITH), UpgradeCategory.TOOLS,
            "Needs Miner (raw iron) + Blacksmith (smelts) — both from previous tier"),
    TOOL_DIAMOND("Diamond Tools", "Diamond tools, 2.0× speed, fastest", 3,
            Map.of("minecraft:diamond", 4, "minecraft:iron_ingot", 8),
            Set.of(TOOL_IRON), UpgradeCategory.TOOLS,
            "Late game — Miner at iron tier can mine diamonds"),
    UNLOCK_COURIER("Courier", "Unlocks Courier (bulk chest logistics, 9 stacks/trip)", 2,
            Map.of("minecraft:leather", 8, "minecraft:oak_planks", 16, "minecraft:string", 4),
            Set.of(UNLOCK_BUILDER, UNLOCK_BUTCHER), UpgradeCategory.PROFESSION,
            "Needs Builder (to have warehouse) + Butcher (leather), both Tier1"),
    UNLOCK_LEATHERWORKER("Leatherworker", "Unlocks Leatherworker (leather armor)", 2,
            Map.of("minecraft:leather", 12),
            Set.of(UNLOCK_BUTCHER), UpgradeCategory.PROFESSION,
            "Needs Butcher leather, no iron"),
    WAREHOUSE_CAPACITY("Warehouse Network", "Courier carries +50% (hub more chests)", 2,
            Map.of("minecraft:chest", 8, "minecraft:oak_planks", 32),
            Set.of(UNLOCK_COURIER), UpgradeCategory.BUFF,
            "After courier, just wood"),
    UNLOCK_SOLDIER("Soldiers", "Unlocks Barracks & Soldier/Guard (war)", 2,
            Map.of("minecraft:cobblestone", 64, "minecraft:iron_ingot", 4),
            Set.of(UNLOCK_BUILDER, TOOL_IRON), UpgradeCategory.PROFESSION,
            "Needs Builder + Iron tools (iron ingots via Blacksmith)"),

    // Tier 3 — Diamond/advanced, needs diamonds/redstone (only mineable with iron pick)
    EFFICIENCY_2("Efficiency II", "+30% total speed", 3,
            Map.of("minecraft:emerald", 8, "minecraft:diamond", 1),
            Set.of(EFFICIENCY_1, TOOL_IRON), UpgradeCategory.BUFF,
            "Needs trading/mining at iron tier"),
    SCHEMATIC_CANNON("Schematic Cannon", "Builder can paste Create schematics for player", 3,
            Map.of("minecraft:iron_block", 2, "minecraft:redstone", 8, "minecraft:oak_planks", 8),
            Set.of(UNLOCK_BUILDER, TOOL_IRON), UpgradeCategory.BUFF,
            "Needs iron to mine redstone/iron");

    public final String displayName;
    public final String description;
    public final int tier;
    public final Map<String, Integer> cost;
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
        return Set.of(TOOL_WOOD, PROF_FARMER, PROF_LUMBERJACK, UNLOCK_BUILDER, UNLOCK_MINER);
    }
}
