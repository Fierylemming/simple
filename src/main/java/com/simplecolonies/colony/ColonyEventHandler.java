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
            ensureColoniesHaveVillagers(level);
            trySpawnNaturalColonies(level);
        }
    }

    private static void ensureColoniesHaveVillagers(ServerLevel level) {
        if (tickCounter % 200 != 0) return;
        for (Colony c : ColonyManager.getInstance().getAll()) {
            if (c.getCenter().distSqr(level.getSharedSpawnPos()) > 500_000) continue;
            if (c.citizenCount() == 0) {
                SimpleColonies.LOGGER.info("Fixing colony #{} at {} with 0 citizens — spawning starters", c.getId(), c.getCenter().toShortString());
                // Re-use spawn logic via reflection to avoid circular dep — or duplicate
                try {
                    var mgr = ColonyManager.getInstance();
                    var m = mgr.getClass().getDeclaredMethod("spawnInitialVillagers", ServerLevel.class, Colony.class);
                    m.setAccessible(true); m.invoke(mgr, level, c);
                } catch (Exception ignored) {}
            }
        }
    }

    private static int naturalSpawnCooldown = 0;
    private static void trySpawnNaturalColonies(ServerLevel level) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;
        if (ColonyManager.getInstance().colonyCount() >= 4) return; // cap natural
        if (naturalSpawnCooldown-- > 0) return;
        naturalSpawnCooldown = 6000; // ~5 mins
        var center = level.getSharedSpawnPos();
        var r = level.random;
        for (int attempt = 0; attempt < 20; attempt++) {
            int dx = r.nextInt(2000) - 1000;
            int dz = r.nextInt(2000) - 1000;
            var pos = center.offset(dx, 0, dz);
            pos = new net.minecraft.core.BlockPos(pos.getX(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()), pos.getZ());
            if (level.getBlockState(pos.below()).isAir()) continue;
            if (ColonyManager.getInstance().getColonyAt(pos).isPresent()) continue;
            var biome = level.getBiome(pos);
            var culture = com.simplecolonies.culture.CultureType.fromBiomeTemperature(biome.value().getBaseTemperature(), biome);
            // Only spawn in matching tagged biome
            var tag = com.simplecolonies.worldgen.ModWorldGen.tag(culture.id);
            if (!level.getBiome(pos).is(tag)) continue;
            var colony = ColonyManager.getInstance().createColony(level, culture, pos);
            if (colony != null) {
                SimpleColonies.LOGGER.info("Naturally spawned {} colony #{} at {}", culture.displayName, colony.getId(), pos.toShortString());
            }
            break;
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
