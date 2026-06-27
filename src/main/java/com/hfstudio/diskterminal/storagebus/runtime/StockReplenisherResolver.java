package com.hfstudio.diskterminal.storagebus.runtime;

import java.util.Optional;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import com.glodblock.github.common.tile.TileSuperStockReplenisher;
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;

/**
 * Resolves AE2FluidCraft Super Stock Replenisher tiles by re-reading the host tile from the world on
 * every call.
 */
public class StockReplenisherResolver implements StorageBusResolver {

    @Override
    public Optional<StorageBusHandle> resolve(StorageBusId id) {
        World world = DimensionManager.getWorld(id.getDimension());
        if (world == null) return Optional.empty();

        BlockPos pos = id.getPosition();
        TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
        if (!(tile instanceof TileSuperStockReplenisher replenisher)) return Optional.empty();

        return Optional.of(new StockReplenisherHandle(id, replenisher));
    }
}
