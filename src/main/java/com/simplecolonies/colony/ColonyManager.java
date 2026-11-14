package com.simplecolonies.colony;

import com.simplecolonies.SimpleColonies;
import com.simplecolonies.block.ModBlocks;
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
            // Relations are world data, even when neither colony's chunks are loaded.
            // Anchor legacy durations together at load and persist the migration.
            Diplomacy.migrateLegacyRelations(colonies.values(), Math.floorDiv(overworld.getDayTime(), 24000L));
            saveAll();
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
        return createColony(level, culture, center, null);
    }

    private Colony createColony(ServerLevel level, CultureType culture, BlockPos center, StarterPlan plan) {
        if (level != null && !level.dimension().equals(Level.OVERWORLD)) return null;
        if (colonies.size() >= com.simplecolonies.config.ColonyConfig.MAX_COLONIES_PER_DIMENSION.get()) return null;
        int id = nextId++;
        Colony colony = new Colony(id, culture, center);
        {
            colony.depositToWarehouse("minecraft:oak_planks", 32);
            colony.depositToWarehouse("minecraft:oak_log", 16);
            colony.depositToWarehouse("minecraft:bread", 16);
            colony.depositToWarehouse("minecraft:cobblestone", 32);
            colony.depositToWarehouse("minecraft:stone_pickaxe", 1);
            colony.depositToWarehouse("minecraft:stone_axe", 1);
            colony.depositToWarehouse("minecraft:oak_sapling", 4);
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
            if (plan == null) {
                placeInitialTownHall(level, colony);
                placeStarterStructures(level, colony, false);
            } else placePlannedStarters(level, colony, plan);
            transferNewStarterSupplies(level, colony);
            spawnInitialVillagers(level, colony);
            placeTownHallIfNeeded(level, colony);
        }
        return colony;
    }

    /** New starter halls own their freshly created chest. Move the founding
     * supplies into it once, rather than duplicating them in the virtual ledger.
     * Existing colonies are never migrated implicitly, and a missing/unsafe hall
     * keeps the legacy stock as a fallback instead of claiming player storage. */
    private void transferNewStarterSupplies(ServerLevel level, Colony colony) {
        BlockPos hall = colony.getCenter();
        if (colony.getBuildings().stream().noneMatch(b -> b.getType() == com.simplecolonies.building.BuildingType.TOWN_HALL
                && b.getPos().equals(hall))) return;
        BlockPos chest = hall.offset(2, 0, 2);
        if (!level.isLoaded(chest) || !level.getBlockState(chest).is(Blocks.CHEST)) return;
        var storage = colony.getWarehouseManager();
        if (!storage.registerStorage(level, chest)) return;
        // Transfer only amounts actually accepted. A full or changed container
        // cannot lose any founding items; no other container is touched.
        for (var entry : colony.getWarehouseSnapshot(null).entrySet()) {
            int deposited = storage.deposit(level, chest, entry.getKey(), entry.getValue());
            if (deposited > 0) colony.depositToWarehouse(entry.getKey(), -deposited);
        }
    }

    private void spawnInitialVillagers(ServerLevel level, Colony colony) {
        if (colony.citizenCount() > 0) return;
        BlockPos c = colony.getCenter();
        BlockPos house = colony.getBuildings().stream()
                .filter(b -> b.getType() == com.simplecolonies.building.BuildingType.HOUSE || b.getType() == com.simplecolonies.building.BuildingType.TOWN_HALL)
                .map(com.simplecolonies.building.Building::getPos).findFirst().orElse(c);
        BlockPos farm = colony.getBuildings().stream()
                .filter(b -> b.getType() == com.simplecolonies.building.BuildingType.FARM)
                .map(com.simplecolonies.building.Building::getPos).findFirst().orElse(c);
        spawnVillagerAt(level, house.offset(0, 0, -5), colony, com.simplecolonies.villager.job.JobType.LUMBERJACK);
        spawnVillagerAt(level, farm.offset(0, 0, -6), colony, com.simplecolonies.villager.job.JobType.FARMER);
        spawnVillagerAt(level, house.offset(2, 0, -5), colony, com.simplecolonies.villager.job.JobType.BUILDER);
        spawnVillagerAt(level, house.offset(-2, 0, -5), colony, com.simplecolonies.villager.job.JobType.MINER);
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
            // A rough shore can block the normal farm/worksite approach. The
            // already-built hall is a safe second search, not a terrain edit.
            if (spawnPos == null) spawnPos = findSafeSpawn(level, colony.getCenter().offset(1, 0, 1));
            if (spawnPos == null) {
                SimpleColonies.LOGGER.warn("No safe loaded spawn location for colony #{} near {}; leaving terrain untouched", colony.getId(), pos);
                return;
            }
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

    BlockPos findSafeSpawn(ServerLevel level, BlockPos pos) {
        // Try exact
        if (isOpenFounderSpot(level, pos)) return pos;
        // Never inspect or load chunks merely to find a spawn.
        if (!level.isLoaded(pos)) return null;
        // Heightmap top
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        BlockPos h = new BlockPos(pos.getX(), y, pos.getZ());
        if (isOpenFounderSpot(level, h)) return h;
        // Also check WORLD_SURFACE for water case (your beach farm over water)
        int wy = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        BlockPos wh = new BlockPos(pos.getX(), wy + 1, pos.getZ());
        if (isOpenFounderSpot(level, wh)) return wh;
        // Search 7x7x 8
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -3; dy <= 4; dy++) {
            BlockPos p = pos.offset(dx, dy, dz);
            if (isOpenFounderSpot(level, p)) return p;
        }
        // No safe position: do not construct a platform over somebody's blocks
        // or force a spawn in water, a wall, an unloaded chunk or the void.
        return null;
    }

    private boolean isOpenFounderSpot(ServerLevel level, BlockPos pos) {
        if (!isSpawnable(level, pos)) return false;
        var box = new net.minecraft.world.phys.AABB(pos.getX() + 0.2, pos.getY(), pos.getZ() + 0.2,
                pos.getX() + 0.8, pos.getY() + 1.8, pos.getZ() + 0.8);
        return level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box,
                net.minecraft.world.entity.LivingEntity::isAlive).isEmpty();
    }

    private boolean isSpawnable(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !level.isLoaded(pos.above()) || !level.isLoaded(pos.below())) return false;
        var below = level.getBlockState(pos.below());
        var at = level.getBlockState(pos);
        // Need air at pos, solid below (or farmland/dirt), not water
        if ((!at.isAir() && !at.canBeReplaced()) || !at.getFluidState().isEmpty()) return false;
        if (!level.getBlockState(pos.above()).isAir() || !below.getFluidState().isEmpty()) return false;
        return below.isSolidRender(level, pos.below()) || below.is(Blocks.FARMLAND) || below.is(Blocks.DIRT) || below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.SAND) || below.is(Blocks.SANDSTONE);
    }

    private void placeTownHallIfNeeded(ServerLevel level, Colony colony) {
        BlockPos p = colony.getCenter();
        // Never create a free-standing hall block when no hall shell was built.
        boolean hasHall = colony.getBuildings().stream().anyMatch(b -> b.getType() == com.simplecolonies.building.BuildingType.TOWN_HALL && b.getPos().equals(p));
        if (!hasHall && !level.getBlockState(p).is(ModBlocks.TOWN_HALL.get())) return;
        if (hasHall && (level.getBlockState(p).isAir() || level.getBlockState(p).canBeReplaced())) {
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
        boolean missingHall = colony.getBuildings().stream().noneMatch(b -> b.getType() == com.simplecolonies.building.BuildingType.TOWN_HALL);
        // Only rebuild at the original center when the entire plot is safe. Never
        // replace an existing building or flatten player property to repair a save.
        if (missingHall && colony.getBuildings().stream().noneMatch(b -> b.getPos().equals(colony.getCenter())))
            placeInitialTownHall(level, colony);
        placeStarterStructures(level, colony, false);
        spawnInitialVillagers(level, colony);
        placeTownHallIfNeeded(level, colony);
        markDirty(colony);
    }

    private void placeStarterStructures(ServerLevel level, Colony colony, boolean createLegacyHouse) {
        if (createLegacyHouse && colony.getBuildings().stream().noneMatch(b -> b.getType() == com.simplecolonies.building.BuildingType.HOUSE)) {
            BlockPos housePos = findStarterSite(level, colony, 3, 8);
            if (housePos != null) {
                prepareStarterPlot(level, housePos, 3);
                placeSimpleHouse(level, housePos, colony.getCulture());
                colony.registerPlayerBuilding(new com.simplecolonies.building.Building(com.simplecolonies.building.BuildingType.HOUSE, housePos, colony.getCulture(), true));
            } else SimpleColonies.LOGGER.warn("No safe starter house site for colony #{}", colony.getId());
        }
        if (colony.getBuildings().stream().noneMatch(b -> b.getType() == com.simplecolonies.building.BuildingType.FARM)) {
            int farmHalfSize = 5;
            BlockPos farmPos = findStarterSite(level, colony, farmHalfSize, 14);
            if (farmPos == null) {
                farmHalfSize = 3;
                farmPos = findStarterSite(level, colony, farmHalfSize, 14);
            }
            if (farmPos != null) {
                prepareStarterPlot(level, farmPos, farmHalfSize);
                placeSimpleFarm(level, farmPos, farmHalfSize);
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
        var placed = new ArrayList<BlockPos>();
        for (int i = 0; i < jobs.length; i++) {
            var predicate = com.simplecolonies.villager.ColonyProfessions.profession(jobs[i]).heldJobSite();
            if (level.getPoiManager().getInRange(predicate, colony.getCenter(), colony.getRadius(),
                    net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).findAny().isPresent()) continue;
            BlockPos site = null;
            for (var offset : StarterSiteSearch.offsets(42, 2)) {
                if (offset.x() * offset.x() + offset.z() * offset.z() < 64) continue;
                BlockPos candidate = naturalSurface(level, colony.getCenter().getX() + offset.x(), colony.getCenter().getZ() + offset.z());
                if (candidate == null || placed.stream().anyMatch(p -> p.distSqr(candidate) < 144)) continue;
                if (colony.isClearWorksite(level, candidate, false)) { site = candidate; break; }
            }
            // In cramped terrain retain a usable POI even when a surrounding workshop
            // cannot yet be planned; never overwrite blocks to force the larger plot.
            if (site == null) site = findStarterSite(level, colony, 1, 8);
            if (site == null) {
                SimpleColonies.LOGGER.warn("No safe starter workstation site for {} in colony #{}", jobs[i], colony.getId());
                continue;
            }
            level.setBlock(site, states[i], 3);
            placed.add(site);
        }
    }

    private boolean isNaturalGround(net.minecraft.world.level.block.state.BlockState state) {
        return NaturalGround.supportsConstruction(state);
    }

    /** Natural foliage may be cleared, but player-placed leaves and logs are protected. */
    private boolean isClearable(net.minecraft.world.level.block.state.BlockState state) {
        if (!state.getFluidState().isEmpty()) return false;
        if (state.is(net.minecraft.tags.BlockTags.LEAVES)) {
            return state.hasProperty(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)
                    && !state.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT);
        }
        return com.simplecolonies.building.ConstructionSafety.clearable(state);
    }

    /** Find actual soil below foliage, but never search through timber or buildings. */
    private BlockPos naturalSurface(ServerLevel level, int x, int z) {
        BlockPos probe = new BlockPos(x, level.getMinBuildHeight(), z);
        if (!level.isLoaded(probe)) return null;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        for (int y = top - 1; y >= Math.max(level.getMinBuildHeight(), top - 16); y--) {
            BlockPos p = new BlockPos(x, y, z);
            var state = level.getBlockState(p);
            if (isNaturalGround(state) && state.getFluidState().isEmpty()) return p.above();
            if (!isClearable(state) || level.getBlockEntity(p) != null) return null;
        }
        return null;
    }

    /** Validate every block we may alter, including the foundation and roof clearance. */
    private boolean isSafeStarterPlot(ServerLevel level, BlockPos origin, int halfSize) {
        return isSafeStarterPlot(level, origin, halfSize, false);
    }
    private boolean isSafeStarterPlot(ServerLevel level, BlockPos origin, int halfSize, boolean allowTownHall) {
        int y = origin.getY();
        for (int dx = -halfSize; dx <= halfSize; dx++) for (int dz = -halfSize; dz <= halfSize; dz++) {
            BlockPos column = origin.offset(dx, 0, dz);
            BlockPos surface = allowTownHall && dx == 0 && dz == 0
                    && level.getBlockState(column).is(ModBlocks.TOWN_HALL.get()) && isNaturalGround(level.getBlockState(column.below()))
                    ? column : naturalSurface(level, column.getX(), column.getZ());
            if (surface == null || Math.abs(surface.getY() - y) > 2) return false;
            for (int dy = -3; dy <= 4; dy++) {
                BlockPos p = origin.offset(dx, dy, dz);
                var state = level.getBlockState(p);
                if (allowTownHall && p.equals(origin) && state.is(ModBlocks.TOWN_HALL.get())) continue;
                if (!StarterSiteSearch.safeBlock(dy, !state.getFluidState().isEmpty(),
                        level.getBlockEntity(p) != null, isNaturalGround(state) && p.getY() < surface.getY(), isClearable(state))) return false;
            }
        }
        return true;
    }

    /** Find safe terrain, trying closer sites first without loading new chunks. */
    private BlockPos findStarterSite(ServerLevel level, Colony colony, int halfSize, int minRadius) {
        BlockPos center = colony.getCenter();
        for (var offset : StarterSiteSearch.offsets(42, 2)) {
            int dx = offset.x(), dz = offset.z();
            if (dx * dx + dz * dz < minRadius * minRadius) continue;
            BlockPos candidate = naturalSurface(level, center.getX() + dx, center.getZ() + dz);
            if (candidate == null || !colony.isInside(candidate)) continue;
            if (colony.getBuildings().stream().anyMatch(b -> {
                long bx = b.getPos().getX() - candidate.getX(), bz = b.getPos().getZ() - candidate.getZ();
                return bx * bx + bz * bz < 225;
            })) continue;
            if (isSafeStarterPlot(level, candidate, halfSize)) return candidate;
        }
        return null;
    }

    private void prepareStarterPlot(ServerLevel level, BlockPos origin, int halfSize) {
        for (int x = -halfSize; x <= halfSize; x++) for (int z = -halfSize; z <= halfSize; z++) {
            for (int y = 0; y <= 4; y++) {
                BlockPos p = origin.offset(x, y, z);
                var state = level.getBlockState(p);
                if (!state.isAir() && (isNaturalGround(state) || isClearable(state))) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
            for (int y = -1; y >= -3; y--) {
                BlockPos p = origin.offset(x, y, z);
                if (level.getBlockState(p).isAir() || level.getBlockState(p).canBeReplaced()) level.setBlock(p, Blocks.DIRT.defaultBlockState(), 3);
                else break;
            }
        }
    }

    public record StarterPlan(BlockPos center, BlockPos house, BlockPos farm, int farmHalfSize) {}

    /** Plan both plots before creating a colony. Only already-loaded, dry land is considered. */
    public StarterPlan findNaturalStarterPlan(ServerLevel level, BlockPos anchor) {
        var houses = new ArrayList<BlockPos>();
        var fullFarms = new ArrayList<BlockPos>();
        var compactFarms = new ArrayList<BlockPos>();
        for (var offset : StarterSiteSearch.offsets(52, 4)) {
            BlockPos pos = naturalSurface(level, anchor.getX() + offset.x(), anchor.getZ() + offset.z());
            if (pos == null || getColonyAt(pos).isPresent()) continue;
            if (isSafeStarterPlot(level, pos, 3)) {
                houses.add(pos);
                compactFarms.add(pos);
                if (isSafeStarterPlot(level, pos, 5)) fullFarms.add(pos);
            }
        }
        StarterPlan plan = chooseStarterPlan(level, anchor, houses, fullFarms, 5);
        return plan != null ? plan : chooseStarterPlan(level, anchor, houses, compactFarms, 3);
    }

    private StarterPlan chooseStarterPlan(ServerLevel level, BlockPos anchor, List<BlockPos> houses, List<BlockPos> farms, int farmHalfSize) {
        int pairsExamined = 0;
        Map<Long, Optional<BlockPos>> centers = new HashMap<>();
        for (BlockPos house : houses) for (BlockPos farm : farms) {
            if (++pairsExamined > 10000) return null;
            if (!StarterSiteSearch.separated(house.getX(), house.getZ(), farm.getX(), farm.getZ(), 15)
                    || house.distSqr(farm) > 48L * 48L) continue;
            int mx = (int) Math.floorDiv((long) house.getX() + farm.getX(), 2);
            int mz = (int) Math.floorDiv((long) house.getZ() + farm.getZ(), 2);
            for (var offset : StarterSiteSearch.offsets(12, 4)) {
                int x = mx + offset.x(), z = mz + offset.z();
                long ax = (long) x - anchor.getX(), az = (long) z - anchor.getZ();
                if (ax * ax + az * az > 40L * 40L) continue;
                long key = ((long) x << 32) | (z & 0xffffffffL);
                BlockPos center = centers.computeIfAbsent(key, ignored -> {
                    BlockPos p = naturalSurface(level, x, z);
                    return p != null && getColonyAt(p).isEmpty() && isSpawnable(level, p) ? Optional.of(p) : Optional.empty();
                }).orElse(null);
                if (center == null || center.distSqr(house) > 48L * 48L || center.distSqr(farm) > 48L * 48L) continue;
                if (Math.abs(center.getX() - house.getX()) <= 4 && Math.abs(center.getZ() - house.getZ()) <= 4) continue;
                if (Math.abs(center.getX() - farm.getX()) <= farmHalfSize + 1 && Math.abs(center.getZ() - farm.getZ()) <= farmHalfSize + 1) continue;
                return new StarterPlan(center, house, farm, farmHalfSize);
            }
        }
        return null;
    }

    public Colony createNaturalColony(ServerLevel level, CultureType culture, StarterPlan plan) {
        // Recheck in case the terrain changed since planning; do not create partial colonies.
        if (!isSafeStarterPlot(level, plan.house(), 3) || !isSafeStarterPlot(level, plan.farm(), plan.farmHalfSize())
                || !isSpawnable(level, plan.center())) return null;
        return createColony(level, culture, plan.house(), plan);
    }

    private void placeInitialTownHall(ServerLevel level, Colony colony) {
        BlockPos site = colony.getCenter();
        if (!isSafeStarterPlot(level, site, 3, true)) {
            SimpleColonies.LOGGER.warn("No safe town hall shell at colony #{}; leaving existing blocks untouched", colony.getId());
            return;
        }
        prepareStarterPlot(level, site, 3);
        placeSimpleHouse(level, site, colony.getCulture());
        colony.registerPlayerBuilding(new com.simplecolonies.building.Building(com.simplecolonies.building.BuildingType.TOWN_HALL, site, colony.getCulture(), true));
    }

    private void placePlannedStarters(ServerLevel level, Colony colony, StarterPlan plan) {
        prepareStarterPlot(level, plan.house(), 3);
        placeSimpleHouse(level, plan.house(), colony.getCulture());
        colony.registerPlayerBuilding(new com.simplecolonies.building.Building(com.simplecolonies.building.BuildingType.TOWN_HALL, plan.house(), colony.getCulture(), true));
        placeTownHallIfNeeded(level, colony);
        prepareStarterPlot(level, plan.farm(), plan.farmHalfSize());
        placeSimpleFarm(level, plan.farm(), plan.farmHalfSize());
        colony.registerPlayerBuilding(new com.simplecolonies.building.Building(com.simplecolonies.building.BuildingType.FARM, plan.farm(), colony.getCulture(), true));
        placeStarterWorkstations(level, colony);
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
        level.setBlock(origin.offset(0, 0, -3), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.NORTH).setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), 2);
        level.setBlock(origin.offset(0, 1, -3), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.NORTH).setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
        // Starter shelter intentionally has no beds; wool requires later production.
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

    private void placeSimpleFarm(ServerLevel level, BlockPos origin, int halfSize) {
        // The validated plot and prepared foundation are dry and loaded. Limit
        // changes to the validated footprint; never fill caves below it.
        for (int x = -halfSize; x <= halfSize; x++) for (int z = -halfSize; z <= halfSize; z++) {
            BlockPos ground = origin.offset(x, -1, z);
            // The topsoil layer was validated before constructing the farm.
            level.setBlock(ground, Blocks.DIRT.defaultBlockState(), 3);
            // Clear air above
            BlockPos above = origin.offset(x, 0, z);
            if (level.getBlockState(above).getFluidState().isSource() || level.getBlockState(above).canBeReplaced()) {
                // Will be overwritten with crop/water later, but ensure air for now
                level.setBlock(above, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // Farmland + crops (9x9)
        for (int x = -halfSize + 1; x < halfSize; x++) for (int z = -halfSize + 1; z < halfSize; z++) {
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
        for (int x = -halfSize; x <= halfSize; x++) for (int z = -halfSize; z <= halfSize; z++) if (x == -halfSize || x == halfSize || z == -halfSize || z == halfSize) level.setBlock(origin.offset(x, 0, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
        // Gate gives villagers access without stepping on crops.
        level.setBlock(origin.offset(0, 0, -halfSize), Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.FenceGateBlock.OPEN, true), 3);
        level.setBlock(origin.offset(halfSize, 0, 0), Blocks.COMPOSTER.defaultBlockState(), 3);
    }

    public Optional<Colony> getColony(int id) { return Optional.ofNullable(colonies.get(id)); }

    public Optional<Colony> getColonyAt(BlockPos pos) {
        return colonies.values().stream().filter(c -> c.isInside(pos)).findFirst();
    }

    /** Colonies are stored in the Overworld; equal coordinates elsewhere are not colony territory. */
    public Optional<Colony> getColonyAt(net.minecraft.resources.ResourceKey<Level> dimension, BlockPos pos) {
        return Level.OVERWORLD.equals(dimension) ? getColonyAt(pos) : Optional.empty();
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
