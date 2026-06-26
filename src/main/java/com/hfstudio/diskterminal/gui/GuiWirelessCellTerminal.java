package com.hfstudio.diskterminal.gui;

import java.awt.Rectangle;
import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;

import com.hfstudio.diskterminal.container.ContainerWirelessCellTerminal;

import appeng.helpers.WirelessTerminalGuiObject;

/**
 * GUI for the Wireless Cell Terminal.
 * Same functionality as GuiCellTerminal but for the wireless version.
 */
public class GuiWirelessCellTerminal extends GuiCellTerminalBase {

    private final WirelessTerminalGuiObject wirelessTerminalGuiObject;

    public GuiWirelessCellTerminal(InventoryPlayer playerInventory, WirelessTerminalGuiObject wth) {
        super(new ContainerWirelessCellTerminal(playerInventory, wth));
        this.wirelessTerminalGuiObject = wth;
    }

    @Override
    protected String getGuiTitle() {
        return I18n.format("gui.disk_terminal.wireless_cell_terminal.title");
    }

    /**
     * Get the WirelessTerminalGuiObject backing this terminal.
     */
    public WirelessTerminalGuiObject getWirelessTerminalGuiObject() {
        return this.wirelessTerminalGuiObject;
    }

    public ContainerWirelessCellTerminal getWirelessContainer() {
        return (ContainerWirelessCellTerminal) this.inventorySlots;
    }

    @Override
    public List<Rectangle> getNEIExclusionArea() {
        return super.getNEIExclusionArea();
    }
}
