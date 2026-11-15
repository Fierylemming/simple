package com.simplecolonies.colony;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ColonyCommandTreeTest {
    @Test void documentedCommandsAreDirectChildrenOfColony() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        ColonyCommand.register(dispatcher);
        var colony = dispatcher.getRoot().getChild("colony");
        assertNotNull(colony);
        for (String name : List.of("list", "info", "research", "warehouse", "give", "unlock",
                "buy", "tickprogress", "status", "build", "spawn", "repair", "tp", "delete")) {
            assertNotNull(colony.getChild(name), "/colony " + name + " must be a direct child");
        }
        assertNotNull(colony.getChild("build").getChild("house"));
        assertNotNull(colony.getChild("spawn").getChild("culture"));
        assertNotNull(colony.getChild("repair").getChild("id").getChild("reset_citizens"));
        assertNotNull(colony.getChild("status").getCommand(), "status must work without an id");
        assertNotNull(colony.getChild("give").getChild("item").getChild("count").getCommand());
    }
}
