package com.simplecolonies.item;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import com.simplecolonies.entity.ColonyVillager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Leader can toggle an individual defender or rally loaded defenders in their colony. */
public class CommandStaffItem extends Item {
    public CommandStaffItem(Properties props) { super(props); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) return InteractionResultHolder.success(player.getItemInHand(hand));
        if (player.isShiftKeyDown() && level instanceof ServerLevel serverLevel) {
            Colony colony = ColonyManager.getInstance().getColonyAt(player.blockPosition()).orElse(null);
            if (colony == null || !colony.isLeader(player)) {
                player.displayClientMessage(Component.literal("§cOnly a colony leader can rally defenders here."), true);
            } else {
                int rallied = 0;
                for (var citizen : colony.getCitizens()) {
                    if (!citizen.job.isCombat) continue;
                    var entity = serverLevel.getEntity(citizen.uuid);
                    if (entity instanceof ColonyVillager villager && villager.getColonyId() == colony.getId()
                            && villager.commandFollow(player, true)) rallied++;
                }
                player.displayClientMessage(Component.literal("§6Rallied " + rallied + " loaded defenders. Unloaded citizens were not summoned."), false);
            }
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (!(target instanceof ColonyVillager villager)) return InteractionResult.PASS;
        boolean follow = !villager.isFollowingCommander();
        if (!villager.commandFollow(player, follow)) {
            player.displayClientMessage(Component.literal("§cOnly the colony leader can command soldiers or guards."), true);
        } else {
            player.displayClientMessage(Component.literal("§6" + villager.getName().getString()
                    + (follow ? " will follow you." : " will stop following.")), true);
        }
        return InteractionResult.SUCCESS;
    }
}
