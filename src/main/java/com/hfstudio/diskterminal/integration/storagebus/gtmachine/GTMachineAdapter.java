package com.hfstudio.diskterminal.integration.storagebus.gtmachine;

import java.util.Optional;

import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.IGridNode;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

public interface GTMachineAdapter {

    String getId();

    boolean supports(MetaTileEntity metaTileEntity);

    GTMachineTargetKind getTargetKind(MetaTileEntity metaTileEntity);

    Optional<GTMachineScanResult> scan(IGridNode node, MetaTileEntity metaTileEntity, IGregTechTileEntity baseTile,
        TileEntity hostTile, int contentLimit);
}
