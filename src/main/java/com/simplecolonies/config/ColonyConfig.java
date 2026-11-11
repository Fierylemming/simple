package com.simplecolonies.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ColonyConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    // --- Gameplay ---
    public static final ModConfigSpec.IntValue MAX_COLONIES_PER_DIMENSION;
    public static final ModConfigSpec.IntValue MAX_CITIZENS_PER_COLONY;
    public static final ModConfigSpec.IntValue TICKS_BETWEEN_COLONY_TICK;
    public static final ModConfigSpec.BooleanValue VILLAGERS_NEED_HOUSING;
    public static final ModConfigSpec.BooleanValue VILLAGERS_DIE_WITHOUT_FOOD;
    public static final ModConfigSpec.IntValue DAYS_WITHOUT_FOOD_BEFORE_STARVATION;
    public static final ModConfigSpec.DoubleValue FOOD_CONSUMPTION_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue ENABLE_SUPPLY_CHAIN;
    public static final ModConfigSpec.BooleanValue ENABLE_NATURAL_EXPANSION;
    public static final ModConfigSpec.IntValue EXPANSION_CHECK_DAYS;
    public static final ModConfigSpec.BooleanValue ENABLE_WARFARE;
    public static final ModConfigSpec.BooleanValue REPLACE_VANILLA_VILLAGES;
    public static final ModConfigSpec.DoubleValue REPUTATION_NEEDED_TO_LEAD;

    // --- Culture ---
    public static final ModConfigSpec.BooleanValue ENABLE_EGYPTIAN;
    public static final ModConfigSpec.BooleanValue ENABLE_ROMAN;
    public static final ModConfigSpec.BooleanValue ENABLE_GREEK;
    public static final ModConfigSpec.BooleanValue ENABLE_PERSIAN;
    public static final ModConfigSpec.BooleanValue ENABLE_MESOPOTAMIAN;

    // --- Performance ---
    public static final ModConfigSpec.IntValue MAX_BUILDER_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue MAX_PATHFIND_RANGE;


    static {
        BUILDER.comment("Simple Colonies - Ancient Villages Config",
                "All values are server-side unless noted. Restart or /reload to apply most.");

        BUILDER.push("colony");
        MAX_COLONIES_PER_DIMENSION = BUILDER.comment("Max colonies allowed per dimension").defineInRange("maxColoniesPerDimension", 32, 1, 500);
        MAX_CITIZENS_PER_COLONY = BUILDER.comment("Max villagers per colony before growth stops").defineInRange("maxCitizensPerColony", 60, 5, 300);
        TICKS_BETWEEN_COLONY_TICK = BUILDER.comment("Ticks between colony simulation ticks (20 = 1 sec)").defineInRange("ticksBetweenColonyTick", 200, 20, 24000);
        VILLAGERS_NEED_HOUSING = BUILDER.comment("If true, new villagers need a free bed/house or they leave/get sick").define("villagersNeedHousing", true);
        VILLAGERS_DIE_WITHOUT_FOOD = BUILDER.comment("If true, starvation can kill villagers after grace period").define("villagersDieWithoutFood", true);
        DAYS_WITHOUT_FOOD_BEFORE_STARVATION = BUILDER.comment("Minecraft days without food before health loss").defineInRange("daysWithoutFoodBeforeStarvation", 3, 0, 30);
        FOOD_CONSUMPTION_MULTIPLIER = BUILDER.comment("Multiplier for daily food consumption").defineInRange("foodConsumptionMultiplier", 1.0, 0.1, 5.0);
        ENABLE_SUPPLY_CHAIN = BUILDER.comment("Enable inter-worker supply chain: butcher->leatherworker->armorer etc.").define("enableSupplyChain", true);
        ENABLE_NATURAL_EXPANSION = BUILDER.comment("Colonies expand on their own if resources allow").define("enableNaturalExpansion", true);
        EXPANSION_CHECK_DAYS = BUILDER.comment("How many days between expansion attempts").defineInRange("expansionCheckDays", 5, 1, 100);
        ENABLE_WARFARE = BUILDER.comment("Enable soldier villagers, wars and alliances").define("enableWarfare", true);
        REPLACE_VANILLA_VILLAGES = BUILDER.comment("If true, vanilla villages are replaced by ancient colonies; if false, both spawn").define("replaceVanillaVillages", false);
        REPUTATION_NEEDED_TO_LEAD = BUILDER.comment("Reputation needed (0-100) to be elected leader").defineInRange("reputationNeededToLead", 75.0, 0.0, 150.0);
        BUILDER.pop();

        BUILDER.push("cultures");
        ENABLE_EGYPTIAN = BUILDER.comment("Spawn Egyptian colonies in hot sandy biomes (desert, badlands)").define("enableEgyptian", true);
        ENABLE_ROMAN = BUILDER.comment("Spawn Roman colonies in warm/plains biomes").define("enableRoman", true);
        ENABLE_GREEK = BUILDER.comment("Spawn Greek colonies in mediterranean/coastal biomes").define("enableGreek", true);
        ENABLE_PERSIAN = BUILDER.comment("Spawn Persian colonies in savanna/dry biomes").define("enablePersian", true);
        ENABLE_MESOPOTAMIAN = BUILDER.comment("Spawn Mesopotamian colonies in river/swamp/beach biomes").define("enableMesopotamian", true);
        BUILDER.pop();

        BUILDER.push("performance");
        MAX_BUILDER_BLOCKS_PER_TICK = BUILDER.comment("How many blocks builder can place per tick (anti-lag)").defineInRange("maxBuilderBlocksPerTick", 4, 1, 50);
        MAX_PATHFIND_RANGE = BUILDER.comment("Max pathfind distance for workers").defineInRange("maxPathfindRange", 64, 16, 256);
        BUILDER.pop();

        SPEC = BUILDER.build();

        // Client config (separate)
        ModConfigSpec.Builder clientBuilder = new ModConfigSpec.Builder();
        clientBuilder.comment("Simple Colonies Client Config");
        clientBuilder.push("visual");
        clientBuilder.comment("Show colony border particles when holding town hall item").define("showBorderParticles", true);
        clientBuilder.comment("Show villager job icons above heads").define("showJobIcons", true);
        clientBuilder.pop();
        CLIENT_SPEC = clientBuilder.build();
    }
}
