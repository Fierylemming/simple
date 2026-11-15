package com.simplecolonies.building;

import com.simplecolonies.culture.CultureType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.BiConsumer;

/** Level-one houses made exclusively from colony-gatherable oak, cobblestone and coal. */
final class CulturalHousePlans {
    private static int hx(int variant) { return variant == 0 ? 2 : 3; }
    private static int hz(int variant) { return variant == 2 ? 3 : 2; }

    // Third layout is an L-shaped house, rather than merely a stretched rectangle.
    static boolean footprint(int variant, int x, int z) {
        return Math.abs(x) <= hx(variant) && Math.abs(z) <= hz(variant)
                && !(variant == 2 && x >= 2 && z >= 2);
    }
    static boolean perimeter(int variant, int x, int z) {
        return footprint(variant, x, z) && (!footprint(variant, x - 1, z) || !footprint(variant, x + 1, z)
                || !footprint(variant, x, z - 1) || !footprint(variant, x, z + 1));
    }
    static void generate(BlockPos origin, CultureType culture, int variant, BiConsumer<BlockPos, BlockState> add) {
        if (variant < 0 || variant >= 3) throw new IllegalArgumentException("Invalid house variant");
        int halfX = hx(variant), halfZ = hz(variant);
        for (int x = -halfX; x <= halfX; x++) for (int z = -halfZ; z <= halfZ; z++) {
            if (footprint(variant, x, z)) add.accept(origin.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState());
        }
        for (int y = 1; y <= 3; y++) for (int x = -halfX; x <= halfX; x++) for (int z = -halfZ; z <= halfZ; z++) {
            if (!perimeter(variant, x, z) || (x == 0 && z == -halfZ && y <= 2)) continue;
            boolean endX = !footprint(variant, x - 1, z) || !footprint(variant, x + 1, z);
            boolean endZ = !footprint(variant, x, z - 1) || !footprint(variant, x, z + 1);
            boolean corner = endX && endZ;
            add.accept(origin.offset(x, y, z), wall(culture, variant, x, y, z, corner, endX));
        }
        add.accept(origin.offset(0, 1, -halfZ), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH));
        add.accept(origin.offset(1, 1, 1), Blocks.TORCH.defaultBlockState());
        // Complete under-roof prevents open seams; the raised layer provides distinct silhouettes.
        for (int x = -halfX; x <= halfX; x++) for (int z = -halfZ; z <= halfZ; z++) {
            if (!footprint(variant, x, z)) continue;
            BlockState ceiling = switch (culture) {
                case EGYPTIAN, MESOPOTAMIAN -> Blocks.COBBLESTONE.defaultBlockState();
                default -> Blocks.OAK_PLANKS.defaultBlockState();
            };
            add.accept(origin.offset(x, 4, z), ceiling);
            BlockState detail = roofDetail(culture, variant, x, z);
            if (detail != null) add.accept(origin.offset(x, 5, z), detail);
        }
    }
    private static BlockState wall(CultureType culture, int variant, int x, int y, int z, boolean corner, boolean endX) {
        boolean stone = switch (culture) {
            case ROMAN -> y == 1 || y == 3 || (corner && y == 2);
            case GREEK -> !corner && (y == 1 || y == 3);
            case EGYPTIAN -> y != 2 || (corner && variant != 1);
            case PERSIAN -> y == 1 || (y == 3 && ((x + z + variant) & 1) == 0);
            case MESOPOTAMIAN -> y == 1 || (y == 2 && endX) || (y == 3 && !corner);
        };
        if (stone) return Blocks.COBBLESTONE.defaultBlockState();
        return corner ? Blocks.OAK_LOG.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState();
    }
    private static BlockState roofDetail(CultureType culture, int variant, int x, int z) {
        return switch (culture) {
            // Raised timber ridge for a compact Roman tile-roof profile.
            case ROMAN -> x == 0 ? Blocks.OAK_SLAB.defaultBlockState() : null;
            // A gabled crest with stair slopes over a solid ceiling.
            case GREEK -> Math.abs(x) == 1 ? Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, x < 0 ? Direction.EAST : Direction.WEST)
                    : x == 0 ? Blocks.OAK_SLAB.defaultBlockState() : null;
            // Flat terrace with alternating parapet stones.
            case EGYPTIAN -> perimeter(variant, x, z) && ((x + z) & 1) == 0
                    ? Blocks.COBBLESTONE.defaultBlockState() : null;
            // Cross-ridge suggests an arched roof using only oak stairs and slabs.
            case PERSIAN -> x == 0 || z == 0 ? Blocks.OAK_SLAB.defaultBlockState() : null;
            // Stepped upper platform, set back from the exterior walls.
            case MESOPOTAMIAN -> !perimeter(variant, x, z) && Math.abs(x) <= 1 && Math.abs(z) <= 1
                    ? Blocks.COBBLESTONE.defaultBlockState() : null;
        };
    }
    private CulturalHousePlans() {}
}
