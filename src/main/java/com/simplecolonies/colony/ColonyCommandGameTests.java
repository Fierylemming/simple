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
        if (!root.getChild("buy").canUse(ordinary)) helper.fail("Leader research command was hidden from players");
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(8, 1, 8)));
        colony.depositToWarehouse("minecraft:cobblestone", 32);
        colony.depositToWarehouse("minecraft:oak_planks", 16);
        colony.depositToWarehouse("minecraft:coal", 4);
        if (ColonyCommand.tryBuy(ordinary, colony, "TOOL_STONE", null) != 0
                || colony.getWarehouseCount("minecraft:cobblestone") != 32) helper.fail("Non-leader spent colony stock");
        if (ColonyCommand.tryBuy(operator, colony, "TOOL_STONE", null) != 1
                || colony.getWarehouseCount("minecraft:cobblestone") != 0) helper.fail("Operator could not buy research");
        helper.succeed();
    }
}
