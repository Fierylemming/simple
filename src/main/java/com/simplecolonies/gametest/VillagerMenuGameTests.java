package com.simplecolonies.gametest;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.entity.ModEntities;
import com.simplecolonies.menu.VillagerInteractionMenu;
import com.simplecolonies.villager.job.JobType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class VillagerMenuGameTests {
    @GameTest(template = "empty")
    public static void assignmentRequiresReputationResearchAndNearbyCitizen(GameTestHelper helper) {
        var worker = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(8, 1, 8));
        Colony colony = new Colony(-1000, CultureType.ROMAN, helper.absolutePos(new BlockPos(8, 1, 8)));
        worker.setColony(colony);
        var citizen = colony.getCitizens().stream().filter(c -> c.uuid.equals(worker.getUUID())).findFirst().orElseThrow();
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var near = helper.absolutePos(new BlockPos(7, 1, 8));
        player.moveTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
        var menu = new VillagerInteractionMenu(1, player.getInventory(), worker.getId(), colony, citizen);
        CompoundTag research = new CompoundTag();
        ListTag unlocked = new ListTag();
        unlocked.add(StringTag.valueOf("UNLOCK_FISHERMAN"));
        unlocked.add(StringTag.valueOf("UNLOCK_SOLDIER"));
        research.put("unlocked", unlocked);
        colony.getResearch().load(research);
        menu.clickMenuButton(player, VillagerInteractionMenu.BTN_SET_FISHERMAN);
        if (worker.getColonyJob() == JobType.FISHERMAN) helper.fail("Assigned without reputation");
        colony.addReputation(player, 30, "test");
        menu.clickMenuButton(player, VillagerInteractionMenu.BTN_SET_FISHERMAN);
        if (worker.getColonyJob() != JobType.FISHERMAN || citizen.job != JobType.FISHERMAN)
            helper.fail("Fisherman assignment was not applied to entity and citizen");
        menu.clickMenuButton(player, VillagerInteractionMenu.BTN_SET_GUARD);
        if (worker.getColonyJob() != JobType.FISHERMAN) helper.fail("Combat assignment bypassed 50-reputation requirement");
        colony.addReputation(player, 20, "test");
        menu.clickMenuButton(player, VillagerInteractionMenu.BTN_SET_GUARD);
        if (worker.getColonyJob() != JobType.GUARD || citizen.job != JobType.GUARD || !citizen.isSoldier)
            helper.fail("Guard assignment not applied");
        menu.clickMenuButton(player, VillagerInteractionMenu.BTN_SET_BAKER);
        if (worker.getColonyJob() != JobType.GUARD) helper.fail("Locked profession was assigned");
        player.moveTo(near.getX() + 40, near.getY(), near.getZ());
        menu.clickMenuButton(player, VillagerInteractionMenu.BTN_SET_UNEMPLOYED);
        if (worker.getColonyJob() != JobType.GUARD) helper.fail("Distant player reassigned a villager");
        player.moveTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
        menu.clickMenuButton(player, VillagerInteractionMenu.BTN_SET_UNEMPLOYED);
        if (worker.getColonyJob() != JobType.UNEMPLOYED || citizen.isSoldier) helper.fail("Could not unassign guard");
        helper.succeed();
    }
}
