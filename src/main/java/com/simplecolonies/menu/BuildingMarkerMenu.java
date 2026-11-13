package com.simplecolonies.menu;

import com.simplecolonies.block.entity.BuildingMarkerBlockEntity;
import com.simplecolonies.building.Building;
import com.simplecolonies.building.BuildingState;
import com.simplecolonies.building.BuildingType;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for Building Marker — lets leader pick what a player-built structure counts as.
 * This is the "town setter you right click then select what building it is" you described.
 *
 * All building types available; picking one registers a colony Building at marker pos.
 * If the type is Warehouse, it also sets colony's warehouse hub for couriers.
 */
public class BuildingMarkerMenu extends AbstractContainerMenu {
    public final Colony colony;
    public final BlockPos markerPos;
    public final BuildingType currentType;

    public static final int BTN_SET_HOUSE = 10;
    public static final int BTN_SET_FARM = 11;
    public static final int BTN_SET_LUMBER = 12;
    public static final int BTN_SET_MINE = 13;
    public static final int BTN_SET_WAREHOUSE = 14;
    public static final int BTN_SET_BARRACKS = 15;
    public static final int BTN_SET_BLACKSMITH = 16;
    public static final int BTN_SET_BAKERY = 17;
    public static final int BTN_SET_BUTCHER = 18;
    public static final int BTN_SET_LEATHER = 19;
    public static final int BTN_SET_TEMPLE = 20;
    public static final int BTN_SET_MARKET = 21;
    public static final int BTN_SET_PASTURE = 22;
    public static final int BTN_CLEAR = 30;

    public BuildingMarkerMenu(int containerId, Inventory inv, Colony colony, BlockPos markerPos, BuildingType current) {
        super(ModMenuTypes.BUILDING_MARKER.get(), containerId);
        this.colony = colony;
        this.markerPos = markerPos;
        this.currentType = current;
    }

    public static BuildingMarkerMenu fromNetwork(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int colonyId = buf.readInt();
        String typeStr = buf.readUtf();
        var snapshot = buf.readNbt();
        Colony c = snapshot != null ? Colony.load(snapshot) : new Colony(colonyId, com.simplecolonies.culture.CultureType.ROMAN, pos);
        BuildingType t = typeStr.equals("NONE") ? null : BuildingType.valueOf(typeStr);
        return new BuildingMarkerMenu(containerId, inv, c, pos, t);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (colony == null || colony.getId() == -1 || player.level().isClientSide || !stillValid(player)
                || !colony.isInside(markerPos) || !(player.level().getBlockEntity(markerPos) instanceof BuildingMarkerBlockEntity)) return false;
        if (!colony.isLeader(player) && colony.getReputation(player) < 20) {
            player.displayClientMessage(Component.literal("§cNeed 20 rep to designate buildings"), true);
            return true;
        }
        BuildingType chosen = switch (id) {
            case BTN_SET_HOUSE -> BuildingType.HOUSE;
            case BTN_SET_FARM -> BuildingType.FARM;
            case BTN_SET_LUMBER -> BuildingType.LUMBERJACK_HUT;
            case BTN_SET_MINE -> BuildingType.MINE;
            case BTN_SET_WAREHOUSE -> BuildingType.WAREHOUSE;
            case BTN_SET_BARRACKS -> BuildingType.BARRACKS;
            case BTN_SET_BLACKSMITH -> BuildingType.BLACKSMITH;
            case BTN_SET_BAKERY -> BuildingType.BAKERY;
            case BTN_SET_BUTCHER -> BuildingType.BUTCHER_HUT;
            case BTN_SET_LEATHER -> BuildingType.LEATHER_SHOP;
            case BTN_SET_TEMPLE -> BuildingType.TEMPLE;
            case BTN_SET_MARKET -> BuildingType.MARKET;
            case BTN_SET_PASTURE -> BuildingType.PASTURE;
            case BTN_CLEAR -> null;
            default -> null;
        };
        if (chosen == null && id != BTN_CLEAR) return false;
        var marker = (BuildingMarkerBlockEntity) player.level().getBlockEntity(markerPos);
        boolean linked = markerPos.equals(marker.getLinkedBuildingPos());
        var atMarker = colony.getBuildings().stream().filter(b -> b.getPos().equals(markerPos)).toList();
        if (linked && (!atMarker.isEmpty() && (atMarker.size() != 1 || !atMarker.getFirst().isPlayerPlaced
                || marker.getBuildingType() != atMarker.getFirst().getType()))) {
            player.displayClientMessage(Component.literal("§cMarker no longer matches its building record."), true);
            return true;
        }
        if (!colony.updateMarkerBuilding(markerPos, linked, chosen)) {
            player.displayClientMessage(Component.literal("§cThat position is already used by another building."), true);
            return true;
        }
        marker.setBuildingType(chosen);
        marker.setLinkedBuildingPos(chosen == null ? null : markerPos);
        player.level().sendBlockUpdated(markerPos, player.level().getBlockState(markerPos), player.level().getBlockState(markerPos), 3);
        player.displayClientMessage(Component.literal(chosen == null ? "§7Building designation cleared."
                : "§aDesignated " + chosen.displayName + "."), false);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return player.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                && player.distanceToSqr(markerPos.getX()+0.5, markerPos.getY()+0.5, markerPos.getZ()+0.5) < 256;
    }
}
