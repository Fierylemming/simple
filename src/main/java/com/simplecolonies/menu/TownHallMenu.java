package com.simplecolonies.menu;

import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingUpgrade;
import com.simplecolonies.colony.CitizenData;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import com.simplecolonies.progression.ColonyUpgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Town Hall Menu — now with Research, Building Upgrade, and Create Schematic support.
 */
public class TownHallMenu extends AbstractContainerMenu {
    public final Colony colony;
    public final BlockPos pos;
    public final Player player;
    // Freeze button targets when the menu opens. Lists may change while it is open.
    private final List<Building> buildingTargets;
    public record DiplomacyTarget(int id, String culture) {}
    private final List<DiplomacyTarget> diplomacyTargets;
    private final java.util.function.IntFunction<Colony> diplomacyLookup;
    public List<DiplomacyTarget> getDiplomacyTargets() { return diplomacyTargets; }

    /** Capture the exact ordered targets once for both client and server menus. */
    public static List<DiplomacyTarget> snapshotDiplomacyTargets(Colony colony) {
        return ColonyManager.getInstance().getAll().stream().filter(c -> c.getId() != colony.getId())
                .sorted(java.util.Comparator.comparingDouble((Colony c) -> c.getCenter().distSqr(colony.getCenter()))
                        .thenComparingInt(Colony::getId))
                .limit(100).map(c -> new DiplomacyTarget(c.getId(), c.getCulture().displayName)).toList();
    }
    public static net.minecraft.nbt.CompoundTag snapshotWithTargets(Colony colony, List<DiplomacyTarget> targets) {
        var snapshot = colony.save();
        var list = new net.minecraft.nbt.ListTag();
        for (var target : targets) {
            var entry = new net.minecraft.nbt.CompoundTag();
            entry.putInt("id", target.id()); entry.putString("culture", target.culture()); list.add(entry);
        }
        snapshot.put("menuDiplomacyTargets", list);
        return snapshot;
    }

    // Button IDs — keep old + new
    public static final int BTN_TAB_OVERVIEW = 0;
    public static final int BTN_TAB_CITIZENS = 1;
    public static final int BTN_TAB_BUILDINGS = 2;
    public static final int BTN_TAB_WAREHOUSE = 3;
    public static final int BTN_TAB_DIPLOMACY = 4;
    public static final int BTN_TAB_RESEARCH = 5;

    public static final int BTN_ADD_BUILDING_HOUSE = 10;
    public static final int BTN_ADD_BUILDING_FARM = 11;
    public static final int BTN_ADD_BUILDING_LUMBER = 12;
    public static final int BTN_ADD_BUILDING_MINE = 13;
    public static final int BTN_ADD_BUILDING_BARRACKS = 14;
    public static final int BTN_LEVEL_UP = 20;
    public static final int BTN_RALLY_SOLDIERS = 21;
    public static final int BTN_CLAIM_LEADER = 40;
    public static final int BTN_DONATE_FOOD = 41;

    // New: research 100-119, building upgrade 200+index, create schematic 300
    public static final int BTN_RESEARCH_BASE = 100; // + ordinal
    public static final int BTN_UPGRADE_BUILDING_BASE = 200;
    public static final int BTN_CREATE_SCHEMATIC = 300;
    public static final int BTN_WAR_TARGET_BASE = 400, BTN_ALLY_TARGET_BASE = 500, BTN_PEACE_TARGET_BASE = 600;

    public TownHallMenu(int containerId, Inventory inv, Colony colony, BlockPos pos) {
        this(containerId, inv, colony, pos, snapshotDiplomacyTargets(colony));
    }
    public TownHallMenu(int containerId, Inventory inv, Colony colony, BlockPos pos, List<DiplomacyTarget> targets) {
        this(containerId, inv, colony, pos, targets, id -> ColonyManager.getInstance().getColony(id).orElse(null));
    }
    public TownHallMenu(int containerId, Inventory inv, Colony colony, BlockPos pos, List<DiplomacyTarget> targets, java.util.function.IntFunction<Colony> lookup) {
        super(ModMenuTypes.TOWN_HALL.get(), containerId);
        this.colony = colony;
        this.pos = pos;
        this.player = inv.player;
        this.buildingTargets = List.copyOf(colony.getBuildings());
        this.diplomacyTargets = List.copyOf(targets);
        this.diplomacyLookup = lookup;
    }

    public static TownHallMenu fromNetwork(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        int colonyId = buf.readInt();
        BlockPos pos = buf.readBlockPos();
        var snapshot = buf.readNbt();
        Colony c = snapshot != null ? Colony.load(snapshot) : new Colony(colonyId, com.simplecolonies.culture.CultureType.ROMAN, pos);
        var targets = new ArrayList<DiplomacyTarget>();
        if (snapshot != null) {
            var list = snapshot.getList("menuDiplomacyTargets", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(100, list.size()); i++) {
                var entry = list.getCompound(i);
                targets.add(new DiplomacyTarget(entry.getInt("id"), entry.getString("culture")));
            }
        }
        return new TownHallMenu(containerId, inv, c, pos, targets);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().isClientSide || !stillValid(player)) return false;
        boolean isLeader = colony.isLeader(player);
        var level = player.level() instanceof net.minecraft.server.level.ServerLevel sl ? sl : null;

        switch (id) {
            case BTN_CLAIM_LEADER -> {
                if (colony.getLeaderId() == null && colony.getReputation(player) >= com.simplecolonies.config.ColonyConfig.REPUTATION_NEEDED_TO_LEAD.get()) {
                    colony.setLeader(player);
                } else if (colony.getLeaderId() == null) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("Need " + (int)Math.ceil(com.simplecolonies.config.ColonyConfig.REPUTATION_NEEDED_TO_LEAD.get()) + " rep, you have " + colony.getReputation(player)), true);
                } else player.displayClientMessage(net.minecraft.network.chat.Component.literal("Already has leader"), true);
                return true;
            }
            case BTN_DONATE_FOOD -> {
                int donated = 0;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack st = player.getInventory().getItem(i);
                    if (st.is(net.minecraft.world.item.Items.BREAD) && st.getCount() > 0
                            && ItemStack.isSameItemSameComponents(st, net.minecraft.world.item.Items.BREAD.getDefaultInstance())) {
                        // Donation is a real transfer into nearby explicitly shared
                        // hall storage. If it is missing or full, leave the remainder
                        // in the player's inventory; never manufacture virtual stock.
                        int offered = Math.min(st.getCount(), 5 - donated);
                        int stored = level == null ? 0 : colony.getWarehouseManager()
                                .depositNearby(level, pos, "minecraft:bread", offered, 36);
                        st.shrink(stored);
                        donated += stored;
                        if (donated >= 5) break;
                    }
                }
                if (donated > 0) {
                    colony.addReputation(player, 2 * donated, "donated food");
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§aDonated " + donated + " bread! +" + (2*donated) + " rep"), false);
                } else player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cNeed plain bread and room in shared storage near the hall"), true);
                return true;
            }
            case BTN_ADD_BUILDING_HOUSE -> { if (isLeader) queueBuilding(colony, com.simplecolonies.building.BuildingType.HOUSE, player); else deny(player); return true; }
            case BTN_ADD_BUILDING_FARM -> { if (isLeader) queueBuilding(colony, com.simplecolonies.building.BuildingType.FARM, player); else deny(player); return true; }
            case BTN_ADD_BUILDING_LUMBER -> { if (isLeader) queueBuilding(colony, com.simplecolonies.building.BuildingType.LUMBERJACK_HUT, player); else deny(player); return true; }
            case BTN_ADD_BUILDING_MINE -> { if (isLeader) queueBuilding(colony, com.simplecolonies.building.BuildingType.MINE, player); else deny(player); return true; }
            case BTN_ADD_BUILDING_BARRACKS -> { if (isLeader) queueBuilding(colony, com.simplecolonies.building.BuildingType.BARRACKS, player); else deny(player); return true; }
            case BTN_LEVEL_UP -> {
                if (!isLeader) { deny(player); return true; }
                String blocker = colony.getLevelUpBlocker();
                if (blocker != null) player.displayClientMessage(net.minecraft.network.chat.Component.literal("§e" + blocker), false);
                else if (colony.tryLevelUp()) player.displayClientMessage(net.minecraft.network.chat.Component.literal("§aColony advanced to level " + colony.getLevel()), false);
                return true;
            }
            case BTN_RALLY_SOLDIERS -> {
                if (!isLeader) { deny(player); return true; }
                if (level == null) return true;
                int rallied = 0;
                for (var citizen : colony.getCitizens()) {
                    if (!citizen.job.isCombat) continue;
                    var entity = level.getEntity(citizen.uuid);
                    if (entity instanceof com.simplecolonies.entity.ColonyVillager villager
                            && villager.getColonyId() == colony.getId() && villager.commandFollow(player, true)) rallied++;
                }
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6Rallied " + rallied + " loaded defenders."), false);
                return true;
            }
            case BTN_CREATE_SCHEMATIC -> {
                if (!isLeader) { deny(player); return true; }
                var result = com.simplecolonies.integration.CreateSchematicIntegration.queue(colony, level, true);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        (result.task() == null ? "§e" : "§a") + result.message()), false);
                return true;
            }
        }
        if (id >= BTN_WAR_TARGET_BASE && id < BTN_PEACE_TARGET_BASE + 100) {
            if (!isLeader) { deny(player); return true; }
            if (level == null) return true;
            if (!com.simplecolonies.config.ColonyConfig.ENABLE_WARFARE.get()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("Warfare and diplomacy are disabled."), true);
                return true;
            }
            int action = (id - BTN_WAR_TARGET_BASE) / 100;
            int index = (id - BTN_WAR_TARGET_BASE) % 100;
            if (index >= diplomacyTargets.size()) return false;
            int targetId = diplomacyTargets.get(index).id();
            var other = diplomacyLookup.apply(targetId);
            if (other == null || other.getId() == colony.getId()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("That colony no longer exists; reopen the menu."), true);
                return true;
            }
            long day = Math.floorDiv(level.getDayTime(), 24000L);
            if (action == 0) {
                if (colony.getDiplomacy().getRelationAt(other.getId(), day) == com.simplecolonies.colony.Diplomacy.Relation.PEACE_TREATY) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("A peace treaty is still in force."), true);
                    return true;
                }
                colony.getDiplomacy().declareWar(colony, other, day);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("Declared war on colony #" + other.getId()), false);
            } else if (action == 1) {
                colony.getDiplomacy().proposeAlliance(colony, other);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("Allied with colony #" + other.getId()), false);
            } else {
                colony.getDiplomacy().makePeace(colony, other, day);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("Made peace with colony #" + other.getId()), false);
            }
            return true;
        }
        // Research buttons: 100 + ordinal
        if (id >= BTN_RESEARCH_BASE && id < BTN_RESEARCH_BASE + 50) {
            if (!isLeader) { deny(player); return true; }
            int ordinal = id - BTN_RESEARCH_BASE;
            ColonyUpgrade[] all = ColonyUpgrade.values();
            if (ordinal >= 0 && ordinal < all.length) {
                ColonyUpgrade up = all[ordinal];
                if (level == null) { player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cServer level required"), true); return true; }
                colony.getResearch().tryBuy(up, level, player);
                return true;
            }
        }
        // Building upgrade: 200 + index
        if (id >= BTN_UPGRADE_BUILDING_BASE && id < BTN_UPGRADE_BUILDING_BASE + 100) {
            int idx = id - BTN_UPGRADE_BUILDING_BASE;
            if (level == null) return true;
            if (!isLeader) { deny(player); return true; }
            var buildings = colony.getBuildings();
            if (idx >= 0 && idx < buildingTargets.size() && buildings.contains(buildingTargets.get(idx))) {
                BuildingUpgrade.tryUpgrade(colony, buildingTargets.get(idx), level, player);
            } else player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cBuilding list changed; reopen the town hall."), true);
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    private void deny(Player p) { p.displayClientMessage(net.minecraft.network.chat.Component.literal("§cOnly the colony leader can do that"), true); }

    private void queueBuilding(Colony colony, com.simplecolonies.building.BuildingType type, Player player) {
        var level = player.level();
        if (!(level instanceof net.minecraft.server.level.ServerLevel sl)) return;
        if (type != com.simplecolonies.building.BuildingType.HOUSE) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Only house construction is implemented yet; designate existing buildings with a marker."), false);
            return;
        }
        Building building = colony.queueHouse(sl);
        if (building == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cA building is unfinished or no clear, flat site was found."), false);
        } else {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§aQueued House at " + building.getPos().toShortString()), false);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override
    public boolean stillValid(Player player) {
        var level = player.level();
        return colony != null && level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                && level.hasChunkAt(pos) && colony.isInside(pos)
                && level.getBlockEntity(pos) instanceof com.simplecolonies.block.entity.TownHallBlockEntity hall
                && hall.getColonyId() == colony.getId()
                && player.distanceToSqr(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5) < 400;
    }
    public List<CitizenData> getCitizensSorted() {
        var list = new ArrayList<>(colony.getCitizens());
        list.sort(java.util.Comparator.comparing(c -> c.job.displayName));
        return list;
    }
}
