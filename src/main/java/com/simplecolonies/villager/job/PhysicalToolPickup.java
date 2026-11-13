package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.entity.ColonyVillager;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Fetch an actual tool, preserving damage and components, without remote stock. */
final class PhysicalToolPickup {
    enum Result { EQUIPPED, MOVING, MISSING, BLOCKED }
    private final ColonyVillager worker;
    private final Colony colony;
    private final Map<BlockPos, Long> unreachable = new HashMap<>();
    PhysicalToolPickup(ColonyVillager worker, Colony colony) { this.worker = worker; this.colony = colony; }

    Result equip(ServerLevel level, Item[] tools) {
        var inventory = worker.getInventory();
        for (var item : tools) for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item)) {
                worker.setItemSlot(EquipmentSlot.MAINHAND, inventory.removeItem(i, 1));
                return Result.EQUIPPED;
            }
        }
        var warehouse = colony.getWarehouseManager();
        var positions = warehouse.courierStoragePositions(level);
        unreachable.entrySet().removeIf(e -> e.getValue() <= level.getGameTime());
        boolean found = false;
        for (Item item : tools) {
            var candidates = positions.stream().filter(p -> {
                var c = warehouse.courierContainer(level, p);
                for (int i = 0; c != null && i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) return true;
                return false;
            }).sorted(Comparator.comparingDouble(p -> p.distSqr(worker.blockPosition()))).toList();
            if (!candidates.isEmpty()) found = true;
            for (var pos : candidates) {
                if (unreachable.containsKey(pos)) continue;
                if (worker.distanceToSqr(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5) > 9) {
                    if (!worker.onGround() && !worker.isInLiquid() && !worker.isPassenger()) return Result.MOVING;
                    var path = worker.getNavigation().createPath(pos, 1);
                    if (path == null || !path.canReach()) { unreachable.put(pos, level.getGameTime()+200); continue; }
                    worker.getNavigation().moveTo(path, .7);
                    return Result.MOVING;
                }
                var c = warehouse.courierContainer(level, pos);
                for (int i = 0; c != null && i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) {
                    ItemStack tool = c.removeItem(i, 1);
                    if (tool.isEmpty()) continue;
                    worker.setItemSlot(EquipmentSlot.MAINHAND, tool);
                    c.setChanged(); worker.swing(InteractionHand.MAIN_HAND);
                    return Result.EQUIPPED;
                }
            }
        }
        return found ? Result.BLOCKED : Result.MISSING;
    }
}
