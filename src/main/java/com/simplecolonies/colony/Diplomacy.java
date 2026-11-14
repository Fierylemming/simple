package com.simplecolonies.colony;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/** Persistent bilateral relations. Temporary relations use the world day, not
 * an exact server-tick modulo, so both sides agree after unloads and time skips. */
public class Diplomacy {
    public enum Relation { NEUTRAL, ALLIED, WAR, PEACE_TREATY }
    private static final long NO_DEADLINE = Long.MIN_VALUE;

    public static class RelationData {
        public int otherColonyId;
        public Relation relation;
        public int daysRemaining;
        private long expiresDay = NO_DEADLINE;

        public RelationData(int otherColonyId, Relation relation, int daysRemaining) {
            this.otherColonyId = otherColonyId;
            this.relation = relation;
            this.daysRemaining = daysRemaining;
        }
    }

    private final Map<Integer, RelationData> relations = new HashMap<>();

    public Relation getRelation(int otherId) {
        var data = relations.get(otherId);
        return data == null ? Relation.NEUTRAL : data.relation;
    }

    /** Read a timed relation accurately even if that colony's chunk has not ticked yet. */
    public Relation getRelationAt(int otherId, long day) {
        var data = relations.get(otherId);
        if (data == null) return Relation.NEUTRAL;
        if (data.expiresDay != NO_DEADLINE && day >= data.expiresDay) return Relation.NEUTRAL;
        return data.relation;
    }

    private void setBilateral(Colony self, Colony other, Relation relation, int duration, long day) {
        if (self == other || self.getId() == other.getId()) return;
        var first = new RelationData(other.getId(), relation, duration);
        var second = new RelationData(self.getId(), relation, duration);
        if (duration > 0) {
            long deadline;
            try { deadline = Math.addExact(day, duration); }
            catch (ArithmeticException e) { deadline = Long.MAX_VALUE; }
            first.expiresDay = second.expiresDay = deadline;
        }
        relations.put(other.getId(), first);
        other.getDiplomacy().relations.put(self.getId(), second);
        ColonyManager.getInstance().markDirty(self);
    }

    public void declareWar(Colony self, Colony other, long day) { setBilateral(self, other, Relation.WAR, 14, day); }
    public void proposeAlliance(Colony self, Colony other) { setBilateral(self, other, Relation.ALLIED, -1, 0); }
    public void makePeace(Colony self, Colony other, long day) { setBilateral(self, other, Relation.PEACE_TREATY, 30, day); }
    public Collection<RelationData> getAll() { return Collections.unmodifiableCollection(relations.values()); }

    public void tick(Colony self, ServerLevel level) { updateDay(Math.floorDiv(level.getDayTime(), 24000L)); }

    /** Migrate all colonies together at server load, including unloaded ones.
     * A partially migrated pair inherits the existing earlier deadline rather
     * than extending its treaty when the second colony is finally observed. */
    static void migrateLegacyRelations(Collection<Colony> colonies, long day) {
        Map<Integer, Colony> byId = new HashMap<>();
        for (var colony : colonies) byId.put(colony.getId(), colony);
        for (var colony : colonies) for (var rd : colony.getDiplomacy().relations.values()) {
            if ((rd.relation != Relation.WAR && rd.relation != Relation.PEACE_TREATY) || rd.expiresDay != NO_DEADLINE) continue;
            var other = byId.get(rd.otherColonyId);
            var reverse = other == null ? null : other.getDiplomacy().relations.get(colony.getId());
            if (reverse != null && reverse.relation == rd.relation && reverse.expiresDay != NO_DEADLINE) {
                rd.expiresDay = reverse.expiresDay;
            } else if (reverse != null && reverse.relation == rd.relation && reverse.expiresDay == NO_DEADLINE) {
                int days = Math.max(0, Math.min(rd.daysRemaining, reverse.daysRemaining));
                long deadline;
                try { deadline = Math.addExact(day, days); } catch (ArithmeticException e) { deadline = Long.MAX_VALUE; }
                rd.expiresDay = reverse.expiresDay = deadline;
            }
        }
        for (var colony : colonies) colony.getDiplomacy().updateDay(day);
    }

    /** Legacy unpaired relations retain their remaining duration from the migration day. */
    void updateDay(long day) {
        for (var rd : relations.values()) {
            if (rd.relation != Relation.WAR && rd.relation != Relation.PEACE_TREATY) continue;
            if (rd.expiresDay == NO_DEADLINE) {
                if (rd.daysRemaining <= 0) { rd.relation = Relation.NEUTRAL; rd.daysRemaining = 0; continue; }
                try { rd.expiresDay = Math.addExact(day, rd.daysRemaining); }
                catch (ArithmeticException e) { rd.expiresDay = Long.MAX_VALUE; }
            }
            if (day >= rd.expiresDay) { rd.relation = Relation.NEUTRAL; rd.daysRemaining = 0; }
            else {
                long remaining;
                try { remaining = Math.subtractExact(rd.expiresDay, day); }
                catch (ArithmeticException e) { remaining = Integer.MAX_VALUE; }
                rd.daysRemaining = (int) Math.min(remaining, Integer.MAX_VALUE);
            }
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (RelationData rd : relations.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("other", rd.otherColonyId);
            t.putString("relation", rd.relation.name());
            t.putInt("days", rd.daysRemaining);
            if (rd.expiresDay != NO_DEADLINE) t.putLong("expiresDay", rd.expiresDay);
            list.add(t);
        }
        tag.put("relations", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        relations.clear();
        ListTag list = tag.getList("relations", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            try {
                RelationData rd = new RelationData(t.getInt("other"), Relation.valueOf(t.getString("relation")), t.getInt("days"));
                if (t.contains("expiresDay")) rd.expiresDay = t.getLong("expiresDay");
                relations.put(rd.otherColonyId, rd);
            } catch (IllegalArgumentException ignored) { /* Unknown future relation; leave neutral. */ }
        }
    }
}
