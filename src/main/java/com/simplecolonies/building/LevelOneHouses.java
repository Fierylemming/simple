package com.simplecolonies.building;

import com.simplecolonies.culture.CultureType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import java.util.function.BiConsumer;

/** Three compact footprints; each culture has its own accessible-material facade. */
final class LevelOneHouses {
    static final int VARIANTS = 3;
    static int variant(BlockPos p) { return Math.floorMod(p.getX() * 31 + p.getZ() * 17, VARIANTS); }
    static void generate(BlockPos origin, CultureType culture, int variant, BiConsumer<BlockPos, BlockState> add) {
        int halfX = variant == 1 ? 3 : 2;
        int halfZ = variant == 2 ? 3 : 2;
        // Raised wooden floor.
        for (int x = -halfX; x <= halfX; x++) for (int z = -halfZ; z <= halfZ; z++)
            add.accept(origin.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState());
        for (int y = 1; y <= 3; y++) for (int x = -halfX; x <= halfX; x++) for (int z = -halfZ; z <= halfZ; z++) {
            if (Math.abs(x) != halfX && Math.abs(z) != halfZ) continue;
            if (x == 0 && z == -halfZ && y <= 2) continue;
            boolean corner = Math.abs(x) == halfX && Math.abs(z) == halfZ;
            BlockState wall = wall(culture, variant, y, x, z, corner);
            add.accept(origin.offset(x, y, z), wall);
        }
        // Door is one placement; the builder places both halves atomically.
        add.accept(origin.offset(0, 1, -halfZ), Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        // A torch inside keeps the shelter lit; crafted from coal and sticks.
        add.accept(origin.offset(1, 1, 1), Blocks.TORCH.defaultBlockState());
        // A low roof with a stair eave and slab ridge, no inaccessible resources.
        for (int x = -halfX; x <= halfX; x++) for (int z = -halfZ; z <= halfZ; z++) {
            BlockState roof;
            if (x == -halfX || x == halfX) roof = Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, x < 0 ? Direction.EAST : Direction.WEST);
            else if (x == 0) roof = Blocks.OAK_SLAB.defaultBlockState();
            else roof = Blocks.OAK_PLANKS.defaultBlockState();
            add.accept(origin.offset(x, 4, z), roof);
        }
    }
    private static BlockState wall(CultureType culture, int variant, int y, int x, int z, boolean corner) {
        boolean stone = switch (culture) {
            case ROMAN -> y == 1 || (corner && y == 2);
            case GREEK -> corner || y == 1;
            case EGYPTIAN -> y != 2 || corner;
            case PERSIAN -> y == 1 || (y == 3 && !corner);
            case MESOPOTAMIAN -> y == 1 || (y == 2 && (Math.abs(x + z + variant) % 2 == 0));
        };
        return stone ? Blocks.COBBLESTONE.defaultBlockState()
                : corner ? Blocks.OAK_LOG.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState();
    }
    private LevelOneHouses() {}
}
