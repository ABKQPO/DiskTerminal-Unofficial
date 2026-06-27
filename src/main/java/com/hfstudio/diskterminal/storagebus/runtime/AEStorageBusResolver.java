package com.hfstudio.diskterminal.storagebus.runtime;

import java.util.Optional;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;

/**
 * Resolves AE2 part-based storage buses by re-reading the host tile from the world on every call.
 */
public class AEStorageBusResolver implements StorageBusResolver {

    @Override
    public Optional<StorageBusHandle> resolve(StorageBusId id) {
        World world = DimensionManager.getWorld(id.getDimension());
        if (world == null) return Optional.empty();

        BlockPos pos = id.getPosition();
        TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
        if (!(tile instanceof IPartHost host)) return Optional.empty();

        ForgeDirection side = ForgeDirection.getOrientation(id.getSideOrdinal());
        IPart part = host.getPart(side);
        if (part == null) return Optional.empty();

        return Optional.of(new AEStorageBusHandle(id, part, tile));
    }
}
