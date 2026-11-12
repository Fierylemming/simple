package com.simplecolonies.menu;

import com.simplecolonies.colony.CitizenData;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import com.simplecolonies.villager.job.JobType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for shift-right-clicking a colony villager.
 */
public class VillagerInteractionMenu extends AbstractContainerMenu {
    public final int entityId;
    public final Colony colony;
    public final CitizenData citizen;
    public final Player player;

    public static final int BTN_TRADE = 100;
    public static final int BTN_SET_FARMER = 101;
    public static final int BTN_SET_LUMBERJACK = 102;
    public static final int BTN_SET_MINER = 103;
    public static final int BTN_SET_BUILDER = 104;
    public static final int BTN_SET_SOLDIER = 105;
    public static final int BTN_SET_BUTCHER = 106;
    public static final int BTN_SET_LEATHERWORKER = 107;
    public static final int BTN_SET_BLACKSMITH = 108;
    public static final int BTN_COMMAND_FOLLOW = 110;
    public static final int BTN_LEVEL_UP_JOB = 111;
    public static final int BTN_SET_GUARD = 112;
    public static final int BTN_SET_BAKER = 113;
    public static final int BTN_SET_FISHERMAN = 114;
    public static final int BTN_SET_COURIER = 115;
    public static final int BTN_SET_UNEMPLOYED = 116;

    /** Explicit mapping keeps assignment buttons separate from commands. */
    public static JobType assignmentForButton(int id) {
        return switch (id) {
            case BTN_SET_FARMER -> JobType.FARMER;
            case BTN_SET_LUMBERJACK -> JobType.LUMBERJACK;
            case BTN_SET_MINER -> JobType.MINER;
            case BTN_SET_BUILDER -> JobType.BUILDER;
            case BTN_SET_SOLDIER -> JobType.SOLDIER;
            case BTN_SET_BUTCHER -> JobType.BUTCHER;
            case BTN_SET_LEATHERWORKER -> JobType.LEATHERWORKER;
            case BTN_SET_BLACKSMITH -> JobType.BLACKSMITH;
            case BTN_SET_GUARD -> JobType.GUARD;
            case BTN_SET_BAKER -> JobType.BAKER;
            case BTN_SET_FISHERMAN -> JobType.FISHERMAN;
            case BTN_SET_COURIER -> JobType.COURIER;
            case BTN_SET_UNEMPLOYED -> JobType.UNEMPLOYED;
            default -> null;
        };
    }

    public VillagerInteractionMenu(int containerId, Inventory inv, int entityId, Colony colony, CitizenData citizen) {
        super(ModMenuTypes.VILLAGER.get(), containerId);
        this.entityId = entityId;
        this.colony = colony;
        this.citizen = citizen;
        this.player = inv.player;
    }

    public static VillagerInteractionMenu fromNetwork(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        int entityId = buf.readInt();
        int colonyId = buf.readInt();
        var colonyData = buf.readNbt();
        var citizenData = buf.readNbt();
        Colony c = colonyData != null ? Colony.load(colonyData) : null;
        CitizenData cd = citizenData != null ? CitizenData.load(citizenData) : new CitizenData(java.util.UUID.randomUUID(), "Villager", JobType.UNEMPLOYED);
        return new VillagerInteractionMenu(containerId, inv, entityId, c, cd);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (colony == null || citizen == null) return false;
        boolean isLeader = colony.isLeader(player);
        JobType newJob = assignmentForButton(id);
        if (newJob != null) {
            int requiredRep = newJob.isCombat ? 50 : 30;
            if (!isLeader && colony.getReputation(player) < requiredRep) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cNeed " + requiredRep + " rep to assign this job"), true);
                return true;
            }
            var target = player.level().getEntity(entityId);
            if (!(target instanceof com.simplecolonies.entity.ColonyVillager cv) || !cv.isAlive()
                    || !cv.getUUID().equals(citizen.uuid) || cv.getColonyId() != colony.getId() || !stillValid(player)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cVillager is no longer available"), true);
                return true;
            }
            // Progression gate: must have researched profession (farmer/lumberjack always free)
            if (!colony.getResearch().isProfessionUnlocked(newJob)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cProfession locked — unlock in Town Hall Research tab (need mats + prereqs)"), true);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§7Current unlocked: " + colony.getResearch().getUnlocked().stream().filter(u->u.isProfession()).map(u->u.displayName).reduce((a,b)->a+", "+b).orElse("none")), false);
                return true;
            }
            // Tool tier check: warn if tool tier too low for job
            int tier = colony.getResearch().getToolTier();
            if ((newJob == JobType.MINER || newJob == JobType.BUILDER) && tier < 1) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§eWarning: Stone Tools not yet researched — job will be 30% slower"), false);
            }
            citizen.job = newJob;
            citizen.isSoldier = newJob.isCombat;
            ColonyManager.getInstance().markDirty(colony);
            cv.setJob(newJob);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§aSet " + citizen.name + " to " + newJob.displayName), false);
            // The menu carries a snapshot; reopen it to display the new profession.
            player.closeContainer();
            return true;
        }
        if (id == BTN_TRADE) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§eClose this and right-click without shift for vanilla trades"), false);
            return true;
        }
        if (id == BTN_COMMAND_FOLLOW) {
            if (!isLeader) { player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cOnly leader can command soldiers"), true); return true; }
            var ent = player.level().getEntity(entityId);
            if (!(ent instanceof com.simplecolonies.entity.ColonyVillager cv) || !cv.getUUID().equals(citizen.uuid)
                    || cv.getColonyId() != colony.getId() || !stillValid(player)) return false;
            boolean follow = !cv.isFollowingCommander();
            if (!cv.commandFollow(player, follow)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cOnly soldiers or guards can follow"), true);
            } else {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6" + citizen.name
                        + (follow ? " will follow you." : " will stop following.")), false);
                player.closeContainer();
            }
            return true;
        }
        if (id == BTN_LEVEL_UP_JOB) {
            if (citizen.level < 5) {
                citizen.level++;
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§aLeveled up " + citizen.name + " to " + citizen.level), false);
                ColonyManager.getInstance().markDirty(colony);
            }
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        var ent = player.level().getEntity(entityId);
        return ent != null && ent.isAlive() && player.distanceTo(ent) < 8;
    }
}
