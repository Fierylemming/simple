package com.simplecolonies.colony;

import com.mojang.brigadier.CommandDispatcher;
import com.simplecolonies.culture.CultureType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class ColonyCommandGameTests {
    @GameTest(template = "empty")
    public static void debuggingCommandsRequireOperatorAndResearchCannotBeSpentByStrangers(GameTestHelper helper) {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        ColonyCommand.register(dispatcher);
        var root = dispatcher.getRoot().getChild("colony");
        var operator = helper.getLevel().getServer().createCommandSourceStack().withPermission(2);
        var ordinary = operator.withPermission(0);
        for (String name : new String[]{"give", "unlock", "tickprogress", "spawn", "tp", "delete", "build", "repair", "cancel_building"}) {
            var command = root.getChild(name);
            if (command == null || command.canUse(ordinary) || !command.canUse(operator)) helper.fail("Wrong permissions for " + name);
        }
        if (!root.getChild("buy").canUse(ordinary) || !root.getChild("cancel_research").canUse(ordinary))
            helper.fail("Leader research commands were hidden from players");
        var chestPos = new BlockPos(8, 1, 8); helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);
        var chest = (net.minecraft.world.Container) helper.getBlockEntity(chestPos);
        chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 32));
        chest.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 16));
        chest.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL, 4));
        Colony colony = new Colony(com.simplecolonies.gametest.GameTestColonyIds.next(), CultureType.ROMAN, helper.absolutePos(chestPos));
        colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chestPos));
        if (ColonyCommand.tryBuy(ordinary, colony, "TOOL_STONE", null) != 0
                || chest.countItem(net.minecraft.world.item.Items.COBBLESTONE) != 32) helper.fail("Non-leader queued research");
        if (ColonyCommand.tryBuy(operator, colony, "TOOL_STONE", null) != 1
                || colony.getResearch().getPending() != com.simplecolonies.progression.ColonyUpgrade.TOOL_STONE
                || chest.countItem(net.minecraft.world.item.Items.COBBLESTONE) != 32) helper.fail("Operator could not queue research safely");
        if (ColonyCommand.cancelResearch(ordinary, colony, null) != 0 || colony.getResearch().getPending() == null)
            helper.fail("Non-leader cancelled research");
        if (ColonyCommand.cancelResearch(operator, colony, null) != 1 || colony.getResearch().getPending() != null
                || chest.countItem(net.minecraft.world.item.Items.COBBLESTONE) != 32)
            helper.fail("Operator could not cancel research safely");
        helper.succeed();
    }

    /** The exact failure reported from play: /colony give must hand workers real
     * items, accept the plural typo, and refuse unknown ids instead of inventing
     * a fake item id that no builder can fetch. */
    @GameTest(template = "empty")
    public static void giveDeliversRealItemsBuildersCanUse(GameTestHelper helper) {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        ColonyCommand.register(dispatcher);
        var operator = helper.getLevel().getServer().createCommandSourceStack().withPermission(2);
        var chestPos = new BlockPos(8, 1, 8);
        helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);
        var chest = (net.minecraft.world.Container) helper.getBlockEntity(chestPos);
        Colony colony = new Colony(com.simplecolonies.gametest.GameTestColonyIds.next(),
                CultureType.ROMAN, helper.absolutePos(chestPos));
        colony.getWarehouseManager().registerStorage(helper.getLevel(), helper.absolutePos(chestPos));

        if (ColonyCommand.giveToColony(operator, colony, "oak_logs", 8) != 1)
            helper.fail("Accepted item was rejected");
        if (chest.countItem(net.minecraft.world.item.Items.OAK_LOG) != 8)
            helper.fail("Given logs did not reach the shared chest: "
                    + chest.countItem(net.minecraft.world.item.Items.OAK_LOG));
        if (colony.getWarehouseCount("minecraft:oak_log") != 0)
            helper.fail("Given logs stayed in virtual stock, which builders cannot use");

        if (ColonyCommand.giveToColony(operator, colony, "oak_log", 2) != 1
                || chest.countItem(net.minecraft.world.item.Items.OAK_LOG) != 10)
            helper.fail("Singleton id did not deposit");

        if (ColonyCommand.giveToColony(operator, colony, "oak_logs_but_not_really", 64) != 0)
            helper.fail("Unknown item id was accepted");
        if (colony.getWarehouseCount("minecraft:oak_logs_but_not_really") != 0)
            helper.fail("Unknown item id invented virtual stock");
        if (chest.countItem(net.minecraft.world.item.Items.OAK_LOG) != 10)
            helper.fail("A rejected give changed the chest");

        // The logs a player hands over must be craftable into the parts a house
        // actually spends: planks first, then stairs/slabs/doors, sticks, torches.
        var shared = colony.getWarehouseManager().snapshot();
        for (String part : new String[]{"minecraft:oak_planks", "minecraft:oak_stairs",
                "minecraft:oak_slab", "minecraft:oak_door", "minecraft:stick"}) {
            var inputs = com.simplecolonies.building.ConstructionCrafting.requiredInputs(shared, part);
            if (inputs == null)
                helper.fail("Shared logs cannot be crafted into " + part + " (stock " + shared + ")");
        }
        helper.succeed();
    }
}
