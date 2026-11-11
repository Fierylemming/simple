package com.simplecolonies.building;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Instance of a building in a colony. Handles blueprint -> construction -> completed lifecycle.
 * Schematics are NBT structures per culture (data/simplecolonies/structure/egyptian/*.nbt)
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

    public Building(BuildingType type, BlockPos pos, CultureType culture) {
        this.type = type;
        this.pos = pos;
        this.culture = culture;
        this.requiredResources.putAll(type.baseCost);
        loadSchematicPlacements();
    }

    public BuildingType getType() { return type; }
    public BlockPos getPos() { return pos; }
    public BuildingState getState() { return state; }
    public void setState(BuildingState state) { this.state = state; }
    public int getHousingCapacity() { return type.housingCapacity * level; }
    public CultureType getCulture() { return culture; }

    private void loadSchematicPlacements() {
        // In real mod: load StructureTemplate NBT via StructureManager and convert to placements
        // Here we simulate with baseCost -> placements queue
        // Example: 40 oak_planks becomes 40 placements of oak_planks at incrementing positions
        int idx = 0;
        for (Map.Entry<String, Integer> e : requiredResources.entrySet()) {
            for (int i = 0; i < e.getValue(); i++) {
                BlockPos p = pos.offset(idx % 5, idx / 25, (idx / 5) % 5);
                // State lookup via registry - simplified
                // placements.add(new BlockPlacement(p, RegistryState.of(e.getKey()), e.getKey()));
                placements.add(BlockPlacement.dummy(p, e.getKey()));
                idx++;
            }
        }
    }

    public boolean hasResources(Colony colony) {
        for (Map.Entry<String, Integer> e : requiredResources.entrySet()) {
            if (colony.getWarehouseCount(e.getKey()) < e.getValue()) return false;
        }
        return true;
    }

    public Map<String, Integer> getMissingResources(Colony colony) {
        Map<String, Integer> missing = new HashMap<>();
        for (Map.Entry<String, Integer> e : requiredResources.entrySet()) {
            int have = colony.getWarehouseCount(e.getKey());
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

    public void tick(ServerLevel level, Colony colony) {
        // Visual: show outline particles if blueprint
        // Could spawn item frames for missing resources
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        tag.putLong("pos", pos.asLong());
        tag.putString("culture", culture.id);
        tag.putString("state", state.name());
        tag.putInt("level", level);
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
        Building b = new Building(type, pos, culture);
        b.state = BuildingState.valueOf(tag.getString("state"));
        b.level = tag.getInt("level");
        // placements count restoration omitted for brevity
        return b;
    }

    public static class BlockPlacement {
        public final BlockPos pos;
        public final BlockState state;
        public final String itemId;

        public BlockPlacement(BlockPos pos, BlockState state, String itemId) {
            this.pos = pos;
            this.state = state;
            this.itemId = itemId;
        }

        static BlockPlacement dummy(BlockPos pos, String itemId) {
            return new BlockPlacement(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), itemId);
        }
    }
}
