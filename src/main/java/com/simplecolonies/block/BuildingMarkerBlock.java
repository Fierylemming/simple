package com.simplecolonies.block;

import com.simplecolonies.block.entity.BuildingMarkerBlockEntity;
import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
 * Building Marker — the block you asked for to turn player-built structures into colony buildings.
 *
 * Usage:
 *  1. Build whatever you want: a house, farm, barracks — any shape/style.
 *  2. Place this marker inside it (like a sign).
 *  3. Right-click it → choose what building it should count as (House, Farm, Warehouse, etc).
 *  4. Colony will now treat that area as that building: villagers will use it, housing counts, jobs spawn there.
 *
 * This is far simpler than MineColonies' blueprint paste: you build freely, we register it.
 * The marker also defines the workplace chest area: couriers will prioritize chests near markers.
 */
public class BuildingMarkerBlock extends Block implements EntityBlock {
    public BuildingMarkerBlock(Properties props) { super(props); }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BuildingMarkerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.CONSUME;

        Colony colony = ColonyManager.getInstance().getColonyAt(pos).orElse(null);
        if (colony == null) {
            sp.displayClientMessage(Component.literal("§cNo colony here. Place a Town Hall nearby first, or expand colony radius to include this spot."), true);
            return InteractionResult.FAIL;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BuildingMarkerBlockEntity marker)) return InteractionResult.FAIL;

        // Permission: only leader or high rep can designate buildings
        if (!colony.isLeader(player) && colony.getReputation(player) < 20) {
            sp.displayClientMessage(Component.literal("§cNeed 20 rep or be leader to designate buildings (you have " + colony.getReputation(player) + ")"), true);
            return InteractionResult.FAIL;
        }

        // Open marker GUI
        final Colony fColony = colony;
        final BuildingMarkerBlockEntity fMarker = marker;
        sp.openMenu(new SimpleMenuProvider(
                (containerId, inv, ply) -> new com.simplecolonies.menu.BuildingMarkerMenu(containerId, inv, fColony, pos, fMarker.getBuildingType()),
                Component.literal("Designate Building — " + colony.getCulture().displayName)
        ), buf -> {
            buf.writeBlockPos(pos);
            buf.writeInt(colony.getId());
            buf.writeUtf(fMarker.getBuildingType() != null ? fMarker.getBuildingType().name() : "NONE");
            buf.writeNbt(colony.save());
        });

        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            // Optionally unregister building if marker removed — we keep it for now but mark needs repair
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BuildingMarkerBlockEntity marker && marker.getLinkedBuildingPos() != null) {
                // Could remove from colony — but we keep so player doesn't lose housing accidentally
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
