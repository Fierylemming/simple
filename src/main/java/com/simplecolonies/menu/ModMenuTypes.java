package com.simplecolonies.menu;

import com.simplecolonies.SimpleColonies;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, SimpleColonies.MOD_ID);

    public static final Supplier<MenuType<TownHallMenu>> TOWN_HALL = MENUS.register("town_hall",
            () -> IMenuTypeExtension.create(TownHallMenu::fromNetwork));

    public static final Supplier<MenuType<VillagerInteractionMenu>> VILLAGER = MENUS.register("villager_interaction",
            () -> IMenuTypeExtension.create(VillagerInteractionMenu::fromNetwork));
}
