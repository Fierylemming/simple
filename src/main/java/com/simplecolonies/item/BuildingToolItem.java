package com.simplecolonies.item;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Building Tool (wand) — optional helper to quickly turn any player build into colony building without placing marker block.
 * Right-click a block inside colony → opens same marker GUI (or directly cycles type for speed).
 *
 * For now implemented as right-click tool that directly opens Building Marker GUI at clicked pos
 * (creates temporary marker logic). You can also just place the Building Marker block.
 */
public class BuildingToolItem extends Item {
    public BuildingToolItem(Properties props) { super(props); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Colony colony = ColonyManager.getInstance().getColonyAt(pos).orElse(null);
        if (colony == null) {
            ctx.getPlayer().displayClientMessage(Component.literal("§cNot in a colony radius. Place Town Hall first."), true);
            return InteractionResult.FAIL;
        }
        if (colony.isLeader(ctx.getPlayer()) || colony.getReputation(ctx.getPlayer()) >= 20) {
            ctx.getPlayer().displayClientMessage(Component.literal("§aTip: Place a Building Marker block here and right-click it to designate as House/Farm/Warehouse etc. Or right-click marker directly."), false);
            ctx.getPlayer().displayClientMessage(Component.literal("§7Tool use: marker at " + pos.toShortString() + " would become building. Place Building Marker for GUI."), false);
        }
        return InteractionResult.SUCCESS;
    }
}
