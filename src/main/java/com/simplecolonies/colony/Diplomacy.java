package com.simplecolonies.colony;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/**
 * Alliance / war between colonies. Leader can propose alliance via Town Hall.
 * Wars are declared, then soldier villagers from both sides fight.
 * Alliances give shared defense + trade bonuses.
 */
public class Diplomacy {
    public enum Relation { NEUTRAL, ALLIED, WAR, PEACE_TREATY }

    public static class RelationData {
        public int otherColonyId;
        public Relation relation;
        public int daysRemaining; // for wars/peace treaties

        public RelationData(int otherColonyId, Relation relation, int daysRemaining) {
            this.otherColonyId = otherColonyId;
            this.relation = relation;
            this.daysRemaining = daysRemaining;
        }
    }

    private final Map<Integer, RelationData> relations = new HashMap<>();

    public Relation getRelation(int otherId) {
        return relations.getOrDefault(otherId, new RelationData(otherId, Relation.NEUTRAL, 0)).relation;
    }

    public void declareWar(Colony self, Colony other) {
        relations.put(other.getId(), new RelationData(other.getId(), Relation.WAR, 14));
        other.getDiplomacy().relations.put(self.getId(), new RelationData(self.getId(), Relation.WAR, 14));
    }

    public void proposeAlliance(Colony self, Colony other) {
        relations.put(other.getId(), new RelationData(other.getId(), Relation.ALLIED, -1));
        other.getDiplomacy().relations.put(self.getId(), new RelationData(self.getId(), Relation.ALLIED, -1));
    }

    public void makePeace(Colony self, Colony other) {
        relations.put(other.getId(), new RelationData(other.getId(), Relation.PEACE_TREATY, 30));
        other.getDiplomacy().relations.put(self.getId(), new RelationData(self.getId(), Relation.PEACE_TREATY, 30));
    }

    public Collection<RelationData> getAll() { return relations.values(); }

    public void tick(Colony self, ServerLevel level) {
        // Decrement war duration, auto peace
        Iterator<Map.Entry<Integer, RelationData>> it = relations.entrySet().iterator();
        while (it.hasNext()) {
            RelationData rd = it.next().getValue();
            if (rd.relation == Relation.WAR && rd.daysRemaining > 0) {
                // decrement each new day
                if (level.getGameTime() % 24000 == 0) {
                    rd.daysRemaining--;
                    if (rd.daysRemaining <= 0) {
                        rd.relation = Relation.NEUTRAL;
                    }
                }
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
            RelationData rd = new RelationData(t.getInt("other"), Relation.valueOf(t.getString("relation")), t.getInt("days"));
            relations.put(rd.otherColonyId, rd);
        }
    }
}
