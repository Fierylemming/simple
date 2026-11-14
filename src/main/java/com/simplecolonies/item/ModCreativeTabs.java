package com.simplecolonies.item;

import com.simplecolonies.SimpleColonies;
import com.simplecolonies.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SimpleColonies.MOD_ID);
    public static final Supplier<CreativeModeTab> COLONIES_TAB = TABS.register("colonies",
        () -> CreativeModeTab.builder()
            .title(Component.literal("Simple Colonies"))
            .icon(() -> new ItemStack(ModBlocks.TOWN_HALL.get()))
            .displayItems((params, output) -> {
                output.accept(ModBlocks.TOWN_HALL.get());
                output.accept(ModBlocks.WAREHOUSE_CHEST.get());
                output.accept(ModBlocks.COLONY_BANNER.get());
                output.accept(ModBlocks.BUILDING_MARKER.get());
                output.accept(ModItems.COMMAND_STAFF.get());
                output.accept(ModItems.BUILDING_TOOL.get());
                output.accept(ModItems.TOWN_HALL_ITEM.get());
                output.accept(ModItems.COLONY_BANNER_ITEM.get());
            }).build());
}
