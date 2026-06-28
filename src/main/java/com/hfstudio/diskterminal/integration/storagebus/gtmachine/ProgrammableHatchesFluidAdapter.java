package com.hfstudio.diskterminal.integration.storagebus.gtmachine;

import java.util.Optional;

import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.IGridNode;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

public class ProgrammableHatchesFluidAdapter implements GTMachineAdapter {

    private final StandardGTFluidHatchAdapter delegate = new StandardGTFluidHatchAdapter();

    @Override
    public String getId() {
        return "programmable_hatches_fluid";
    }

    @Override
    public boolean supports(MetaTileEntity metaTileEntity) {
        return GTMachineReflectionHelper
            .hasAnyClassName(metaTileEntity, GTMachineClassNames.PROGRAMMABLE_FLUID_INPUT_CLASSES);
    }

    @Override
    public GTMachineTargetKind getTargetKind(MetaTileEntity metaTileEntity) {
        return GTMachineTargetKind.FLUID;
    }

    @Override
    public Optional<GTMachineScanResult> scan(IGridNode node, MetaTileEntity metaTileEntity,
        IGregTechTileEntity baseTile, TileEntity hostTile, int contentLimit) {
        return delegate.scan(node, metaTileEntity, baseTile, hostTile, contentLimit);
    }
}
