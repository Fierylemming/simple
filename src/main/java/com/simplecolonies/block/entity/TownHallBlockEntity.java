package com.simplecolonies.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.simplecolonies.block.ModBlocks;

public class TownHallBlockEntity extends BlockEntity {
    private int colonyId = -1;

    public TownHallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.TOWN_HALL_BE.get(), pos, state);
    }

    public void setColonyId(int id) { this.colonyId = id; setChanged(); }
    public int getColonyId() { return colonyId; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("colonyId", colonyId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        colonyId = tag.getInt("colonyId");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag t = super.getUpdateTag(registries);
        saveAdditional(t, registries);
        return t;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
