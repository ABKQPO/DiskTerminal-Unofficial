package com.hfstudio.diskterminal.integration.storagebus;

import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import com.hfstudio.diskterminal.client.BusRole;
import com.hfstudio.diskterminal.client.StorageType;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;

import appeng.parts.automation.PartSharedItemBus;

/**
 * Base class for storage bus scanners.
 */
public abstract class AbstractStorageBusScanner implements IStorageBusScanner {

    /**
     * Apply common capability flags to the provided NBT payload.
     */
    protected void applyCapabilities(NBTTagCompound nbt) {
        applyCapabilities(nbt, supportsPriority(), supportsIOMode());
    }

    /**
     * Apply capability flags to the provided NBT payload.
     */
    protected void applyCapabilities(NBTTagCompound nbt, boolean supportsPriority, boolean supportsIOMode) {
        nbt.setBoolean("supportsPriority", supportsPriority);
        nbt.setBoolean("supportsIOMode", supportsIOMode);
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
        trackerMap.put(
            busId,
            new StorageBusTracker(
                busId,
                bus,
                hostTile,
                bus.getSide()
                    .ordinal(),
                storageType));
    }

    protected StorageType storageTypeFrom(PartSharedItemBus<?> bus) {
        String typeId = bus.getStackType() == null ? ""
            : bus.getStackType()
                .getId();
        if ("fluid".equals(typeId)) return StorageType.FLUID;
        if ("essentia".equals(typeId)) return StorageType.ESSENTIA;

        return StorageType.ITEM;
    }
}
