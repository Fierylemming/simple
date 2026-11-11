package com.simplecolonies.building;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Building instance — now supports both blueprint-generated and player-designated via Building Marker.
 * For player buildings, isPlayerPlaced=true and we skip placement queue (already complete).
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
    public final boolean isPlayerPlaced; // true if via Building Marker — already built

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
            // Player-built is instantly complete, but still counts for housing/jobs/warehouse hub
            this.requiredResources.clear();
            this.state = BuildingState.COMPLETE;
        }
    }

    public BuildingType getType() { return type; }
    public BlockPos getPos() { return pos; }
    public BuildingState getState() { return state; }
    public void setState(BuildingState state) { this.state = state; }
    public int getHousingCapacity() { return type.housingCapacity * level; }
    public CultureType getCulture() { return culture; }

    private void loadSchematicPlacements() {
        int idx = 0;
        for (Map.Entry<String, Integer> e : requiredResources.entrySet()) {
            for (int i = 0; i < e.getValue(); i++) {
                BlockPos p = pos.offset(idx % 5, idx / 25, (idx / 5) % 5);
                placements.add(BlockPlacement.dummy(p, e.getKey()));
                idx++;
            }
        }
    }

    // Chest-aware resource check: checks both abstract and chest counts
    public boolean hasResources(Colony colony) {
        // Need level to check chests; fallback to abstract if no level
        for (Map.Entry<String, Integer> e : requiredResources.entrySet()) {
            int have = colony.getWarehouseCount(e.getKey()); // abstract fallback is safe for blueprint preview
            // Try chest-aware if colony has manager with chests
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

    public BlockPlacement getNextPlacement() {
        return placements.peek();
    }

    public void markPlaced(BlockPlacement p) {
        placements.poll();
        completed.add(p);
    }

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
        return b;
    }

    public static class BlockPlacement {
        public final BlockPos pos;
        public final BlockState state;
        public final String itemId;
        public BlockPlacement(BlockPos pos, BlockState state, String itemId) {
            this.pos = pos; this.state = state; this.itemId = itemId;
        }
        static BlockPlacement dummy(BlockPos pos, String itemId) {
            return new BlockPlacement(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), itemId);
        }
    }
}
