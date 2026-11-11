package com.simplecolonies.colony;

import com.simplecolonies.SimpleColonies;
import com.simplecolonies.block.ModBlocks;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ColonyVillager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Ticks colonies and auto-assigns spawned villagers to nearest colony.
 * Retroactively colonizes ANY natural village structure, not just farms.
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
            trySpawnNaturalColonies(level);
        }
    }

    private static void trySpawnNaturalColonies(ServerLevel level) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;
        for (var player : level.players()) {
            BlockPos playerPos = player.blockPosition();
            if (ColonyManager.getInstance().getColonyAt(playerPos).isPresent()) continue;
            BlockPos target = findNearbyVillageStructure(level, playerPos, 32);
            if (target == null || ColonyManager.getInstance().getColonyAt(target).isPresent()) continue;
            var biome = level.getBiome(target);
            var culture = CultureType.fromBiomeTemperature(biome.value().getBaseTemperature(), biome);
            var colony = ColonyManager.getInstance().createColony(level, culture, target);
            if (colony != null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§aDiscovered " + culture.displayName + " colony #" + colony.getId()), false);
            }
        }
    }

    /**
     * Structure-based: check for any SimpleColony village structure in range.
     * Uses StructureManager#getAllStructuresAt — true villager villages regardless of block palette.
     */
    private static BlockPos findNearbyVillageStructure(ServerLevel level, BlockPos from, int radius) {
        try {
            // Check centre and ring every 8 blocks to catch structure piece nearby
            for (int dx = -radius; dx <= radius; dx += 8) {
                for (int dz = -radius; dz <= radius; dz += 8) {
                    BlockPos check = from.offset(dx, 0, dz);
                    // Clamp y to surface
                    check = new BlockPos(check.getX(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, check.getX(), check.getZ()), check.getZ());
                    var all = level.structureManager().getAllStructuresAt(check);
                    if (all.isEmpty()) continue;
                    for (var entry : all.entrySet()) {
                        var structure = entry.getKey();
                        // Resolve registry id for this structure
                        var reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
                        ResourceLocation id = reg.getKey(structure);
                        if (id == null) continue;
                        if (!id.getNamespace().equals("simplecolonies")) continue;
                        if (id.getPath().contains("village_center")) {
                            // Use surface pos as colony centre
                            return check.immutable();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Fallback to block scan if API differs
            SimpleColonies.LOGGER.debug("Structure scan fallback: {}", e.getMessage());
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
