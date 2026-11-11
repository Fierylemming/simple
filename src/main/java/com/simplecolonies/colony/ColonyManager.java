package com.simplecolonies.colony;

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
            holder = storage.computeIfAbsent(new SavedData.Factory<>(SavedDataHolder::new, SavedDataHolder::load), "simplecolonies");
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
            // Starter warehouse chest hub with wood for early game
            colony.depositToWarehouse("minecraft:oak_planks", 32);
            colony.depositToWarehouse("minecraft:oak_log", 16);
            colony.depositToWarehouse("minecraft:bread", 16);
            // Culture-specific starter mats: Greek wood, Egyptian sandstone, etc at tier 1
            var tier = com.simplecolonies.building.MaterialProgression.get(culture, 1);
            String primaryItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(tier.primary().get(0).asItem()).toString();
            colony.depositToWarehouse(primaryItem, 24);
        } catch (Exception ignored) {}
        colonies.put(id, colony);
        markDirty(colony);
        return colony;
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
