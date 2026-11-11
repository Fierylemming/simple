package com.simplecolonies;

import com.simplecolonies.block.ModBlocks;
import com.simplecolonies.colony.ColonyManager;
import com.simplecolonies.config.ColonyConfig;
import com.simplecolonies.entity.ModEntities;
import com.simplecolonies.item.ModItems;
import com.simplecolonies.worldgen.ModWorldGen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple Colonies - Ancient Villages
 * A MineColonies-inspired mod that is deliberately simpler but deeper in simulation.
 *
 * Design pillars:
 *  1. Self-sufficiency: every villager has a job that feeds the colony's supply chain
 *  2. Cultural identity: 5 ancient styles mapped to climate (heat) biomes
 *  3. Player as leader: reputation -> election -> command buildings / armies / diplomacy
 *  4. Natural expansion: builder villagers consume real resources and grow the colony over time
 *  5. Create Aeronautics compatible: runs on NeoForge 1.21.1 with optional logistics integration
 */
@Mod(SimpleColonies.MOD_ID)
public class SimpleColonies {
    public static final String MOD_ID = "simplecolonies";
    public static final Logger LOGGER = LoggerFactory.getLogger(SimpleColonies.class);

    public SimpleColonies(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[SimpleColonies] Initializing {} - Ancient Villages for MC 1.21.1 / NeoForge {}", MOD_ID, "21.1.265");

        // Register deferred registers
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModBlocks.BLOCK_ENTITIES.register(modEventBus);

        // Config
        modContainer.registerConfig(ModConfig.Type.COMMON, ColonyConfig.SPEC, "simplecolonies-common.toml");
        modContainer.registerConfig(ModConfig.Type.CLIENT, ColonyConfig.CLIENT_SPEC, "simplecolonies-client.toml");

        modEventBus.addListener(this::commonSetup);

        // Server lifecycle for colony persistence
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);

        LOGGER.info("[SimpleColonies] Registered blocks, items, entities and worldgen. Compatible with Create Aeronautics if present.");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModWorldGen.register();
            LOGGER.info("[SimpleColonies] Common setup complete - worldgen registered for 5 cultures");
        });
    }

    private void onServerStarting(ServerStartingEvent event) {
        ColonyManager.getInstance().onServerStarting(event.getServer());
        LOGGER.info("[SimpleColonies] ColonyManager loaded {} colonies from disk", ColonyManager.getInstance().colonyCount());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        ColonyManager.getInstance().saveAll();
        LOGGER.info("[SimpleColonies] Saved colonies on server stop");
    }
}
