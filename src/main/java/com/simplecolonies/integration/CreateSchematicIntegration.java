package com.simplecolonies.integration;

import com.simplecolonies.building.Building;
import com.simplecolonies.colony.Colony;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Reserved integration point. Schematic construction is not implemented yet. */
public final class CreateSchematicIntegration {
    public static boolean isCreateLoaded() {
        try { Class.forName("com.simibubi.create.Create"); return true; } catch (Exception e) { return false; }
    }
    public static boolean isSchematic(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains("schematic");
    }
    /** Never consume an item or queue a fake placeholder in lieu of a schematic. */
    public static Building tryCreateSchematicTask(Colony colony, ServerLevel level, BlockPos builderPos) { return null; }
    public static void notifyPlayer(Player player, boolean hasCreate) {
        player.displayClientMessage(Component.literal("§eCreate schematic construction is not implemented; no items were consumed."), false);
    }
    private CreateSchematicIntegration() {}
}
