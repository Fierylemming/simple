package com.simplecolonies.entity;

import com.simplecolonies.SimpleColonies;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, SimpleColonies.MOD_ID);

    public static final Supplier<EntityType<ColonyVillager>> COLONY_VILLAGER = ENTITY_TYPES.register("colony_villager",
            () -> EntityType.Builder.of(ColonyVillager::new, MobCategory.MISC)
                    .sized(0.6f, 1.9f)
                    .build("colony_villager"));
}
