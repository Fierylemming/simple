package com.simplecolonies.warehouse;

import com.simplecolonies.colony.Colony;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WarehouseManager — the distributed chest network.
 *
 * How it works (what you asked for):
 *  - Every chest/barrel/shulker inside colony radius is part of the stockpile.
 *     Villagers just interact with the nearest chest to deposit/fetch, no central inventory required.
 *  - The Warehouse building (many chests) is the hub: Couriers consolidate there and
 *     it dramatically raises the effective scan capacity.
 *  - Courier job moves stacks in bulk (9× normal) and at 2.5× speed.
 *
 * This manager is ticked every 200 ticks (10s) to rescan chests (cheap), and on-demand
 * when a villager requests an item. It also provides count/get/extract helpers used by all jobs.
 */
public class WarehouseManager {
    private final Colony colony;
    private final Map<BlockPos, Container> cachedChests = new ConcurrentHashMap<>();
    private final Map<String, Integer> aggregatedCounts = new HashMap<>();
    private long lastScanGameTime = -200;
    private BlockPos warehouseCenter = null; // set when Warehouse building completed
    private int chestCount = 0;

    public WarehouseManager(Colony colony) {
        this.colony = colony;
    }

    public void setWarehouseCenter(BlockPos pos) {
        this.warehouseCenter = pos;
    }

    public BlockPos getWarehouseCenter() {
        return warehouseCenter != null ? warehouseCenter : colony.getCenter();
    }

    public int getChestCount() { return chestCount; }

    /** Scan colony radius for containers (chests, barrels, trapped chests, shulkers, etc). Cheap — runs every 10s. */
    public void scan(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (gameTime - lastScanGameTime < 200) return;
        lastScanGameTime = gameTime;
        cachedChests.clear();
        aggregatedCounts.clear();
        chestCount = 0;

        BlockPos center = colony.getCenter();
        int radius = colony.getRadius();
        int radiusSq = radius * radius;

        // Iterate existing block entities in loaded chunks rather than hundreds of
        // thousands of individual block positions, and never load new chunks.
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (var entry : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    if (center.distSqr(pos) > radiusSq) continue;
                    if (!(entry.getValue() instanceof Container container)) continue;
                    // Each block entity is one half of a double chest, so count both.
                    cachedChests.put(pos.immutable(), container);
                    chestCount++;
                    for (int slot = 0; slot < container.getContainerSize(); slot++) {
                        ItemStack stack = container.getItem(slot);
                        if (!stack.isEmpty()) {
                            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                            aggregatedCounts.merge(id, stack.getCount(), Integer::sum);
                        }
                    }
                }
            }
        }
        // Debug
        // System.out.println("[Warehouse] Scanned " + chestCount + " chests for colony #" + colony.getId());
    }

    /** Quick count from cache (no level needed if scan fresh). */
    public int count(String itemId) {
        return aggregatedCounts.getOrDefault(itemId, 0);
    }

    public Map<String, Integer> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(aggregatedCounts));
    }

    /** Try to extract amount of itemId from any chests near pos (nearest-first). Returns actually extracted. */
    public int extract(ServerLevel level, BlockPos requesterPos, String itemId, int amount, boolean simulate) {
        scan(level);
        int remaining = amount;
        int extracted = 0;
        // Sort chests by distance to requester
        List<Map.Entry<BlockPos, Container>> sorted = new ArrayList<>(cachedChests.entrySet());
        sorted.sort(Comparator.comparingDouble(e -> e.getKey().distSqr(requesterPos)));

        for (var entry : sorted) {
            if (remaining <= 0) break;
            Container container = entry.getValue();
            for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty()) continue;
                String regId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (!regId.equals(itemId)) continue;
                int take = Math.min(stack.getCount(), remaining);
                if (!simulate) {
                    stack.shrink(take);
                    container.setChanged();
                    // Update cache counts
                    aggregatedCounts.merge(regId, -take, Integer::sum);
                    if (aggregatedCounts.getOrDefault(regId, 0) <= 0) aggregatedCounts.remove(regId);
                }
                remaining -= take;
                extracted += take;
            }
        }
        return extracted;
    }

    /** Deposit item stacks into nearest chest with space. Returns amount actually deposited. */
    public int deposit(ServerLevel level, BlockPos depositorPos, String itemId, int amount) {
        scan(level);
        // Resolve item from id
        var itemOpt = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(
                net.minecraft.resources.ResourceLocation.parse(itemId)
        );
        if (itemOpt.isEmpty()) return 0;
        var item = itemOpt.get();

        int remaining = amount;
        List<Map.Entry<BlockPos, Container>> sorted = new ArrayList<>(cachedChests.entrySet());
        sorted.sort(Comparator.comparingDouble(e -> e.getKey().distSqr(depositorPos)));

        // Try existing stacks first (merge)
        for (var entry : sorted) {
            if (remaining <= 0) break;
            Container container = entry.getValue();
            for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty()) continue;
                if (!stack.is(item)) continue;
                int space = stack.getMaxStackSize() - stack.getCount();
                if (space <= 0) continue;
                int put = Math.min(space, remaining);
                stack.grow(put);
                container.setChanged();
                aggregatedCounts.merge(itemId, put, Integer::sum);
                remaining -= put;
            }
        }
        // Then empty slots
        for (var entry : sorted) {
            if (remaining <= 0) break;
            Container container = entry.getValue();
            for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty()) continue;
                int put = Math.min(item.getDefaultMaxStackSize(), remaining);
                ItemStack toPut = new ItemStack(item, put);
                container.setItem(slot, toPut);
                container.setChanged();
                aggregatedCounts.merge(itemId, put, Integer::sum);
                remaining -= put;
            }
        }
        int deposited = amount - remaining;
        if (deposited > 0 && warehouseCenter != null && !isWarehouseChest(depositorPos)) {
            // If depositor not at warehouse, leave courier hint
            // Courier will consolidate later from scattered chests to warehouse hub
        }
        return deposited;
    }

    private boolean isWarehouseChest(BlockPos pos) {
        if (warehouseCenter == null) return false;
        return pos.distSqr(warehouseCenter) < 100; // 10 blocks of warehouse
    }

    /** Courier helper: move stacks from scattered chests to warehouse hub (or vice versa for delivery). */
    public int courierMoveToWarehouse(ServerLevel level, int maxStacks) {
        scan(level);
        if (warehouseCenter == null || cachedChests.isEmpty()) return 0;
        Container warehouseContainer = cachedChests.get(warehouseCenter);
        if (warehouseContainer == null) {
            // Find nearest chest to warehouseCenter as hub
            BlockPos hub = cachedChests.keySet().stream().min(Comparator.comparingDouble(p -> p.distSqr(warehouseCenter))).orElse(null);
            if (hub == null) return 0;
            warehouseContainer = cachedChests.get(hub);
        }
        int moved = 0;
        // Pick random scattered chest not at warehouse and move one stack
        for (int i = 0; i < maxStacks; i++) {
            BlockPos sourcePos = cachedChests.keySet().stream()
                    .filter(p -> p.distSqr(warehouseCenter) > 64)
                    .findFirst().orElse(null);
            if (sourcePos == null) break;
            Container source = cachedChests.get(sourcePos);
            for (int slot = 0; slot < source.getContainerSize(); slot++) {
                ItemStack stack = source.getItem(slot);
                if (stack.isEmpty()) continue;
                // Try deposit this stack into warehouse hub
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                int deposited = deposit(level, warehouseCenter, id, stack.getCount());
                if (deposited > 0) {
                    stack.shrink(deposited);
                    source.setChanged();
                    moved++;
                    break;
                }
            }
        }
        return moved;
    }

    /** Quick has check for jobs (avoids level lookup). */
    public boolean has(String itemId, int amount) {
        return count(itemId) >= amount;
    }
}
