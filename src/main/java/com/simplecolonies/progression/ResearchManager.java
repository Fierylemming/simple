package com.simplecolonies.progression;

import com.simplecolonies.colony.Colony;
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
 * - Players queue upgrades using loaded shared storage.
 * - Builders physically carry the inputs to a hall or library.
 * - Automatic research queues when a physical reserve exists.
 */
public class ResearchManager {
    private final Colony colony;
    private final Set<ColonyUpgrade> unlocked = new HashSet<>();
    private int autoProgressCooldown = 5; // days
    private ColonyUpgrade pending;
    private UUID assignedWorker;
    private boolean pendingAutomatic;
    private int automaticSlotsRemaining;

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

    /** Only loaded, explicitly shared physical containers can supply new research.
     * Legacy abstract stock is retained, but cannot be materialized by research. */
    private ResearchCosts.Stock stock(ServerLevel level) {
        return new ResearchCosts.Stock() {
            public int count(String id) {
                if (level == null) return 0;
                colony.getWarehouseManager().scan(level);
                return colony.getWarehouseManager().count(id);
            }
            public boolean take(String id, int amount) { throw new UnsupportedOperationException(); }
            public void refund(String id, int amount) { throw new UnsupportedOperationException(); }
        };
    }

    public boolean canAfford(ColonyUpgrade upgrade, ServerLevel level) {
        return ResearchCosts.affordable(stock(level), upgrade.cost);
    }

    public boolean canUnlock(ColonyUpgrade upgrade) {
        if (upgrade == ColonyUpgrade.SCHEMATIC_CANNON || isUnlocked(upgrade)) return false;
        for (var pre : upgrade.prerequisites) if (!isUnlocked(pre)) return false;
        return true;
    }

    public ColonyUpgrade getPending() { return pending; }
    public UUID getAssignedWorker() { return assignedWorker; }
    public boolean isPendingAutomatic() { return pendingAutomatic; }

    /** Queue, rather than remotely withdrawing from storage. One builder owns the
     * order until completion, reassignment, death or explicit cancellation. */
    public boolean tryBuy(ColonyUpgrade upgrade, ServerLevel level, Player player) {
        if (pending != null || !canUnlock(upgrade)) {
            if (player != null) player.displayClientMessage(Component.literal("§cAnother topic is queued or this research is unavailable"), true);
            return false;
        }
        if (!canAfford(upgrade, level)) {
            if (player != null) player.displayClientMessage(Component.literal("§cPut the full cost in loaded shared storage first"), true);
            return false;
        }
        pending = upgrade; assignedWorker = null; pendingAutomatic = false; automaticSlotsRemaining = 0;
        com.simplecolonies.colony.ColonyManager.getInstance().markDirty(colony);
        if (player != null) player.displayClientMessage(Component.literal("§aQueued " + upgrade.displayName + "; a builder must carry the materials to a hall or library."), false);
        return true;
    }

    public boolean cancelPending() {
        if (pending == null) return false;
        pending = null; assignedWorker = null; pendingAutomatic = false; automaticSlotsRemaining = 0;
        com.simplecolonies.colony.ColonyManager.getInstance().markDirty(colony);
        return true;
    }

    public boolean claimWorker(UUID worker) {
        if (pending == null || (assignedWorker != null && !assignedWorker.equals(worker))) return false;
        if (assignedWorker == null) {
            assignedWorker = worker;
            com.simplecolonies.colony.ColonyManager.getInstance().markDirty(colony);
        }
        return true;
    }

    public void releaseWorker(UUID worker) {
        if (worker != null && worker.equals(assignedWorker)) {
            assignedWorker = null;
            com.simplecolonies.colony.ColonyManager.getInstance().markDirty(colony);
        }
    }

    public boolean canComplete(UUID worker, ColonyUpgrade topic) {
        return topic != null && topic == pending && worker.equals(assignedWorker) && canUnlock(topic);
    }

    /** Called only after the assigned builder has delivered and consumed the full
     * physical cost in the same server tick. */
    public boolean complete(UUID worker, ColonyUpgrade topic, ServerLevel level) {
        if (!canComplete(worker, topic)) return false;
        boolean wasAutomatic = pendingAutomatic;
        pending = null; assignedWorker = null; pendingAutomatic = false;
        unlocked.add(topic);
        colony.broadcast(Component.literal("§6Colony #" + colony.getId() + " unlocked " + topic.displayName + " — " + topic.description));
        if (wasAutomatic && automaticSlotsRemaining > 0) queueNextAutomatic(level);
        else automaticSlotsRemaining = 0;
        com.simplecolonies.colony.ColonyManager.getInstance().markDirty(colony);
        return true;
    }

    /** A completed library affects the interval and number of sequential orders. */
    public void tickAutoProgress(ServerLevel level) {
        if (pending != null || autoProgressCooldown-- > 0) return;
        int libraryLevel = colony.getBuildings().stream()
                .filter(b -> b.getType() == com.simplecolonies.building.BuildingType.LIBRARY && b.isOperational())
                .mapToInt(com.simplecolonies.building.Building::getLevel).max().orElse(0);
        autoProgressCooldown = libraryLevel == 0 ? 5 : Math.max(0, 3 - libraryLevel);
        automaticSlotsRemaining = Math.max(1, libraryLevel - 2);
        queueNextAutomatic(level);
        com.simplecolonies.colony.ColonyManager.getInstance().markDirty(colony);
    }

    private void queueNextAutomatic(ServerLevel level) {
        for (ColonyUpgrade up : ColonyUpgrade.values()) {
            if (canUnlock(up) && ResearchCosts.hasSurplus(stock(level), up.cost)) {
                pending = up; assignedWorker = null; pendingAutomatic = true; automaticSlotsRemaining--;
                colony.broadcast(Component.literal("§eColony #" + colony.getId() + " queued research: " + up.displayName));
                return;
            }
        }
        automaticSlotsRemaining = 0;
    }

    /** Recheck the auto-research reserve after pickup, not just when queued. */
    public boolean hasAutomaticReserve(ServerLevel level, ColonyUpgrade topic) {
        return !pendingAutomatic || ResearchCosts.affordable(stock(level), topic.cost);
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
        if (pending != null) {
            tag.putString("pending", pending.name());
            tag.putBoolean("pendingAutomatic", pendingAutomatic);
            tag.putInt("automaticSlotsRemaining", automaticSlotsRemaining);
            if (assignedWorker != null) tag.putUUID("assignedWorker", assignedWorker);
        }
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
        pending = null; assignedWorker = null; pendingAutomatic = false; automaticSlotsRemaining = 0;
        if (tag.contains("pending")) {
            try {
                var saved = ColonyUpgrade.valueOf(tag.getString("pending"));
                if (canUnlock(saved)) {
                    pending = saved;
                    pendingAutomatic = tag.getBoolean("pendingAutomatic");
                    automaticSlotsRemaining = pendingAutomatic ? Math.clamp(tag.getInt("automaticSlotsRemaining"), 0, 3) : 0;
                    if (tag.hasUUID("assignedWorker")) assignedWorker = tag.getUUID("assignedWorker");
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
