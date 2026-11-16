package com.simplecolonies.colony;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps mob explosions out of colony structures without closing the door on
 * sieges or deliberate demolition.
 *
 * <p>A creeper, ghast or wither explosion cannot break a block a colony owns
 * (its planned buildings, wings, town hall shell, protected plots and player
 * placements). Everything else about the explosion still happens: entities
 * take damage, so defenders and players can still be hurt while fighting in a
 * village.</p>
 *
 * <p>Player-caused explosions (TNT, beds, respawn anchors, end crystals and
 * player-owned projectiles) are deliberately left alone, so a siege or a player
 * who wants to clear a building can still do it.</p>
 */
public final class ColonyProtection {
    private ColonyProtection() {}

    /** True for explosions caused by a mob or by a projectile a mob owns. */
    public static boolean isMobExplosion(@Nullable Explosion explosion) {
        if (explosion == null) return false;
        var damage = explosion.getDamageSource();
        if (damage == null) return false;
        return isMobCaused(damage.getEntity()) || isMobCaused(damage.getDirectEntity());
    }

    /** Creepers and other mobs, plus projectiles such as ghast or wither shots. */
    static boolean isMobCaused(@Nullable Entity entity) {
        if (entity instanceof Mob) return true;
        if (entity instanceof Projectile projectile && projectile.getOwner() instanceof Mob) return true;
        return false;
    }

    /** True when a colony owns this block for griefing purposes. */
    public static boolean protectsBlock(ServerLevel level, BlockPos pos) {
        return ColonyManager.getInstance().protectsStructure(level, pos);
    }
}
