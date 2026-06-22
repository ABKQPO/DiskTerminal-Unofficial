package com.hfstudio.diskterminal.container;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;

import com.hfstudio.diskterminal.part.PartCellTerminal;

import appeng.util.Platform;

/**
 * Container for the part-based Cell Terminal GUI.
 * Scans the ME network for all drives and chests with their storage cells.
 */
public class ContainerCellTerminal extends ContainerCellTerminalBase {

    private final PartCellTerminal part;

    public ContainerCellTerminal(InventoryPlayer ip, PartCellTerminal part) {
        super(ip, part);
        this.part = part;

        if (Platform.isServer()) {
            this.grid = part.getActionableNode()
                .getGrid();
        }

        this.bindPlayerInventory(ip, 0, 0);
    }

    @Override
    public IInventory getTempCellInventory() {
        return this.part != null ? this.part.getTempCellInventory() : null;
    }
}
