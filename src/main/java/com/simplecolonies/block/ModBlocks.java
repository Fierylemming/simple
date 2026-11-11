package com.simplecolonies.block;

import com.simplecolonies.SimpleColonies;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SimpleColonies.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SimpleColonies.MOD_ID);

    public static final Supplier<Block> TOWN_HALL = BLOCKS.register("town_hall",
            () -> new TownHallBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(5.0f).requiresCorrectToolForDrops()));

    public static final Supplier<Block> COLONY_BANNER = BLOCKS.register("colony_banner",
            () -> new ColonyBannerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(1.0f).noOcclusion()));

    public static final Supplier<Block> WAREHOUSE_CHEST = BLOCKS.register("warehouse_chest",
            () -> new WarehouseBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f)));

    // Block entities registered in separate holder - simplified
    // public static final Supplier<BlockEntityType<TownHallBlockEntity>> TOWN_HALL_BE = ...
}
