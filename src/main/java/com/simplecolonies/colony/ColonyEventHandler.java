package com.simplecolonies.colony;

import com.simplecolonies.SimpleColonies;
import com.simplecolonies.entity.ColonyVillager;
import net.minecraft.core.BlockPos;
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
        if (tickCounter % 100 != 0) return;
        for (Colony c : ColonyManager.getInstance().getAll()) {
            // no distance filter — fix even far colonies like your Greek at 753,833
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
        if (ColonyManager.getInstance().colonyCount() >= 6) return;
        // 1) Retroactively colonize any vanilla village farm the player is standing near (fixes screenshot: roman field with no villagers)
        for (var player : level.players()) {
            if (tickCounter % 40 != 0) break; // check slowly
            BlockPos ppos = player.blockPosition();
            if (ColonyManager.getInstance().getColonyAt(ppos).isPresent()) continue;
            // If near farmland or structure, create colony at nearest farm centre
            BlockPos farmPos = findNearbyFarmland(level, ppos, 32);
            if (farmPos != null) {
                var biome = level.getBiome(farmPos);
                var culture = com.simplecolonies.culture.CultureType.fromBiomeTemperature(biome.value().getBaseTemperature(), biome);
                var tag = com.simplecolonies.worldgen.ModWorldGen.tag(culture.id);
                // allow even if tag mismatch — just use culture from temp, but prefer tag biomes
                var colony = ColonyManager.getInstance().createColony(level, culture, farmPos);
                if (colony != null) {
                    SimpleColonies.LOGGER.info("Retroactively spawned {} colony #{} at player farm {} for {}", culture.displayName, colony.getId(), farmPos.toShortString(), player.getName().getString());
                    level.players().forEach(pl -> pl.displayClientMessage(net.minecraft.network.chat.Component.literal("§aDiscovered " + culture.displayName + " colony #" + colony.getId() + " — villagers spawning!"), false));
                    return;
                }
            }
        }
        if (naturalSpawnCooldown-- > 0) return;
        naturalSpawnCooldown = 1200; // ~1 min, more aggressive
        var center = level.getSharedSpawnPos();
        var r = level.random;
        for (int attempt = 0; attempt < 30; attempt++) {
            int dx = r.nextInt(2400) - 1200;
            int dz = r.nextInt(2400) - 1200;
            var pos = center.offset(dx, 0, dz);
            pos = new net.minecraft.core.BlockPos(pos.getX(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()), pos.getZ());
            if (level.getBlockState(pos.below()).isAir()) continue;
            if (ColonyManager.getInstance().getColonyAt(pos).isPresent()) continue;
            var biome = level.getBiome(pos);
            var culture = com.simplecolonies.culture.CultureType.fromBiomeTemperature(biome.value().getBaseTemperature(), biome);
            var tag = com.simplecolonies.worldgen.ModWorldGen.tag(culture.id);
            if (!level.getBiome(pos).is(tag)) continue;
            var colony = ColonyManager.getInstance().createColony(level, culture, pos);
            if (colony != null) {
                SimpleColonies.LOGGER.info("Naturally spawned {} colony #{} at {}", culture.displayName, colony.getId(), pos.toShortString());
            }
            break;
        }
    }

    private static BlockPos findNearbyFarmland(ServerLevel level, BlockPos from, int radius) {
        for (BlockPos p : BlockPos.betweenClosed(from.offset(-radius, -6, -radius), from.offset(radius, 6, radius))) {
            if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.FARMLAND)) return p.immutable().above().above(); // centre above farm
        }
        return null;
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
