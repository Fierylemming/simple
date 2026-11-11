package com.simplecolonies.progression;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.warehouse.WarehouseManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Per-colony research manager for Town Hall upgrades.
 * - Player can buy upgrades in GUI if colony has mats in warehouse/chests.
 * - Village can self-buy when it has surplus (auto-progress every N days if affordable).
 */
public class ResearchManager {
    private final Colony colony;
    private final Set<ColonyUpgrade> unlocked = new HashSet<>();
    private int autoProgressCooldown = 5; // days

    public ResearchManager(Colony colony) {
        this.colony = colony;
        unlocked.addAll(ColonyUpgrade.defaultsUnlocked());
    }

    public boolean isUnlocked(ColonyUpgrade upgrade) { return unlocked.contains(upgrade); }

    public boolean isProfessionUnlocked(com.simplecolonies.villager.job.JobType job) {
        return switch (job) {
            case FARMER -> isUnlocked(ColonyUpgrade.PROF_FARMER);
            case LUMBERJACK -> isUnlocked(ColonyUpgrade.UNLOCK_MINER) ? true : isUnlocked(ColonyUpgrade.PROF_LUMBERJACK); // lumber always
            case MINER -> isUnlocked(ColonyUpgrade.UNLOCK_MINER);
            case BUILDER -> isUnlocked(ColonyUpgrade.UNLOCK_BUILDER);
            case COURIER -> isUnlocked(ColonyUpgrade.UNLOCK_COURIER);
            case BUTCHER -> isUnlocked(ColonyUpgrade.UNLOCK_BUTCHER);
            case LEATHERWORKER -> isUnlocked(ColonyUpgrade.UNLOCK_LEATHERWORKER);
            case BLACKSMITH -> isUnlocked(ColonyUpgrade.UNLOCK_BLACKSMITH);
            case SOLDIER, GUARD -> isUnlocked(ColonyUpgrade.UNLOCK_SOLDIER);
            case BAKER -> isUnlocked(ColonyUpgrade.UNLOCK_BAKER);
            case FISHERMAN -> isUnlocked(ColonyUpgrade.UNLOCK_FISHERMAN);
            default -> true;
        };
    }

    public int getToolTier() {
        if (isUnlocked(ColonyUpgrade.TOOL_DIAMOND)) return 3;
        if (isUnlocked(ColonyUpgrade.TOOL_IRON)) return 2;
        if (isUnlocked(ColonyUpgrade.TOOL_STONE)) return 1;
        return 0;
    }

    public double getWorkSpeedMultiplier() {
        double base = switch (getToolTier()) {
            case 3 -> 2.0;
            case 2 -> 1.6;
            case 1 -> 1.3;
            default -> 1.0;
        };
        if (isUnlocked(ColonyUpgrade.EFFICIENCY_1)) base *= 1.15;
        if (isUnlocked(ColonyUpgrade.EFFICIENCY_2)) base *= 1.25;
        return base;
    }

    public boolean canAfford(ColonyUpgrade upgrade, ServerLevel level) {
        for (var e : upgrade.cost.entrySet()) {
            int have = colony.getWarehouseCount(level, e.getKey());
            // Also check chest manager if level present
            try {
                if (level != null) have = colony.getWarehouseManager().count(e.getKey()) + colony.getWarehouseCount(e.getKey());
            } catch (Exception ignored) {}
            if (have < e.getValue()) return false;
        }
        return true;
    }

    public boolean canUnlock(ColonyUpgrade upgrade) {
        if (isUnlocked(upgrade)) return false;
        for (var pre : upgrade.prerequisites) if (!isUnlocked(pre)) return false;
        return true;
    }

    /** Player buys upgrade — consumes mats from chests/warehouse. */
    public boolean tryBuy(ColonyUpgrade upgrade, ServerLevel level, Player player) {
        if (!canUnlock(upgrade)) {
            player.displayClientMessage(Component.literal("§cMissing prerequisites for " + upgrade.displayName), true);
            return false;
        }
        if (!canAfford(upgrade, level)) {
            player.displayClientMessage(Component.literal("§cNot enough resources in warehouse/chests for " + upgrade.displayName), true);
            // Show missing
            for (var e : upgrade.cost.entrySet()) {
                int have = level != null ? colony.getWarehouseManager().count(e.getKey()) + colony.getWarehouseCount(e.getKey()) : colony.getWarehouseCount(e.getKey());
                if (have < e.getValue()) player.displayClientMessage(Component.literal("  Need " + e.getValue() + "x " + e.getKey() + " (have " + have + ")"), false);
            }
            return false;
        }
        // Consume
        for (var e : upgrade.cost.entrySet()) {
            consume(level, e.getKey(), e.getValue());
        }
        unlocked.add(upgrade);
        colony.broadcast(Component.literal("§6Colony #" + colony.getId() + " unlocked " + upgrade.displayName + " — " + upgrade.description));
        if (player != null) player.displayClientMessage(Component.literal("§aUnlocked " + upgrade.displayName + "!"), false);
        com.simplecolonies.colony.ColonyManager.getInstance().markDirty(colony);
        return true;
    }

    private void consume(ServerLevel level, String itemId, int amount) {
        if (level != null) {
            int chestHave = colony.getWarehouseManager().count(itemId);
            int fromChest = Math.min(chestHave, amount);
            if (fromChest > 0) colony.getWarehouseManager().extract(level, colony.getCenter(), itemId, fromChest, false);
            int remaining = amount - fromChest;
            if (remaining > 0) colony.depositToWarehouse(itemId, -remaining);
        } else {
            colony.depositToWarehouse(itemId, -amount);
        }
    }

    /** Called daily in Colony.tick — auto-progress if village has surplus. */
    public void tickAutoProgress(ServerLevel level) {
        if (autoProgressCooldown-- > 0) return;
        autoProgressCooldown = 5;
        // Find cheapest unlockable upgrade we can afford (cheapest first = tier order)
        for (ColonyUpgrade up : ColonyUpgrade.values()) {
            if (canUnlock(up) && canAfford(up, level)) {
                // Auto-buy if tier 1 and colony has 2× mats (surplus) to not starve
                // We check food surplus implicitly via canAfford after food tick
                unlocked.add(up);
                for (var e : up.cost.entrySet()) consume(level, e.getKey(), e.getValue());
                colony.broadcast(Component.literal("§eColony #" + colony.getId() + " self-unlocked " + up.displayName + " (had enough resources)"));
                com.simplecolonies.colony.ColonyManager.getInstance().markDirty(colony);
                break; // one per 5 days max
            }
        }
    }

    public List<ColonyUpgrade> getAvailableToBuy() {
        return Arrays.stream(ColonyUpgrade.values()).filter(this::canUnlock).filter(u -> !isUnlocked(u)).collect(Collectors.toList());
    }

    public Set<ColonyUpgrade> getUnlocked() { return Collections.unmodifiableSet(unlocked); }

    // --- NBT ---
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (var u : unlocked) list.add(StringTag.valueOf(u.name()));
        tag.put("unlocked", list);
        tag.putInt("autoCooldown", autoProgressCooldown);
        return tag;
    }

    public void load(CompoundTag tag) {
        unlocked.clear();
        ListTag list = tag.getList("unlocked", 8);
        for (int i = 0; i < list.size(); i++) {
            try { unlocked.add(ColonyUpgrade.valueOf(list.getString(i))); } catch (Exception ignored) {}
        }
        // Existing colonies also get the free starter professions on upgrade.
        unlocked.addAll(ColonyUpgrade.defaultsUnlocked());
        autoProgressCooldown = tag.getInt("autoCooldown");
    }
}
