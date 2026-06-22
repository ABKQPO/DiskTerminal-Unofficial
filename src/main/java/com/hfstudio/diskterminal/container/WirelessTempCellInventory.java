package com.hfstudio.diskterminal.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.hfstudio.diskterminal.items.ItemWirelessCellTerminal;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.storage.ICellWorkbenchItem;
import baubles.api.BaublesApi;
import cpw.mods.fml.common.Loader;

/**
 * {@link IInventory} view over a wireless terminal's temp cell storage, persisted in the terminal
 * item's NBT. Lets the temp-area tab work with wireless terminals. Supports a Baubles slot when
 * Baubles-Expanded is present.
 */
public class WirelessTempCellInventory implements IInventory {

    private static final String BAUBLES_MODID = "Baubles|Expanded";

    private final EntityPlayer player;
    private final int terminalSlot;
    private final boolean isBauble;

    public WirelessTempCellInventory(EntityPlayer player, int terminalSlot, boolean isBauble) {
        this.player = player;
        this.terminalSlot = terminalSlot;
        this.isBauble = isBauble;
    }

    private ItemStack getTerminalStack() {
        if (isBauble) {
            if (!Loader.isModLoaded(BAUBLES_MODID)) return null;
            IInventory baubles = BaublesApi.getBaubles(player);

            return baubles == null ? null : baubles.getStackInSlot(terminalSlot);
        }

        return player.inventory.getStackInSlot(terminalSlot);
    }

    private boolean isValidTerminal() {
        ItemStack stack = getTerminalStack();

        return !ItemStacks.isEmpty(stack) && stack.getItem() instanceof ItemWirelessCellTerminal;
    }

    @Override
    public int getSizeInventory() {
        return isValidTerminal() ? ItemWirelessCellTerminal.getMaxTempCells() : 0;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (!isValidTerminal()) return null;

        return ItemWirelessCellTerminal.getTempCell(getTerminalStack(), slot);
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (!isValidTerminal()) return;

        ItemWirelessCellTerminal.setTempCell(getTerminalStack(), slot, stack);
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        ItemStack existing = getStackInSlot(slot);
        if (ItemStacks.isEmpty(existing)) return null;

        setInventorySlotContents(slot, null);

        return existing;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return ItemStacks.isEmpty(stack) || stack.getItem() instanceof ICellWorkbenchItem;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void markDirty() {}

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public String getInventoryName() {
        return "disk_terminal.temp_cells";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }
}
