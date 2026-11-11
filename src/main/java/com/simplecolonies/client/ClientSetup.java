package com.simplecolonies.client;

import com.simplecolonies.SimpleColonies;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = SimpleColonies.MOD_ID, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        SimpleColonies.LOGGER.info("[SimpleColonies] Client setup - registering renderers for colony villagers with culture clothing");
    }
}
