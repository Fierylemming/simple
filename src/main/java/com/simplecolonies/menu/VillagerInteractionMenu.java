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
        if (id >= BTN_SET_FARMER && id <= BTN_SET_BLACKSMITH) {
            if (!isLeader && colony.getReputation(player) < 30) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cNeed 30 rep to assign jobs"), true);
                return true;
            }
            JobType newJob = switch (id) {
                case BTN_SET_FARMER -> JobType.FARMER;
                case BTN_SET_LUMBERJACK -> JobType.LUMBERJACK;
                case BTN_SET_MINER -> JobType.MINER;
                case BTN_SET_BUILDER -> JobType.BUILDER;
                case BTN_SET_SOLDIER -> JobType.SOLDIER;
                case BTN_SET_BUTCHER -> JobType.BUTCHER;
                case BTN_SET_LEATHERWORKER -> JobType.LEATHERWORKER;
                case BTN_SET_BLACKSMITH -> JobType.BLACKSMITH;
                default -> JobType.UNEMPLOYED;
            };
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
            ColonyManager.getInstance().markDirty(colony);
            var ent = player.level().getEntity(entityId);
            if (ent instanceof com.simplecolonies.entity.ColonyVillager cv) {
                cv.setJob(newJob);
            }
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§aSet " + citizen.name + " to " + newJob.displayName + (colony.getResearch().isProfessionUnlocked(newJob)?"":" §c(locked)")), false);
            return true;
        }
        if (id == BTN_TRADE) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§eClose this and right-click without shift for vanilla trades"), false);
            return true;
        }
        if (id == BTN_COMMAND_FOLLOW) {
            if (!isLeader) { player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cOnly leader can command soldiers"), true); return true; }
            citizen.isSoldier = true;
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§6" + citizen.name + " will now follow you! Use Command Staff to rally."), false);
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
