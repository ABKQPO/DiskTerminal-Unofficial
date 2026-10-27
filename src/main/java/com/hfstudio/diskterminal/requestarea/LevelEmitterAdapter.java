package com.hfstudio.diskterminal.requestarea;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.diskterminal.client.RequestAreaControlInfo;
import com.hfstudio.diskterminal.util.AEStackUtil;

import appeng.api.AEApi;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.features.ILevelViewable;
import appeng.api.features.LevelItemInfo;
import appeng.api.implementations.ITypeFilterProvider;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.parts.automation.PartLevelEmitter;
import appeng.tile.inventory.IAEStackInventory;

public class LevelEmitterAdapter implements RequestAreaAdapter {

    @Override
    public String getTypeId() {
        return "level_emitter";
    }

    @Override
    public boolean supports(ILevelViewable device) {
        return device instanceof PartLevelEmitter;
    }

    @Override
    public ItemStack getIcon() {
        return AEApi.instance()
            .definitions()
            .parts()
            .levelEmitter()
            .maybeStack(1)
            .orNull();
    }

    @Override
    public int getUpgradeSlotLimit(ILevelViewable device) {
        // ContainerLevelEmitter exposes one slot, despite the larger backing inventory.
        return Math.min(1, RequestAreaAdapter.super.getUpgradeSlotLimit(device));
    }

    @Override
    public int getSlotCount(ILevelViewable device) {
        return 1;
    }

    @Override
    public boolean canToggleDeviceMode(ILevelViewable device) {
        return false;
    }

    @Override
    public String getDeviceMode(ILevelViewable device) {
        PartLevelEmitter emitter = (PartLevelEmitter) device;
        return emitter.getConfigManager()
            .getSetting(Settings.REDSTONE_EMITTER)
            .name();
    }

    @Override
    public boolean isDeviceOutputActive(ILevelViewable device) {
        return ((PartLevelEmitter) device).isProvidingStrongPower() > 0;
    }

    @Override
    public long getTargetQuantity(ILevelViewable device, int slot, LevelItemInfo info) {
        return ((PartLevelEmitter) device).getReportingValue();
    }

    @Override
    public long getStoredQuantity(ILevelViewable device, int slot, LevelItemInfo info) {
        // TODO: Read this from the upstream API when getStoredAmount() is available in the dependency.
        return -1;
    }

    @Override
    public boolean canEditTarget(ILevelViewable device, int slot) {
        return slot == 0;
    }

    @Override
    public long getThreshold(ILevelViewable device, int slot, LevelItemInfo info) {
        return getTargetQuantity(device, slot, info);
    }

    @Override
    public List<RequestAreaControlInfo> getControls(ILevelViewable device) {
        if (!(device instanceof PartLevelEmitter emitter)) return List.of();

        List<RequestAreaControlInfo> controls = new ArrayList<>();
        addSettingControl(controls, Settings.LEVEL_TYPE, emitter, true);
        addSettingControl(
            controls,
            Settings.REDSTONE_EMITTER,
            emitter,
            emitter.getInstalledUpgrades(Upgrades.REDSTONE) > 0);
        addSettingControl(controls, Settings.FUZZY_MODE, emitter, emitter.getInstalledUpgrades(Upgrades.FUZZY) > 0);
        addSettingControl(
            controls,
            Settings.CRAFT_VIA_REDSTONE,
            emitter,
            emitter.getInstalledUpgrades(Upgrades.CRAFTING) > 0);

        LevelItemInfo[] itemInfo = emitter.getLevelItemInfoList();
        boolean hasConfiguredStack = itemInfo != null && itemInfo.length > 0
            && itemInfo[0] != null
            && itemInfo[0].stack != null;
        if (!hasConfiguredStack) {
            for (IAEStackType<?> type : AEStackTypeRegistry.getSortedTypes()) {
                if (type.getButtonTexture() == null || type.getButtonIcon() == null) continue;
                controls.add(
                    new RequestAreaControlInfo(
                        "type:" + type.getId(),
                        "TYPE_FILTER",
                        "",
                        type.getDisplayName(),
                        ((ITypeFilterProvider) emitter).getTypeFilters()
                            .isEnabled(type)));
            }
        }
        return controls;
    }

    @Override
    public boolean applyControl(ILevelViewable device, String controlId, long direction) {
        if (!(device instanceof PartLevelEmitter emitter) || controlId == null) return false;
        if (controlId.startsWith("type:")) {
            IAEStackType<?> type = AEStackTypeRegistry.getType(controlId.substring("type:".length()));
            if (type == null) return false;
            ITypeFilterProvider provider = emitter;
            provider.getTypeFilters()
                .toggle(type);
            provider.onChangeTypeFilters();
            return true;
        }

        Settings setting;
        try {
            setting = Settings.valueOf(controlId);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (setting != Settings.LEVEL_TYPE && setting != Settings.REDSTONE_EMITTER
            && setting != Settings.FUZZY_MODE
            && setting != Settings.CRAFT_VIA_REDSTONE) return false;

        Enum<?> current = emitter.getConfigManager()
            .getSetting(setting);
        List<? extends Enum<?>> values = new ArrayList<>(setting.getPossibleValues());
        int currentIndex = values.indexOf(current);
        if (currentIndex < 0 || values.isEmpty()) return false;
        int directionStep = direction < 0 ? -1 : 1;
        int nextIndex = Math.floorMod(currentIndex + directionStep, values.size());
        emitter.getConfigManager()
            .putSetting(setting, values.get(nextIndex));
        return true;
    }

    private void addSettingControl(List<RequestAreaControlInfo> controls, Settings setting, PartLevelEmitter emitter,
        boolean visible) {
        if (!visible) return;
        Enum<?> value = emitter.getConfigManager()
            .getSetting(setting);
        controls.add(new RequestAreaControlInfo(setting.name(), setting.name(), value.name(), "", true));
    }

    @Override
    public boolean applyAction(ILevelViewable device, int slot, RequestAreaAction action, long value,
        NBTTagCompound stackData) {
        if (!(device instanceof PartLevelEmitter emitter)) return false;
        if (action == RequestAreaAction.CYCLE_CONTROL) {
            return stackData != null && applyControl(device, stackData.getString("controlId"), value);
        }
        if (slot != 0 && action != RequestAreaAction.TOGGLE_DEVICE_MODE) return false;

        switch (action) {
            case SET_TARGET -> emitter.setReportingValue(Math.max(0, value));
            case ADJUST_TARGET -> emitter.setReportingValue(adjustNonNegative(emitter.getReportingValue(), value));
            case SET_MATERIAL -> updateMaterial(emitter, stackData);
            case CLEAR_MATERIAL -> updateMaterial(emitter, null);
            case TOGGLE_DEVICE_MODE -> {
                RedstoneMode mode = (RedstoneMode) emitter.getConfigManager()
                    .getSetting(Settings.REDSTONE_EMITTER);
                emitter.getConfigManager()
                    .putSetting(
                        Settings.REDSTONE_EMITTER,
                        mode == RedstoneMode.HIGH_SIGNAL ? RedstoneMode.LOW_SIGNAL : RedstoneMode.HIGH_SIGNAL);
            }
            default -> {
                return false;
            }
        }

        return true;
    }

    protected void updateMaterial(PartLevelEmitter emitter, NBTTagCompound stackData) {
        IAEStack<?> stack = stackData == null ? null : AEStackUtil.readPartitionStack(stackData, null);
        IAEStackInventory inventory = emitter.getAEInventoryByName(StorageName.CONFIG);
        if (inventory == null) return;

        inventory.putAEStackInSlot(0, stack);
        emitter.saveAEStackInv();
    }
}
