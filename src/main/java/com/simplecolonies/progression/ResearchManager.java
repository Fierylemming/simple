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

    private ResearchCosts.Stock stock(ServerLevel level) {
        return new ResearchCosts.Stock() {
            public int count(String id) { return colony.getWarehouseCount(level, id); }
            public boolean take(String id, int amount) { return colony.consumeStock(level, id, amount); }
            public void refund(String id, int amount) { colony.depositToWarehouse(id, amount); }
        };
    }

    public boolean canAfford(ColonyUpgrade upgrade, ServerLevel level) {
        return ResearchCosts.affordable(stock(level), upgrade.cost);
    }

    public boolean canUnlock(ColonyUpgrade upgrade) {
        // Preserve old saved unlocks but never charge for unsupported effects.
        if (upgrade == ColonyUpgrade.SCHEMATIC_CANNON) return false;
        if (isUnlocked(upgrade)) return false;
        for (var pre : upgrade.prerequisites) if (!isUnlocked(pre)) return false;
        return true;
    }

    /** Player buys upgrade — consumes mats from chests/warehouse. */
    public boolean tryBuy(ColonyUpgrade upgrade, ServerLevel level, Player player) {
        if (!canUnlock(upgrade)) {
            if (player != null) player.displayClientMessage(Component.literal("§cResearch unavailable or missing prerequisites for " + upgrade.displayName), true);
            return false;
        }
        if (!canAfford(upgrade, level)) {
            if (player != null) {
                player.displayClientMessage(Component.literal("§cNot enough resources in warehouse/chests for " + upgrade.displayName), true);
                for (var e : upgrade.cost.entrySet()) {
                    int have = colony.getWarehouseCount(level, e.getKey());
                    if (have < e.getValue()) player.displayClientMessage(Component.literal("  Need " + e.getValue() + "x " + e.getKey() + " (have " + have + ")"), false);
                }
            }
            return false;
        }
        if (!ResearchCosts.pay(stock(level), upgrade.cost)) {
            if (player != null) player.displayClientMessage(Component.literal("§cResources changed before research could be paid for"), true);
            return false;
        }
        unlocked.add(upgrade);
        colony.broadcast(Component.literal("§6Colony #" + colony.getId() + " unlocked " + upgrade.displayName + " — " + upgrade.description));
        if (player != null) player.displayClientMessage(Component.literal("§aUnlocked " + upgrade.displayName + "!"), false);
        com.simplecolonies.colony.ColonyManager.getInstance().markDirty(colony);
        return true;
    }

    /** Called daily in Colony.tick — auto-progress if village has surplus. */
    public void tickAutoProgress(ServerLevel level) {
        if (autoProgressCooldown-- > 0) return;
        // A completed library shortens the next automatic-research interval.
        // Research is daily simulation, so time skips still catch it up normally.
        boolean library = colony.getBuildings().stream().anyMatch(b ->
                b.getType() == com.simplecolonies.building.BuildingType.LIBRARY
                        && b.getState() == com.simplecolonies.building.BuildingState.COMPLETE);
        autoProgressCooldown = library ? 2 : 5;
        // Find cheapest unlockable upgrade we can afford (cheapest first = tier order)
        for (ColonyUpgrade up : ColonyUpgrade.values()) {
            if (canUnlock(up) && ResearchCosts.hasSurplus(stock(level), up.cost)) {
                // Keep a full second cost in stock; manual research can still
                // be bought with the exact cost if the leader chooses.
                if (!ResearchCosts.pay(stock(level), up.cost)) continue;
                unlocked.add(up);
                colony.broadcast(Component.literal("§eColony #" + colony.getId() + " self-unlocked " + up.displayName + " (had enough resources)"));
                com.simplecolonies.colony.ColonyManager.getInstance().markDirty(colony);
                break; // at most one per research interval
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
