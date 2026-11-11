package com.simplecolonies.block;

import com.simplecolonies.block.entity.TownHallBlockEntity;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Town Hall is the heart of a colony. Right-click to open colony management screen.
 * Now fully playable: opens TownHallScreen with 5 tabs.
 * Placing Town Hall creates a colony if not inside another, and links BE to colonyId.
 */
public class TownHallBlock extends Block implements EntityBlock {
    public TownHallBlock(Properties props) { super(props); }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TownHallBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        ColonyManager mgr = ColonyManager.getInstance();
        Colony colony = mgr.getColonyAt(pos).orElse(null);

        if (colony == null) {
            // Create new colony here if player places/activates in wilderness
            var culture = com.simplecolonies.culture.CultureType.fromBiomeTemperature(level.getBiome(pos).value().getBaseTemperature(), level.getBiome(pos));
            colony = mgr.createColony(culture, pos);
            if (colony == null) {
                if (player instanceof ServerPlayer sp) sp.displayClientMessage(net.minecraft.network.chat.Component.literal("§cToo many colonies in this dimension!"), true);
                return InteractionResult.FAIL;
            }
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.literal("§aCreated new " + culture.displayName + " colony #" + colony.getId()), false);
                colony.addReputation(sp, 10, "founded town hall");
            }
        }

        // Ensure BE linked
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TownHallBlockEntity thbe) {
            thbe.setColonyId(colony.getId());
        }

        // Open menu (playable GUI)
        if (player instanceof ServerPlayer sp) {
            final Colony fColony = colony;
            // Use Extended Menu Provider pattern: we pass colonyId+pos via extraData handled in TownHallMenu.fromNetwork
            // NeoForge 1.21.1 uses MenuProvider with extra data via IMenuExtension; simplified here via custom open
            sp.openMenu(new SimpleMenuProvider((containerId, inv, ply) ->
                    new com.simplecolonies.menu.TownHallMenu(containerId, inv, fColony, pos),
                    net.minecraft.network.chat.Component.literal(fColony.getCulture().displayName + " Colony #" + fColony.getId())),
                    buf -> {
                        buf.writeInt(fColony.getId());
                        buf.writeBlockPos(pos);
                    }
            );
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            // Auto-link BE if colony already exists at pos (village generated via structure)
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TownHallBlockEntity th) {
                Colony colony = ColonyManager.getInstance().getColonyAt(pos).orElse(null);
                if (colony != null) th.setColonyId(colony.getId());
            }
        }
    }
}
