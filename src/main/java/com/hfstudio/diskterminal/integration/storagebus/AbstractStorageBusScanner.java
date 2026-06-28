package com.hfstudio.diskterminal.integration.storagebus;

import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import com.hfstudio.diskterminal.client.BusRole;
import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusSource;

import appeng.parts.automation.PartSharedItemBus;
import appeng.parts.misc.PartStorageBus;

/**
 * Base class for storage bus scanners.
 */
public abstract class AbstractStorageBusScanner implements IStorageBusScanner {

    /**
     * Apply common capability flags to the provided NBT payload.
     */
    protected void applyCapabilities(NBTTagCompound nbt) {
        applyCapabilities(nbt, supportsPriority(), supportsIOMode(), true);
    }

    /**
     * Apply capability flags to the provided NBT payload.
     */
    protected void applyCapabilities(NBTTagCompound nbt, boolean supportsPriority, boolean supportsIOMode) {
        applyCapabilities(nbt, supportsPriority, supportsIOMode, true);
    }

    /**
     * Apply capability flags to the provided NBT payload.
     */
    protected void applyCapabilities(NBTTagCompound nbt, boolean supportsPriority, boolean supportsIOMode,
        boolean supportsRename) {
        nbt.setBoolean("supportsPriority", supportsPriority);
        nbt.setBoolean("supportsIOMode", supportsIOMode);
        nbt.setBoolean("supportsRename", supportsRename);
    }

    /**
     * Default base number of config slots without capacity upgrades.
     */
    protected int getBaseConfigSlots() {
        return 18;
    }

    /**
     * Default extra slots per capacity upgrade.
     */
    protected int getSlotsPerCapacityUpgrade() {
        return 9;
    }

    /**
     * Default maximum config slots (with 5 upgrades).
     */
    protected int getMaxConfigSlots() {
        return 63;
    }

    /**
     * Apply slot limit parameters to the provided NBT payload.
     */
    protected void applySlotParameters(NBTTagCompound nbt) {
        applySlotParameters(nbt, getBaseConfigSlots(), getSlotsPerCapacityUpgrade(), getMaxConfigSlots());
    }

    /**
     * Apply slot limit parameters to the provided NBT payload.
     */
    protected void applySlotParameters(NBTTagCompound nbt, int baseConfigSlots, int slotsPerUpgrade,
        int maxConfigSlots) {
        nbt.setInteger("baseConfigSlots", baseConfigSlots);
        nbt.setInteger("slotsPerUpgrade", slotsPerUpgrade);
        nbt.setInteger("maxConfigSlots", maxConfigSlots);
    }

    protected void appendSharedBus(PartSharedItemBus<?> bus, BusRole role, NBTTagList out, int contentLimit,
        Map<Long, StorageBusTracker> trackerMap) {
        TileEntity hostTile = bus.getHost()
            .getTile();
        if (hostTile == null) return;

        StorageType storageType = storageTypeFrom(bus);
        int typeFlag = storageType.ordinal() + role.ordinal() * 16;
        long busId = StorageBusDataHandler.createBusId(
            hostTile,
            bus.getSide()
                .ordinal(),
            typeFlag);

        NBTTagCompound nbt = StorageBusDataHandler.createSharedBusData(bus, busId, storageType, role, contentLimit);
        applyCapabilities(nbt, false, false);
        applySlotParameters(nbt, 1, 4, 9);
        out.appendTag(nbt);
        StorageBusId targetId = StorageBusId.of(
            hostTile,
            bus.getSide()
                .ordinal(),
            role,
            storageType);
        trackerMap.put(
            busId,
            new StorageBusTracker(
                busId,
                bus,
                hostTile,
                bus.getSide()
                    .ordinal(),
                storageType).withTarget(targetId, StorageBusSource.AE_SHARED_BUS));
    }

    protected void appendStorageBus(PartStorageBus bus, BusRole role, NBTTagList out, int contentLimit,
        Map<Long, StorageBusTracker> trackerMap) {
        TileEntity hostTile = bus.getHost()
            .getTile();
        if (hostTile == null) return;

        StorageType storageType = storageTypeFrom(bus);
        int typeFlag = storageType.ordinal() + role.ordinal() * 16;
        long busId = StorageBusDataHandler.createBusId(
            hostTile,
            bus.getSide()
                .ordinal(),
            typeFlag);

        NBTTagCompound nbt = switch (storageType) {
            case FLUID -> StorageBusDataHandler.createFluidStorageBusData(bus, busId, contentLimit);
            case ESSENTIA, ITEM -> StorageBusDataHandler.createItemStorageBusData(bus, busId, contentLimit);
        };
        applyCapabilities(nbt);
        applySlotParameters(nbt);
        out.appendTag(nbt);

        StorageBusId targetId = StorageBusId.of(
            hostTile,
            bus.getSide()
                .ordinal(),
            role,
            storageType);
        trackerMap.put(
            busId,
            new StorageBusTracker(
                busId,
                bus,
                hostTile,
                bus.getSide()
                    .ordinal(),
                storageType).withTarget(targetId, StorageBusSource.AE_STORAGE_BUS));
    }

    protected StorageType storageTypeFrom(PartSharedItemBus<?> bus) {
        String typeId = bus.getStackType() == null ? ""
            : bus.getStackType()
                .getId();
        if ("fluid".equals(typeId)) return StorageType.FLUID;
        if ("essentia".equals(typeId)) return StorageType.ESSENTIA;

        return StorageType.ITEM;
    }

    protected StorageType storageTypeFrom(PartStorageBus bus) {
        String typeId = bus.getStackType() == null ? ""
            : bus.getStackType()
                .getId();
        if ("fluid".equals(typeId)) return StorageType.FLUID;
        if ("essentia".equals(typeId)) return StorageType.ESSENTIA;

        return StorageType.ITEM;
    }
}
