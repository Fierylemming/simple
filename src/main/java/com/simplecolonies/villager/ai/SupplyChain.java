package com.simplecolonies.villager.ai;

import com.simplecolonies.colony.Colony;
import java.util.*;

/**
 * Supply Chain board: villagers post requests, other villagers fulfill.
 * Example flow from user request:
 *  Leatherworker needs 4 leather -> posts REQUEST(leather,4)
 *  Butcher sees request, has cows, fulfills by depositing leather -> request resolved
 *  Leatherworker then crafts chestplate, deposits to warehouse, notifies requester
 *
 * Also: Blacksmith needs raw_iron -> Miner fulfills; Miner needs pickaxe -> Blacksmith fulfills etc.
 * This creates emergent self-sufficiency without hardcoding every link.
 */
public class SupplyChain {
    public record Request(String itemId, int amount, String requester, long gameTime, boolean fulfilled) {}

    private final Map<String, Integer> pendingRequests = new HashMap<>();
    private final Colony colony;

    public SupplyChain(Colony colony) { this.colony = colony; }

    public void request(String itemId, int amount, String requester) {
        pendingRequests.merge(itemId, amount, Integer::sum);
        colony.broadcast(net.minecraft.network.chat.Component.literal("§7[Supply] " + requester + " needs " + amount + "x " + itemId));
    }

    public void fulfill(String itemId, int amount) {
        pendingRequests.computeIfPresent(itemId, (k,v) -> {
            int remaining = v - amount;
            return remaining <= 0 ? null : remaining;
        });
    }

    public Map<String, Integer> getPending() { return Collections.unmodifiableMap(pendingRequests); }

    public void tick() {
        // Auto-decrement stale requests after 5 days
    }
}
