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
}
