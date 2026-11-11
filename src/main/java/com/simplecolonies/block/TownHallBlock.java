package com.simplecolonies.block;

import com.simplecolonies.colony.Colony;
import com.simplecolonies.colony.ColonyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Town Hall is the heart of a colony. Right-click to open colony management screen:
 * - citizen list + job assignment
 * - warehouse stockpile + requests
 * - building queue + expansion
 * - diplomacy (alliances/war)
 * - reputation display
 *
 * Placing Town Hall creates a colony if not inside another.
 */
public class TownHallBlock extends Block {
    public TownHallBlock(Properties props) { super(props); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.CONSUME;

        ColonyManager mgr = ColonyManager.getInstance();
        Colony colony = mgr.getColonyAt(pos).orElse(null);

        if (colony == null) {
            // Create new colony here
            var culture = com.simplecolonies.culture.CultureType.fromBiomeTemperature(level.getBiome(pos).value().getBaseTemperature(), level.getBiome(pos));
            colony = mgr.createColony(culture, pos);
            if (colony == null) {
                sp.displayClientMessage(Component.literal("§cToo many colonies in this dimension!"), true);
                return InteractionResult.FAIL;
            }
            sp.displayClientMessage(Component.literal("§aCreated new " + culture.displayName + " colony #" + colony.getId()), false);
            colony.addReputation(sp, 10, "founded town hall");
        } else {
            sp.displayClientMessage(Component.literal(
                    "§6Colony #" + colony.getId() + " - " + colony.getCulture().displayName +
                    " Lv" + colony.getLevel() + " | Citizens: " + colony.citizenCount() +
                    " | Rep: " + colony.getReputation(sp) + "/100" +
                    (colony.isLeader(sp) ? " §a[LEADER]" : " §7[visitor]")
            ), false);
            // Real mod would open Screen: new TownHallScreen(colony)
            if (!colony.isLeader(sp) && colony.getReputation(sp) >= com.simplecolonies.config.ColonyConfig.REPUTATION_NEEDED_TO_LEAD.get()) {
                colony.setLeader(sp);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
