package com.hfstudio.diskterminal.storagebus.capability.refresh;

import net.minecraft.tileentity.TileEntity;

import com.hfstudio.diskterminal.api.capability.IRefreshCapability;
import com.hfstudio.diskterminal.integration.storagebus.gtmachine.GTMachineClassNames;
import com.hfstudio.diskterminal.integration.storagebus.gtmachine.GTMachineReflectionHelper;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;
import gregtech.common.tileentities.machines.MTEHatchInputME;

/**
 * Refresh capability for GregTech ME input buses and hatches. Their slot config setter only mutates
 * the stored config; the extracted preview used by recipe pulls is refreshed separately. Trigger that
 * refresh immediately after filter edits so newly marked partitions become effective without waiting
 * for the machine's next polling cycle.
 */
public class GTMachineRefreshCapability implements IRefreshCapability {

    private final MetaTileEntity metaTileEntity;
    private final TileEntity hostTile;

    public GTMachineRefreshCapability(MetaTileEntity metaTileEntity, TileEntity hostTile) {
        this.metaTileEntity = metaTileEntity;
        this.hostTile = hostTile;
    }

    @Override
    public void markDirty() {
        if (hostTile != null) hostTile.markDirty();
    }

    @Override
    public void requestRefresh() {
        boolean isSuperItemVariant = GTMachineReflectionHelper.readBooleanField(metaTileEntity, "isSuper")
            .orElse(false);

        if (GTMachineReflectionHelper.hasClassName(metaTileEntity, GTMachineClassNames.SUPER_DUAL_INPUT_HATCH_ME)) {
            refreshByReflection(metaTileEntity, "isAutoPullItemListForGui");
            return;
        }

        if (GTMachineReflectionHelper.hasClassName(metaTileEntity, GTMachineClassNames.SUPER_INPUT_BUS_ME)
            || GTMachineReflectionHelper
                .hasAnyClassName(metaTileEntity, GTMachineClassNames.GTNL_SUPER_ITEM_INPUT_CLASSES)
                && isSuperItemVariant) {
            refreshByReflection(metaTileEntity, "isAutoPullItemList");
            return;
        }

        if (GTMachineReflectionHelper.hasClassName(metaTileEntity, GTMachineClassNames.SUPER_INPUT_HATCH_ME)) {
            refreshByReflection(metaTileEntity, "isAutoPullFluidListForGui");
            return;
        }

        if (metaTileEntity instanceof MTEHatchInputBusME inputBus) {
            refreshGregTechMachine(inputBus);
            return;
        }

        if (metaTileEntity instanceof MTEHatchInputME inputHatch) {
            refreshGregTechMachine(inputHatch);
        }
    }

    private void refreshGregTechMachine(MTEHatchInputBusME inputBus) {
        if (inputBus.isAutoPullItemList()) return;

        GTMachineReflectionHelper.invokeVoid(inputBus, "updateAllInformationSlots");
        if (inputBus.doFastRecipeCheck()) GTMachineReflectionHelper.invokeVoid(inputBus, "configureWatchers");
    }

    private void refreshGregTechMachine(MTEHatchInputME inputHatch) {
        if (inputHatch.isAutoPullFluidList()) return;

        GTMachineReflectionHelper.invokeVoid(inputHatch, "updateAllInformationSlots");
        if (inputHatch.doFastRecipeCheck()) GTMachineReflectionHelper.invokeVoid(inputHatch, "configureWatchers");
    }

    private void refreshByReflection(Object target, String autoPullMethodName) {
        if (GTMachineReflectionHelper.invokeBoolean(target, autoPullMethodName)
            .orElse(false)) {
            return;
        }

        GTMachineReflectionHelper.invokeVoid(target, "updateAllInformationSlots");
        if (GTMachineReflectionHelper.invokeBoolean(target, "doFastRecipeCheck")
            .orElse(false)) {
            GTMachineReflectionHelper.invokeVoid(target, "configureWatchers");
        }
    }
}
