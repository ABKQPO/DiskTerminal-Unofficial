package com.hfstudio.diskterminal.storagebus.runtime;

import java.util.Optional;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * Resolves GregTech ME input bus/hatch meta tiles by re-reading the host tile from the world on every
 * call.
 */
public class GTStorageBusResolver implements StorageBusResolver {

    @Override
    public Optional<StorageBusHandle> resolve(StorageBusId id) {
        World world = DimensionManager.getWorld(id.getDimension());
        if (world == null) return Optional.empty();

        BlockPos pos = id.getPosition();
        TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
        if (!(tile instanceof IGregTechTileEntity gtTile)) return Optional.empty();

        MetaTileEntity meta = asMetaTileEntity(gtTile.getMetaTileEntity());
        if (meta == null) return Optional.empty();

        return Optional.of(new GTStorageBusHandle(id, meta, tile));
    }

    private MetaTileEntity asMetaTileEntity(Object metaTileEntity) {
        return metaTileEntity instanceof MetaTileEntity meta ? meta : null;
    }
}
