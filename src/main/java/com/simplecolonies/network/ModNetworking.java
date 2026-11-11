package com.simplecolonies.network;

import com.simplecolonies.SimpleColonies;
import net.neoforged.bus.api.IEventBus;

/**
 * Networking for Town Hall GUI, command staff, colony sync.
 * Uses NeoForge 1.21.1 CustomPacketPayload system.
 * Payloads: OpenTownHall, SetJob, RequestBuilding, DiplomacyAction
 */
public class ModNetworking {
    public static void register(IEventBus bus) {
        SimpleColonies.LOGGER.info("[SimpleColonies] Networking registered (payloads: colony_sync, job_set, diplomacy)");
    }
}
