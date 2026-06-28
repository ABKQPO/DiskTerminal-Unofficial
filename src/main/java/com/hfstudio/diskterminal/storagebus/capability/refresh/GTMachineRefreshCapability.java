package com.hfstudio.diskterminal.storagebus.capability.refresh;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.minecraft.tileentity.TileEntity;

import com.hfstudio.diskterminal.api.capability.IRefreshCapability;

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

        invokeNoArg(inputBus, "updateAllInformationSlots");
        if (inputBus.doFastRecipeCheck()) invokeNoArg(inputBus, "configureWatchers");
    }

    private void refreshGregTechMachine(MTEHatchInputME inputHatch) {
        if (inputHatch.isAutoPullFluidList()) return;

        invokeNoArg(inputHatch, "updateAllInformationSlots");
        if (inputHatch.doFastRecipeCheck()) invokeNoArg(inputHatch, "configureWatchers");
    }

    private void invokeNoArg(Object target, String methodName) {
        if (target == null) return;

        try {
            Method method = findMethod(target.getClass(), methodName);
            if (method == null) return;

            method.setAccessible(true);
            method.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {}
    }

    private Method findMethod(Class<?> type, String methodName, Class<?>... parameterTypes)
        throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }

        throw new NoSuchMethodException(methodName);
    }
}
