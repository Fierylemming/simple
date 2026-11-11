package com.simplecolonies.culture;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Set;

/**
 * Five ancient cultures, each mapped to climate (temperature/heat) and biome tags.
 * This drives:
 *  - where villages spawn
 *  - what palette they build with
 *  - which building schematics are picked
 *  - villager clothing/name pools
 *
 * Heat mapping (mirrors vanilla biome temperature logic):
 *  Egyptian: hot & dry (desert, badlands) - temp >1.5
 *  Persian: warm dry savanna - temp 1.0-1.5 dry
 *  Mesopotamian: river valleys, swamp-adjacent - temp 0.7-1.0 humid, near water
 *  Roman: temperate plains/forest - temp 0.6-0.9
 *  Greek: mediterranean / coastal - temp 0.5-0.9 near ocean
 */
public enum CultureType {
    EGYPTIAN(
            "egyptian",
            "Egyptian",
            "Sun-baked mudbrick, sandstone pyramids and open courtyards",
            CultureClimate.HOT_ARID,
            Set.of("desert", "badlands", "mesa"),
            List.of(Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_SLAB, Blocks.TERRACOTTA, Blocks.YELLOW_TERRACOTTA),
            List.of(Blocks.ACACIA_PLANKS, Blocks.ACACIA_FENCE, Blocks.ACACIA_DOOR),
            "desert_village"
    ),
    PERSIAN(
            "persian",
            "Persian",
            "Glazed brick, grand archways, hanging gardens",
            CultureClimate.HOT_DRY,
            Set.of("savanna", "savanna_plateau", "wooded_badlands"),
            List.of(Blocks.TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.BRICKS, Blocks.CHISELED_STONE_BRICKS),
            List.of(Blocks.ACACIA_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.BAMBOO_MOSAIC),
            "savanna_village"
    ),
    MESOPOTAMIAN(
            "mesopotamian",
            "Mesopotamian",
            "Mud-brick ziggurats, canals, reed roofs",
            CultureClimate.WARM_HUMID,
            Set.of("river", "swamp", "mangrove_swamp", "beach"),
            List.of(Blocks.MUD_BRICKS, Blocks.MUD_BRICK_SLAB, Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_WALL, Blocks.PACKED_MUD),
            List.of(Blocks.MANGROVE_PLANKS, Blocks.BAMBOO_PLANKS, Blocks.OAK_PLANKS),
            "swamp_village"
    ),
    ROMAN(
            "roman",
            "Roman",
            "Marble columns, red tile roofs, forums and aqueducts",
            CultureClimate.TEMPERATE,
            Set.of("plains", "forest", "flower_forest", "sunflower_plains"),
            List.of(Blocks.QUARTZ_BLOCK, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_PILLAR, Blocks.QUARTZ_STAIRS, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.BRICKS, Blocks.RED_TERRACOTTA),
            List.of(Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS),
            "plains_village"
    ),
    GREEK(
            "greek",
            "Greek",
            "White marble temples, blue domes, coastal agoras",
            CultureClimate.MEDITERRANEAN,
            Set.of("beach", "stony_shore", "windswept_hills", "lukewarm_ocean"),
            List.of(Blocks.WHITE_CONCRETE, Blocks.WHITE_TERRACOTTA, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_PILLAR, Blocks.CALCITE, Blocks.WHITE_CONCRETE_POWDER),
            List.of(Blocks.OAK_PLANKS, Blocks.CHERRY_PLANKS, Blocks.BIRCH_PLANKS),
            "coastal_village"
    );

    public final String id;
    public final String displayName;
    public final String description;
    public final CultureClimate climate;
    public final Set<String> preferredBiomeIds;
    public final List<Block> primaryPalette;
    public final List<Block> woodPalette;
    public final String structureTemplatePrefix;

    CultureType(String id, String displayName, String description, CultureClimate climate, Set<String> preferredBiomeIds, List<Block> primaryPalette, List<Block> woodPalette, String structureTemplatePrefix) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.climate = climate;
        this.preferredBiomeIds = preferredBiomeIds;
        this.primaryPalette = primaryPalette;
        this.woodPalette = woodPalette;
        this.structureTemplatePrefix = structureTemplatePrefix;
    }

    public ResourceLocation getStructure(String buildingId) {
        // e.g. simplecolonies:egyptian/town_hall_1
        return ResourceLocation.fromNamespaceAndPath("simplecolonies", id + "/" + buildingId);
    }

    public TagKey<Biome> getBiomeTag() {
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("simplecolonies", "has_" + id + "_villages"));
    }

    public static CultureType byId(String id) {
        for (CultureType c : values()) if (c.id.equals(id)) return c;
        return ROMAN;
    }

    /**
     * Picks culture from biome temperature (heat) - used at worldgen when biome tags not set
     */
    public static CultureType fromBiomeTemperature(float temperature, Holder<Biome> biomeHolder) {
        // temperature from biome: desert ~2.0, savanna 1.2, plains 0.8, swamp 0.8 humid, ocean/coast ~0.5-0.8
        String biomeName = biomeHolder.unwrapKey().map(k -> k.location().getPath()).orElse("plains");
        // Hard overrides for distinctive biomes
        if (biomeName.contains("desert") || biomeName.contains("badlands") || biomeName.contains("mesa")) return EGYPTIAN;
        if (biomeName.contains("savanna")) return PERSIAN;
        if (biomeName.contains("swamp") || biomeName.contains("mangrove") || biomeName.contains("river")) return MESOPOTAMIAN;
        if (biomeName.contains("beach") || biomeName.contains("ocean") || biomeName.contains("shore")) return GREEK;

        if (temperature >= 1.6f) return EGYPTIAN;
        if (temperature >= 1.0f) return PERSIAN;
        if (temperature >= 0.7f && isHumid(biomeName)) return MESOPOTAMIAN;
        if (temperature >= 0.6f) return ROMAN;
        return GREEK;
    }

    private static boolean isHumid(String biomeName) {
        return biomeName.contains("swamp") || biomeName.contains("river") || biomeName.contains("jungle") || biomeName.contains("beach");
    }

    public enum CultureClimate {
        HOT_ARID,      // temp >1.5 dry
        HOT_DRY,       // 1.0-1.5
        WARM_HUMID,    // 0.7-1.0 + water
        TEMPERATE,     // 0.5-0.9
        MEDITERRANEAN  // coastal
    }
}
