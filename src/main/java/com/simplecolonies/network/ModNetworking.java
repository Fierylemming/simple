package com.simplecolonies.network;

import com.simplecolonies.SimpleColonies;

/**
 * Placeholder for future CustomPacketPayload sync (colony delta, warehouse, diplomacy).
 * Current playable version uses MenuType extraData + ColonyManager shared singleton (singleplayer).
 * Dedicated server sync will be added via net.neoforged.neoforge.network.registration.PayloadRegistrar.
 */
public class ModNetworking {
    public static void register(net.neoforged.bus.api.IEventBus bus) {
        SimpleColonies.LOGGER.info("[SimpleColonies] Networking stub - menu extraData handles colony sync for now");
    }
}
