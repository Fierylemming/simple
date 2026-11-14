package com.simplecolonies.entity;

import java.util.ArrayDeque;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;

/** Conservative bounded check for roof and enclosure at walking height.
 * Doors are considered closable boundaries, not a guarantee against hostile door breaking. */
final class ShelterSafety {
    static boolean enclosed(ServerLevel level, BlockPos start) {
        var seen = new HashSet<BlockPos>();
        var todo = new ArrayDeque<BlockPos>();
        seen.add(start); todo.add(start);
        while (!todo.isEmpty()) {
            BlockPos pos = todo.removeFirst();
            if (Math.abs(pos.getX() - start.getX()) > 8 || Math.abs(pos.getZ() - start.getZ()) > 8
                    || seen.size() > 256 || !level.isLoaded(pos) || !level.isLoaded(pos.below()) || !level.isLoaded(pos.above(5))) return false;
            var floor = level.getBlockState(pos.below());
            if (!floor.isFaceSturdy(level, pos.below(), Direction.UP) || floor.is(Blocks.MAGMA_BLOCK)
                    || !level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) return false;
            boolean roof = false;
            for (int dy = 2; dy <= 5; dy++) {
                BlockPos p = pos.above(dy);
                if (level.getBlockState(p).isFaceSturdy(level, p, Direction.DOWN)) { roof = true; break; }
            }
            if (!roof) return false;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = pos.relative(direction);
                if (!level.isLoaded(next) || !level.isLoaded(next.above())) return false;
                var low = level.getBlockState(next);
                var high = level.getBlockState(next.above());
                // A standard two-block door can be closed after passage. Do not
                // treat fences, slabs or other partial barriers as proof of safety.
                if (low.getBlock() instanceof DoorBlock && high.getBlock() instanceof DoorBlock) continue;
                if (low.isCollisionShapeFullBlock(level, next) && high.isCollisionShapeFullBlock(level, next.above())) continue;
                if (seen.add(next)) todo.addLast(next);
            }
        }
        return true;
    }
    private ShelterSafety() {}
}
