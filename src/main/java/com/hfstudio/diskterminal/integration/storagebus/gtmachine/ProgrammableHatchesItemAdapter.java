package com.hfstudio.diskterminal.integration.storagebus.gtmachine;

import java.util.Optional;

import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.IGridNode;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

public class ProgrammableHatchesItemAdapter implements GTMachineAdapter {

    private final StandardGTItemBusAdapter delegate = new StandardGTItemBusAdapter();

    @Override
    public String getId() {
        return "programmable_hatches_item";
    }

    @Override
    public boolean supports(MetaTileEntity metaTileEntity) {
        return GTMachineReflectionHelper
            .hasAnyClassName(metaTileEntity, GTMachineClassNames.PROGRAMMABLE_ITEM_INPUT_CLASSES);
    }

    @Override
    public GTMachineTargetKind getTargetKind(MetaTileEntity metaTileEntity) {
        return GTMachineTargetKind.ITEM;
    }

    @Override
    public Optional<GTMachineScanResult> scan(IGridNode node, MetaTileEntity metaTileEntity,
        IGregTechTileEntity baseTile, TileEntity hostTile, int contentLimit) {
        return delegate.scan(node, metaTileEntity, baseTile, hostTile, contentLimit);
    }
}
