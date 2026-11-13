package com.simplecolonies.item;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Shift-use on a container to opt it into or out of this colony's storage. */
public class BuildingToolItem extends Item {
    public BuildingToolItem(Properties props) { super(props); }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, Item.TooltipContext context,
                                java.util.List<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        lines.add(Component.literal("§7Sneak-use a chest, barrel or shulker to share or unshare it."));
        lines.add(Component.literal("§7Double chests: designate each half separately."));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (ctx.getPlayer() == null) return InteractionResult.FAIL;
        Colony colony = ColonyManager.getInstance().getColonyAt(level.dimension(), pos).orElse(null);
        if (colony == null) {
            ctx.getPlayer().displayClientMessage(Component.literal("§cNot in a colony radius. Place Town Hall first."), true);
            return InteractionResult.FAIL;
        }
        if (colony.isLeader(ctx.getPlayer()) || colony.getReputation(ctx.getPlayer()) >= 20) {
            if (ctx.getPlayer().isShiftKeyDown() && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                var storage = colony.getWarehouseManager();
                if (storage.isRegisteredStorage(serverLevel, pos)) {
                    storage.unregisterStorage(serverLevel, pos);
                    ctx.getPlayer().displayClientMessage(Component.literal("§eContainer removed from colony storage."), true);
                    return InteractionResult.SUCCESS;
                }
                if (storage.registerStorage(serverLevel, pos)) {
                    ctx.getPlayer().displayClientMessage(Component.literal("§aContainer added to colony storage. For a double chest, mark both halves."), true);
                    return InteractionResult.SUCCESS;
                }
                ctx.getPlayer().displayClientMessage(Component.literal("§cNot a supported container, or it belongs to another colony."), true);
                return InteractionResult.FAIL;
            }
            ctx.getPlayer().displayClientMessage(Component.literal("§aTip: Place a Building Marker block here and right-click it to designate as House/Farm/Warehouse etc. Or right-click marker directly."), false);
            ctx.getPlayer().displayClientMessage(Component.literal("§7Sneak-use this tool on a container to share or unshare colony storage."), false);
        } else {
            ctx.getPlayer().displayClientMessage(Component.literal("§cNeed 20 reputation to designate colony storage."), true);
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }
}
