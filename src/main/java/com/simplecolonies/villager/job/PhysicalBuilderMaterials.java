package com.simplecolonies.villager.job;

import com.simplecolonies.building.BuildingState;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.building.ConstructionCrafting;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.entity.ColonyVillager;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** A builder fetches plain inputs from shared containers and carries them.
 * Crafting operates transactionally on a copy of the persisted carried inventory. */
final class PhysicalBuilderMaterials {
    enum Result { READY, MOVING, WORKING, MISSING, BLOCKED }
    private final ColonyVillager worker;
    private final Colony colony;
    private final Map<BlockPos, Long> unreachable = new HashMap<>();

    PhysicalBuilderMaterials(ColonyVillager worker, Colony colony) { this.worker = worker; this.colony = colony; }
    private SimpleContainer held() { return worker.getBuilderMaterials(); }
    private static boolean plain(ItemStack stack) {
        return !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, stack.getItem().getDefaultInstance());
    }
    private static String id(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    private static Map<String, Integer> snapshot(Container c) {
        var result = new HashMap<String, Integer>();
        for (int i = 0; i < c.getContainerSize(); i++) {
            var stack = c.getItem(i);
            if (plain(stack)) result.merge(id(stack), stack.getCount(), Integer::sum);
        }
        return result;
    }

    Result prepare(ServerLevel level, String wanted) { return prepare(level, wanted, null); }

    Result prepare(ServerLevel level, String wanted, BlockPos station) {
        if (snapshot(held()).getOrDefault(wanted, 0) > 0) return Result.READY;
        var local = snapshot(held());
        if (ConstructionCrafting.requiredInputs(local, wanted) != null) {
            for (BlockPos table : station == null ? findCraftingTables(level) : java.util.List.of(station)) {
                if (worker.distanceToSqr(table.getX()+.5, table.getY()+.5, table.getZ()+.5) > 9) {
                    if (!worker.onGround() && !worker.isInLiquid() && !worker.isPassenger()) return Result.MOVING;
                    var path = worker.getNavigation().createPath(table, 1);
                    if (path == null || !path.canReach()) continue;
                    worker.getNavigation().moveTo(path, .7);
                    return Result.MOVING;
                }
                if (!craftOne(wanted)) return Result.BLOCKED;
                worker.swing(InteractionHand.MAIN_HAND);
                return Result.WORKING;
            }
            return Result.BLOCKED;
        }
        var warehouse = colony.getWarehouseManager();
        var positions = warehouse.courierStoragePositions(level);
        var available = new HashMap<>(local);
        for (var pos : positions) {
            var container = warehouse.courierContainer(level, pos);
            if (container != null) snapshot(container).forEach((key, value) -> available.merge(key, value, Integer::sum));
        }
        var needed = ConstructionCrafting.requiredInputs(available, wanted);
        if (needed == null) return Result.MISSING;
        unreachable.entrySet().removeIf(e -> e.getValue() <= level.getGameTime());
        for (var input : needed.entrySet()) {
            if (input.getValue() <= local.getOrDefault(input.getKey(), 0)) continue;
            var candidates = positions.stream().filter(p -> !unreachable.containsKey(p))
                    .filter(p -> {
                        var c = warehouse.courierContainer(level, p);
                        return c != null && snapshot(c).getOrDefault(input.getKey(), 0) > 0;
                    }).sorted(Comparator.comparingDouble(p -> p.distSqr(worker.blockPosition()))).toList();
            for (var pos : candidates) {
                if (worker.distanceToSqr(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5) > 9) {
                    if (!worker.onGround() && !worker.isInLiquid() && !worker.isPassenger()) return Result.MOVING;
                    var path = worker.getNavigation().createPath(pos, 1);
                    if (path == null || !path.canReach()) { unreachable.put(pos, level.getGameTime()+200); continue; }
                    worker.getNavigation().moveTo(path, .7);
                    return Result.MOVING;
                }
                var c = warehouse.courierContainer(level, pos);
                if (c == null) continue;
                for (int i = 0; i < c.getContainerSize(); i++) {
                    var stack = c.getItem(i);
                    if (!plain(stack) || !id(stack).equals(input.getKey())) continue;
                    int room = capacity(held(), stack);
                    if (room <= 0) return Result.BLOCKED;
                    // Batch pickup to avoid one storage trip per block.
                    var taken = c.removeItem(i, Math.min(room, stack.getCount()));
                    if (taken.isEmpty()) continue;
                    var remainder = held().addItem(taken);
                    if (!remainder.isEmpty()) {
                        // Capacity was checked on the same server tick. Preserve
                        // any unexpected remainder as a real item, never virtual stock.
                        worker.spawnAtLocation(remainder);
                    }
                    c.setChanged(); worker.swing(InteractionHand.MAIN_HAND);
                    return Result.WORKING;
                }
            }
            return Result.BLOCKED;
        }
        return Result.BLOCKED;
    }

    int countAvailable(ServerLevel level, String id) {
        int result = snapshot(held()).getOrDefault(id, 0);
        var warehouse = colony.getWarehouseManager();
        for (var pos : warehouse.courierStoragePositions(level)) {
            var container = warehouse.courierContainer(level, pos);
            if (container != null) result += snapshot(container).getOrDefault(id, 0);
        }
        return result;
    }

    boolean canPrepare(ServerLevel level, String wanted) {
        var available = snapshot(held());
        var warehouse = colony.getWarehouseManager();
        for (var pos : warehouse.courierStoragePositions(level)) {
            var container = warehouse.courierContainer(level, pos);
            if (container != null) snapshot(container).forEach((key, value) -> available.merge(key, value, Integer::sum));
        }
        return ConstructionCrafting.requiredInputs(available, wanted) != null;
    }

    /** Fetch exactly the requested plain items, without crafting or consulting
     * virtual stock. Partial collections remain in the persisted inventory. */
    Result prepareCount(ServerLevel level, String wanted, int count) {
        int local = snapshot(held()).getOrDefault(wanted, 0);
        if (local >= count) return Result.READY;
        var warehouse = colony.getWarehouseManager();
        var positions = warehouse.courierStoragePositions(level);
        int total = local;
        for (var pos : positions) {
            var c = warehouse.courierContainer(level, pos);
            if (c != null) total += snapshot(c).getOrDefault(wanted, 0);
        }
        if (total < count) return Result.MISSING;
        unreachable.entrySet().removeIf(e -> e.getValue() <= level.getGameTime());
        var candidates = positions.stream().filter(p -> !unreachable.containsKey(p)).filter(p -> {
            var c = warehouse.courierContainer(level, p);
            return c != null && snapshot(c).getOrDefault(wanted, 0) > 0;
        }).sorted(Comparator.comparingDouble(p -> p.distSqr(worker.blockPosition()))).toList();
        for (var pos : candidates) {
            if (worker.distanceToSqr(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5) > 9) {
                if (!worker.onGround() && !worker.isInLiquid() && !worker.isPassenger()) return Result.MOVING;
                var path = worker.getNavigation().createPath(pos, 1);
                if (path == null || !path.canReach()) { unreachable.put(pos, level.getGameTime()+200); continue; }
                worker.getNavigation().moveTo(path, .7); return Result.MOVING;
            }
            var c = warehouse.courierContainer(level, pos);
            for (int i = 0; c != null && i < c.getContainerSize(); i++) {
                var stack = c.getItem(i);
                if (!plain(stack) || !id(stack).equals(wanted)) continue;
                int amount = Math.min(count - local, Math.min(capacity(held(), stack), stack.getCount()));
                if (amount <= 0) return Result.BLOCKED;
                var taken = c.removeItem(i, amount);
                var remainder = held().addItem(taken);
                if (!remainder.isEmpty()) worker.spawnAtLocation(remainder);
                c.setChanged(); worker.swing(InteractionHand.MAIN_HAND); return Result.WORKING;
            }
        }
        return Result.BLOCKED;
    }

    int countPrepared(String wanted) { return snapshot(held()).getOrDefault(wanted, 0); }

    private static int capacity(Container c, ItemStack stack) {
        int room = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            var existing = c.getItem(i);
            if (existing.isEmpty()) room += Math.min(stack.getMaxStackSize(), c.getMaxStackSize());
            else if (ItemStack.isSameItemSameComponents(existing, stack))
                room += Math.max(0, Math.min(existing.getMaxStackSize(), c.getMaxStackSize()) - existing.getCount());
        }
        return room;
    }

    private java.util.List<BlockPos> findCraftingTables(ServerLevel level) {
        var tables = new java.util.HashSet<BlockPos>();
        for (var building : colony.getBuildings()) {
            if (!building.isOperational()
                    || (building.getType() != BuildingType.TOWN_HALL && building.getType() != BuildingType.HOUSE)) continue;
            for (BlockPos site : building.shelterSites())
                for (BlockPos p : BlockPos.betweenClosed(site.offset(-3, -1, -3), site.offset(3, 3, 3))) {
                    if (level.isLoaded(p) && colony.isInside(p) && level.getBlockState(p).is(Blocks.CRAFTING_TABLE)) tables.add(p.immutable());
                }
        }
        return tables.stream().sorted(Comparator.comparingDouble(p -> p.distSqr(worker.blockPosition()))).toList();
    }

    /** Craft on a temporary copy and commit only if every result fits. */
    private boolean craftOne(String wanted) {
        var copy = new SimpleContainer(held().getContainerSize());
        for (int i = 0; i < held().getContainerSize(); i++) copy.setItem(i, held().getItem(i).copy());
        boolean[] overflow = {false};
        ConstructionCrafting.Stock stock = new ConstructionCrafting.Stock() {
            public int count(String key) { return snapshot(copy).getOrDefault(key, 0); }
            public boolean consume(String key, int amount) {
                if (count(key) < amount) return false;
                for (int i = 0; i < copy.getContainerSize() && amount > 0; i++) {
                    var stack = copy.getItem(i);
                    if (!plain(stack) || !id(stack).equals(key)) continue;
                    int take = Math.min(amount, stack.getCount());
                    copy.removeItem(i, take); amount -= take;
                }
                return amount == 0;
            }
            public void add(String key, int amount) {
                var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(key));
                if (item.isEmpty() || amount <= 0) { overflow[0] = true; return; }
                int remaining = amount;
                while (remaining > 0) {
                    var stack = new ItemStack(item.get(), Math.min(remaining, item.get().getDefaultMaxStackSize()));
                    int n = stack.getCount();
                    if (!copy.addItem(stack).isEmpty()) overflow[0] = true;
                    remaining -= n;
                }
            }
        };
        if (!ConstructionCrafting.take(stock, wanted)) return false;
        stock.add(wanted, 1); // The builder has crafted, but has not yet placed the block.
        if (overflow[0]) return false;
        for (int i = 0; i < held().getContainerSize(); i++) held().setItem(i, copy.getItem(i));
        return true;
    }

    boolean takePrepared(String wanted) {
        for (int i = 0; i < held().getContainerSize(); i++) {
            var stack = held().getItem(i);
            if (plain(stack) && id(stack).equals(wanted)) return !held().removeItem(i, 1).isEmpty();
        }
        return false;
    }

    void refund(String id) {
        var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id));
        if (item.isEmpty()) return;
        var remainder = held().addItem(new ItemStack(item.get()));
        if (!remainder.isEmpty()) worker.spawnAtLocation(remainder);
    }

    Result returnSurplus(ServerLevel level) {
        if (held().isEmpty()) return Result.READY;
        var warehouse = colony.getWarehouseManager();
        unreachable.entrySet().removeIf(e -> e.getValue() <= level.getGameTime());
        var positions = warehouse.courierStoragePositions(level).stream().filter(p -> !unreachable.containsKey(p))
                .filter(p -> {
                    var c = warehouse.courierContainer(level, p);
                    for (int i = 0; c != null && i < held().getContainerSize(); i++)
                        if (CourierJob.canInsert(c, held().getItem(i))) return true;
                    return false;
                }).sorted(Comparator.comparingDouble(p -> p.distSqr(worker.blockPosition()))).toList();
        for (var pos : positions) {
            if (worker.distanceToSqr(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5) > 9) {
                if (!worker.onGround() && !worker.isInLiquid() && !worker.isPassenger()) return Result.MOVING;
                var path = worker.getNavigation().createPath(pos, 1);
                if (path == null || !path.canReach()) { unreachable.put(pos, level.getGameTime()+200); continue; }
                worker.getNavigation().moveTo(path, .7); return Result.MOVING;
            }
            var c = warehouse.courierContainer(level, pos);
            if (c == null) continue;
            for (int i = 0; i < held().getContainerSize(); i++) CourierJob.insert(c, held().getItem(i));
            worker.swing(InteractionHand.MAIN_HAND); return Result.WORKING;
        }
        return Result.BLOCKED;
    }
}
