package com.simplecolonies.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.BiConsumer;

/** Compact early workshops built around an existing workstation, not over it. */
final class LevelOneWorksites {
    static boolean supports(BuildingType type) { return supports(type, 9); }
    static boolean supports(BuildingType type, int version) {
        return type == BuildingType.LUMBERJACK_HUT || type == BuildingType.MINE || type == BuildingType.BUILDER_HUT
                || (version >= 7 && (type == BuildingType.BLACKSMITH || type == BuildingType.BUTCHER_HUT))
                || (version >= 8 && type == BuildingType.HARBOR)
                || (version >= 9 && (type == BuildingType.BAKERY || type == BuildingType.LEATHER_SHOP));
    }

    static void generate(BuildingType type, BlockPos station, BiConsumer<BlockPos, BlockState> add) {
        if (!supports(type)) throw new IllegalArgumentException("No worksite plan for " + type);
        // The center is the pre-existing POI. Do not replace it or the ground floor.
        for (int y = 0; y <= 2; y++) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if (Math.abs(x) != 2 && Math.abs(z) != 2) continue;
            if (x == 0 && z == -2 && y < 2) continue;
            boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
            BlockState wall = switch (type) {
                case LUMBERJACK_HUT -> corner ? Blocks.OAK_LOG.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState();
                case MINE -> y == 2 && !corner ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
                case BUILDER_HUT -> y == 0 ? Blocks.COBBLESTONE.defaultBlockState()
                        : corner ? Blocks.OAK_LOG.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState();
                case BLACKSMITH -> y == 0 || corner ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState();
                case BUTCHER_HUT -> corner ? (y == 0 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.OAK_LOG.defaultBlockState())
                        : Blocks.OAK_PLANKS.defaultBlockState();
                case HARBOR -> y == 0 ? Blocks.OAK_LOG.defaultBlockState()
                        : corner ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState();
                case BAKERY -> corner || y == 0 ? Blocks.OAK_LOG.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
                case LEATHER_SHOP -> y == 0 || (corner && y == 2) ? Blocks.OAK_PLANKS.defaultBlockState()
                        : corner ? Blocks.OAK_LOG.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
                default -> throw new IllegalStateException();
            };
            add.accept(station.offset(x, y, z), wall);
        }
        add.accept(station.offset(0, 0, -2), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH));
        add.accept(station.offset(1, 0, 1), Blocks.TORCH.defaultBlockState());
        // SmithFurnace deliberately accepts only a furnace adjacent to the claimed table.
        if (type == BuildingType.BLACKSMITH) add.accept(station.offset(1, 0, 0), Blocks.FURNACE.defaultBlockState());
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            BlockState roof = x == -2 || x == 2
                    ? Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, x < 0 ? Direction.EAST : Direction.WEST)
                    : Blocks.OAK_PLANKS.defaultBlockState();
            add.accept(station.offset(x, 3, z), roof);
        }
    }
    private LevelOneWorksites() {}
}
