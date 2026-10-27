package com.hfstudio.diskterminal.requestarea;

import java.util.Optional;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import com.hfstudio.diskterminal.client.RequestAreaControlInfo;
import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.ItemStacks;
import com.hfstudio.diskterminal.util.PosUtil;

import appeng.api.features.ILevelViewable;
import appeng.api.features.LevelItemInfo;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;

public class RequestAreaService {

    private final RequestAreaAdapterRegistry adapterRegistry;

    public RequestAreaService(RequestAreaAdapterRegistry adapterRegistry) {
        this.adapterRegistry = adapterRegistry;
    }

    public NBTTagList collect(IGrid grid) {
        NBTTagList entries = new NBTTagList();
        if (grid == null) return entries;

        for (Class<? extends IGridHost> machineClass : grid.getMachinesClasses()) {
            if (!ILevelViewable.class.isAssignableFrom(machineClass)) continue;

            for (IGridNode node : grid.getMachines(machineClass)) {
                if (node == null || node.getGrid() != grid) continue;
                if (!(node.getMachine() instanceof ILevelViewable device)) continue;

                Optional<RequestAreaAdapter> adapterResult = adapterRegistry.find(device);
                if (adapterResult.isEmpty()) continue;

                NBTTagCompound entry = createEntry(device, adapterResult.get());
                if (entry != null) entries.appendTag(entry);
            }
        }

        return entries;
    }

    public boolean applyAction(IGrid grid, int dimension, long position, int side, long id, int slot,
        RequestAreaAction action, long value, NBTTagCompound stackData) {
        if (grid == null || action == null) return false;

        for (Class<? extends IGridHost> machineClass : grid.getMachinesClasses()) {
            if (!ILevelViewable.class.isAssignableFrom(machineClass)) continue;

            for (IGridNode node : grid.getMachines(machineClass)) {
                if (node == null || node.getGrid() != grid) continue;
                if (!(node.getMachine() instanceof ILevelViewable device)) continue;

                TileEntity tile = device.getTile();
                if (tile == null || tile.getWorldObj() == null) continue;
                int deviceSide = device.getSide() != null ? device.getSide()
                    .ordinal() : -1;
                if (tile.getWorldObj().provider.dimensionId != dimension || deviceSide != side
                    || PosUtil.toLong(tile.xCoord, tile.yCoord, tile.zCoord) != position
                    || createId(dimension, position, side) != id) continue;

                Optional<RequestAreaAdapter> adapter = adapterRegistry.find(device);
                if (adapter.isEmpty()) return false;
                RequestAreaAdapter resolvedAdapter = adapter.get();
                if (action == RequestAreaAction.CYCLE_CONTROL) {
                    return stackData != null
                        && resolvedAdapter.applyControl(device, stackData.getString("controlId"), value);
                }
                if (action == RequestAreaAction.INSERT_UPGRADE) {
                    if (stackData == null) return false;
                    ItemStack upgrade = ItemStack.loadItemStackFromNBT(stackData);
                    return resolvedAdapter.insertUpgrade(device, slot, upgrade);
                }
                if (action == RequestAreaAction.EXTRACT_UPGRADE) {
                    if (stackData == null) return false;
                    ItemStack upgrade = resolvedAdapter.extractUpgrade(device, slot);
                    if (ItemStacks.isEmpty(upgrade)) return false;
                    stackData.setTag("extracted", upgrade.writeToNBT(new NBTTagCompound()));
                    return true;
                }
                return resolvedAdapter.applyAction(device, slot, action, value, stackData);
            }
        }

        return false;
    }

    public static long createId(int dimension, long position, int side) {
        long value = position ^ Long.rotateLeft((long) dimension * 0x9E3779B97F4A7C15L, 23);
        return value ^ Long.rotateLeft((long) side * 0xC2B2AE3D27D4EB4FL, 41);
    }

    private NBTTagCompound createEntry(ILevelViewable device, RequestAreaAdapter adapter) {
        TileEntity tile = device.getTile();
        if (tile == null || tile.getWorldObj() == null) return null;

        int dimension = tile.getWorldObj().provider.dimensionId;
        long position = PosUtil.toLong(tile.xCoord, tile.yCoord, tile.zCoord);
        int side = device.getSide() != null ? device.getSide()
            .ordinal() : -1;
        long id = createId(dimension, position, side);

        NBTTagCompound entry = new NBTTagCompound();
        entry.setLong("id", id);
        entry.setLong("pos", position);
        entry.setInteger("dim", dimension);
        entry.setInteger("side", side);
        entry.setString("type", adapter.getTypeId());
        entry.setBoolean("outputActive", adapter.isDeviceOutputActive(device));
        entry.setBoolean("canToggleMode", adapter.canToggleDeviceMode(device));
        entry.setString("mode", adapter.getDeviceMode(device));

        String customName = device.hasCustomName() ? device.getCustomName() : "";
        entry.setString("name", customName == null ? "" : customName);

        ItemStack icon = adapter.getIcon();
        if (!ItemStacks.isEmpty(icon)) {
            entry.setTag("icon", icon.writeToNBT(new NBTTagCompound()));
        }

        LevelItemInfo[] itemInfo = device.getLevelItemInfoList();
        int slotCount = Math.max(0, adapter.getSlotCount(device));
        NBTTagList slots = new NBTTagList();
        for (int slot = 0; slot < slotCount; slot++) {
            LevelItemInfo info = itemInfo != null && slot < itemInfo.length ? itemInfo[slot] : null;
            slots.appendTag(createSlotEntry(device, adapter, slot, info));
        }
        entry.setTag("slots", slots);

        NBTTagList controls = new NBTTagList();
        for (RequestAreaControlInfo control : adapter.getControls(device)) {
            NBTTagCompound controlData = new NBTTagCompound();
            controlData.setString("id", control.id());
            controlData.setString("setting", control.settingId());
            controlData.setString("value", control.valueId());
            controlData.setString("name", control.displayName());
            controlData.setBoolean("active", control.active());
            controls.appendTag(controlData);
        }
        entry.setTag("controls", controls);

        NBTTagList upgrades = new NBTTagList();
        entry.setInteger("upgradeSlotLimit", Math.max(0, adapter.getUpgradeSlotLimit(device)));
        int upgradeCount = Math.max(0, adapter.getUpgradeSlotCount(device));
        for (int slot = 0; slot < upgradeCount; slot++) {
            NBTTagCompound upgradeData = new NBTTagCompound();
            ItemStack upgrade = adapter.getUpgradeStack(device, slot);
            if (!ItemStacks.isEmpty(upgrade)) {
                upgradeData.setTag("stack", upgrade.writeToNBT(new NBTTagCompound()));
            }
            upgrades.appendTag(upgradeData);
        }
        entry.setTag("upgrades", upgrades);
        return entry;
    }

    private NBTTagCompound createSlotEntry(ILevelViewable device, RequestAreaAdapter adapter, int slot,
        LevelItemInfo info) {
        NBTTagCompound data = new NBTTagCompound();
        if (info != null && info.stack != null) AEStackUtil.writeStackToNBT(data, info.stack);

        data.setLong("stored", adapter.getStoredQuantity(device, slot, info));
        data.setLong("target", adapter.getTargetQuantity(device, slot, info));
        data.setLong("batch", adapter.getBatchSize(device, slot, info));
        data.setLong("threshold", adapter.getThreshold(device, slot, info));
        data.setBoolean("canEditTarget", adapter.canEditTarget(device, slot));
        data.setBoolean("canEditBatch", adapter.canEditBatch(device, slot));
        data.setBoolean("canToggle", adapter.canToggleSlot(device, slot));
        data.setBoolean("enabled", adapter.isSlotEnabled(device, slot));
        data.setBoolean("canInvert", adapter.canToggleInverted(device, slot));
        data.setBoolean("inverted", adapter.isSlotInverted(device, slot));
        data.setInteger("state", info != null && info.state != null ? info.state.ordinal() : 0);
        return data;
    }
}
