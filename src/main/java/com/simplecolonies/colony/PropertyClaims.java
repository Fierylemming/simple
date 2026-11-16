package com.simplecolonies.colony;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Bounded memory of blocks a player physically placed inside a colony.
 *
 * <p>Natural-block heuristics cannot tell player-placed stone from terrain, and
 * the protected plot only covers an explicitly designated column. This registry
 * records blocks as players place them, so miners, woodcutters, farmers and
 * builders can leave a player's own building alone wherever it stands.</p>
 *
 * <p>This is deliberately best effort and is not a claim system: it only covers
 * blocks placed by a player while inside a loaded colony (commands, worldgen and
 * blocks placed before the colony existed are not known). Claims are evicted
 * oldest-first beyond {@link #MAX_CLAIMS}, pruned when their block disappears,
 * and never restrict players or other mobs.</p>
 */
public final class PropertyClaims {
    /** Cap per colony so a large build or griefing cannot grow saved data forever. */
    public static final int MAX_CLAIMS = 8192;

    // Insertion order drives oldest-first eviction.
    private final LinkedHashSet<Long> positions = new LinkedHashSet<>();
    // Chunk bucket index keeps proximity checks cheap during job safety scans.
    private final Map<Long, Set<Long>> byChunk = new HashMap<>();

    private static long chunkKey(int x, int z) {
        return ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL);
    }

    /** @return true when this position was not already recorded. */
    public boolean record(BlockPos pos) {
        long key = pos.asLong();
        if (!positions.add(key)) return false;
        byChunk.computeIfAbsent(chunkKey(pos.getX(), pos.getZ()), ignored -> new LinkedHashSet<>()).add(key);
        while (positions.size() > MAX_CLAIMS) {
            Iterator<Long> oldest = positions.iterator();
            if (!oldest.hasNext()) break;
            drop(oldest.next());
        }
        return true;
    }

    public boolean remove(BlockPos pos) {
        long key = pos.asLong();
        return positions.contains(key) && drop(key);
    }

    public boolean contains(BlockPos pos) {
        return positions.contains(pos.asLong());
    }

    /**
     * Exact-match proximity test, using {@code margin} blocks in every direction
     * (0 means only the given position). It never loads a chunk and is safe to
     * call from job logic.
     */
    public boolean near(BlockPos pos, int margin) {
        if (positions.isEmpty()) return false;
        int m = Math.max(0, margin);
        int minChunkX = (pos.getX() - m) >> 4, maxChunkX = (pos.getX() + m) >> 4;
        int minChunkZ = (pos.getZ() - m) >> 4, maxChunkZ = (pos.getZ() + m) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                Set<Long> bucket = byChunk.get(chunkKey(cx << 4, cz << 4));
                if (bucket == null) continue;
                for (long key : bucket) {
                    BlockPos claimed = BlockPos.of(key);
                    if (Math.abs((long) claimed.getX() - pos.getX()) <= m
                            && Math.abs((long) claimed.getY() - pos.getY()) <= m
                            && Math.abs((long) claimed.getZ() - pos.getZ()) <= m) return true;
                }
            }
        }
        return false;
    }

    /** Remove claims whose block is gone (or that are explicitly unwanted). */
    public int prune(Predicate<BlockPos> keep) {
        List<Long> stale = new ArrayList<>();
        for (long key : positions) {
            if (!keep.test(BlockPos.of(key))) stale.add(key);
        }
        for (long key : stale) drop(key);
        return stale.size();
    }

    public void clear() {
        positions.clear();
        byChunk.clear();
    }

    public int size() {
        return positions.size();
    }

    public boolean isEmpty() {
        return positions.isEmpty();
    }

    private boolean drop(long key) {
        if (!positions.remove(key)) return false;
        BlockPos pos = BlockPos.of(key);
        long chunk = chunkKey(pos.getX(), pos.getZ());
        Set<Long> bucket = byChunk.get(chunk);
        if (bucket != null) {
            bucket.remove(key);
            if (bucket.isEmpty()) byChunk.remove(chunk);
        }
        return true;
    }

    public CompoundTag save() {
        long[] keys = new long[positions.size()];
        int i = 0;
        for (long key : positions) keys[i++] = key;
        CompoundTag tag = new CompoundTag();
        tag.put("positions", new LongArrayTag(keys));
        return tag;
    }

    public void load(CompoundTag tag) {
        clear();
        for (long key : tag.getLongArray("positions")) {
            BlockPos pos = BlockPos.of(key);
            if (!positions.add(key)) continue;
            byChunk.computeIfAbsent(chunkKey(pos.getX(), pos.getZ()), ignored -> new LinkedHashSet<>()).add(key);
        }
        // Old saves may exceed a later, smaller cap. Keep the newest entries.
        while (positions.size() > MAX_CLAIMS) {
            Iterator<Long> oldest = positions.iterator();
            if (!oldest.hasNext()) break;
            drop(oldest.next());
        }
    }
}
