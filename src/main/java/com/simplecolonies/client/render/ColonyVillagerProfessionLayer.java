package com.simplecolonies.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.metadata.animation.VillagerMetaDataSection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Render vanilla biome, hat and badge layers, using vanilla outfits for colony jobs. */
final class ColonyVillagerProfessionLayer extends RenderLayer<Villager, VillagerModel<Villager>> {
    private final ResourceManager resources;
    private final Map<ResourceLocation, VillagerMetaDataSection.Hat> hats = new HashMap<>();
    private static final String[] LEVELS = {"stone", "iron", "gold", "emerald", "diamond"};

    ColonyVillagerProfessionLayer(RenderLayerParent<Villager, VillagerModel<Villager>> parent, ResourceManager resources) {
        super(parent);
        this.resources = resources;
    }
    private static ResourceLocation texture(String part, ResourceLocation id) {
        return id.withPath(path -> "textures/entity/villager/" + part + "/" + path + ".png");
    }
    private VillagerMetaDataSection.Hat hat(ResourceLocation texture) {
        if (texture == null) return VillagerMetaDataSection.Hat.NONE;
        return hats.computeIfAbsent(texture, id -> resources.getResource(id).flatMap(resource -> {
            try { return resource.metadata().getSection(VillagerMetaDataSection.SERIALIZER).map(VillagerMetaDataSection::getHat); }
            catch (IOException ignored) { return java.util.Optional.empty(); }
        }).orElse(VillagerMetaDataSection.Hat.NONE));
    }
    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, Villager villager,
                       float limbSwing, float limbAmount, float partialTick, float age, float yaw, float pitch) {
        if (villager.isInvisible()) return;
        var data = villager.getVillagerData();
        var profession = data.getProfession();
        var professionId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        var typeTexture = texture("type", BuiltInRegistries.VILLAGER_TYPE.getKey(data.getType()));
        ResourceLocation professionTexture = null;
        if (professionId != null && profession != VillagerProfession.NONE && profession != VillagerProfession.NITWIT) {
            if (professionId.getNamespace().equals("simplecolonies")) {
                String name = ProfessionAppearance.vanillaOverlay(professionId.getPath());
                if (name != null) professionTexture = texture("profession", ResourceLocation.withDefaultNamespace(name));
            } else professionTexture = texture("profession", professionId);
        } else if (profession == VillagerProfession.NITWIT) {
            professionTexture = texture("profession", professionId);
        }
        var typeHat = hat(typeTexture);
        var professionHat = hat(professionTexture);
        var model = getParentModel();
        model.hatVisible(professionHat == VillagerMetaDataSection.Hat.NONE
                || professionHat == VillagerMetaDataSection.Hat.PARTIAL && typeHat != VillagerMetaDataSection.Hat.FULL);
        renderColoredCutoutModel(model, typeTexture, pose, buffer, light, villager, -1);
        model.hatVisible(true);
        if (!villager.isBaby() && professionTexture != null) {
            renderColoredCutoutModel(model, professionTexture, pose, buffer, light, villager, -1);
            if (profession != VillagerProfession.NITWIT) {
                var level = ResourceLocation.withDefaultNamespace(LEVELS[Mth.clamp(data.getLevel(), 1, 5) - 1]);
                renderColoredCutoutModel(model, texture("profession_level", level), pose, buffer, light, villager, -1);
            }
        }
    }
}
