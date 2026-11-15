package com.simplecolonies.colony;

import com.simplecolonies.culture.CultureType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiplomacyTest {
    @Test void bilateralDeadlinesSurviveReloadAndSkip() {
        var a = new Colony(1, CultureType.ROMAN, BlockPos.ZERO);
        var b = new Colony(2, CultureType.GREEK, new BlockPos(100, 0, 0));
        a.getDiplomacy().declareWar(a, b, 100);
        var left = new Diplomacy(); left.load(a.getDiplomacy().save());
        var right = new Diplomacy(); right.load(b.getDiplomacy().save());
        assertEquals(Diplomacy.Relation.WAR, left.getRelationAt(2, 113));
        assertEquals(Diplomacy.Relation.WAR, right.getRelationAt(1, 113));
        assertEquals(Diplomacy.Relation.NEUTRAL, left.getRelationAt(2, 114));
        assertEquals(Diplomacy.Relation.NEUTRAL, right.getRelationAt(1, 114));
        left.updateDay(113);
        assertEquals(1, left.getAll().iterator().next().daysRemaining);
        left.updateDay(1000);
        assertEquals(Diplomacy.Relation.NEUTRAL, left.getRelation(2));
        assertEquals(0, left.getAll().iterator().next().daysRemaining);
    }

    @Test void peaceExpiresAndAllianceHasNoDeadline() {
        var a = new Colony(1, CultureType.ROMAN, BlockPos.ZERO);
        var b = new Colony(2, CultureType.GREEK, new BlockPos(100, 0, 0));
        a.getDiplomacy().makePeace(a, b, 10);
        assertEquals(Diplomacy.Relation.PEACE_TREATY, b.getDiplomacy().getRelationAt(1, 39));
        assertEquals(Diplomacy.Relation.NEUTRAL, b.getDiplomacy().getRelationAt(1, 40));
        a.getDiplomacy().proposeAlliance(a, b);
        a.getDiplomacy().updateDay(1000);
        assertEquals(Diplomacy.Relation.ALLIED, a.getDiplomacy().getRelationAt(2, 1000));
        assertEquals(Diplomacy.Relation.ALLIED, b.getDiplomacy().getRelationAt(1, 1000));
    }

    @Test void legacyPairsMigrateTogetherAndPartiallyMigratedPairDoesNotExtend() {
        var a = new Colony(1, CultureType.ROMAN, BlockPos.ZERO);
        var b = new Colony(2, CultureType.GREEK, new BlockPos(100, 0, 0));
        var left = new CompoundTag(); var right = new CompoundTag();
        var l = new ListTag(); var r = new ListTag();
        var le = new CompoundTag(); le.putInt("other", 2); le.putString("relation", "WAR"); le.putInt("days", 5); l.add(le);
        var re = new CompoundTag(); re.putInt("other", 1); re.putString("relation", "WAR"); re.putInt("days", 3); r.add(re);
        left.put("relations", l); right.put("relations", r);
        a.getDiplomacy().load(left); b.getDiplomacy().load(right);
        Diplomacy.migrateLegacyRelations(java.util.List.of(a, b), 20);
        assertEquals(Diplomacy.Relation.WAR, a.getDiplomacy().getRelationAt(2, 22));
        assertEquals(Diplomacy.Relation.NEUTRAL, a.getDiplomacy().getRelationAt(2, 23));
        assertEquals(Diplomacy.Relation.NEUTRAL, b.getDiplomacy().getRelationAt(1, 23));
        // Simulate one side having already been upgraded, while the other is old.
        b.getDiplomacy().load(right);
        Diplomacy.migrateLegacyRelations(java.util.List.of(a, b), 22);
        assertEquals(Diplomacy.Relation.NEUTRAL, b.getDiplomacy().getRelationAt(1, 23));
    }

    @Test void oldTimedRelationsMigrateWithoutDiscardingRemainingDays() {
        var root = new CompoundTag(); var list = new ListTag(); var entry = new CompoundTag();
        entry.putInt("other", 2); entry.putString("relation", "WAR"); entry.putInt("days", 3);
        list.add(entry); root.put("relations", list);
        var diplomacy = new Diplomacy(); diplomacy.load(root); diplomacy.updateDay(25);
        assertEquals(Diplomacy.Relation.WAR, diplomacy.getRelationAt(2, 27));
        assertEquals(Diplomacy.Relation.NEUTRAL, diplomacy.getRelationAt(2, 28));
        var restored = new Diplomacy(); restored.load(diplomacy.save()); restored.updateDay(28);
        assertEquals(Diplomacy.Relation.NEUTRAL, restored.getRelation(2));
    }
}
