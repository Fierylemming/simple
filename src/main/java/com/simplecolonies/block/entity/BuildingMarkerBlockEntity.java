package com.simplecolonies.block.entity;

import com.simplecolonies.block.ModBlocks;
import com.simplecolonies.building.BuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class BuildingMarkerBlockEntity extends BlockEntity {
    private BuildingType buildingType = null; // null = not yet designated
    private BlockPos linkedBuildingPos = null; // where colony building was registered

    public BuildingMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.BUILDING_MARKER_BE.get(), pos, state);
    }

    public BuildingType getBuildingType() { return buildingType; }
    public void setBuildingType(BuildingType t) { this.buildingType = t; setChanged(); }

    public BlockPos getLinkedBuildingPos() { return linkedBuildingPos; }
    public void setLinkedBuildingPos(BlockPos p) { this.linkedBuildingPos = p; setChanged(); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (buildingType != null) tag.putString("buildingType", buildingType.name());
        if (linkedBuildingPos != null) tag.putLong("linkedPos", linkedBuildingPos.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("buildingType")) buildingType = BuildingType.valueOf(tag.getString("buildingType"));
        if (tag.contains("linkedPos")) linkedBuildingPos = BlockPos.of(tag.getLong("linkedPos"));
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
