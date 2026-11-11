package com.simplecolonies.item;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Command Staff: Leader right-clicks soldier villagers to make them follow into battle.
 * Shift-right-click to call all soldiers in colony to you.
 * Right-click enemy colony Town Hall to declare war (if leader).
 */
public class CommandStaffItem extends Item {
    public CommandStaffItem(Properties props) { super(props); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) return InteractionResultHolder.success(player.getItemInHand(hand));
        if (player.isShiftKeyDown()) {
            Colony colony = ColonyManager.getInstance().getColonyAt(player.blockPosition()).orElse(null);
            if (colony != null && colony.isLeader(player)) {
                player.displayClientMessage(Component.literal("§6Rallying soldiers of colony #" + colony.getId() + " to your position!"), true);
                // Real: iterate soldier entities in colony radius and set commander
            } else if (colony != null) {
                player.displayClientMessage(Component.literal("§cYou are not the leader of this colony"), true);
            }
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public net.minecraft.world.InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        // If target is colony villager soldier, assign commander
        player.displayClientMessage(Component.literal("§eCommand staff used on " + target.getName().getString()), true);
        return net.minecraft.world.InteractionResult.SUCCESS;
    }
}
