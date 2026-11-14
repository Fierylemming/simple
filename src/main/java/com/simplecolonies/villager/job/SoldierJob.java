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
    private java.util.UUID commanderId;

    public SoldierJob(Villager villager, Colony colony) {
        super(villager, colony, JobType.SOLDIER);
        if (villager instanceof com.simplecolonies.entity.ColonyVillager citizen)
            commanderId = citizen.getFollowCommanderId();
    }
    public void setCommander(Player player, boolean follow) {
        if (!follow && commanderId != null) villager.getNavigation().stop();
        commander = follow ? player : null;
        commanderId = follow ? player.getUUID() : null;
        if (villager instanceof com.simplecolonies.entity.ColonyVillager citizen)
            citizen.setFollowCommanderId(commanderId);
    }
    public boolean isFollowing() { return commanderId != null; }

    public boolean hasValidCommander(ServerLevel level) {
        if (commanderId == null) return false;
        // A leadership change revokes the order, including while its issuer is offline.
        if (!commanderId.equals(colony.getLeaderId())) {
            setCommander(null, false);
            return false;
        }
        if (commander == null || commander.isRemoved() || commander.level() != level)
            commander = level.getPlayerByUUID(commanderId);
        return commander != null && commander.isAlive() && !commander.isRemoved()
                && commander.level() == level && colony.isLeader(commander);
    }

    // A follow order must keep working after crossing the colony boundary.
    // An invalid, disconnected or dismissed commander restores the ordinary
    // return-to-colony behavior; no chunks are loaded to find a player.
    @Override protected boolean mayWorkOutsideColony(ServerLevel level) { return hasValidCommander(level); }

    @Override protected void onTick(ServerLevel level) {
        // Vanilla villagers panic and replace the combat path with a flee target.
        // During an active defense, let this job own navigation and suppress that
        // panic; ordinary villagers and reassigned guards retain vanilla behavior.
        if (state != JobState.GOING_TO_WORK && state != JobState.WORKING) return;
        var brain = villager.getBrain();
        brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_HOSTILE);
        brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HURT_BY);
        brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HURT_BY_ENTITY);
        brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
    }

    @Override protected void doWork(ServerLevel level) {
        LivingEntity target = null;
        if (hasValidCommander(level)) {
            LivingEntity recent = commander.getLastHurtMob();
            if (recent instanceof Monster && recent.isAlive() && villager.distanceToSqr(recent) < 400) target = recent;
        }
        if (target == null && level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD))
            target = level.getEntitiesOfClass(Monster.class, villager.getBoundingBox().inflate(16, 6, 16),
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
                var weapon = villager.getMainHandItem();
                float damage = weapon.is(net.minecraft.world.item.Items.IRON_SWORD) ? 6.0f
                        : weapon.is(net.minecraft.world.item.Items.STONE_SWORD) ? 5.0f : 2.0f;
                if (target.hurt(level.damageSources().mobAttack(villager), damage)
                        && (weapon.is(net.minecraft.world.item.Items.IRON_SWORD) || weapon.is(net.minecraft.world.item.Items.STONE_SWORD)))
                    weapon.hurtAndBreak(1, villager, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                state = JobState.WORKING;
            }
            ticksUntilNextWork = 20;
            return;
        }
        if (hasValidCommander(level) && villager.distanceToSqr(commander) > 36) {
            villager.getNavigation().moveTo(commander, 1.0);
            state = JobState.GOING_TO_WORK;
        } else {
            // Defense and following the commander always take priority over resupply.
            var equipment = GuardEquipment.collectChestplate(level, colony, villager);
            if (equipment == GuardEquipment.Result.NONE) equipment = GuardEquipment.collectSword(level, colony, villager);
            state = switch (equipment) {
                case MOVING -> JobState.GOING_TO_WORK;
                case EQUIPPED -> JobState.WORKING;
                case NONE -> JobState.IDLE;
            };
        }
        ticksUntilNextWork = 40;
    }

    // Combat is tick-driven rather than attaching goals that persist after reassignment.
    public static void attachGoals(Villager villager) {}
}
