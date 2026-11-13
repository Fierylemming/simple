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
    public static final int BTN_DECLARE_WAR_NEXT = 30;
    public static final int BTN_PROPOSE_ALLIANCE = 31;
    public static final int BTN_CLAIM_LEADER = 40;
    public static final int BTN_DONATE_FOOD = 41;

    // New: research 100-119, building upgrade 200+index, create schematic 300
    public static final int BTN_RESEARCH_BASE = 100; // + ordinal
    public static final int BTN_UPGRADE_BUILDING_BASE = 200;
    public static final int BTN_CREATE_SCHEMATIC = 300;

    public TownHallMenu(int containerId, Inventory inv, Colony colony, BlockPos pos) {
        super(ModMenuTypes.TOWN_HALL.get(), containerId);
        this.colony = colony;
        this.pos = pos;
        this.player = inv.player;
    }

    public static TownHallMenu fromNetwork(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        int colonyId = buf.readInt();
        BlockPos pos = buf.readBlockPos();
        var snapshot = buf.readNbt();
        Colony c = snapshot != null ? Colony.load(snapshot) : new Colony(colonyId, com.simplecolonies.culture.CultureType.ROMAN, pos);
        return new TownHallMenu(containerId, inv, c, pos);
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
                        int take = Math.min(st.getCount(), 5 - donated);
                        st.shrink(take);
                        colony.depositToWarehouse("minecraft:bread", take);
                        donated += take;
                        if (donated >= 5) break;
                    }
                }
                if (donated > 0) {
                    colony.addReputation(player, 2 * donated, "donated food");
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§aDonated " + donated + " bread! +" + (2*donated) + " rep"), false);
                } else player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cNo bread in inventory"), true);
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
            case BTN_DECLARE_WAR_NEXT -> {
                if (!isLeader) { deny(player); return true; }
                var nearby = ColonyManager.getInstance().getAll().stream().filter(c -> c.getId() != colony.getId()).findFirst();
                if (nearby.isPresent()) { colony.getDiplomacy().declareWar(colony, nearby.get()); player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cDeclared war on #" + nearby.get().getId()), false); }
                else player.displayClientMessage(net.minecraft.network.chat.Component.literal("No other colonies nearby"), true);
                return true;
            }
            case BTN_PROPOSE_ALLIANCE -> {
                if (!isLeader) { deny(player); return true; }
                var nearby = ColonyManager.getInstance().getAll().stream().filter(c -> c.getId() != colony.getId()).findFirst();
                if (nearby.isPresent()) { colony.getDiplomacy().proposeAlliance(colony, nearby.get()); player.displayClientMessage(net.minecraft.network.chat.Component.literal("§aAllied with #" + nearby.get().getId()), false); }
                else player.displayClientMessage(net.minecraft.network.chat.Component.literal("No other colonies nearby"), true);
                return true;
            }
            case BTN_CREATE_SCHEMATIC -> {
                if (!isLeader) { deny(player); return true; }
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§eCreate schematic construction is not implemented; no items were consumed."), true);
                return true;
            }
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
            if (idx >= 0 && idx < buildings.size()) {
                Building b = buildings.get(idx);
                BuildingUpgrade.tryUpgrade(colony, b, level, player);
            }
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
