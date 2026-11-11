package com.simplecolonies.item;

import com.simplecolonies.SimpleColonies;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SimpleColonies.MOD_ID);

    public static final Supplier<Item> TOWN_HALL_ITEM = ITEMS.register("town_hall",
            () -> new BlockItem(com.simplecolonies.block.ModBlocks.TOWN_HALL.get(), new Item.Properties()));

    public static final Supplier<Item> COMMAND_STAFF = ITEMS.register("command_staff",
            () -> new CommandStaffItem(new Item.Properties().stacksTo(1)));

    public static final Supplier<Item> CULTURE_SCROLL = ITEMS.register("culture_scroll",
            () -> new Item(new Item.Properties()));

    public static final Supplier<Item> COLONY_BANNER_ITEM = ITEMS.register("colony_banner",
            () -> new BlockItem(com.simplecolonies.block.ModBlocks.COLONY_BANNER.get(), new Item.Properties()));
}
