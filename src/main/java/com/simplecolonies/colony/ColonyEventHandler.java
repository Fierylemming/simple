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
        // 1) Retroactively colonize ANY village the player is standing near — not just farms
        for (var player : level.players()) {
            if (tickCounter % 40 != 0) break; // check slowly
            BlockPos ppos = player.blockPosition();
            if (ColonyManager.getInstance().getColonyAt(ppos).isPresent()) continue;

            // Try structure-based detection first (most accurate for worldgen villages)
            BlockPos structPos = findNearbyVillageStructure(level, ppos, 32);
            BlockPos target = structPos;

            // Fallback: block indicator scan (covers player-built settlements like screenshot sandstone enclosure)
            if (target == null) {
                target = findNearbyVillageIndicator(level, ppos, 32);
            }

            if (target != null) {
                var biome = level.getBiome(target);
                var culture = CultureType.fromBiomeTemperature(biome.value().getBaseTemperature(), biome);
                // use temp-derived culture — works even if biome tag mismatched (your beach Greek etc)
                var colony = ColonyManager.getInstance().createColony(level, culture, target);
                if (colony != null) {
                    SimpleColonies.LOGGER.info("Retroactively spawned {} colony #{} at {} for {} (indicator at {})", culture.displayName, colony.getId(), target.toShortString(), player.getName().getString(), target.toShortString());
                    level.players().forEach(pl -> pl.displayClientMessage(net.minecraft.network.chat.Component.literal("§aDiscovered " + culture.displayName + " colony #" + colony.getId() + " — villagers spawning!"), false));
                    return;
                }
            }
        }
        if (naturalSpawnCooldown-- > 0) return;
        naturalSpawnCooldown = 1200; // ~1 min
        var center = level.getSharedSpawnPos();
        var r = level.random;
        for (int attempt = 0; attempt < 30; attempt++) {
            int dx = r.nextInt(2400) - 1200;
            int dz = r.nextInt(2400) - 1200;
            var pos = center.offset(dx, 0, dz);
            pos = new BlockPos(pos.getX(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()), pos.getZ());
            if (level.getBlockState(pos.below()).isAir()) continue;
            if (ColonyManager.getInstance().getColonyAt(pos).isPresent()) continue;
            var biome = level.getBiome(pos);
            var culture = CultureType.fromBiomeTemperature(biome.value().getBaseTemperature(), biome);
            var tag = com.simplecolonies.worldgen.ModWorldGen.tag(culture.id);
            if (!level.getBiome(pos).is(tag)) continue;
            var colony = ColonyManager.getInstance().createColony(level, culture, pos);
            if (colony != null) {
                SimpleColonies.LOGGER.info("Naturally spawned {} colony #{} at {}", culture.displayName, colony.getId(), pos.toShortString());
            }
            break;
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

    /**
     * Block indicator scan — fires for ANY settlement, not just farmland.
     * Your screenshot beach enclosure (sandstone walls + fountain + fence gate) has
     * no farmland, so old farm-only scan missed it. This covers:
     *  - Mod blocks (town hall, marker, banner, warehouse)
     *  - All culture palettes (sandstone, quartz, white concrete, terracotta, mud bricks...)
     *  - Vanilla village job sites (hay, bell, composter, blast furnace, etc)
     *  - Generic settlement traces (fence, wall, glass pane from our starter house)
     */
    private static BlockPos findNearbyVillageIndicator(ServerLevel level, BlockPos from, int radius) {
        for (BlockPos p : BlockPos.betweenClosed(from.offset(-radius, -6, -radius), from.offset(radius, 6, radius))) {
            BlockState state = level.getBlockState(p);
            if (state.isAir()) continue;
            if (isVillageIndicator(state)) {
                // Return position slightly above indicator as colony centre
                return p.immutable().above().above();
            }
        }
        return null;
    }

    private static boolean isVillageIndicator(BlockState state) {
        Block b = state.getBlock();
        // 1) Our mod blocks — strongest signal
        try {
            if (state.is(ModBlocks.TOWN_HALL.get()) || state.is(ModBlocks.BUILDING_MARKER.get())
                    || state.is(ModBlocks.WAREHOUSE_CHEST.get()) || state.is(ModBlocks.COLONY_BANNER.get())) {
                return true;
            }
        } catch (Exception ignored) {}

        // 2) Vanilla farm + village essentials (previously farm-only)
        if (state.is(Blocks.FARMLAND) || state.is(Blocks.HAY_BLOCK) || state.is(Blocks.COMPOSTER)
                || state.is(Blocks.BELL) || state.is(Blocks.SMOKER) || state.is(Blocks.BLAST_FURNACE)
                || state.is(Blocks.BARREL) || state.is(Blocks.SMITHING_TABLE) || state.is(Blocks.GRINDSTONE)
                || state.is(Blocks.CARTOGRAPHY_TABLE) || state.is(Blocks.FLETCHING_TABLE) || state.is(Blocks.LECTERN)
                || state.is(Blocks.STONECUTTER) || state.is(Blocks.LOOM) || state.is(Blocks.CAULDRON)
                || state.is(Blocks.BREWING_STAND) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.CHEST)
                || state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.FURNACE)) {
            return true;
        }

        // 3) All culture primary palettes — catches your sandstone enclosure (Egyptian),
        // Greek white concrete on beach, Roman quartz, Persian terracotta, Mesopotamian mud bricks, etc.
        for (CultureType c : CultureType.values()) {
            if (c.primaryPalette.contains(b)) return true;
        }

        // 4) Settlement traces common in villages/starter builds (covers fence gate in your screenshot)
        if (state.is(Blocks.OAK_FENCE) || state.is(Blocks.OAK_FENCE_GATE) || state.is(Blocks.SPRUCE_FENCE) || state.is(Blocks.ACACIA_FENCE)
                || state.is(Blocks.DARK_OAK_FENCE) || state.is(Blocks.BIRCH_FENCE) || state.is(Blocks.BAMBOO_FENCE)
                || state.is(Blocks.SANDSTONE_WALL) || state.is(Blocks.COBBLESTONE_WALL) || state.is(Blocks.MUD_BRICK_WALL)
                || state.is(Blocks.BRICK_WALL) || state.is(Blocks.QUARTZ_PILLAR) || state.is(Blocks.GLASS_PANE) || state.is(Blocks.WHITE_BED)
                || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
            // Fence/wall alone is weak — but on beach with grass interior it's clearly built, so treat as indicator
            // Only count if near other indicator or as secondary signal — we allow it
            return true;
        }

        // 5) Crops/water features from farms and fountain in screenshot
        if (state.is(Blocks.WHEAT) || state.is(Blocks.CARROTS) || state.is(Blocks.POTATOES) || state.is(Blocks.BEETROOTS)
                || state.is(Blocks.WATER) || state.is(Blocks.SANDSTONE) || state.is(Blocks.SMOOTH_SANDSTONE)
                || state.is(Blocks.CUT_SANDSTONE) || state.is(Blocks.CHISELED_SANDSTONE)) {
            // Sandstone already in palette but keep explicit for safety
            return true;
        }

        return false;
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
