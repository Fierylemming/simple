package com.simplecolonies.colony;

import com.simplecolonies.SimpleColonies;
import com.simplecolonies.entity.ColonyVillager;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Ticks colonies and auto-assigns spawned villagers to nearest colony.
 */
@EventBusSubscriber(modid = SimpleColonies.MOD_ID)
public class ColonyEventHandler {
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        int interval = com.simplecolonies.config.ColonyConfig.TICKS_BETWEEN_COLONY_TICK.get();
        if (tickCounter % interval != 0) return;
        var server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            ColonyManager.getInstance().tickAll(level);
        }
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.getEntity() instanceof ColonyVillager cv) {
            var level = event.getLevel().getLevel();
            var colonyOpt = ColonyManager.getInstance().getColonyAt(cv.blockPosition());
            colonyOpt.ifPresent(colony -> {
                cv.setColony(colony);
                SimpleColonies.LOGGER.debug("Assigned spawned ColonyVillager {} to colony #{}", cv.getUUID(), colony.getId());
            });
        }
    }
}
