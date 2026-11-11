package com.simplecolonies.client;

import com.simplecolonies.SimpleColonies;
import com.simplecolonies.client.gui.BuildingMarkerScreen;
import com.simplecolonies.client.gui.TownHallScreen;
import com.simplecolonies.client.gui.VillagerInteractionScreen;
import com.simplecolonies.menu.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = SimpleColonies.MOD_ID, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        SimpleColonies.LOGGER.info("[SimpleColonies] Client setup - TownHall + Villager + BuildingMarker GUIs + Warehouse chest courier system");
    }

    @SubscribeEvent
    public static void registerEntityRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get(),
                net.minecraft.client.renderer.entity.VillagerRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.TOWN_HALL.get(), TownHallScreen::new);
        event.register(ModMenuTypes.VILLAGER.get(), VillagerInteractionScreen::new);
        event.register(ModMenuTypes.BUILDING_MARKER.get(), BuildingMarkerScreen::new);
        SimpleColonies.LOGGER.info("[SimpleColonies] Registered 3 screens (TownHall, Villager, BuildingMarker)");
    }
}
