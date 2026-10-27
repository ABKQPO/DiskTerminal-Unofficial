package com.hfstudio.diskterminal.requestarea;

import java.util.Collections;
import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.diskterminal.client.RequestAreaControlInfo;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.features.ILevelViewable;
import appeng.api.features.LevelItemInfo;
import appeng.api.implementations.IUpgradeableHost;

public interface RequestAreaAdapter {

    String getTypeId();

    boolean supports(ILevelViewable device);

    ItemStack getIcon();

    int getSlotCount(ILevelViewable device);

    default boolean canToggleDeviceMode(ILevelViewable device) {
        return false;
    }

    default String getDeviceMode(ILevelViewable device) {
        return "";
    }

    default boolean isDeviceOutputActive(ILevelViewable device) {
        return false;
    }

    default long getTargetQuantity(ILevelViewable device, int slot, LevelItemInfo info) {
        return info != null ? info.quantity : 0;
    }

    default long getStoredQuantity(ILevelViewable device, int slot, LevelItemInfo info) {
        return -1;
    }

    default long getBatchSize(ILevelViewable device, int slot, LevelItemInfo info) {
        return info != null ? info.batchSize : 0;
    }

    default long getThreshold(ILevelViewable device, int slot, LevelItemInfo info) {
        return -1;
    }

    default long adjustNonNegative(long current, long delta) {
        long base = Math.max(0, current);
        if (delta > 0 && base > Long.MAX_VALUE - delta) return Long.MAX_VALUE;
        if (delta < 0 && delta < -base) return 0;
        return base + delta;
    }

    default boolean canEditTarget(ILevelViewable device, int slot) {
        return false;
    }

    default boolean canEditBatch(ILevelViewable device, int slot) {
        return false;
    }

    default boolean canToggleSlot(ILevelViewable device, int slot) {
        return false;
    }

    default boolean isSlotEnabled(ILevelViewable device, int slot) {
        return false;
    }

    default boolean canToggleInverted(ILevelViewable device, int slot) {
        return false;
    }

    default boolean isSlotInverted(ILevelViewable device, int slot) {
        return false;
    }

    default List<RequestAreaControlInfo> getControls(ILevelViewable device) {
        return Collections.emptyList();
    }

    default boolean applyControl(ILevelViewable device, String controlId, long direction) {
        return false;
    }

    default int getUpgradeSlotLimit(ILevelViewable device) {
        IInventory inventory = getUpgradeInventory(device);
        return inventory == null ? 0 : inventory.getSizeInventory();
    }

    default int getUpgradeSlotCount(ILevelViewable device) {
        IInventory inventory = getUpgradeInventory(device);
        if (inventory == null) return 0;
        int limit = Math.max(0, Math.min(getUpgradeSlotLimit(device), inventory.getSizeInventory()));
        // Keep previously overfilled slots visible so their cards can still be extracted.
        for (int slot = inventory.getSizeInventory() - 1; slot >= limit; slot--) {
            if (!ItemStacks.isEmpty(inventory.getStackInSlot(slot))) return slot + 1;
        }
        return limit;
    }

    default ItemStack getUpgradeStack(ILevelViewable device, int slot) {
        IInventory inventory = getUpgradeInventory(device);
        if (inventory == null || slot < 0 || slot >= inventory.getSizeInventory()) return null;
        ItemStack stack = inventory.getStackInSlot(slot);
        return ItemStacks.isEmpty(stack) ? null : stack.copy();
    }

    default boolean insertUpgrade(ILevelViewable device, int slot, ItemStack stack) {
        IInventory inventory = getUpgradeInventory(device);
        if (inventory == null || ItemStacks.isEmpty(stack)) return false;
        int limit = Math.max(0, Math.min(getUpgradeSlotLimit(device), inventory.getSizeInventory()));
        if (slot < 0 || slot >= limit) return false;

        int installed = 0;
        for (int index = 0; index < inventory.getSizeInventory(); index++) {
            if (!ItemStacks.isEmpty(inventory.getStackInSlot(index))) installed++;
        }
        if (installed >= limit) {
            return false;
        }
        if (!ItemStacks.isEmpty(inventory.getStackInSlot(slot)) || !inventory.isItemValidForSlot(slot, stack)) {
            return false;
        }

        ItemStack inserted = stack.copy();
        inserted.stackSize = 1;
        inventory.setInventorySlotContents(slot, inserted);
        inventory.markDirty();
        return true;
    }

    default ItemStack extractUpgrade(ILevelViewable device, int slot) {
        IInventory inventory = getUpgradeInventory(device);
        if (inventory == null || slot < 0 || slot >= inventory.getSizeInventory()) return null;
        ItemStack extracted = inventory.decrStackSize(slot, 1);
        if (!ItemStacks.isEmpty(extracted)) inventory.markDirty();
        return ItemStacks.isEmpty(extracted) ? null : extracted;
    }

    private IInventory getUpgradeInventory(ILevelViewable device) {
        if (!(device instanceof IUpgradeableHost upgradeable)) return null;
        return upgradeable.getInventoryByName("upgrades");
    }

    boolean applyAction(ILevelViewable device, int slot, RequestAreaAction action, long value,
        NBTTagCompound stackData);
}
