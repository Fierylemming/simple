package com.simplecolonies.colony;

import com.simplecolonies.SimpleColonies;
import com.simplecolonies.culture.CultureType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
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
        // Starter buildings — villages spawn with wood-tier houses and farm, using local resources that match style
        // Greek wood at lvl1 → stone bases lvl2 → terracotta etc as described
        var starterHouse = new com.simplecolonies.building.Building(com.simplecolonies.building.BuildingType.HOUSE, center.offset(6,0,0), culture, true);
        var starterFarm = new com.simplecolonies.building.Building(com.simplecolonies.building.BuildingType.FARM, center.offset(-8,0,0), culture, true);
        try {
            var f = Colony.class.getDeclaredField("buildings");
            f.setAccessible(true);
            var list = (java.util.List<com.simplecolonies.building.Building>) f.get(colony);
            list.add(starterHouse);
            list.add(starterFarm);
            // Starter warehouse chest hub with wood + starter cobble/coal so Farmer+Lumberjack can bootstrap Stone tier without Miner
            // Fixes: with only Farmer+Woodcutter, how does village get cobble for Stone tools? Answer: starter stash + foraging
            colony.depositToWarehouse("minecraft:oak_planks", 32);
            colony.depositToWarehouse("minecraft:oak_log", 16);
            colony.depositToWarehouse("minecraft:bread", 16);
            colony.depositToWarehouse("minecraft:cobblestone", 32); // starter rocks for Stone tools → Miner
            colony.depositToWarehouse("minecraft:coal", 8);
            colony.depositToWarehouse("minecraft:stick", 16);
            // Culture-specific starter mats: Greek wood, Egyptian sandstone, etc at tier 1
            var tier = com.simplecolonies.building.MaterialProgression.get(culture, 1);
            String primaryItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(tier.primary().get(0).asItem()).toString();
            colony.depositToWarehouse(primaryItem, 24);
        } catch (Exception ignored) {}
        colonies.put(id, colony);
        markDirty(colony);
        // Spawn initial villagers so village can progress immediately
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
        // Spawn just outside buildings so they don't suffocate in walls — door positions
        spawnVillagerAt(level, c.offset(6, 0, -4), colony, com.simplecolonies.villager.job.JobType.LUMBERJACK);
        spawnVillagerAt(level, c.offset(-8, 0, 4), colony, com.simplecolonies.villager.job.JobType.FARMER);
        spawnVillagerAt(level, c.offset(0, 0, 5), colony, com.simplecolonies.villager.job.JobType.UNEMPLOYED);
    }

    private void spawnVillagerAt(ServerLevel level, BlockPos pos, Colony colony, com.simplecolonies.villager.job.JobType job) {
        try {
            var type = com.simplecolonies.entity.ModEntities.COLONY_VILLAGER.get();
            var villager = type.create(level);
            if (villager == null) return;
            BlockPos spawnPos = findSafeSpawn(level, pos);
            villager.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, level.random.nextFloat() * 360, 0);
            villager.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), net.minecraft.world.entity.MobSpawnType.STRUCTURE, null);
            villager.setColony(colony);
            villager.setJob(job);
            level.addFreshEntity(villager);
            SimpleColonies.LOGGER.info("Spawned {} for colony #{} at {}", job.name(), colony.getId(), spawnPos.toShortString());
        } catch (Exception e) { SimpleColonies.LOGGER.warn("Failed to spawn villager for colony #{}: {}", colony.getId(), e.getMessage()); e.printStackTrace(); }
    }

    private BlockPos findSafeSpawn(ServerLevel level, BlockPos pos) {
        // Try exact, then heightmap, then search 5x5
        if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isSolidRender(level, pos.below())) return pos;
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        BlockPos h = new BlockPos(pos.getX(), y, pos.getZ());
        if (level.getBlockState(h).isAir() && level.getBlockState(h.below()).isSolidRender(level, h.below())) return h;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -2; dy <= 4; dy++) {
            BlockPos p = pos.offset(dx, dy, dz);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isSolidRender(level, p.below())) return p;
        }
        return pos.above();
    }

    private void placeTownHallIfNeeded(ServerLevel level, Colony colony) {
        BlockPos p = colony.getCenter();
        if (level.getBlockState(p).isAir() || level.getBlockState(p).canBeReplaced()) {
            level.setBlock(p, com.simplecolonies.block.ModBlocks.TOWN_HALL.get().defaultBlockState(), 3);
            var be = level.getBlockEntity(p);
            if (be instanceof com.simplecolonies.block.entity.TownHallBlockEntity th) th.setColonyId(colony.getId());
        }
    }

    private void placeStarterStructures(ServerLevel level, Colony colony) {
        BlockPos c = colony.getCenter();
        // House at +6,0,0 : 7x7 platform with walls, door, bed, chest — always visible
        placeSimpleHouse(level, c.offset(6, 0, 0), colony.getCulture());
        // Farm is already there via vanilla structure, but ensure our offset farm also has farmland if empty
        // Only place if no farmland nearby (avoid overwriting vanilla farm)
        BlockPos farmPos = c.offset(-8, 0, 0);
        boolean hasFarm = false;
        for (BlockPos p : BlockPos.betweenClosed(farmPos.offset(-4, -1, -4), farmPos.offset(4, 2, 4))) {
            if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.FARMLAND)) { hasFarm = true; break; }
        }
        if (!hasFarm) placeSimpleFarm(level, farmPos);
    }

    private void placeSimpleHouse(ServerLevel level, BlockPos origin, com.simplecolonies.culture.CultureType culture) {
        var palette = com.simplecolonies.building.MaterialProgression.get(culture, 1).primary();
        var wood = palette.isEmpty() ? net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState() : palette.get(0).defaultBlockState();
        var cobble = net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState();
        // Clear 7x7 area to ground
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            BlockPos p = origin.offset(x, -1, z);
            if (level.getBlockState(p).isAir() || level.getBlockState(p).canBeReplaced()) level.setBlock(p, cobble, 3);
            for (int y = 0; y < 4; y++) {
                BlockPos pp = origin.offset(x, y, z);
                if (level.getBlockState(pp).canBeReplaced()) level.setBlock(pp, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // Floor
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) level.setBlock(origin.offset(x, -1, z), wood, 3);
        // Walls 3 high
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) if (x == -3 || x == 3 || z == -3 || z == 3) {
            for (int y = 0; y < 3; y++) level.setBlock(origin.offset(x, y, z), y == 2 && (x == 0 && z == -3) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : cobble, 3);
        }
        // Door south
        level.setBlock(origin.offset(0, 0, -3), net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH).setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), 3);
        level.setBlock(origin.offset(0, 1, -3), net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH).setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
        // Beds
        level.setBlock(origin.offset(-2, 0, 0), net.minecraft.world.level.block.Blocks.WHITE_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST).setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), 3);
        level.setBlock(origin.offset(-1, 0, 0), net.minecraft.world.level.block.Blocks.WHITE_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST).setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
        level.setBlock(origin.offset(-2, 0, 1), net.minecraft.world.level.block.Blocks.WHITE_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST).setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), 3);
        level.setBlock(origin.offset(-1, 0, 1), net.minecraft.world.level.block.Blocks.WHITE_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST).setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
        // Chest + crafting + torch
        level.setBlock(origin.offset(2, 0, 2), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
        level.setBlock(origin.offset(2, 0, -2), net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
        level.setBlock(origin.offset(0, 0, 2), net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState(), 3);
        // Roof — simple gable with stairs for better look
        for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) if (Math.abs(x)==3 || Math.abs(z)==3) {
            // already walls, add roof cap at y=3
            level.setBlock(origin.offset(x,3,z), wood,3);
        }
        for (int x=-2;x<=2;x++) level.setBlock(origin.offset(x,4,0), wood,3); // ridge
        // Windows
        level.setBlock(origin.offset(3,1,0), net.minecraft.world.level.block.Blocks.GLASS_PANE.defaultBlockState(),3);
        level.setBlock(origin.offset(-3,1,0), net.minecraft.world.level.block.Blocks.GLASS_PANE.defaultBlockState(),3);
    }

    private void placeSimpleFarm(ServerLevel level, BlockPos origin) {
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            BlockPos p = origin.offset(x, -1, z);
            if (level.getBlockState(p).isAir() || level.getBlockState(p).canBeReplaced()) level.setBlock(p, net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7), 3);
            BlockPos above = origin.offset(x, 0, z);
            if (x == 0) level.setBlock(above, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 3);
            else if (level.getBlockState(above).isAir()) level.setBlock(above, net.minecraft.world.level.block.Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7), 3);
        }
        // Border fence
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) if (x == -5 || x == 5 || z == -5 || z == 5) level.setBlock(origin.offset(x, 0, z), net.minecraft.world.level.block.Blocks.OAK_FENCE.defaultBlockState(), 3);
        level.setBlock(origin.offset(2, 0, 0), net.minecraft.world.level.block.Blocks.COMPOSTER.defaultBlockState(), 3);
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
            if (c.getCenter().distSqr(level.getSharedSpawnPos()) < 500_000) { // simple distance check; real uses loaded check
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
