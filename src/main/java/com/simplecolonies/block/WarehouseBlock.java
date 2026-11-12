package com.simplecolonies.block;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A warehouse hub marker; actual containers must be designated separately. */
public class WarehouseBlock extends Block {
    public WarehouseBlock(Properties props) { super(props); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Colony colony = ColonyManager.getInstance().getColonyAt(pos).orElse(null);
        if (colony != null && player instanceof ServerPlayer sp) {
            // Set as warehouse hub if leader
            if (colony.isLeader(player)) {
                colony.setWarehouseCenter(pos);
                sp.displayClientMessage(Component.literal("§aWarehouse hub set to " + pos.toShortString() + " — mark containers with the Building Tool"), false);
            }
            sp.displayClientMessage(Component.literal("§7Warehouse: " + colony.getWarehouseManager().getChestCount() + " chests in colony, hub=" + colony.getWarehouseManager().getWarehouseCenter().toShortString()), false);
            sp.displayClientMessage(Component.literal("§7Contents preview: " + colony.getWarehouseSnapshot((net.minecraft.server.level.ServerLevel) level).size() + " item types"), false);
        }
        return InteractionResult.SUCCESS;
    }
}
