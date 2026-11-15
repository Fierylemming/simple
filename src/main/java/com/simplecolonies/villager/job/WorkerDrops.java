package com.simplecolonies.villager.job;

import com.simplecolonies.SimpleColonies;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** Capture only drop entities from a synchronous worker attack. */
@EventBusSubscriber(modid = SimpleColonies.MOD_ID)
public final class WorkerDrops {
    private record Attack(Villager worker, LivingEntity target, List<ItemEntity> drops) {}
    private static final ThreadLocal<Attack> ACTIVE = new ThreadLocal<>();

    static List<ItemEntity> attack(Villager worker, LivingEntity target, float damage) {
        Attack attack = new Attack(worker, target, new ArrayList<>());
        Attack previous = ACTIVE.get();
        ACTIVE.set(attack);
        try {
            target.hurt(worker.damageSources().mobAttack(worker), damage);
            return attack.drops();
        } finally {
            if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        Attack attack = ACTIVE.get();
        if (attack != null && event.getEntity() == attack.target() && event.getSource().getEntity() == attack.worker())
            attack.drops().addAll(event.getDrops());
    }
    private WorkerDrops() {}
}
