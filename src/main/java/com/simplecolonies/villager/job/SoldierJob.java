package com.simplecolonies.villager.job;

import com.simplecolonies.colony.Colony;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

/**
 * Soldier: defends colony and can be commanded by leader to attack other colonies.
 * - When leader right-clicks soldier with command staff, soldier enters FOLLOW_LEADER mode and attacks what leader attacks.
 * - Otherwise patrols colony border and attacks hostiles + war enemies.
 */
public class SoldierJob extends AbstractJob {
    private Player commander; // leader commanding this soldier
    private boolean followingLeader = false;

    public SoldierJob(Villager villager, Colony colony) {
        super(villager, colony, JobType.SOLDIER);
    }

    public void setCommander(Player player, boolean follow) {
        this.commander = player;
        this.followingLeader = follow;
    }

    @Override
    protected void doWork(ServerLevel level) {
        // Soldiers use vanilla AI goals more than tick logic - this just handles commander follow
        state = JobState.WORKING;
        if (followingLeader && commander != null) {
            // Move towards commander if too far
            if (villager.distanceToSqr(commander) > 100) {
                villager.getNavigation().moveTo(commander, 1.2);
            }
            // Attack commander's target
            LivingEntity target = commander.getLastHurtMob();
            if (target != null && target.isAlive() && villager.distanceToSqr(target) < 5000) {
                villager.setTarget(target);
            }
        } else {
            // Patrol: if colony under attack (war), go to border
            if (colony.getState() == com.simplecolonies.colony.ColonyState.UNDER_ATTACK) {
                // Find nearest enemy colony member - simplified to nearest monster
                // Real would have cross-colony entity tracking
            }
        }
        ticksUntilNextWork = 20;
    }

    public static void attachGoals(Villager villager) {
        // Called when villager job set to soldier - adds combat AI
        // villager.goalSelector.addGoal(2, new MeleeAttackGoal(...));
        // villager.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(villager, Monster.class, true));
    }
}
