package com.simplecolonies.villager;

import com.google.common.collect.ImmutableSet;
import com.simplecolonies.SimpleColonies;
import com.simplecolonies.villager.job.JobType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.EnumMap;
import java.util.Map;

/** Colony-only professions never use vanilla's autonomous job-site acquisition. */
public final class ColonyProfessions {
    public static final DeferredRegister<PoiType> POIS = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, SimpleColonies.MOD_ID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, SimpleColonies.MOD_ID);
    private static final DeferredHolder<PoiType, PoiType> ANVIL = poi("builder", Blocks.ANVIL);
    private static final DeferredHolder<PoiType, PoiType> FURNACE = poi("miner", Blocks.FURNACE);
    private static final DeferredHolder<PoiType, PoiType> CAMPFIRE = POIS.register("woodcutter", () -> new PoiType(
            Blocks.CAMPFIRE.getStateDefinition().getPossibleStates().stream().filter(s -> !s.getValue(CampfireBlock.LIT)).collect(ImmutableSet.toImmutableSet()), 1, 1));
    private static final Map<JobType, DeferredHolder<VillagerProfession, VillagerProfession>> JOBS = new EnumMap<>(JobType.class);
    static {
        JOBS.put(JobType.UNEMPLOYED, PROFESSIONS.register("unemployed", () -> new VillagerProfession(
                "simplecolonies:unemployed", holder -> false, holder -> false,
                ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_MASON)));
        register(JobType.FARMER, PoiTypes.FARMER);
        register(JobType.LUMBERJACK, CAMPFIRE.getKey());
        register(JobType.MINER, FURNACE.getKey());
        register(JobType.BUILDER, ANVIL.getKey());
        register(JobType.BUTCHER, PoiTypes.BUTCHER);
        register(JobType.LEATHERWORKER, PoiTypes.LEATHERWORKER);
        register(JobType.BLACKSMITH, PoiTypes.TOOLSMITH);
        register(JobType.SOLDIER, PoiTypes.WEAPONSMITH);
        register(JobType.GUARD, PoiTypes.WEAPONSMITH);
        register(JobType.BAKER, PoiTypes.BUTCHER);
        register(JobType.FISHERMAN, PoiTypes.FISHERMAN);
        register(JobType.COURIER, PoiTypes.FISHERMAN);
        register(JobType.SHEPHERD, PoiTypes.SHEPHERD);
    }
    private static DeferredHolder<PoiType, PoiType> poi(String name, Block block) {
        return POIS.register(name, () -> new PoiType(ImmutableSet.copyOf(block.getStateDefinition().getPossibleStates()), 1, 1));
    }
    private static void register(JobType job, ResourceKey<PoiType> poi) {
        String name = job.name().toLowerCase(java.util.Locale.ROOT);
        JOBS.put(job, PROFESSIONS.register(name, () -> new VillagerProfession(
                ResourceLocation.fromNamespaceAndPath(SimpleColonies.MOD_ID, name).toString(),
                holder -> holder.is(poi), holder -> false, ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_MASON)));
    }
    public static VillagerProfession profession(JobType job) {
        var holder = JOBS.get(job);
        return holder == null ? VillagerProfession.NONE : holder.get();
    }
    public static void addTrades(VillagerTradesEvent event) {
        for (var entry : JOBS.entrySet()) {
            if (event.getType() != entry.getValue().get()) continue;
            VillagerProfession base = switch (entry.getKey()) {
                case FARMER, BAKER -> VillagerProfession.FARMER;
                case LUMBERJACK -> VillagerProfession.FLETCHER;
                case MINER, BUILDER -> VillagerProfession.MASON;
                case BUTCHER -> VillagerProfession.BUTCHER;
                case LEATHERWORKER -> VillagerProfession.LEATHERWORKER;
                case BLACKSMITH -> VillagerProfession.TOOLSMITH;
                case SOLDIER, GUARD -> VillagerProfession.WEAPONSMITH;
                case FISHERMAN, COURIER -> VillagerProfession.FISHERMAN;
                case SHEPHERD -> VillagerProfession.SHEPHERD;
                default -> VillagerProfession.NONE;
            };
            var trades = VillagerTrades.TRADES.get(base);
            if (trades != null) for (int level = 1; level <= 5; level++) {
                var listings = trades.get(level);
                if (listings != null) java.util.Collections.addAll(event.getTrades().get(level), listings);
            }
        }
    }
    private ColonyProfessions() {}
}
