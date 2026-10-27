package com.hfstudio.diskterminal.requestarea;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.glodblock.github.common.tile.TileLevelMaintainer;
import com.glodblock.github.loader.ItemAndBlockHolder;
import com.hfstudio.diskterminal.client.RequestAreaControlInfo;
import com.hfstudio.diskterminal.util.AEStackUtil;

import appeng.api.features.ILevelViewable;

public class AE2FCLevelMaintainerAdapter implements RequestAreaAdapter {

    @Override
    public String getTypeId() {
        return "level_maintainer";
    }

    @Override
    public boolean supports(ILevelViewable device) {
        return device instanceof TileLevelMaintainer;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(ItemAndBlockHolder.LEVEL_MAINTAINER);
    }

    @Override
    public int getSlotCount(ILevelViewable device) {
        return TileLevelMaintainer.REQ_COUNT;
    }

    @Override
    public boolean canToggleDeviceMode(ILevelViewable device) {
        return false;
    }

    @Override
    public String getDeviceMode(ILevelViewable device) {
        return ((TileLevelMaintainer) device).isLiteMode() ? "LITE" : "FULL";
    }

    @Override
    public boolean isDeviceOutputActive(ILevelViewable device) {
        return ((TileLevelMaintainer) device).isActive();
    }

    @Override
    public List<RequestAreaControlInfo> getControls(ILevelViewable device) {
        if (!(device instanceof TileLevelMaintainer maintainer)) return List.of();
        return List.of(
            new RequestAreaControlInfo(
                "lite_mode",
                "AE2FC_LITE_MODE",
                maintainer.isLiteMode() ? "LITE" : "FULL",
                "gui.disk_terminal.request_area.control.lite_mode",
                maintainer.isLiteMode()));
    }

    @Override
    public boolean applyControl(ILevelViewable device, String controlId, long direction) {
        if (!(device instanceof TileLevelMaintainer maintainer) || !"lite_mode".equals(controlId)) return false;
        if (direction == 0) {
            maintainer.clearLiteMode();
            return true;
        }
        maintainer.toggleLiteMode();
        return true;
    }

    @Override
    public boolean canEditTarget(ILevelViewable device, int slot) {
        return getRequest(device, slot) != null;
    }

    @Override
    public boolean canToggleSlot(ILevelViewable device, int slot) {
        return getRequest(device, slot) != null;
    }

    @Override
    public boolean canEditBatch(ILevelViewable device, int slot) {
        return getRequest(device, slot) != null;
    }

    @Override
    public boolean isSlotEnabled(ILevelViewable device, int slot) {
        TileLevelMaintainer.RequestInfo request = getRequest(device, slot);
        return request != null && request.isEnable();
    }

    @Override
    public boolean applyAction(ILevelViewable device, int slot, RequestAreaAction action, long value,
        NBTTagCompound stackData) {
        if (!(device instanceof TileLevelMaintainer maintainer)) return false;
        if (action == RequestAreaAction.CYCLE_CONTROL) {
            return stackData != null && applyControl(device, stackData.getString("controlId"), value);
        }
        if (action == RequestAreaAction.TOGGLE_DEVICE_MODE) {
            maintainer.toggleLiteMode();
            return true;
        }
        if (slot < 0 || slot >= TileLevelMaintainer.REQ_COUNT) return false;

        switch (action) {
            case SET_TARGET -> maintainer.updateQuantity(slot, Math.max(0, value));
            case ADJUST_TARGET -> {
                TileLevelMaintainer.RequestInfo request = getRequest(device, slot);
                if (request == null) return false;
                maintainer.updateQuantity(slot, adjustNonNegative(request.getQuantity(), value));
            }
            case SET_BATCH -> maintainer.updateBatchSize(slot, Math.max(0, value));
            case ADJUST_BATCH -> {
                TileLevelMaintainer.RequestInfo request = getRequest(device, slot);
                if (request == null) return false;
                maintainer.updateBatchSize(slot, adjustNonNegative(request.getBatchSize(), value));
            }
            case TOGGLE_ENABLED -> {
                TileLevelMaintainer.RequestInfo request = getRequest(device, slot);
                if (request == null) return false;
                maintainer.updateStatus(slot, !request.isEnable());
            }
            case SET_MATERIAL -> maintainer
                .updateStack(slot, stackData == null ? null : AEStackUtil.readPartitionStack(stackData, null));
            case CLEAR_MATERIAL -> maintainer.updateStack(slot, null);
            default -> {
                return false;
            }
        }

        return true;
    }

    private TileLevelMaintainer.RequestInfo getRequest(ILevelViewable device, int slot) {
        TileLevelMaintainer.RequestInfo[] requests = ((TileLevelMaintainer) device).requests;
        return slot >= 0 && slot < requests.length ? requests[slot] : null;
    }
}
