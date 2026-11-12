package com.simplecolonies.gametest;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.villager.job.SoldierJob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class GuardGameTests {
    @GameTest(template = "isolated", timeoutTicks = 1200)
    public static void guardApproachesAndAttacksHostile(GameTestHelper helper) {
        for (int x = 45; x <= 67; x++) for (int z = 50; z <= 62; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        var guard = helper.spawn(EntityType.VILLAGER, new BlockPos(51, 1, 56));
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(60, 1, 56));
        zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        Colony colony = new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(56, 1, 56)));
        SoldierJob job = new SoldierJob(guard, colony);
        for (int tick = 1; tick < 1200; tick++) helper.runAtTickTime(tick, () -> {
            float health = zombie.getHealth();
            job.tick(helper.getLevel());
            if (zombie.getHealth() < health && guard.distanceToSqr(zombie) > 4)
                helper.fail("Guard attacked a hostile remotely");
        });
        helper.succeedWhen(() -> {
            if (zombie.isAlive()) helper.fail("Guard " + job.getState() + " at " + guard.blockPosition().toShortString()
                    + ", hostile at " + zombie.blockPosition().toShortString() + ", distance " + guard.distanceToSqr(zombie)
                    + ", health " + zombie.getHealth());
        });
    }
}
