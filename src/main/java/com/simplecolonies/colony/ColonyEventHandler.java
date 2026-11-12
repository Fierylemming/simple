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
    private static long tickCounter = 0;
    private static final java.util.Map<java.util.UUID, Long> nextPlacementAttempt = new java.util.HashMap<>();

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        int interval = com.simplecolonies.config.ColonyConfig.TICKS_BETWEEN_COLONY_TICK.get();
        if (tickCounter % interval != 0) return;
        var server = event.getServer();
        nextPlacementAttempt.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        for (ServerLevel level : server.getAllLevels()) {
            ColonyManager.getInstance().tickAll(level);
            trySpawnNaturalColonies(level);
        }
    }

    private record FoundVillage(BlockPos anchor, CultureType culture) {}

    private static void trySpawnNaturalColonies(ServerLevel level) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;
        for (var player : level.players()) {
            if (tickCounter < nextPlacementAttempt.getOrDefault(player.getUUID(), 0L)) continue;
            BlockPos playerPos = player.blockPosition();
            if (ColonyManager.getInstance().getColonyAt(playerPos).isPresent()) continue;
            FoundVillage found = findNearbyVillageStructure(level, playerPos, 32);
            if (found == null) continue;
            // The anchor is stable even when a different player approaches the same structure.
            boolean alreadyKnown = ColonyManager.getInstance().getAll().stream().anyMatch(c -> {
                long dx = c.getCenter().getX() - found.anchor().getX();
                long dz = c.getCenter().getZ() - found.anchor().getZ();
                return dx * dx + dz * dz < 96L * 96L;
            });
            if (alreadyKnown) continue;
            nextPlacementAttempt.put(player.getUUID(), tickCounter + 600);
            var plan = ColonyManager.getInstance().findNaturalStarterPlan(level, found.anchor());
            if (plan == null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§eVillage structure found, but no safe loaded land for its starter buildings yet."), true);
                continue;
            }
            var colony = ColonyManager.getInstance().createNaturalColony(level, found.culture(), plan);
            if (colony != null) {
                nextPlacementAttempt.remove(player.getUUID());
                BlockPos target = colony.getCenter();
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§aDiscovered " + found.culture().displayName + " colony #" + colony.getId()
                                + " at " + target.toShortString() + " (" + colony.citizenCount() + " citizens, "
                                + colony.getBuildings().size() + " buildings)"), false);
            }
        }
    }

    /** References alone cover entire chunks and do not prove a village is present there. */
    private static FoundVillage findNearbyVillageStructure(ServerLevel level, BlockPos from, int radius) {
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (int dx = -radius; dx <= radius; dx += 8) for (int dz = -radius; dz <= radius; dz += 8) {
            BlockPos sample = from.offset(dx, 0, dz);
            if (!level.isLoaded(sample)) continue;
            int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sample.getX(), sample.getZ());
            BlockPos column = new BlockPos(sample.getX(), surface, sample.getZ());
            for (var entry : level.structureManager().getAllStructuresAt(column).entrySet()) {
                var structure = entry.getKey();
                ResourceLocation id = registry.getKey(structure);
                if (id == null || !id.getNamespace().equals("simplecolonies") || !id.getPath().endsWith("/village_center")) continue;
                for (int dy = -6; dy <= 2; dy += 2) {
                    var start = level.structureManager().getStructureAt(column.offset(0, dy, 0), structure);
                    if (!start.isValid()) continue;
                    var middle = start.getBoundingBox().getCenter();
                    BlockPos anchor = new BlockPos(middle.getX(), middle.getY(), middle.getZ());
                    String culture = id.getPath().substring(0, id.getPath().indexOf('/'));
                    return new FoundVillage(anchor, CultureType.byId(culture));
                }
            }
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
