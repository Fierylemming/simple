package com.simplecolonies.entity;

import com.simplecolonies.villager.job.JobType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class ToolReassignmentGameTests {
    @GameTest(template = "empty")
    public static void jobChangesStowRealToolsAndSaveThem(GameTestHelper helper) {
        var villager = helper.spawn(ModEntities.COLONY_VILLAGER.get(), new BlockPos(8, 1, 8));
        villager.setJob(JobType.LUMBERJACK);
        ItemStack axe = new ItemStack(Items.STONE_AXE); axe.setDamageValue(17);
        villager.setItemSlot(EquipmentSlot.MAINHAND, axe);
        villager.setJob(JobType.MINER);
        if (!villager.getMainHandItem().isEmpty()) helper.fail("Axe blocked the new miner");
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE); pickaxe.setDamageValue(23);
        villager.setItemSlot(EquipmentSlot.MAINHAND, pickaxe);
        villager.setJob(JobType.LUMBERJACK);
        if (!villager.getMainHandItem().isEmpty()) helper.fail("Pickaxe blocked the new woodcutter");
        CompoundTag save = villager.saveWithoutId(new CompoundTag());
        var copy = ModEntities.COLONY_VILLAGER.get().create(helper.getLevel());
        if (copy == null) { helper.fail("Could not create saved worker"); return; }
        copy.load(save);
        boolean foundAxe = false, foundPickaxe = false;
        for (int i = 0; i < copy.getInventory().getContainerSize(); i++) {
            ItemStack tool = copy.getInventory().getItem(i);
            if (tool.is(Items.STONE_AXE) && tool.getDamageValue() == 17) foundAxe = true;
            if (tool.is(Items.IRON_PICKAXE) && tool.getDamageValue() == 23) foundPickaxe = true;
        }
        if (!foundAxe || !foundPickaxe) helper.fail("Stowed tool or its damage was lost on save");
        helper.succeed();
    }
}
