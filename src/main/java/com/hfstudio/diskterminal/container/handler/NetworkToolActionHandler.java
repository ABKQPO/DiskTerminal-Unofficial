package com.hfstudio.diskterminal.container.handler;

import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;

import appeng.api.networking.IGrid;

import com.hfstudio.diskterminal.client.CellFilter;
import com.hfstudio.diskterminal.container.ContainerCellTerminalBase.StorageTracker;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;

/**
 * Executes network-tool batch operations on cells and storage buses.
 * <p>
 * The concrete tools live in the GUI network-tools subsystem (Phase 4). Until those are ported this
 * is a no-op so the terminal compiles and the Network Tools tab degrades gracefully.
 */
public class NetworkToolActionHandler {

    private NetworkToolActionHandler() {}

    public static void handleAction(String toolId, Map<CellFilter, CellFilter.State> activeFilters,
        Map<Long, StorageTracker> storageById, Map<Long, StorageBusTracker> storageBusById, IGrid grid,
        EntityPlayer player) {
        // Network tools are ported in Phase 4.
    }
}
