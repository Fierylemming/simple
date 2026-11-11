package com.simplecolonies;

import com.simplecolonies.block.ModBlocks;
import com.simplecolonies.colony.ColonyCommand;
import com.simplecolonies.colony.ColonyManager;
import com.simplecolonies.config.ColonyConfig;
import com.simplecolonies.entity.ModEntities;
import com.simplecolonies.item.ModItems;
import com.simplecolonies.item.ModCreativeTabs;
import com.simplecolonies.menu.ModMenuTypes;
import com.simplecolonies.worldgen.ModWorldGen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SimpleColonies.MOD_ID)
public class SimpleColonies {
    public static final String MOD_ID = "simplecolonies";
    public static final Logger LOGGER = LoggerFactory.getLogger(SimpleColonies.class);

    public SimpleColonies(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[SimpleColonies] Initializing {} - Ancient Villages for MC 1.21.1 / NeoForge {}", MOD_ID, "21.1.265");

        com.simplecolonies.villager.ColonyProfessions.POIS.register(modEventBus);
        com.simplecolonies.villager.ColonyProfessions.PROFESSIONS.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(com.simplecolonies.villager.ColonyProfessions::addTrades);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModBlocks.BLOCK_ENTITIES.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        ModMenuTypes.MENUS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, ColonyConfig.SPEC, "simplecolonies-common.toml");
        modContainer.registerConfig(ModConfig.Type.CLIENT, ColonyConfig.CLIENT_SPEC, "simplecolonies-client.toml");

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerEntityAttributes);

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

        LOGGER.info("[SimpleColonies] All registries (blocks, entities, menus, worldgen) queued.");
    }

    private void registerEntityAttributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(ModEntities.COLONY_VILLAGER.get(), net.minecraft.world.entity.npc.Villager.createAttributes().build());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModWorldGen.register();
            LOGGER.info("[SimpleColonies] Common setup complete - worldgen + 5 cultures ready");
        });
    }

    private void onServerStarting(ServerStartingEvent event) {
        ColonyManager.getInstance().onServerStarting(event.getServer());
        LOGGER.info("[SimpleColonies] ColonyManager loaded {} colonies", ColonyManager.getInstance().colonyCount());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        ColonyManager.getInstance().saveAll();
        LOGGER.info("[SimpleColonies] Saved colonies");
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ColonyCommand.register(event.getDispatcher());
        LOGGER.info("[SimpleColonies] Registered /colony commands");
    }
}
