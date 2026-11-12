package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

/** Close-range defensive combat against hostiles; commander following remains optional. */
public class SoldierJob extends AbstractJob {
    private Player commander;
    private boolean followingLeader;

    public SoldierJob(Villager villager, Colony colony) { super(villager, colony, JobType.SOLDIER); }
    public void setCommander(Player player, boolean follow) { commander = player; followingLeader = follow; }

    @Override protected void doWork(ServerLevel level) {
        LivingEntity target = null;
        if (followingLeader && commander != null && commander.isAlive() && commander.level() == level) {
            LivingEntity recent = commander.getLastHurtMob();
            if (recent instanceof Monster && recent.isAlive() && villager.distanceToSqr(recent) < 400) target = recent;
        }
        if (target == null) target = level.getEntitiesOfClass(Monster.class, villager.getBoundingBox().inflate(16, 6, 16),
                        m -> m.isAlive() && colony.isInside(m.blockPosition()))
                .stream().min(java.util.Comparator.comparingDouble(villager::distanceToSqr)).orElse(null);
        if (target != null) {
            villager.getLookControl().setLookAt(target);
            if (villager.distanceToSqr(target) > 4 || !villager.hasLineOfSight(target)) {
                var path = villager.getNavigation().createPath(target, 0);
                if (path == null || !path.canReach()) { state = JobState.STUCK; ticksUntilNextWork = 30; return; }
                villager.getNavigation().moveTo(path, 1.0);
                state = JobState.GOING_TO_WORK;
            } else {
                villager.swing(InteractionHand.MAIN_HAND);
                target.hurt(level.damageSources().mobAttack(villager), 2.0f);
                state = JobState.WORKING;
            }
            ticksUntilNextWork = 20;
            return;
        }
        if (followingLeader && commander != null && commander.isAlive() && commander.level() == level
                && villager.distanceToSqr(commander) > 36) {
            villager.getNavigation().moveTo(commander, 1.0);
            state = JobState.GOING_TO_WORK;
        } else state = JobState.IDLE;
        ticksUntilNextWork = 40;
    }

    // Combat is tick-driven rather than attaching goals that persist after reassignment.
    public static void attachGoals(Villager villager) {}
}
