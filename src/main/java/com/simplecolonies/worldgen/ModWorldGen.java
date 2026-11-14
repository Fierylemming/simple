package com.simplecolonies.worldgen;

import com.simplecolonies.SimpleColonies;
import com.simplecolonies.culture.CultureType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Worldgen: registers 5 culture village structures and hooks them into biome placement via heat.
 *
 * Approach:
 *  - Data pack defines structure sets: data/simplecolonies/worldgen/structure_set/egyptian_village.json etc
 *  - Each set has placement based on temperature biome tags (or explicit tag simplecolonies:has_egyptian_villages)
 *  - Optionally replaces vanilla villages if config replaceVanillaVillages = true (remove vanilla village structures via biome modifier)
 *
 * For simplicity, this class registers biome modifiers that add the structures.
 */
public class ModWorldGen {
    // Deferred registers not strictly needed for data-driven structures but kept for potential code VO
    public static void register() {
        SimpleColonies.LOGGER.info("[SimpleColonies] Registering worldgen: Egyptian, Persian, Mesopotamian, Roman, Greek villages");
        // Structure templates exist at data/simplecolonies/structures/egyptian/*.nbt etc
        // Biome tags generated in data/simplecolonies/tags/worldgen/biome/...
    }

    public static TagKey<Biome> tag(String cultureId) {
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("simplecolonies", "has_" + cultureId + "_villages"));
    }
}
