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
        lines.add(Component.literal("§7Sneak-use on a cow, pig or sheep to protect it from workers."));
    }

    @Override
    public InteractionResult interactLivingEntity(net.minecraft.world.item.ItemStack stack,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.LivingEntity target,
            net.minecraft.world.InteractionHand hand) {
        if (!player.isShiftKeyDown() || !(target instanceof net.minecraft.world.entity.animal.Animal animal)
                || !(animal instanceof net.minecraft.world.entity.animal.Cow || animal instanceof net.minecraft.world.entity.animal.Pig
                    || animal instanceof net.minecraft.world.entity.animal.Sheep)) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        Colony colony = ColonyManager.getInstance().getColonyAt(player.level().dimension(), animal.blockPosition()).orElse(null);
        return toggleAnimalProtection(player, animal, colony);
    }

    /** Server-side authorization is rechecked for every toggle. */
    public static InteractionResult toggleAnimalProtection(net.minecraft.world.entity.player.Player player,
            net.minecraft.world.entity.animal.Animal animal, Colony colony) {
        if (colony == null || !player.level().dimension().equals(Level.OVERWORLD) || !colony.isInside(animal.blockPosition())
                || (!colony.isLeader(player) && colony.getReputation(player) < 20)) {
            player.displayClientMessage(Component.literal("§cNeed colony leadership or 20 reputation to protect livestock."), true);
            return InteractionResult.FAIL;
        }
        var owner = com.simplecolonies.entity.AnimalProtection.protector(animal);
        if (owner == null) {
            com.simplecolonies.entity.AnimalProtection.protect(animal, player.getUUID());
            player.displayClientMessage(Component.literal("§aThis animal is protected from colony livestock workers."), true);
        } else if (owner.equals(player.getUUID()) || colony.isLeader(player)) {
            com.simplecolonies.entity.AnimalProtection.unprotect(animal);
            player.displayClientMessage(Component.literal(animal.hasCustomName()
                    ? "§eProtection mark removed; named animals remain protected." : "§eProtection mark removed."), true);
        } else {
            player.displayClientMessage(Component.literal("§cOnly its protector or the colony leader can remove this mark."), true);
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
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
