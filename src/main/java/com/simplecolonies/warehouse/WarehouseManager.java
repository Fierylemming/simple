package com.simplecolonies.warehouse;

import com.simplecolonies.colony.Colony;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Loaded, explicitly designated storage. Unmarked player containers are never stock. */
public class WarehouseManager {
    private static final String OWNER_TAG = "simplecolonies_storage_colony";
    private static final String OWNER_POS_TAG = "simplecolonies_storage_position";
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

    /** Explicit hub; unlike getWarehouseCenter(), null means none has been designated. */
    public BlockPos getConfiguredWarehouseCenter() { return warehouseCenter; }

    /** Explicit opt-in. Claims live on the block entity, not its position, so
     * replacing a container cannot accidentally enroll a player's new container. */
    public boolean registerStorage(ServerLevel level, BlockPos pos) {
        BlockEntity be = storageEntity(level, pos);
        if (be == null || !colony.isInside(pos)) return false;
        var data = be.getPersistentData();
        if (data.contains(OWNER_TAG) && data.contains(OWNER_POS_TAG)
                && data.getLong(OWNER_POS_TAG) == pos.asLong() && data.getInt(OWNER_TAG) != colony.getId()) return false;
        data.putInt(OWNER_TAG, colony.getId());
        data.putLong(OWNER_POS_TAG, pos.asLong());
        be.setChanged();
        lastScanGameTime = level.getGameTime() - 200;
        return true;
    }

    public boolean unregisterStorage(ServerLevel level, BlockPos pos) {
        BlockEntity be = storageEntity(level, pos);
        if (be == null || !owned(be)) return false;
        be.getPersistentData().remove(OWNER_TAG);
        be.getPersistentData().remove(OWNER_POS_TAG);
        be.setChanged();
        cachedChests.remove(pos);
        lastScanGameTime = level.getGameTime() - 200;
        return true;
    }

    private static BlockEntity storageEntity(ServerLevel level, BlockPos pos) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD) || !level.isLoaded(pos)) return null;
        var be = level.getBlockEntity(pos);
        return be instanceof ChestBlockEntity
                || be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity
                || be instanceof net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity ? be : null;
    }

    private boolean owned(BlockEntity be) {
        return be != null && be.getPersistentData().contains(OWNER_TAG) && be.getPersistentData().contains(OWNER_POS_TAG)
                && be.getPersistentData().getInt(OWNER_TAG) == colony.getId()
                && be.getPersistentData().getLong(OWNER_POS_TAG) == be.getBlockPos().asLong();
    }

    public boolean isRegisteredStorage(ServerLevel level, BlockPos pos) {
        return colony.isInside(pos) && owned(storageEntity(level, pos));
    }

    /** Loaded storage positions only: furnaces and other processing inventories are not courier sources. */
    public List<BlockPos> courierStoragePositions(ServerLevel level) {
        scan(level);
        return cachedChests.keySet().stream().filter(p -> courierContainer(level, p) != null).toList();
    }

    public Container courierContainer(ServerLevel level, BlockPos pos) {
        var be = storageEntity(level, pos);
        return colony.isInside(pos) && owned(be) ? (Container) be : null;
    }


    /** ID-only stock operations cannot faithfully represent custom components.
     * Keep named, enchanted, damaged or otherwise modified stacks out of them. */
    private static boolean plainStock(ItemStack stack) {
        return !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, stack.getItem().getDefaultInstance());
    }

    /** Scan loaded, explicitly registered storage within the colony radius. */
    public void scan(ServerLevel level) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            cachedChests.clear(); aggregatedCounts.clear(); chestCount = 0; return;
        }
        long gameTime = level.getGameTime();
        if (gameTime - lastScanGameTime < 200) {
            refreshCounts(level);
            return;
        }
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
                    if (!(entry.getValue() instanceof Container container) || !owned(entry.getValue())) continue;
                    if (!(entry.getValue() instanceof ChestBlockEntity)
                            && !(entry.getValue() instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity)
                            && !(entry.getValue() instanceof net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity)) continue;
                    // A furnace, hopper or other machine is not a warehouse storage container.
                    // Each block entity is one half of a double chest, so count both.
                    cachedChests.put(pos.immutable(), container);
                    chestCount++;
                    for (int slot = 0; slot < container.getContainerSize(); slot++) {
                        ItemStack stack = container.getItem(slot);
                        if (plainStock(stack)) {
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

    /** Refresh player-edited inventories and discard unloaded or replaced block entities. */
    private void refreshCounts(ServerLevel level) {
        aggregatedCounts.clear();
        cachedChests.entrySet().removeIf(entry -> !level.isLoaded(entry.getKey())
                || level.getBlockEntity(entry.getKey()) != entry.getValue()
                || !isRegisteredStorage(level, entry.getKey()));
        chestCount = cachedChests.size();
        for (Container container : cachedChests.values()) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (plainStock(stack)) {
                    String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    aggregatedCounts.merge(id, stack.getCount(), Integer::sum);
                }
            }
        }
    }

    /** Quick count from cache (no level needed if scan fresh). */
    public int count(String itemId) {
        return aggregatedCounts.getOrDefault(itemId, 0);
    }

    public Map<String, Integer> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(aggregatedCounts));
    }

    /** Remove one actual item, retaining damage, enchantments and other components. */
    public ItemStack extractOne(ServerLevel level, BlockPos requesterPos, net.minecraft.world.item.Item item) {
        scan(level);
        var sorted = new ArrayList<>(cachedChests.entrySet());
        sorted.sort(Comparator.comparingDouble(e -> e.getKey().distSqr(requesterPos)));
        for (var entry : sorted) {
            Container container = entry.getValue();
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (!container.getItem(slot).is(item)) continue;
                ItemStack taken = container.removeItem(slot, 1);
                if (!taken.isEmpty()) {
                    container.setChanged();
                    refreshCounts(level);
                    return taken;
                }
            }
        }
        return ItemStack.EMPTY;
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
                if (!plainStock(stack)) continue;
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
                if (!stack.is(item) || !plainStock(stack) || !container.canPlaceItem(slot, item.getDefaultInstance())) continue;
                int space = Math.min(stack.getMaxStackSize(), container.getMaxStackSize()) - stack.getCount();
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
                if (!stack.isEmpty() || !container.canPlaceItem(slot, item.getDefaultInstance())) continue;
                int put = Math.min(Math.min(item.getDefaultMaxStackSize(), container.getMaxStackSize()), remaining);
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
                if (!plainStock(stack)) continue;
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
