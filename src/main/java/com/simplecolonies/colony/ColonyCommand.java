package com.simplecolonies.colony;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /colony info | list | tp <id> | setleader <player> | war <id> | ally <id> | delete <id>
 */
public class ColonyCommand {
    public static void register(CommandDispatcher<CommandSourceStack> disp) {
        disp.register(Commands.literal("colony")
                .then(Commands.literal("list").executes(ctx -> {
                    var src = ctx.getSource();
                    int count = ColonyManager.getInstance().colonyCount();
                    src.sendSuccess(() -> Component.literal("Colonies: " + count), false);
                    for (Colony c : ColonyManager.getInstance().getAll()) {
                        src.sendSuccess(() -> Component.literal(
                                "#" + c.getId() + " " + c.getCulture().displayName + " at " + c.getCenter().toShortString() + " pop:" + c.citizenCount() + " lvl:" + c.getLevel()
                        ), false);
                    }
                    return 1;
                }))
                .then(Commands.literal("info").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    var colony = ColonyManager.getInstance().getColonyAt(p.blockPosition());
                    if (colony.isEmpty()) { ctx.getSource().sendFailure(Component.literal("Not inside a colony")); return 0; }
                    Colony c = colony.get();
                    ctx.getSource().sendSuccess(() -> Component.literal(c.getCulture().displayName + " colony #" + c.getId() + " rep " + c.getReputation(p) + " leader=" + c.getLeaderId()), false);
                    return 1;
                }))
                .then(Commands.literal("tp").then(Commands.argument("id", IntegerArgumentType.integer()).executes(ctx -> {
                    int id = IntegerArgumentType.getInteger(ctx, "id");
                    var colony = ColonyManager.getInstance().getColony(id);
                    if (colony.isEmpty()) { ctx.getSource().sendFailure(Component.literal("No colony #" + id)); return 0; }
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    var pos = colony.get().getCenter();
                    p.teleportTo(pos.getX() + 0.5, pos.getY() + 10, pos.getZ() + 0.5);
                    return 1;
                })))
                .then(Commands.literal("delete").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("id", IntegerArgumentType.integer()).executes(ctx -> {
                            int id = IntegerArgumentType.getInteger(ctx, "id");
                            ColonyManager.getInstance().deleteColony(id);
                            ctx.getSource().sendSuccess(() -> Component.literal("Deleted colony #" + id), true);
                            return 1;
                        })))
        );
    }
}
