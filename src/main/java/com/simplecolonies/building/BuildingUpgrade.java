package com.simplecolonies.building;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles building level-ups: each building has 1 villager working initially,
 * upgrades add +1 job block / worker slot and rebuild bigger with better materials.
 *
 * You described: "each building has 1 villager working and so the buildings can be upgraded by the builder
 * to then put more job blocks inside and after each upgrade the buildings are bigger and contain more job blocks"
 *
 * Size & capacity:
 *  Lv1: 1 worker, small 5×5, +4 beds if House
 *  Lv2: 2 workers, 7×7, +8 beds
 *  Lv3: 3 workers, 9×9, +12
 *  Lv4: 4 workers, 11×11
 *  Lv5: 5 workers, 13×13 (max)
 */
public class BuildingUpgrade {

    public static int maxLevel(BuildingType type) { return 5; }

    public static int workerSlotsForLevel(int level) {
        return Math.min(level, 5); // 1 per level
    }

    public static int sizeForLevel(int level) {
        return 5 + (level - 1) * 2; // 5,7,9,11,13
    }

    public static Map<String, Integer> costForUpgrade(Building building) {
        int nextLevel = building.getLevel() + 1;
        if (nextLevel > 5) return Map.of();
        return costForLevel(building.getType(), building.getCulture(), nextLevel);
    }

    public static Map<String, Integer> costForUpgradePreview(Building building, int targetLevel) {
        return costForLevel(building.getType(), building.getCulture(), targetLevel);
    }

    public static Map<String, Integer> costForLevel(BuildingType type, CultureType culture, int level) {
        if (level < 2 || level > 5) return Map.of();
        Map<String, Integer> base = type.baseCost;
        Map<String, Integer> cost = new HashMap<>();
        double mult = 1.0 + (level - 1) * 0.6;
        for (var e : base.entrySet()) {
            cost.put(e.getKey(), (int)Math.ceil(e.getValue() * mult));
        }
        var tier = com.simplecolonies.building.MaterialProgression.get(culture, Math.min(level, 5));
        Block primary = tier.primary().get(0);
        // Use item id for counting
        String primaryItemId = "minecraft:" + primary.asItem().toString().replace("minecraft:", "");
        try {
            primaryItemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(primary.asItem()).toString();
        } catch (Exception ignored) {}
        int blocksNeeded = sizeForLevel(level) * 4 * 3;
        cost.merge(primaryItemId, blocksNeeded / 4, Integer::sum);
        if (cost.isEmpty()) cost.put("minecraft:cobblestone", 16 * level);
        return cost;
    }

    public static boolean canUpgrade(Colony colony, Building building, net.minecraft.server.level.ServerLevel level) {
        if (!building.isPlayerPlaced || building.getType() != BuildingType.HOUSE) return false;
        // Other marker upgrades currently change no functioning capacity. Never
        // charge for an upgrade that neither builds nor does useful work.
        if (building.getLevel() >= maxLevel(building.getType())) return false;
        if (building.getState() != BuildingState.COMPLETE) return false; // only upgrade finished buildings
        Map<String, Integer> cost = costForUpgrade(building);
        for (var e : cost.entrySet()) {
            if (colony.getWarehouseCount(level, e.getKey()) < e.getValue()) return false;
        }
        return true;
    }

    public static boolean tryUpgrade(Colony colony, Building building, net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.player.Player player) {
        if (!canUpgrade(colony, building, level)) {
            if (player != null) player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cUpgrade unavailable or insufficient materials for " + building.getType().displayName + " to Lv" + (building.getLevel()+1)), true);
            return false;
        }
        // The marker upgrade changes capacity only: it does not build a larger
        // shell or create beds/workstations. Consume live stock transactionally.
        Map<String, Integer> paid = new HashMap<>();
        for (var e : costForUpgrade(building).entrySet()) {
            if (!colony.consumeStock(level, e.getKey(), e.getValue())) {
                paid.forEach(colony::depositToWarehouse);
                if (player != null) player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cUpgrade materials changed; no upgrade was applied."), true);
                return false;
            }
            paid.put(e.getKey(), e.getValue());
        }
        building.upgradeToNextLevel();
        if (player != null) player.displayClientMessage(net.minecraft.network.chat.Component.literal("§aUpgraded marker capacity for " + building.getType().displayName + " → Lv" + building.getLevel()), false);
        colony.broadcast(net.minecraft.network.chat.Component.literal("§6Updated marker capacity for " + building.getType().displayName + " at " + building.getPos().toShortString() + " to level " + building.getLevel()));
        com.simplecolonies.colony.ColonyManager.getInstance().markDirty(colony);
        return true;
    }
}
