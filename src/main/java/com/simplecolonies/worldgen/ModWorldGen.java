package com.simplecolonies.worldgen;

import com.mojang.serialization.MapCodec;
import com.simplecolonies.SimpleColonies;
import com.simplecolonies.culture.CultureType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.world.StructureModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Worldgen: five culture village structures are data-driven, and a structure
 * modifier can remove the vanilla villages so only ancient colonies generate.
 *
 * <p>Each culture has its own structure json under
 * {@code data/simplecolonies/worldgen/structure/<culture>/village_center.json} and
 * a biome tag that says where it may appear. When a player approaches a generated
 * village, {@code ColonyEventHandler} founds a colony there and the mod's own
 * builders raise its halls and houses.</p>
 */
public class ModWorldGen {
    /** Serializer registry entry for {@link ReplaceVanillaVillagesModifier}. */
    public static final DeferredRegister<MapCodec<? extends StructureModifier>> STRUCTURE_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.STRUCTURE_MODIFIER_SERIALIZERS, SimpleColonies.MOD_ID);

    static {
        STRUCTURE_MODIFIER_SERIALIZERS.register("replace_vanilla_villages",
                () -> ReplaceVanillaVillagesModifier.CODEC);
    }

    public static void register() {
        SimpleColonies.LOGGER.info("[SimpleColonies] Registering worldgen: Egyptian, Persian, Mesopotamian, Roman, Greek villages");
    }

    public static TagKey<Biome> tag(String cultureId) {
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("simplecolonies", "has_" + cultureId + "_villages"));
    }

    private ModWorldGen() {}
}
