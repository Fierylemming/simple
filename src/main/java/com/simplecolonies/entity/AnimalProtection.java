package com.simplecolonies.entity;

import java.util.UUID;
import net.minecraft.world.entity.animal.Animal;

/** Player-marked animals are never worked by colony livestock jobs. This is an
 * opt-out, not a way to infer the owner of every unnamed vanilla animal. */
public final class AnimalProtection {
    private static final String OWNER = "simplecolonies:protected_by";
    private AnimalProtection() {}

    public static boolean protectedFromWorkers(Animal animal) {
        return animal.hasCustomName() || animal.getPersistentData().hasUUID(OWNER);
    }

    public static UUID protector(Animal animal) {
        return animal.getPersistentData().hasUUID(OWNER) ? animal.getPersistentData().getUUID(OWNER) : null;
    }

    public static void protect(Animal animal, UUID player) {
        animal.getPersistentData().putUUID(OWNER, player);
    }

    public static void unprotect(Animal animal) {
        animal.getPersistentData().remove(OWNER);
    }
}
