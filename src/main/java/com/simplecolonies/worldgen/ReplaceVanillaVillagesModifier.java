package com.simplecolonies.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simplecolonies.config.ColonyConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.common.world.ModifiableStructureInfo;
import net.neoforged.neoforge.common.world.StructureModifier;

/**
 * Removes vanilla village structures when {@code replaceVanillaVillages} is on,
 * so the only settlements in the world are generated ancient colonies.
 *
 * <p>Structure modifiers are the supported NeoForge hook for changing a
 * structure's biome set. Emptying that set stops the structure from being
 * chosen anywhere without touching chunk generation, other mods' structures or
 * the vanilla village template pools. The JSON that targets the vanilla
 * villages lives at
 * {@code data/simplecolonies/neoforge/structure_modifier/replace_vanilla_villages.json}.</p>
 *
 * <p>The setting is read while structure data is loaded, so a change requires a
 * world (server) restart rather than only a config reload. Existing villages
 * that already generated are left untouched; the mod's own colonisation logic
 * still converts them into colonies when a player approaches.</p>
 */
public record ReplaceVanillaVillagesModifier(HolderSet<Structure> structures) implements StructureModifier {
    public static final MapCodec<ReplaceVanillaVillagesModifier> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RegistryCodecs.homogeneousList(Registries.STRUCTURE, Structure.DIRECT_CODEC)
                    .fieldOf("structures").forGetter(ReplaceVanillaVillagesModifier::structures)
    ).apply(instance, ReplaceVanillaVillagesModifier::new));

    @Override
    public void modify(Holder<Structure> structure, Phase phase, ModifiableStructureInfo.StructureInfo.Builder builder) {
        if (phase != Phase.REMOVE) return;
        if (!structures.contains(structure)) return;
        if (!ColonyConfig.REPLACE_VANILLA_VILLAGES.get()) return;
        // An empty biome set means the structure can never be selected.
        builder.getStructureSettings().setBiomes(HolderSet.<net.minecraft.world.level.biome.Biome>direct());
    }

    @Override
    public MapCodec<? extends StructureModifier> codec() {
        return CODEC;
    }
}
