package com.simplecolonies.colony;

import com.simplecolonies.SimpleColonies;
import com.simplecolonies.culture.CultureType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton managing all colonies. Persists to world/saveddata/simplecolonies.dat via SavedData
 * and also ticks every colony.
 */
public class ColonyManager {
    public static final boolean DEBUG = false;
    private static ColonyManager INSTANCE;

    private final Map<Integer, Colony> colonies = new ConcurrentHashMap<>();
    private int nextId = 1;
    private MinecraftServer server;
    private SavedDataHolder holder;

    private ColonyManager() {}

    public static ColonyManager getInstance() {
        if (INSTANCE == null) INSTANCE = new ColonyManager();
        return INSTANCE;
    }

    public void onServerStarting(MinecraftServer server) {
        this.server = server;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            DimensionDataStorage storage = overworld.getDataStorage();
            holder = storage.computeIfAbsent(new SavedData.Factory<>(SavedDataHolder::new, (tag, provider) -> SavedDataHolder.load(tag), null), "simplecolonies");
            colonies.clear();
            colonies.putAll(holder.colonies);
            nextId = holder.nextId;
        }
    }

    public void saveAll() {
        if (holder != null) {
            holder.colonies = new HashMap<>(colonies);
            holder.nextId = nextId;
            holder.setDirty();
        }
    }

    public void markDirty(Colony colony) {
        saveAll();
    }

    public int colonyCount() { return colonies.size(); }

    public Colony createColony(CultureType culture, BlockPos center) {
        return createColony(null, culture, center);
    }

    public Colony createColony(ServerLevel level, CultureType culture, BlockPos center) {
        if (colonies.size() >= com.simplecolonies.config.ColonyConfig.MAX_COLONIES_PER_DIMENSION.get()) return null;
        int id = nextId++;
        Colony colony = new Colony(id, culture, center);
        {
            colony.depositToWarehouse("minecraft:oak_planks", 32);
            colony.depositToWarehouse("minecraft:oak_log", 16);
            colony.depositToWarehouse("minecraft:bread", 16);
            colony.depositToWarehouse("minecraft:cobblestone", 32);
            colony.depositToWarehouse("minecraft:coal", 8);
            colony.depositToWarehouse("minecraft:stick", 16);
            var tier = com.simplecolonies.building.MaterialProgression.get(culture, 1);
            String primaryItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(tier.primary().get(0).asItem()).toString();
            colony.depositToWarehouse(primaryItem, 24);
        }
        colonies.put(id, colony);
        markDirty(colony);
        if (level == null && server != null) level = server.getLevel(Level.OVERWORLD);
        if (level != null) {
            placeStarterStructures(level, colony);
            spawnInitialVillagers(level, colony);
            placeTownHallIfNeeded(level, colony);
        }
        return colony;
    }

    private void spawnInitialVillagers(ServerLevel level, Colony colony) {
        if (colony.citizenCount() > 0) return;
        BlockPos c = colony.getCenter();
        BlockPos house = colony.getBuildings().stream()
                .filter(b -> b.getType() == com.simplecolonies.building.BuildingType.HOUSE)
                .map(com.simplecolonies.building.Building::getPos).findFirst().orElse(c);
        BlockPos farm = colony.getBuildings().stream()
                .filter(b -> b.getType() == com.simplecolonies.building.BuildingType.FARM)
                .map(com.simplecolonies.building.Building::getPos).findFirst().orElse(c);
        spawnVillagerAt(level, house.offset(0, 0, -5), colony, com.simplecolonies.villager.job.JobType.LUMBERJACK);
        spawnVillagerAt(level, farm.offset(0, 0, -6), colony, com.simplecolonies.villager.job.JobType.FARMER);
        spawnVillagerAt(level, house.offset(2, 0, -5), colony, com.simplecolonies.villager.job.JobType.BUILDER);
    }

    private void spawnVillagerAt(ServerLevel level, BlockPos pos, Colony colony, com.simplecolonies.villager.job.JobType job) {
        com.simplecolonies.entity.ColonyVillager villager = null;
        boolean added = false;
        try {
            villager = com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get().create(level);
            if (villager == null) {
                SimpleColonies.LOGGER.error("Entity creation returned null for colony #{}", colony.getId());
                return;
            }
            BlockPos spawnPos = findSafeSpawn(level, pos);
            villager.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, level.random.nextFloat() * 360, 0);
            villager.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), net.minecraft.world.entity.MobSpawnType.STRUCTURE, null);
            villager.setColony(colony);
            villager.setJob(job);
            added = level.addFreshEntity(villager);
            if (!added) {
                SimpleColonies.LOGGER.warn("World rejected {} spawn for colony #{}", job.name(), colony.getId());
                return;
            }
            SimpleColonies.LOGGER.info("Spawned {} for colony #{} at {}", job.name(), colony.getId(), spawnPos.toShortString());
        } catch (Exception e) {
            SimpleColonies.LOGGER.error("Failed to spawn villager for colony #{}", colony.getId(), e);
        } finally {
            // finalizeSpawn can register a citizen before an exception or rejected entity insertion.
            if (!added && villager != null) colony.removeCitizen(villager.getUUID());
        }
    }

    private BlockPos findSafeSpawn(ServerLevel level, BlockPos pos) {
        // Try exact
        if (isSpawnable(level, pos)) return pos;
        // Heightmap top
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        BlockPos h = new BlockPos(pos.getX(), y, pos.getZ());
        if (isSpawnable(level, h)) return h;
        // Also check WORLD_SURFACE for water case (your beach farm over water)
        int wy = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        BlockPos wh = new BlockPos(pos.getX(), wy + 1, pos.getZ());
        if (isSpawnable(level, wh)) return wh;
        // Search 7x7x 8
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -3; dy <= 4; dy++) {
            BlockPos p = pos.offset(dx, dy, dz);
            if (isSpawnable(level, p)) return p;
        }
        // Last resort: create a 3x3 dirt platform so villager doesn't spawn in water/void (fixes your beach over-water farm)
        BlockPos fallback = new BlockPos(pos.getX(), level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) + 1, pos.getZ());
        // Build platform 3x3
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos plat = fallback.below().offset(dx, 0, dz);
            if (level.getBlockState(plat).isAir() || level.getBlockState(plat).getFluidState().isSource() || !level.getBlockState(plat).isSolidRender(level, plat)) {
                level.setBlock(plat, Blocks.DIRT.defaultBlockState(), 3);
            }
        }
        if (level.getBlockState(fallback).canBeReplaced()) level.setBlock(fallback, Blocks.AIR.defaultBlockState(), 3);
        return fallback;
    }

    private boolean isSpawnable(ServerLevel level, BlockPos pos) {
        var below = level.getBlockState(pos.below());
        var at = level.getBlockState(pos);
        // Need air at pos, solid below (or farmland/dirt), not water
        if ((!at.isAir() && !at.canBeReplaced()) || !at.getFluidState().isEmpty()) return false;
        if (!level.getBlockState(pos.above()).isAir() || !below.getFluidState().isEmpty()) return false;
        return below.isSolidRender(level, pos.below()) || below.is(Blocks.FARMLAND) || below.is(Blocks.DIRT) || below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.SAND) || below.is(Blocks.SANDSTONE);
    }

    private void placeTownHallIfNeeded(ServerLevel level, Colony colony) {
        BlockPos p = colony.getCenter();
        if (level.getBlockState(p).isAir() || level.getBlockState(p).canBeReplaced() || level.getBlockState(p).getFluidState().isSource()) {
            // Ensure solid base
            BlockPos below = p.below();
            if (!level.getBlockState(below).isSolidRender(level, below)) {
                level.setBlock(below, Blocks.DIRT.defaultBlockState(), 3);
            }
            level.setBlock(p, com.simplecolonies.block.ModBlocks.TOWN_HALL.get().defaultBlockState(), 3);
        }
        var be = level.getBlockEntity(p);
        if (be instanceof com.simplecolonies.block.entity.TownHallBlockEntity th) th.setColonyId(colony.getId());
    }

    /** Explicitly repair missing starters; never repopulates colonies automatically after deaths. */
    public void repairStarters(ServerLevel level, Colony colony) {
        placeStarterStructures(level, colony);
        spawnInitialVillagers(level, colony);
        placeTownHallIfNeeded(level, colony);
        markDirty(colony);
    }

    private void placeStarterStructures(ServerLevel level, Colony colony) {
        if (colony.getBuildings().stream().noneMatch(b -> b.getType() == com.simplecolonies.building.BuildingType.HOUSE)) {
            BlockPos housePos = findStarterSite(level, colony, 3, 8);
            if (housePos != null) {
                prepareStarterPlot(level, housePos, 3);
                placeSimpleHouse(level, housePos, colony.getCulture());
                colony.registerPlayerBuilding(new com.simplecolonies.building.Building(com.simplecolonies.building.BuildingType.HOUSE, housePos, colony.getCulture(), true));
            } else SimpleColonies.LOGGER.warn("No safe starter house site for colony #{}", colony.getId());
        }
        if (colony.getBuildings().stream().noneMatch(b -> b.getType() == com.simplecolonies.building.BuildingType.FARM)) {
            BlockPos farmPos = findStarterSite(level, colony, 5, 14);
            if (farmPos != null) {
                prepareStarterPlot(level, farmPos, 5);
                placeSimpleFarm(level, farmPos);
                colony.registerPlayerBuilding(new com.simplecolonies.building.Building(com.simplecolonies.building.BuildingType.FARM, farmPos, colony.getCulture(), true));
            } else SimpleColonies.LOGGER.warn("No safe starter farm site for colony #{}", colony.getId());
        }
        placeStarterWorkstations(level, colony);
    }

    private void placeStarterWorkstations(ServerLevel level, Colony colony) {
        var jobs = new com.simplecolonies.villager.job.JobType[]{
                com.simplecolonies.villager.job.JobType.BUILDER,
                com.simplecolonies.villager.job.JobType.MINER,
                com.simplecolonies.villager.job.JobType.LUMBERJACK};
        var states = new net.minecraft.world.level.block.state.BlockState[]{
                Blocks.ANVIL.defaultBlockState(), Blocks.FURNACE.defaultBlockState(),
                Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false)};
        for (int i = 0; i < jobs.length; i++) {
            var predicate = com.simplecolonies.villager.ColonyProfessions.profession(jobs[i]).heldJobSite();
            if (level.getPoiManager().getInRange(predicate, colony.getCenter(), colony.getRadius(),
                    net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).findAny().isPresent()) continue;
            BlockPos site = findStarterSite(level, colony, 1, 8);
            if (site == null) {
                SimpleColonies.LOGGER.warn("No safe starter workstation site for {} in colony #{}", jobs[i], colony.getId());
                continue;
            }
            level.setBlock(site, states[i], 3);
        }
    }

    private boolean isNaturalGround(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.DIRT) || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL);
    }

    /** Find safe terrain with at most two blocks of slope; never replace structures or water. */
    private BlockPos findStarterSite(ServerLevel level, Colony colony, int halfSize, int minRadius) {
        return findStarterSite(level, colony, halfSize, minRadius, null);
    }

    private BlockPos findStarterSite(ServerLevel level, Colony colony, int halfSize, int minRadius, BlockPos avoid) {
        BlockPos center = colony.getCenter();
        for (int radius = minRadius; radius <= 42; radius += 4) {
            for (int angle = 0; angle < 360; angle += 30) {
                double rad = Math.toRadians(angle);
                int x = center.getX() + (int)(Math.cos(rad) * radius);
                int z = center.getZ() + (int)(Math.sin(rad) * radius);
                BlockPos probe = new BlockPos(x, center.getY(), z);
                if (!level.isLoaded(probe)) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos candidate = new BlockPos(x, y, z);
                if (!colony.isInside(candidate)) continue;
                if (avoid != null) {
                    long ax = avoid.getX() - x, az = avoid.getZ() - z;
                    if (ax * ax + az * az < 225) continue;
                }
                // Distances are horizontal: different elevations must not overlap footprints.
                if (colony.getBuildings().stream().anyMatch(b -> {
                    long dx = b.getPos().getX() - x, dz = b.getPos().getZ() - z;
                    return dx * dx + dz * dz < 225;
                })) continue;
                boolean clear = true;
                for (int dx = -halfSize; dx <= halfSize && clear; dx++) for (int dz = -halfSize; dz <= halfSize && clear; dz++) {
                    BlockPos column = candidate.offset(dx, 0, dz);
                    if (!level.isLoaded(column)) { clear = false; break; }
                    int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
                    if (surface < y - 2 || surface > y + 2 || !isNaturalGround(level.getBlockState(column.atY(surface - 1)))) { clear = false; break; }
                    for (int dy = -3; dy <= 4; dy++) {
                        BlockPos pos = column.offset(0, dy, 0);
                        var state = level.getBlockState(pos);
                        if (!state.getFluidState().isEmpty()) { clear = false; break; }
                        if (dy >= 0 && !state.isAir() && !state.canBeReplaced() && !isNaturalGround(state)) { clear = false; break; }
                    }
                }
                if (clear) return candidate;
            }
        }
        return null;
    }

    /** Level only the natural ground which was checked by findStarterSite. */
    private void prepareStarterPlot(ServerLevel level, BlockPos origin, int halfSize) {
        for (int x = -halfSize; x <= halfSize; x++) for (int z = -halfSize; z <= halfSize; z++) {
            for (int y = 0; y <= 4; y++) {
                BlockPos p = origin.offset(x, y, z);
                if (isNaturalGround(level.getBlockState(p))) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
            for (int y = -1; y >= -3; y--) {
                BlockPos p = origin.offset(x, y, z);
                if (level.getBlockState(p).isAir() || level.getBlockState(p).canBeReplaced()) level.setBlock(p, Blocks.DIRT.defaultBlockState(), 3);
                else break;
            }
        }
    }

    /** Find a dry, accessible centre without forcing chunks to load. */
    public BlockPos findNaturalColonyCenter(ServerLevel level, BlockPos structurePos) {
        for (int radius = 0; radius <= 40; radius += 8) {
            for (int angle = 0; angle < 360; angle += 45) {
                double rad = Math.toRadians(angle);
                int x = structurePos.getX() + (int)(radius * Math.cos(rad));
                int z = structurePos.getZ() + (int)(radius * Math.sin(rad));
                BlockPos probe = new BlockPos(x, structurePos.getY(), z);
                if (!level.isLoaded(probe)) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                if (!isNaturalGround(level.getBlockState(pos.below())) || !isSpawnable(level, pos)
                        || !level.getBlockState(pos.above()).isAir() || getColonyAt(pos).isPresent()) continue;
                Colony probeColony = new Colony(-1, CultureType.ROMAN, pos);
                BlockPos house = findStarterSite(level, probeColony, 3, 8);
                if (house != null && findStarterSite(level, probeColony, 5, 14, house) != null) return pos;
            }
        }
        return null;
    }

    private boolean hasValidFarmNearby(ServerLevel level, BlockPos farmPos) {
        // Look for farmland that is on solid ground, not floating over deep water
        int farmlandCount = 0;
        for (BlockPos p : BlockPos.betweenClosed(farmPos.offset(-4, -1, -4), farmPos.offset(4, -1, 4))) {
            if (level.getBlockState(p).is(Blocks.FARMLAND)) {
                // Check below is solid (not water)
                var below = level.getBlockState(p.below());
                if (below.isSolidRender(level, p.below()) || below.is(Blocks.DIRT)) farmlandCount++;
            }
        }
        // If at least 9 farmland on solid, consider valid
        return farmlandCount >= 9;
    }

    private void repairFarmIfWeird(ServerLevel level, BlockPos origin) {
        // If fence is over water (your screenshot), fix it by placing dirt pillars
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) if (x == -5 || x == 5 || z == -5 || z == 5) {
            BlockPos fencePos = origin.offset(x, 0, z);
            BlockPos ground = fencePos.below();
            var groundState = level.getBlockState(ground);
            var fenceState = level.getBlockState(fencePos);
            // If fence over water/air with no solid ground, place dirt pillar
            if (fenceState.is(Blocks.OAK_FENCE) && !groundState.isSolidRender(level, ground)) {
                level.setBlock(ground, Blocks.DIRT.defaultBlockState(), 3);
                // Fill down to solid if over deep water
                for (int dy = -2; dy >= -5; dy--) {
                    BlockPos below = origin.offset(x, dy, z);
                    var s = level.getBlockState(below);
                    if (s.isAir() || s.getFluidState().isSource()) level.setBlock(below, Blocks.DIRT.defaultBlockState(), 3);
                    else break;
                }
            }
        }
    }

    private void placeSimpleHouse(ServerLevel level, BlockPos origin, com.simplecolonies.culture.CultureType culture) {
        var palette = com.simplecolonies.building.MaterialProgression.get(culture, 1).primary();
        var wood = palette.isEmpty() ? Blocks.OAK_PLANKS.defaultBlockState() : palette.get(0).defaultBlockState();
        var cobble = Blocks.COBBLESTONE.defaultBlockState();
        // Ensure solid 7x7 foundation — build dirt pillars down to solid if over water (your beach case house was on sand edge)
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            BlockPos p = origin.offset(x, -1, z);
            // Always ensure ground, even over water
            var state = level.getBlockState(p);
            if (!state.isSolidRender(level, p)) {
                level.setBlock(p, Blocks.DIRT.defaultBlockState(), 3);
                // Pillars down if over water/air
                for (int dy = -2; dy >= -6; dy--) {
                    BlockPos below = origin.offset(x, dy, z);
                    var bs = level.getBlockState(below);
                    if (bs.isAir() || bs.getFluidState().isSource()) level.setBlock(below, Blocks.DIRT.defaultBlockState(), 3);
                    else break;
                }
            }
            // Clear above to air
            for (int y = 0; y < 4; y++) {
                BlockPos pp = origin.offset(x, y, z);
                if (level.getBlockState(pp).canBeReplaced() || level.getBlockState(pp).getFluidState().isSource()) level.setBlock(pp, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // Floor
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) level.setBlock(origin.offset(x, -1, z), wood, 3);
        // Walls 3 high
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) if (x == -3 || x == 3 || z == -3 || z == 3) {
            for (int y = 0; y < 3; y++) level.setBlock(origin.offset(x, y, z), y == 2 && (x == 0 && z == -3) ? Blocks.AIR.defaultBlockState() : cobble, 3);
        }
        // Door south
        level.setBlock(origin.offset(0, 0, -3), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.NORTH).setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), 3);
        level.setBlock(origin.offset(0, 1, -3), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.NORTH).setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
        // Beds
        level.setBlock(origin.offset(-2, 0, 0), Blocks.WHITE_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST).setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), 3);
        level.setBlock(origin.offset(-1, 0, 0), Blocks.WHITE_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST).setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
        level.setBlock(origin.offset(-2, 0, 1), Blocks.WHITE_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST).setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), 3);
        level.setBlock(origin.offset(-1, 0, 1), Blocks.WHITE_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST).setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
        // Chest + crafting + torch
        level.setBlock(origin.offset(2, 0, 2), Blocks.CHEST.defaultBlockState(), 3);
        level.setBlock(origin.offset(2, 0, -2), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
        level.setBlock(origin.offset(0, 0, 2), Blocks.TORCH.defaultBlockState(), 3);
        // Solid roof: rain and hostile mobs cannot enter through the ceiling.
        for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) {
            level.setBlock(origin.offset(x,3,z), wood,3);
        }
        // Windows
        level.setBlock(origin.offset(3,1,0), Blocks.GLASS_PANE.defaultBlockState(),3);
        level.setBlock(origin.offset(-3,1,0), Blocks.GLASS_PANE.defaultBlockState(),3);
    }

    private void placeSimpleFarm(ServerLevel level, BlockPos origin) {
        // Build solid dirt base first — fixes your screenshot where fence floated over water
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            BlockPos ground = origin.offset(x, -1, z);
            // Always ensure solid ground, overwriting water
            level.setBlock(ground, Blocks.DIRT.defaultBlockState(), 3);
            // Pillars down if over deep water — create island
            for (int dy = -2; dy >= -6; dy--) {
                BlockPos below = origin.offset(x, dy, z);
                var bs = level.getBlockState(below);
                if (bs.isAir() || bs.getFluidState().isSource() || bs.is(Blocks.WATER)) {
                    level.setBlock(below, Blocks.DIRT.defaultBlockState(), 3);
                } else break;
            }
            // Clear air above
            BlockPos above = origin.offset(x, 0, z);
            if (level.getBlockState(above).getFluidState().isSource() || level.getBlockState(above).canBeReplaced()) {
                // Will be overwritten with crop/water later, but ensure air for now
                level.setBlock(above, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // Farmland + crops (9x9)
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            BlockPos p = origin.offset(x, -1, z);
            BlockPos above = origin.offset(x, 0, z);
            if (x == 0) {
                // Irrigation is level with the soil, not above it where it floods crops.
                level.setBlock(p, Blocks.WATER.defaultBlockState(), 3);
            } else {
                level.setBlock(p, Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7), 3);
                level.setBlock(above, Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7), 3);
            }
        }
        // Border fence — now on solid dirt, not floating over water
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) if (x == -5 || x == 5 || z == -5 || z == 5) level.setBlock(origin.offset(x, 0, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
        // Gate gives villagers access without stepping on crops.
        level.setBlock(origin.offset(0, 0, -5), Blocks.OAK_FENCE_GATE.defaultBlockState(), 3);
        level.setBlock(origin.offset(5, 0, 0), Blocks.COMPOSTER.defaultBlockState(), 3);
    }

    public Optional<Colony> getColony(int id) { return Optional.ofNullable(colonies.get(id)); }

    public Optional<Colony> getColonyAt(BlockPos pos) {
        return colonies.values().stream().filter(c -> c.isInside(pos)).findFirst();
    }

    public Collection<Colony> getAll() { return Collections.unmodifiableCollection(colonies.values()); }

    public void deleteColony(int id) {
        colonies.remove(id);
        saveAll();
    }

    public void tickAll(ServerLevel level) {
        for (Colony c : colonies.values()) {
            // Colonies are currently overworld-only; never tick them in another dimension
            // or force-load chunks far from any player.
            if (level.dimension().equals(Level.OVERWORLD) && level.isLoaded(c.getCenter())) {
                c.tick(level);
            }
        }
    }

    // SavedData wrapper
    public static class SavedDataHolder extends SavedData {
        Map<Integer, Colony> colonies = new HashMap<>();
        int nextId = 1;

        public SavedDataHolder() {}
        public SavedDataHolder(CompoundTag tag) { load(tag); }

        public static SavedDataHolder load(CompoundTag tag) {
            SavedDataHolder h = new SavedDataHolder();
            h.nextId = tag.getInt("nextId");
            CompoundTag coloniesTag = tag.getCompound("colonies");
            for (String k : coloniesTag.getAllKeys()) {
                Colony c = Colony.load(coloniesTag.getCompound(k));
                h.colonies.put(c.getId(), c);
            }
            return h;
        }

        @Override
        public @NotNull CompoundTag save(@NotNull CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
            tag.putInt("nextId", nextId);
            CompoundTag coloniesTag = new CompoundTag();
            for (Map.Entry<Integer, Colony> e : colonies.entrySet()) {
                coloniesTag.put(String.valueOf(e.getKey()), e.getValue().save());
            }
            tag.put("colonies", coloniesTag);
            return tag;
        }
    }
}
