package com.hfstudio.diskterminal.storagebus.runtime;

import java.util.Optional;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import com.glodblock.github.common.tile.TileSuperStockReplenisher;
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.hfstudio.diskterminal.integration.storagebus.gtmachine.GTMachineClassNames;
import com.hfstudio.diskterminal.integration.storagebus.gtmachine.GTMachineReflectionHelper;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * Resolves mixed configuration targets by re-reading the host tile from the world on every call.
 */
public class StockReplenisherResolver implements StorageBusResolver {

    @Override
    public Optional<StorageBusHandle> resolve(StorageBusId id) {
        World world = DimensionManager.getWorld(id.getDimension());
        if (world == null) return Optional.empty();

        BlockPos pos = id.getPosition();
        TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
        if (tile instanceof TileSuperStockReplenisher replenisher) {
            return Optional.of(new StockReplenisherHandle(id, replenisher, replenisher));
        }

        if (!(tile instanceof IGregTechTileEntity gtTile)) {
            return Optional.empty();
        }

        MetaTileEntity metaTileEntity = gtTile.getMetaTileEntity() instanceof MetaTileEntity meta ? meta : null;
        if (metaTileEntity == null
            || !GTMachineReflectionHelper.hasClassName(metaTileEntity, GTMachineClassNames.SUPER_DUAL_INPUT_HATCH_ME)) {
            return Optional.empty();
        }

        return Optional.of(new StockReplenisherHandle(id, metaTileEntity, tile));
    }
}
