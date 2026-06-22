package com.hfstudio.diskterminal.integration.storage;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.implementations.tiles.IMEChest;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.tile.storage.TileDrive;

import com.hfstudio.diskterminal.container.handler.CellDataHandler;

/**
 * Default storage scanner for vanilla AE2 storage devices.
 * Scans ME Drives (TileDrive) and ME Chests (via IMEChest) from the network.
 * <p>
 * AE2 drives support priority and have standard 63-slot partition limits.
 */
public class AE2StorageScanner extends AbstractStorageScanner {

    public static final AE2StorageScanner INSTANCE = new AE2StorageScanner();

    private AE2StorageScanner() {}

    @Override
    public String getId() {
        return "appliedenergistics2";
    }

    @Override
    public boolean isAvailable() {
        return true; // Always available - AE2 is a required dependency
    }

    @Override
    public void scanStorages(IGrid grid, NBTTagList storageList, CellDataHandler.StorageTrackerCallback callback,
        int slotLimit) {
        // Scan ME Drives
        for (IGridNode gn : grid.getMachines(TileDrive.class)) {
            if (!gn.isActive()) continue;

            NBTTagCompound storageData = CellDataHandler.createStorageData((IChestOrDrive) gn.getMachine(),
                "tile.appliedenergistics2.drive.name", callback, slotLimit);
            applyCapabilities(storageData);
            storageList.appendTag(storageData);
        }

        // Scan ME Chests. The concrete TileChest class pulls optional-mod supertypes that may be
        // absent at compile time, so chests are discovered by their clean IMEChest interface over
        // the grid's full node set instead of grid.getMachines(TileChest.class).
        for (IGridNode gn : grid.getNodes()) {
            if (!gn.isActive()) continue;

            Object machine = gn.getMachine();
            if (!(machine instanceof IMEChest)) continue;

            NBTTagCompound storageData = CellDataHandler.createStorageData((IChestOrDrive) machine,
                "tile.appliedenergistics2.chest.name", callback, slotLimit);
            applyCapabilities(storageData);
            storageList.appendTag(storageData);
        }
    }
}

