package com.simplecolonies.block;

import com.simplecolonies.SimpleColonies;
import com.simplecolonies.block.entity.BuildingMarkerBlockEntity;
import com.simplecolonies.block.entity.TownHallBlockEntity;
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

    // Building Marker — place in your own builds to designate them as colony buildings
    public static final Supplier<Block> BUILDING_MARKER = BLOCKS.register("building_marker",
            () -> new BuildingMarkerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(2.0f).noOcclusion()));

    public static final Supplier<BlockEntityType<TownHallBlockEntity>> TOWN_HALL_BE = BLOCK_ENTITIES.register("town_hall",
            () -> BlockEntityType.Builder.of(TownHallBlockEntity::new, TOWN_HALL.get()).build(null));

    public static final Supplier<BlockEntityType<BuildingMarkerBlockEntity>> BUILDING_MARKER_BE = BLOCK_ENTITIES.register("building_marker",
            () -> BlockEntityType.Builder.of(BuildingMarkerBlockEntity::new, BUILDING_MARKER.get()).build(null));
}
