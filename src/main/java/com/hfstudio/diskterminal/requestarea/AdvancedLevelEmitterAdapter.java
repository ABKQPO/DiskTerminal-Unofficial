package com.hfstudio.diskterminal.requestarea;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.diskterminal.client.RequestAreaControlInfo;
import com.hfstudio.diskterminal.util.AEStackUtil;

import appeng.api.AEApi;
import appeng.api.config.BooleanOperation;
import appeng.api.config.Settings;
import appeng.api.features.ILevelViewable;
import appeng.api.features.LevelItemInfo;
import appeng.api.parts.IAdvancedLevelEmitter;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.parts.automation.PartAdvancedLevelEmitter;
import appeng.tile.inventory.IAEStackInventory;

public class AdvancedLevelEmitterAdapter extends LevelEmitterAdapter {

    @Override
    public String getTypeId() {
        return "advanced_level_emitter";
    }

    @Override
    public boolean supports(ILevelViewable device) {
        return device instanceof PartAdvancedLevelEmitter;
    }

    @Override
    public ItemStack getIcon() {
        return AEApi.instance()
            .definitions()
            .parts()
            .advancedLevelEmitter()
            .maybeStack(1)
            .orNull();
    }

    @Override
    public int getSlotCount(ILevelViewable device) {
        return IAdvancedLevelEmitter.SLOT_COUNT;
    }

    @Override
    public String getDeviceMode(ILevelViewable device) {
        return ((PartAdvancedLevelEmitter) device).getLogicMode()
            .name();
    }

    @Override
    public boolean canToggleDeviceMode(ILevelViewable device) {
        return false;
    }

    @Override
    public List<RequestAreaControlInfo> getControls(ILevelViewable device) {
        if (!(device instanceof PartAdvancedLevelEmitter emitter)) return List.of();
        return List.of(
            new RequestAreaControlInfo(
                Settings.ADVANCED_LEVEL_EMITTER_LOGIC.name(),
                Settings.ADVANCED_LEVEL_EMITTER_LOGIC.name(),
                emitter.getLogicMode()
                    .name(),
                "",
                true));
    }

    @Override
    public boolean applyControl(ILevelViewable device, String controlId, long direction) {
        if (!(device instanceof PartAdvancedLevelEmitter emitter) || !Settings.ADVANCED_LEVEL_EMITTER_LOGIC.name()
            .equals(controlId)) return false;
        BooleanOperation mode = emitter.getLogicMode();
        emitter.getConfigManager()
            .putSetting(
                Settings.ADVANCED_LEVEL_EMITTER_LOGIC,
                mode == BooleanOperation.AND ? BooleanOperation.OR : BooleanOperation.AND);
        return true;
    }

    @Override
    public boolean isDeviceOutputActive(ILevelViewable device) {
        return ((PartAdvancedLevelEmitter) device).isProvidingStrongPower() > 0;
    }

    @Override
    public long getTargetQuantity(ILevelViewable device, int slot, LevelItemInfo info) {
        return ((IAdvancedLevelEmitter) device).getReportingValue(slot);
    }

    @Override
    public long getStoredQuantity(ILevelViewable device, int slot, LevelItemInfo info) {
        // TODO: Read this from the upstream API when getStoredAmount(int) is available in the dependency.
        return -1;
    }

    @Override
    public boolean canEditTarget(ILevelViewable device, int slot) {
        return slot >= 0 && slot < IAdvancedLevelEmitter.SLOT_COUNT;
    }

    @Override
    public long getThreshold(ILevelViewable device, int slot, LevelItemInfo info) {
        return getTargetQuantity(device, slot, info);
    }

    @Override
    public boolean canToggleSlot(ILevelViewable device, int slot) {
        return true;
    }

    @Override
    public boolean isSlotEnabled(ILevelViewable device, int slot) {
        return ((IAdvancedLevelEmitter) device).isSlotActive(slot);
    }

    @Override
    public boolean canToggleInverted(ILevelViewable device, int slot) {
        return true;
    }

    @Override
    public boolean isSlotInverted(ILevelViewable device, int slot) {
        return ((IAdvancedLevelEmitter) device).isSlotInverted(slot);
    }

    @Override
    public boolean applyAction(ILevelViewable device, int slot, RequestAreaAction action, long value,
        NBTTagCompound stackData) {
        if (!(device instanceof PartAdvancedLevelEmitter emitter)) return false;
        if (action == RequestAreaAction.TOGGLE_DEVICE_MODE) {
            BooleanOperation mode = emitter.getLogicMode();
            emitter.getConfigManager()
                .putSetting(
                    Settings.ADVANCED_LEVEL_EMITTER_LOGIC,
                    mode == BooleanOperation.AND ? BooleanOperation.OR : BooleanOperation.AND);
            return true;
        }
        if (action == RequestAreaAction.CYCLE_CONTROL) {
            return stackData != null && applyControl(device, stackData.getString("controlId"), value);
        }
        if (slot < 0 || slot >= IAdvancedLevelEmitter.SLOT_COUNT) return false;

        switch (action) {
            case SET_TARGET -> emitter.setReportingValue(slot, Math.max(0, value));
            case ADJUST_TARGET -> emitter
                .setReportingValue(slot, adjustNonNegative(emitter.getReportingValue(slot), value));
            case TOGGLE_ENABLED -> emitter.setSlotActive(slot, !emitter.isSlotActive(slot));
            case TOGGLE_INVERTED -> emitter.setSlotInverted(slot, !emitter.isSlotInverted(slot));
            case SET_MATERIAL -> updateMaterial(emitter, slot, stackData);
            case CLEAR_MATERIAL -> updateMaterial(emitter, slot, null);
            default -> {
                return false;
            }
        }

        return true;
    }

    private void updateMaterial(PartAdvancedLevelEmitter emitter, int slot, NBTTagCompound stackData) {
        IAEStack<?> stack = stackData == null ? null : AEStackUtil.readPartitionStack(stackData, null);
        IAEStackInventory inventory = emitter.getAEInventoryByName(StorageName.CONFIG);
        if (inventory == null) return;

        inventory.putAEStackInSlot(slot, stack);
        emitter.saveAEStackInv();
    }
}
