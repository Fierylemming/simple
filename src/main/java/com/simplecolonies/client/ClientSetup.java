package com.simplecolonies.client;

import com.simplecolonies.SimpleColonies;
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
        SimpleColonies.LOGGER.info("[SimpleColonies] Client setup - GUIs ready (Town Hall + Villager)");
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.TOWN_HALL.get(), TownHallScreen::new);
        event.register(ModMenuTypes.VILLAGER.get(), VillagerInteractionScreen::new);
        SimpleColonies.LOGGER.info("[SimpleColonies] Registered TownHallScreen and VillagerInteractionScreen");
    }
}
