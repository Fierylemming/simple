package com.simplecolonies.building;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Building instance — now supports leveling: each building has 1 villager initially, upgrades add job blocks and size,
 * and material palette evolves with culture tier (Greek wood→stone→terracotta, etc).
 */
public class Building {
    private final BuildingType type;
    private final BlockPos pos;
    private final CultureType culture;
    private BuildingState state = BuildingState.BLUEPRINT;
    private int level = 1;
    private final Map<String, Integer> requiredResources = new HashMap<>();
    private final Queue<BlockPlacement> placements = new ArrayDeque<>();
    private final List<BlockPlacement> completed = new ArrayList<>();
    public final boolean isPlayerPlaced;

    public Building(BuildingType type, BlockPos pos, CultureType culture) {
        this(type, pos, culture, false);
    }

    public Building(BuildingType type, BlockPos pos, CultureType culture, boolean isPlayerPlaced) {
        this.type = type;
        this.pos = pos;
        this.culture = culture;
        this.isPlayerPlaced = isPlayerPlaced;
        if (!isPlayerPlaced) {
            this.requiredResources.putAll(type.baseCost);
            loadSchematicPlacements();
            this.state = BuildingState.BLUEPRINT;
        } else {
            this.requiredResources.clear();
            this.state = BuildingState.COMPLETE;
        }
    }

    public BuildingType getType() { return type; }
    public BlockPos getPos() { return pos; }
    public BuildingState getState() { return state; }
    public void setState(BuildingState state) { this.state = state; }
    public int getLevel() { return level; }
    public void setLevel(int lvl) { this.level = Math.clamp(lvl, 1, 5); }
    public int getWorkerSlots() { return BuildingUpgrade.workerSlotsForLevel(level); }
    public int getSize() { return BuildingUpgrade.sizeForLevel(level); }
    public int getHousingCapacity() { return type.housingCapacity * level; }
    public CultureType getCulture() { return culture; }
    public Map<String,Integer> getRequiredResources() { return Collections.unmodifiableMap(requiredResources); }

    private void loadSchematicPlacements() {
        // For colony-generated buildings, placements are derived from requiredResources + culture tier mats
        // For player-schematic via Create, placements come from schematic NBT (handled in integration)
        placements.clear();
        int idx = 0;
        // Include tier palette influence: add tier primary material for bigger, culturally accurate builds
        var tier = MaterialProgression.get(culture, Math.max(1, level));
        // We treat requiredResources as accurate for construction cost; placements are placeholders for builder to place
        for (Map.Entry<String, Integer> e : requiredResources.entrySet()) {
            for (int i = 0; i < e.getValue(); i++) {
                BlockPos p = pos.offset(idx % 5, idx / 25, (idx / 5) % 5);
                placements.add(BlockPlacement.dummy(p, e.getKey()));
                idx++;
            }
        }
        // If Greek etc at higher level, builder will pick tier blocks when placing — simulated via dummy but cost already includes tier mats
    }

    public void upgradeToNextLevel() {
        if (level >= 5) return;
        level++;
        // Rebuild placement queue for the new level's expanded shell
        // For player marker buildings we keep COMPLETE but still grow housing; for blueprints we set UNDER_CONSTRUCTION
        if (!isPlayerPlaced) {
            requiredResources.clear();
            requiredResources.putAll(BuildingUpgrade.costForUpgradePreview(this, level)); // preview of what was consumed; real cost already paid
            placements.clear();
            loadSchematicPlacements();
            state = BuildingState.UNDER_CONSTRUCTION;
        } else {
            // Player building instantly grows: just update level, no placements needed
            state = BuildingState.COMPLETE;
        }
    }

    public boolean hasResources(Colony colony) {
        for (Map.Entry<String, Integer> e : requiredResources.entrySet()) {
            int have = colony.getWarehouseCount(e.getKey());
            try {
                var wm = colony.getWarehouseManager();
                if (wm.getChestCount() > 0) have = wm.count(e.getKey()) + have;
            } catch (Exception ignored) {}
            if (have < e.getValue()) return false;
        }
        return true;
    }

    public Map<String, Integer> getMissingResources(Colony colony) {
        Map<String, Integer> missing = new HashMap<>();
        for (Map.Entry<String, Integer> e : requiredResources.entrySet()) {
            int have = colony.getWarehouseCount(e.getKey());
            try {
                var wm = colony.getWarehouseManager();
                if (wm.getChestCount() > 0) have = Math.max(have, wm.count(e.getKey()));
            } catch (Exception ignored) {}
            if (have < e.getValue()) missing.put(e.getKey(), e.getValue() - have);
        }
        return missing;
    }

    public BlockPlacement getNextPlacement() { return placements.peek(); }
    public void markPlaced(BlockPlacement p) { placements.poll(); completed.add(p); }
    public void tick(ServerLevel level, Colony colony) {}

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        tag.putLong("pos", pos.asLong());
        tag.putString("culture", culture.id);
        tag.putString("state", state.name());
        tag.putInt("level", level);
        tag.putBoolean("isPlayerPlaced", isPlayerPlaced);
        CompoundTag req = new CompoundTag();
        requiredResources.forEach((k,v) -> req.putInt(k, v));
        tag.put("required", req);
        tag.putInt("remainingPlacements", placements.size());
        return tag;
    }

    public static Building load(CompoundTag tag) {
        BuildingType type = BuildingType.valueOf(tag.getString("type"));
        BlockPos pos = BlockPos.of(tag.getLong("pos"));
        CultureType culture = CultureType.byId(tag.getString("culture"));
        boolean isPlayer = tag.getBoolean("isPlayerPlaced");
        Building b = new Building(type, pos, culture, isPlayer);
        b.state = BuildingState.valueOf(tag.getString("state"));
        b.level = tag.getInt("level");
        // Regenerate placements if under construction
        if (b.state == BuildingState.BLUEPRINT || b.state == BuildingState.UNDER_CONSTRUCTION) {
            b.placements.clear();
            b.loadSchematicPlacements();
        }
        return b;
    }

    public static class BlockPlacement {
        public final BlockPos pos;
        public final BlockState state;
        public final String itemId;
        public BlockPlacement(BlockPos pos, BlockState state, String itemId) { this.pos = pos; this.state = state; this.itemId = itemId; }
        static BlockPlacement dummy(BlockPos pos, String itemId) {
            return new BlockPlacement(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), itemId);
        }
    }
}
