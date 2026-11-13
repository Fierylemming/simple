package com.simplecolonies.colony;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.simplecolonies.progression.ColonyUpgrade;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * /colony list | info | tp <id> | delete <id>
 * + debug helpers to test progression without grinding:
 *   /colony research [id]                 — show unlocked + available + costs
 *   /colony warehouse [id]                — dump chests + abstract counts (counts are the “self-sufficient?” proof)
 *   /colony give <item> <count> [id]      — deposit to warehouse for testing (e.g. /colony give minecraft:cobblestone 64)
 *   /colony unlock <upgradeId> [id]       — force-unlock for testing (bypasses cost)
 *   /colony buy <upgradeId> [id]          — try buy normally (tests affordability/prereq logic)
 *   /colony tickprogress [id]             — force auto-progress tick (skip 5-day cooldown)
 *   /colony spawn <culture>               — spawn colony at your position for quick testing
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
                                "#" + c.getId() + " " + c.getCulture().displayName + " at " + c.getCenter().toShortString() + " pop:" + c.citizenCount() + " lvl:" + c.getLevel() + " tool:" + c.getResearch().getToolTier() + " workers:" + c.getResearch().getWorkSpeedMultiplier()
                        ), false);
                    }
                    return 1;
                }))
                .then(Commands.literal("info").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    var colony = ColonyManager.getInstance().getColonyAt(p.blockPosition());
                    if (colony.isEmpty()) { ctx.getSource().sendFailure(Component.literal("Not inside a colony — stand near town hall")); return 0; }
                    Colony c = colony.get();
                    return sendInfo(ctx.getSource(), c, p);
                }).then(Commands.argument("id", IntegerArgumentType.integer()).executes(ctx -> {
                    int id = IntegerArgumentType.getInteger(ctx, "id");
                    var colony = ColonyManager.getInstance().getColony(id);
                    if (colony.isEmpty()) { ctx.getSource().sendFailure(Component.literal("No colony #" + id)); return 0; }
                    Colony c = colony.get();
                    ServerPlayer p = null; try { p = ctx.getSource().getPlayerOrException(); } catch (Exception ignored) {}
                    return sendInfo(ctx.getSource(), c, p);
                })))
                .then(Commands.literal("research")
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            var colony = ColonyManager.getInstance().getColonyAt(p.blockPosition());
                            if (colony.isEmpty()) { ctx.getSource().sendFailure(Component.literal("Not inside a colony")); return 0; }
                            return sendResearch(ctx.getSource(), colony.get(), p);
                        })
                        .then(Commands.argument("id", IntegerArgumentType.integer()).executes(ctx -> {
                            int id = IntegerArgumentType.getInteger(ctx, "id");
                            var colony = ColonyManager.getInstance().getColony(id);
                            if (colony.isEmpty()) { ctx.getSource().sendFailure(Component.literal("No colony #" + id)); return 0; }
                            ServerPlayer p = null; try { p = ctx.getSource().getPlayerOrException(); } catch (Exception ignored) {}
                            return sendResearch(ctx.getSource(), colony.get(), p);
                        }))
                )
                .then(Commands.literal("warehouse")
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            var colony = ColonyManager.getInstance().getColonyAt(p.blockPosition());
                            if (colony.isEmpty()) { ctx.getSource().sendFailure(Component.literal("Not inside a colony")); return 0; }
                            return sendWarehouse(ctx.getSource(), colony.get(), p);
                        })
                        .then(Commands.argument("id", IntegerArgumentType.integer()).executes(ctx -> {
                            int id = IntegerArgumentType.getInteger(ctx, "id");
                            var colony = ColonyManager.getInstance().getColony(id);
                            if (colony.isEmpty()) { ctx.getSource().sendFailure(Component.literal("No colony #" + id)); return 0; }
                            ServerPlayer p = null; try { p = ctx.getSource().getPlayerOrException(); } catch (Exception ignored) {}
                            return sendWarehouse(ctx.getSource(), colony.get(), p);
                        }))
                )
                .then(Commands.literal("give").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("item", StringArgumentType.string())
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 10000))
                                        .executes(ctx -> {
                                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                                            var colony = ColonyManager.getInstance().getColonyAt(p.blockPosition());
                                            if (colony.isEmpty()) { ctx.getSource().sendFailure(Component.literal("Not inside a colony")); return 0; }
                                            String item = StringArgumentType.getString(ctx, "item");
                                            int count = IntegerArgumentType.getInteger(ctx, "count");
                                            colony.get().depositToWarehouse(item.contains(":") ? item : "minecraft:" + item, count);
                                            ColonyManager.getInstance().markDirty(colony.get());
                                            ctx.getSource().sendSuccess(() -> Component.literal("Gave " + count + "x " + item + " to colony #" + colony.get().getId() + " — check /colony warehouse"), true);
                                            return 1;
                                        })
                                        .then(Commands.argument("id", IntegerArgumentType.integer()).executes(ctx -> {
                                            String item = StringArgumentType.getString(ctx, "item");
                                            int count = IntegerArgumentType.getInteger(ctx, "count");
                                            int id = IntegerArgumentType.getInteger(ctx, "id");
                                            var colony = ColonyManager.getInstance().getColony(id);
                                            if (colony.isEmpty()) { ctx.getSource().sendFailure(Component.literal("No colony #" + id)); return 0; }
                                            colony.get().depositToWarehouse(item.contains(":") ? item : "minecraft:" + item, count);
                                            ColonyManager.getInstance().markDirty(colony.get());
                                            ctx.getSource().sendSuccess(() -> Component.literal("Gave " + count + "x " + item + " to colony #" + id), true);
                                            return 1;
                                        }))
                        )))
                .then(Commands.literal("unlock").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("upgradeId", StringArgumentType.string())
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    var colony = ColonyManager.getInstance().getColonyAt(p.blockPosition());
                                    if (colony.isEmpty()) { ctx.getSource().sendFailure(Component.literal("Not inside a colony")); return 0; }
                                    String uid = StringArgumentType.getString(ctx, "upgradeId");
                                    return forceUnlock(ctx.getSource(), colony.get(), uid);
                                })
                                .then(Commands.argument("id", IntegerArgumentType.integer()).executes(ctx -> {
                                    String uid = StringArgumentType.getString(ctx, "upgradeId");
                                    int id = IntegerArgumentType.getInteger(ctx, "id");
                                    var colony = ColonyManager.getInstance().getColony(id);
                                    if (colony.isEmpty()) { ctx.getSource().sendFailure(Component.literal("No colony #" + id)); return 0; }
                                    return forceUnlock(ctx.getSource(), colony.get(), uid);
                                }))
                        ))
                .then(Commands.literal("buy")
                        .then(Commands.argument("upgradeId", StringArgumentType.string())
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    var colony = ColonyManager.getInstance().getColonyAt(p.blockPosition());
                                    if (colony.isEmpty()) { ctx.getSource().sendFailure(Component.literal("Not inside a colony")); return 0; }
                                    String uid = StringArgumentType.getString(ctx, "upgradeId");
                                    return tryBuy(ctx.getSource(), colony.get(), uid, p);
                                })
                                .then(Commands.argument("id", IntegerArgumentType.integer()).executes(ctx -> {
                                    String uid = StringArgumentType.getString(ctx, "upgradeId");
                                    int id = IntegerArgumentType.getInteger(ctx, "id");
                                    var colony = ColonyManager.getInstance().getColony(id);
                                    if (colony.isEmpty()) { ctx.getSource().sendFailure(Component.literal("No colony #" + id)); return 0; }
                                    ServerPlayer p = null; try { p = ctx.getSource().getPlayerOrException(); } catch (Exception ignored) {}
                                    return tryBuy(ctx.getSource(), colony.get(), uid, p);
                                }))
                        ))
                .then(Commands.literal("tickprogress").requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            var colony = ColonyManager.getInstance().getColonyAt(p.blockPosition());
                            if (colony.isEmpty()) { ctx.getSource().sendFailure(Component.literal("Not inside a colony")); return 0; }
                            ServerLevel lvl = p.serverLevel();
                            colony.get().getResearch().tickAutoProgress(lvl);
                            // also tick with cooldown bypass: do 3 manual ticks
                            for (int i=0;i<3;i++) {
                                try {
                                    var f = colony.get().getResearch().getClass().getDeclaredField("autoProgressCooldown");
                                    f.setAccessible(true); f.set(colony.get().getResearch(), 0);
                                    colony.get().getResearch().tickAutoProgress(lvl);
                                } catch (Exception ignored) {}
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("Forced auto-progress ticks for colony #" + colony.get().getId() + " — check /colony research"), true);
                            return 1;
                        })
                        .then(Commands.argument("id", IntegerArgumentType.integer()).executes(ctx -> {
                            int id = IntegerArgumentType.getInteger(ctx, "id");
                            var colony = ColonyManager.getInstance().getColony(id);
                            if (colony.isEmpty()) { ctx.getSource().sendFailure(Component.literal("No colony #" + id)); return 0; }
                            ServerPlayer p = null; try { p = ctx.getSource().getPlayerOrException(); } catch (Exception ignored) {}
                            ServerLevel lvl = p != null ? p.serverLevel() : ctx.getSource().getLevel();
                            for (int i=0;i<4;i++) {
                                try { var f = colony.get().getResearch().getClass().getDeclaredField("autoProgressCooldown"); f.setAccessible(true); f.set(colony.get().getResearch(), 0); } catch (Exception ignored) {}
                                colony.get().getResearch().tickAutoProgress(lvl);
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("Forced auto-progress for colony #" + id), true);
                            return 1;
                        }))
                )
                .then(Commands.literal("status")
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            var colony = ColonyManager.getInstance().getColonyAt(p.blockPosition());
                            if (colony.isEmpty()) { ctx.getSource().sendFailure(Component.literal("Not inside a colony.")); return 0; }
                            return sendWorkStatus(ctx.getSource(), colony.get());
                        })
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> {
                            var colony = ColonyManager.getInstance().getColony(IntegerArgumentType.getInteger(ctx, "id"));
                            if (colony.isEmpty()) { ctx.getSource().sendFailure(Component.literal("No such colony.")); return 0; }
                            return sendWorkStatus(ctx.getSource(), colony.get());
                        })))
                .then(Commands.literal("build").requires(src -> src.hasPermission(2))
                        .then(Commands.literal("house").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            var colony = ColonyManager.getInstance().getColonyAt(p.blockPosition());
                            if (colony.isEmpty()) { ctx.getSource().sendFailure(Component.literal("Stand inside a colony first.")); return 0; }
                            if (!p.serverLevel().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
                                ctx.getSource().sendFailure(Component.literal("Colonies currently build only in the overworld.")); return 0;
                            }
                            var building = colony.get().queueHouse(p.serverLevel());
                            if (building == null) { ctx.getSource().sendFailure(Component.literal("No safe flat site, or another building is unfinished. Use /colony status.")); return 0; }
                            ctx.getSource().sendSuccess(() -> Component.literal("Queued house at " + building.getPos().toShortString() + "; use /colony status for progress."), false);
                            return 1;
                        })))
                .then(Commands.literal("cancel_building").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .then(Commands.argument("position", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                        .executes(ctx -> {
                                            var colony = ColonyManager.getInstance().getColony(IntegerArgumentType.getInteger(ctx, "id"));
                                            if (colony.isEmpty()) { ctx.getSource().sendFailure(Component.literal("No such colony.")); return 0; }
                                            var pos = net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(ctx, "position");
                                            if (!colony.get().cancelUnfinishedBuilding(pos)) {
                                                ctx.getSource().sendFailure(Component.literal("No unique unfinished generated plan at that exact position. Completed and player buildings cannot be cancelled."));
                                                return 0;
                                            }
                                            ctx.getSource().sendSuccess(() -> Component.literal("Cancelled plan at " + pos.toShortString()
                                                    + ". Existing blocks and spent materials remain; no blocks were removed."), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("spawn").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("culture", StringArgumentType.string()).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            String cultStr = StringArgumentType.getString(ctx, "culture");
                            var culture = com.simplecolonies.culture.CultureType.byId(cultStr);
                            if (!p.serverLevel().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
                                ctx.getSource().sendFailure(Component.literal("Colonies currently build only in the overworld.")); return 0;
                            }
                            var manager = ColonyManager.getInstance();
                            var plan = manager.findNaturalStarterPlan(p.serverLevel(), p.blockPosition());
                            if (plan == null) {
                                ctx.getSource().sendFailure(Component.literal("No safe loaded land for both a town hall and farm nearby; try a clear area.")); return 0;
                            }
                            var colony = manager.createNaturalColony(p.serverLevel(), culture, plan);
                            if (colony == null) { ctx.getSource().sendFailure(Component.literal("Could not create colony: limit reached or the starter site changed.")); return 0; }
                            ctx.getSource().sendSuccess(() -> Component.literal("Spawned " + culture.displayName + " colony #" + colony.getId() + " at " + colony.getCenter().toShortString() + " with starter cobble/wood — test with /colony warehouse, /colony research, /colony buy TOOL_STONE"), true);
                            return 1;
                        })))
                .then(Commands.literal("repair").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> repair(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "id"), false))
                                .then(Commands.literal("reset_citizens")
                                        .executes(ctx -> repair(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "id"), true)))))
                .then(Commands.literal("tp").requires(src -> src.hasPermission(2)).then(Commands.argument("id", IntegerArgumentType.integer()).executes(ctx -> {
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

    private static int sendWorkStatus(CommandSourceStack src, Colony colony) {
        ServerLevel level = src.getServer().overworld();
        if (!level.isLoaded(colony.getCenter())) {
            src.sendFailure(Component.literal("Visit the colony so its center is loaded."));
            return 0;
        }
        colony.getWarehouseManager().scan(level);
        boolean builder = colony.getCitizens().stream().anyMatch(c -> c.job == com.simplecolonies.villager.job.JobType.BUILDER);
        src.sendSuccess(() -> Component.literal("Colony #" + colony.getId() + " " + colony.getState() + "; citizens " + colony.citizenCount() + "; builder assigned " + builder
                + ", oak logs " + colony.getWarehouseCount(level, "minecraft:oak_log")
                + ", cobblestone " + colony.getWarehouseCount(level, "minecraft:cobblestone")
                + ", food " + colony.getFoodCount(level)), false);
        if (com.simplecolonies.config.ColonyConfig.ENABLE_NATURAL_EXPANSION.get()) {
            src.sendSuccess(() -> Component.literal("Automatic expansion: " + colony.getExpansionStatus()), false);
        } else src.sendSuccess(() -> Component.literal("Natural expansion is disabled in config."), false);
        for (var citizen : colony.getCitizens()) {
            if (citizen.job != com.simplecolonies.villager.job.JobType.BUILDER
                    && citizen.job != com.simplecolonies.villager.job.JobType.MINER) continue;
            var entity = level.getEntity(citizen.uuid);
            String status = entity instanceof com.simplecolonies.entity.ColonyVillager cv ? cv.getWorkStatus() : "NOT LOADED OR MISSING";
            src.sendSuccess(() -> Component.literal(citizen.job.displayName + " " + citizen.name + ": " + status
                    + "; home " + (citizen.home == null ? "unassigned (hall shelter)" : citizen.home.toShortString())), false);
        }
        boolean unfinished = false;
        for (var building : colony.getBuildings()) {
            if (building.getState() == com.simplecolonies.building.BuildingState.COMPLETE) continue;
            unfinished = true;
            var next = building.getNextPlacement();
            src.sendSuccess(() -> Component.literal(building.getType().displayName + " at " + building.getPos().toShortString()
                    + ": " + building.getState() + (next == null ? "" : "; next block " + next.pos.toShortString() + " needs " + next.itemId)), false);
        }
        if (!unfinished) src.sendSuccess(() -> Component.literal("No construction is queued."), false);
        else src.sendSuccess(() -> Component.literal("Operators can cancel an unfinished generated plan with /colony cancel_building <id> <x y z>; existing blocks remain."), false);
        return 1;
    }

    private static int repair(CommandSourceStack src, int id, boolean resetCitizens) {
        Colony colony = ColonyManager.getInstance().getColony(id).orElse(null);
        if (colony == null) { src.sendFailure(Component.literal("No colony #" + id)); return 0; }
        ServerLevel level = src.getServer().overworld();
        if (!level.isLoaded(colony.getCenter())) {
            src.sendFailure(Component.literal("Visit the colony first so its center is loaded."));
            return 0;
        }
        if (resetCitizens) {
            // Refuse to discard records of currently loaded living villagers. This
            // explicit option can still duplicate villagers in unloaded chunks.
            if (colony.getCitizens().stream().anyMatch(c -> level.getEntity(c.uuid) != null)) {
                src.sendFailure(Component.literal("Loaded citizens exist; refusing to reset them."));
                return 0;
            }
            for (var citizen : java.util.List.copyOf(colony.getCitizens())) colony.removeCitizen(citizen.uuid);
        }
        ColonyManager.getInstance().repairStarters(level, colony);
        src.sendSuccess(() -> Component.literal("Colony #" + id + " repair: " + colony.citizenCount()
                + " citizen records, " + colony.getBuildings().size() + " buildings; center " + colony.getCenter().toShortString()), false);
        if (colony.getBuildings().stream().noneMatch(b -> b.getType() == com.simplecolonies.building.BuildingType.TOWN_HALL))
            src.sendFailure(Component.literal("No town hall shell could be repaired safely at the colony center; existing blocks were left untouched."));
        if (!resetCitizens && colony.citizenCount() > 0) {
            src.sendSuccess(() -> Component.literal("Existing citizen records were kept. See the repair documentation before resetting missing records."), false);
        }
        return 1;
    }

    private static int sendInfo(CommandSourceStack src, Colony c, ServerPlayer p) {
        src.sendSuccess(() -> Component.literal("§l" + c.getCulture().displayName + " colony #" + c.getId() + " §r at " + c.getCenter().toShortString()), false);
        src.sendSuccess(() -> Component.literal("  Level " + c.getLevel() + " radius " + c.getRadius() + " pop " + c.citizenCount() + " state " + c.getState()), false);
        src.sendSuccess(() -> Component.literal("  Tool tier " + c.getResearch().getToolTier() + " (" + c.getResearch().getWorkSpeedMultiplier() + "x speed) | Buildings: " + c.getBuildings().size()), false);
        src.sendSuccess(() -> Component.literal("  Unlocked: " + c.getResearch().getUnlocked().stream().map(u -> u.name()).reduce((a,b)->a+", "+b).orElse("none")), false);
        if (p != null) src.sendSuccess(() -> Component.literal("  Your rep: " + c.getReputation(p) + (c.isLeader(p) ? " §6(LEADER)" : "")), false);
        src.sendSuccess(() -> Component.literal("  §7Try: /colony research, /colony warehouse, /colony buy TOOL_STONE"), false);
        return 1;
    }

    private static int sendResearch(CommandSourceStack src, Colony c, ServerPlayer p) {
        src.sendSuccess(() -> Component.literal("§lResearch for colony #" + c.getId() + " — tool tier " + c.getResearch().getToolTier() + " speed " + c.getResearch().getWorkSpeedMultiplier() + "x"), false);
        src.sendSuccess(() -> Component.literal("§aUnlocked (" + c.getResearch().getUnlocked().size() + "): " + c.getResearch().getUnlocked().stream().map(u -> u.displayName).reduce((a,b)->a+", "+b).orElse("none")), false);
        var avail = c.getResearch().getAvailableToBuy();
        if (avail.isEmpty()) src.sendSuccess(() -> Component.literal("§eNo more unlockable upgrades — need prereqs or already maxed"), false);
        else {
            src.sendSuccess(() -> Component.literal("§eAvailable to buy (prereqs met — check costs in Town Hall Research tab):"), false);
            for (var up : avail) {
                ServerLevel lvl = null; try { if (p != null) lvl = p.serverLevel(); } catch (Exception ignored) {}
                boolean canAfford = c.getResearch().canAfford(up, lvl);
                String costStr = up.cost.entrySet().stream().map(e -> e.getValue() + "x " + e.getKey()).reduce((a,b)->a+", "+b).orElse("free");
                String line = (canAfford ? "§a✓ " : "§c✗ ") + up.name() + " [" + up.tier + "] " + up.displayName + " — cost: " + costStr + (up.prerequisites.isEmpty() ? "" : " | needs: " + up.prerequisites.stream().map(x->x.name()).reduce((a,b)->a+", "+b).orElse(""));
                String lineFinal = line;
                src.sendSuccess(() -> Component.literal(lineFinal), false);
            }
            src.sendSuccess(() -> Component.literal("§7Buy with: /colony buy <UPGRADE_ID> or Town Hall → Research tab | Test afford: /colony give cobblestone 64"), false);
        }
        return 1;
    }

    private static int sendWarehouse(CommandSourceStack src, Colony c, ServerPlayer p) {
        ServerLevel lvl = null; try { if (p != null) lvl = p.serverLevel(); } catch (Exception ignored) {}
        var snap = c.getWarehouseSnapshot(lvl);
        var chests = c.getWarehouseManager().snapshot();
        src.sendSuccess(() -> Component.literal("§lWarehouse colony #" + c.getId() + " — chest network: " + c.getWarehouseManager().getChestCount() + " chests scanned, abstract stash included"), false);
        if (snap.isEmpty()) src.sendSuccess(() -> Component.literal("§cEmpty — this is starter cobble test: fresh colonies have 32 cobble+8 coal+32 planks+16 logs+16 bread (from ColonyManager)"), false);
        else {
            snap.entrySet().stream().sorted((a,b)-> b.getValue()-a.getValue()).forEach(e -> src.sendSuccess(() -> Component.literal("  " + e.getKey() + ": " + e.getValue()), false));
        }
        if (!chests.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7Chest breakdown:"), false);
            chests.entrySet().forEach(e -> src.sendSuccess(() -> Component.literal("  [chest] " + e.getKey() + ": " + e.getValue()), false));
        }
        src.sendSuccess(() -> Component.literal("§7Cobble check for Tier1: need 32 cobble for TOOL_STONE — " + (snap.getOrDefault("minecraft:cobblestone",0) >= 32 ? "§aENOUGH" : "§cNEED " + (32-snap.getOrDefault("minecraft:cobblestone",0)) + " more") + " | Add with /colony give cobblestone 32"), false);
        return 1;
    }

    private static int forceUnlock(CommandSourceStack src, Colony c, String uid) {
        try {
            ColonyUpgrade up = ColonyUpgrade.valueOf(uid.toUpperCase());
            // bypass prereq, just add
            var f = c.getResearch().getClass().getDeclaredField("unlocked");
            f.setAccessible(true);
            ((java.util.Set<ColonyUpgrade>)f.get(c.getResearch())).add(up);
            ColonyManager.getInstance().markDirty(c);
            src.sendSuccess(() -> Component.literal("Force-unlocked " + up.displayName + " for colony #" + c.getId()), true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Unknown upgrade: " + uid + " — try one of " + java.util.Arrays.stream(ColonyUpgrade.values()).map(x->x.name()).reduce((a,b)->a+", "+b).orElse("")));
            return 0;
        }
    }

    static int tryBuy(CommandSourceStack src, Colony c, String uid, ServerPlayer p) {
        if (!src.hasPermission(2) && (p == null || !c.isLeader(p))) {
            src.sendFailure(Component.literal("Only the colony leader or an operator may spend colony stock on research."));
            return 0;
        }
        if (p != null && !p.serverLevel().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            src.sendFailure(Component.literal("Colony research can only be purchased from the Overworld."));
            return 0;
        }
        try {
            ColonyUpgrade up = ColonyUpgrade.valueOf(uid.toUpperCase());
            ServerLevel lvl = src.getServer().overworld();
            boolean ok = c.getResearch().tryBuy(up, lvl, p);
            if (ok) src.sendSuccess(() -> Component.literal("Bought " + up.displayName + " — check /colony research for new avail"), true);
            else src.sendFailure(Component.literal("Buy failed — see chat error or /colony warehouse for mats, /colony research for prereqs"));
            return ok ? 1 : 0;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Unknown upgrade: " + uid));
            return 0;
        }
    }
}
