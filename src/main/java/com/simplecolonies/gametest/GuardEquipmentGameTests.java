package com.simplecolonies.gametest;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.culture.CultureType;
import com.simplecolonies.villager.ColonyProfessions;
import com.simplecolonies.villager.job.JobType;
import com.simplecolonies.villager.job.LeatherworkerJob;
import com.simplecolonies.villager.job.SoldierJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("simplecolonies")
@PrefixGameTestTemplate(false)
public class GuardEquipmentGameTests {
    private static final BlockPos CHEST = new BlockPos(60, 1, 56);
    private static Colony setup(GameTestHelper helper) {
        for (int x = 40; x <= 72; x++) for (int z = 40; z <= 72; z++) helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
        helper.setBlock(CHEST, Blocks.CHEST);
        return new Colony(0, CultureType.ROMAN, helper.absolutePos(new BlockPos(56, 1, 56)));
    }
    @GameTest(template = "isolated", timeoutTicks = 1300)
    public static void guardWalksToRealArmorAndPreservesDamage(GameTestHelper helper) {
        Colony colony = setup(helper);
        var chest = (Container) helper.getBlockEntity(CHEST);
        var plate = new ItemStack(Items.LEATHER_CHESTPLATE);
        plate.setDamageValue(17);
        chest.setItem(0, plate);
        var guard = helper.spawn(EntityType.VILLAGER, new BlockPos(50, 1, 56));
        var job = new SoldierJob(guard, colony);
        for (int tick = 1; tick < 1300; tick++) helper.runAtTickTime(tick, () -> {
            boolean wasEmpty = guard.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
            job.tick(helper.getLevel());
            if (wasEmpty && !guard.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                    && guard.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(CHEST))) > 6.25)
                helper.fail("Guard collected armor remotely");
        });
        helper.succeedWhen(() -> {
            var equipped = guard.getItemBySlot(EquipmentSlot.CHEST);
            if (!equipped.is(Items.LEATHER_CHESTPLATE) || equipped.getDamageValue() != 17 || !chest.getItem(0).isEmpty())
                helper.fail("Armor not collected correctly: " + equipped + " state=" + job.getState() + " pos=" + guard.position());
            CompoundTag saved = guard.saveWithoutId(new CompoundTag());
            var copy = EntityType.VILLAGER.create(helper.getLevel());
            if (copy == null) { helper.fail("Unable to reload guard"); return; }
            copy.load(saved);
            if (!copy.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE)
                    || copy.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() != 17)
                helper.fail("Equipped armor did not survive entity save/reload");
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 600)
    public static void doesNotReplaceArmorOrTakeThroughWall(GameTestHelper helper) {
        Colony colony = setup(helper);
        var chest = (Container) helper.getBlockEntity(CHEST);
        chest.setItem(0, new ItemStack(Items.LEATHER_CHESTPLATE));
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (Math.abs(x) != 1 && Math.abs(z) != 1) continue;
            helper.setBlock(CHEST.offset(x, 0, z), Blocks.STONE);
            helper.setBlock(CHEST.offset(x, 1, z), Blocks.STONE);
        }
        var guard = helper.spawn(EntityType.VILLAGER, new BlockPos(56, 1, 56));
        var armored = helper.spawn(EntityType.VILLAGER, new BlockPos(58, 1, 53));
        armored.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        var first = new SoldierJob(guard, colony);
        var second = new SoldierJob(armored, colony);
        for (int tick = 1; tick < 600; tick++) helper.runAtTickTime(tick, () -> { first.tick(helper.getLevel()); second.tick(helper.getLevel()); });
        helper.runAtTickTime(500, () -> {
            if (!guard.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                    || !armored.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)
                    || !chest.getItem(0).is(Items.LEATHER_CHESTPLATE))
                helper.fail("Guard took unreachable armor or replaced existing armor");
            helper.succeed();
        });
    }

    @GameTest(template = "isolated", timeoutTicks = 1800)
    public static void leatherworkerSuppliesGuardAndReplacesSpare(GameTestHelper helper) {
        Colony colony = setup(helper);
        var chest = (Container) helper.getBlockEntity(CHEST);
        chest.setItem(0, new ItemStack(Items.LEATHER, 16));
        BlockPos site = new BlockPos(54, 1, 56);
        helper.setBlock(site, Blocks.CAULDRON);
        var crafter = helper.spawn(EntityType.VILLAGER, new BlockPos(52, 1, 56));
        crafter.setVillagerData(crafter.getVillagerData().setProfession(ColonyProfessions.profession(JobType.LEATHERWORKER)));
        crafter.refreshBrain(helper.getLevel());
        crafter.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(site)));
        var guard = helper.spawn(EntityType.VILLAGER, new BlockPos(48, 1, 56));
        var crafting = new LeatherworkerJob(crafter, colony);
        var defense = new SoldierJob(guard, colony);
        for (int tick = 1; tick < 1800; tick++) helper.runAtTickTime(tick, () -> {
            crafting.tick(helper.getLevel()); defense.tick(helper.getLevel());
        });
        helper.runAtTickTime(1600, () -> {
            if (!guard.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE)
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:leather") != 0
                    || colony.getWarehouseCount(helper.getLevel(), "minecraft:leather_chestplate") != 1)
                helper.fail("Leatherworker-to-guard chain failed: stock=" + colony.getWarehouseSnapshot(helper.getLevel())
                        + " armor=" + guard.getItemBySlot(EquipmentSlot.CHEST) + " guard=" + defense.getState());
            helper.succeed();
        });
    }
}
