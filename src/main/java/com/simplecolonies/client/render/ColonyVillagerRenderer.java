package com.simplecolonies.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;

/** Keep the vanilla model and layers, replacing only its profession/biome texture layer. */
public class ColonyVillagerRenderer extends VillagerRenderer {
    public ColonyVillagerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.layers.removeIf(layer -> layer instanceof VillagerProfessionLayer<?, ?>);
        this.addLayer(new ColonyVillagerProfessionLayer(this, context.getResourceManager()));
    }
}
