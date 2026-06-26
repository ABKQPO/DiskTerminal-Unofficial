package com.hfstudio.diskterminal.container.handler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

import com.hfstudio.diskterminal.container.ContainerCellTerminalBase;
import com.hfstudio.diskterminal.network.PacketTempCellAction;
import com.hfstudio.diskterminal.network.PacketTempCellPartitionAction;
import com.hfstudio.diskterminal.util.InventoryHelper;
import com.hfstudio.diskterminal.util.ItemStacks;
import com.hfstudio.diskterminal.util.PlayerMessageHelper;

import appeng.api.AEApi;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellWorkbenchItem;

/**
 * Handles temp cell area actions for the cell terminal.
 */
public class TempCellActionHandler {

    /**
     * Handle temp cell action (insert, extract, send, upgrade, swap).
     */
    public static void handleAction(ContainerCellTerminalBase container, PacketTempCellAction.Action action,
        int tempSlotIndex, int playerSlotIndex, EntityPlayer player, boolean toInventory) {
        switch (action) {
            case INSERT -> handleInsert(container, tempSlotIndex, playerSlotIndex, player);
            case EXTRACT -> handleExtract(container, tempSlotIndex, player, toInventory);
            case SEND -> handleSend(container, tempSlotIndex, player);
            case UPGRADE -> handleUpgrade(container, tempSlotIndex, playerSlotIndex, player, toInventory);
            case SWAP -> handleSwap(container, tempSlotIndex, player);
        }
    }

    /**
     * Handle partition action on a temp cell.
     */
    public static void handlePartitionAction(ContainerCellTerminalBase container, int tempSlotIndex,
        PacketTempCellPartitionAction.Action action, int partitionSlot, NBTTagCompound stackData) {
        IInventory tempInv = getTempCellInventory(container);
        if (tempInv == null) return;

        if (tempSlotIndex < 0 || tempSlotIndex >= tempInv.getSizeInventory()) return;

        ItemStack cellStack = tempInv.getStackInSlot(tempSlotIndex);
        if (ItemStacks.isEmpty(cellStack)) return;

        ICellHandler cellHandler = AEApi.instance()
            .registries()
            .cell()
            .getHandler(cellStack);
        if (cellHandler == null) return;

        CellActionHandler.ConfigResult config = CellActionHandler.getCellConfig(cellHandler, cellStack);
        if (config.configAEInv == null) return;

        CellActionHandler
            .executePartitionActionDirect(config, action, partitionSlot, stackData, cellHandler, cellStack);

        tempInv.setInventorySlotContents(tempSlotIndex, cellStack);
        markDirty(container);
    }

    private static void handleInsert(ContainerCellTerminalBase container, int tempSlotIndex, int playerSlotIndex,
        EntityPlayer player) {
        IInventory tempInv = getTempCellInventory(container);
        if (tempInv == null) return;

        int targetSlot = tempSlotIndex;
        if (targetSlot < 0) {
            targetSlot = InventoryHelper.findEmptySlot(tempInv);
            if (targetSlot < 0) {
                PlayerMessageHelper.error(player, "gui.disk_terminal.temp_area.full");

                return;
            }
        }

        if (targetSlot >= tempInv.getSizeInventory()) return;

        if (!ItemStacks.isEmpty(tempInv.getStackInSlot(targetSlot))) {
            PlayerMessageHelper.error(player, "gui.disk_terminal.temp_area.slot_occupied");

            return;
        }

        ItemStack cellStack;
        if (playerSlotIndex >= 0) {
            ItemStack sourceStack = player.inventory.getStackInSlot(playerSlotIndex);
            if (ItemStacks.isEmpty(sourceStack)) {
                PlayerMessageHelper.error(player, "gui.disk_terminal.temp_area.no_cell");

                return;
            }

            cellStack = sourceStack.splitStack(1);

            if (ItemStacks.isEmpty(sourceStack)) {
                player.inventory.setInventorySlotContents(playerSlotIndex, null);
            }

            player.inventory.markDirty();
        } else {
            ItemStack cursorStack = player.inventory.getItemStack();
            if (ItemStacks.isEmpty(cursorStack)) {
                PlayerMessageHelper.error(player, "gui.disk_terminal.temp_area.no_cell");

                return;
            }

            cellStack = cursorStack.splitStack(1);

            if (ItemStacks.isEmpty(cursorStack)) player.inventory.setItemStack(null);
            ((EntityPlayerMP) player).updateHeldItem();
        }

        if (ItemStacks.isEmpty(cellStack)) {
            PlayerMessageHelper.error(player, "gui.disk_terminal.temp_area.no_cell");

            return;
        }

        if (!(cellStack.getItem() instanceof ICellWorkbenchItem)) {
            ItemStack cursorStack = player.inventory.getItemStack();
            if (ItemStacks.isEmpty(cursorStack)) {
                player.inventory.setItemStack(cellStack);
                ((EntityPlayerMP) player).updateHeldItem();
            } else if (cursorStack.isItemEqual(cellStack) && ItemStack.areItemStackTagsEqual(cursorStack, cellStack)
                && cursorStack.stackSize < cursorStack.getMaxStackSize()) {
                    cursorStack.stackSize++;
                    ((EntityPlayerMP) player).updateHeldItem();
                } else if (!player.inventory.addItemStackToInventory(cellStack)) {
                    player.dropPlayerItemWithRandomChoice(cellStack, false);
                }

            PlayerMessageHelper.error(player, "gui.disk_terminal.temp_area.not_cell");

            return;
        }

        tempInv.setInventorySlotContents(targetSlot, cellStack);
        markDirty(container);
    }

    private static void handleExtract(ContainerCellTerminalBase container, int tempSlotIndex, EntityPlayer player,
        boolean toInventory) {
        IInventory tempInv = getTempCellInventory(container);
        if (tempInv == null) return;

        if (tempSlotIndex < 0 || tempSlotIndex >= tempInv.getSizeInventory()) return;

        ItemStack cellStack = tempInv.getStackInSlot(tempSlotIndex);
        if (ItemStacks.isEmpty(cellStack)) return;

        tempInv.setInventorySlotContents(tempSlotIndex, null);

        if (toInventory) {
            if (!player.inventory.addItemStackToInventory(cellStack)) {
                player.dropPlayerItemWithRandomChoice(cellStack, false);
            }

            markDirty(container);

            return;
        }

        ItemStack cursorStack = player.inventory.getItemStack();
        if (ItemStacks.isEmpty(cursorStack)) {
            player.inventory.setItemStack(cellStack);
            ((EntityPlayerMP) player).updateHeldItem();
        } else if (cursorStack.isItemEqual(cellStack) && ItemStack.areItemStackTagsEqual(cursorStack, cellStack)
            && cursorStack.stackSize < cursorStack.getMaxStackSize()) {
                cursorStack.stackSize += cellStack.stackSize;
                ((EntityPlayerMP) player).updateHeldItem();
            } else {
                if (!player.inventory.addItemStackToInventory(cellStack)) {
                    player.dropPlayerItemWithRandomChoice(cellStack, false);
                }
            }

        markDirty(container);
    }

    private static void handleSwap(ContainerCellTerminalBase container, int tempSlotIndex, EntityPlayer player) {
        IInventory tempInv = getTempCellInventory(container);
        if (tempInv == null) return;

        if (tempSlotIndex < 0 || tempSlotIndex >= tempInv.getSizeInventory()) return;

        ItemStack existingCell = tempInv.getStackInSlot(tempSlotIndex);
        if (ItemStacks.isEmpty(existingCell)) return;

        ItemStack cursorStack = player.inventory.getItemStack();
        if (ItemStacks.isEmpty(cursorStack)) return;

        if (!(cursorStack.getItem() instanceof ICellWorkbenchItem)) {
            PlayerMessageHelper.error(player, "gui.disk_terminal.temp_area.not_cell");

            return;
        }

        ItemStack newCell = cursorStack.splitStack(1);

        tempInv.setInventorySlotContents(tempSlotIndex, newCell);

        if (ItemStacks.isEmpty(cursorStack)) {
            player.inventory.setItemStack(existingCell);
        } else {
            if (!player.inventory.addItemStackToInventory(existingCell)) {
                player.dropPlayerItemWithRandomChoice(existingCell, false);
            }
        }

        ((EntityPlayerMP) player).updateHeldItem();
        markDirty(container);
    }

    private static void handleSend(ContainerCellTerminalBase container, int tempSlotIndex, EntityPlayer player) {
        IInventory tempInv = getTempCellInventory(container);
        if (tempInv == null) return;

        if (tempSlotIndex < 0 || tempSlotIndex >= tempInv.getSizeInventory()) return;

        ItemStack cellStack = tempInv.getStackInSlot(tempSlotIndex);
        if (ItemStacks.isEmpty(cellStack)) return;

        InsertResult result = findAndInsertIntoNetwork(container, cellStack);

        if (!result.success) {
            PlayerMessageHelper.error(player, "disk_terminal.temp_area.no_slot");

            return;
        }

        tempInv.setInventorySlotContents(tempSlotIndex, null);
        markDirty(container);
        PlayerMessageHelper.success(player, "disk_terminal.temp_area.sent", container.getGridName());
    }

    private static void handleUpgrade(ContainerCellTerminalBase container, int tempSlotIndex, int fromSlot,
        EntityPlayer player, boolean shiftClick) {
        IInventory tempInv = getTempCellInventory(container);
        if (tempInv == null) return;

        ItemStack upgradeStack = fromSlot >= 0 ? player.inventory.getStackInSlot(fromSlot)
            : player.inventory.getItemStack();

        if (ItemStacks.isEmpty(upgradeStack)) return;

        if (!(upgradeStack.getItem() instanceof IUpgradeModule)) return;

        if (shiftClick || tempSlotIndex < 0) {
            for (int i = 0; i < tempInv.getSizeInventory(); i++) {
                if (tryInsertUpgradeIntoTempCell(tempInv, i, upgradeStack, player, fromSlot)) {
                    markDirty(container);

                    return;
                }
            }

            PlayerMessageHelper.warning(player, "disk_terminal.warning.upgrade_insert_failed_any_temp_cell");

            return;
        }

        if (tempSlotIndex >= tempInv.getSizeInventory()) return;

        if (tryInsertUpgradeIntoTempCell(tempInv, tempSlotIndex, upgradeStack, player, fromSlot)) {
            markDirty(container);

            return;
        }

        PlayerMessageHelper.warning(
            player,
            "disk_terminal.warning.upgrade_insert_failed",
            getTempCellDisplayName(tempInv, tempSlotIndex));
    }

    private static boolean tryInsertUpgradeIntoTempCell(IInventory tempInv, int tempSlotIndex, ItemStack upgradeStack,
        EntityPlayer player, int fromSlot) {
        ItemStack cellStack = tempInv.getStackInSlot(tempSlotIndex);
        if (ItemStacks.isEmpty(cellStack)) return false;

        if (!(cellStack.getItem() instanceof ICellWorkbenchItem cellItem)) return false;

        if (!cellItem.isEditable(cellStack)) return false;

        IInventory upgradesInv = cellItem.getUpgradesInventory(cellStack);
        if (upgradesInv == null) return false;

        ItemStack toInsert = upgradeStack.copy();
        toInsert.stackSize = 1;

        for (int slot = 0; slot < upgradesInv.getSizeInventory(); slot++) {
            ItemStack remainder = InventoryHelper.insert(upgradesInv, slot, toInsert, false);
            if (ItemStacks.isEmpty(remainder)) {
                upgradeStack.stackSize--;

                if (fromSlot >= 0) {
                    if (upgradeStack.stackSize <= 0) player.inventory.setInventorySlotContents(fromSlot, null);
                    player.inventory.markDirty();
                } else {
                    if (upgradeStack.stackSize <= 0) player.inventory.setItemStack(null);
                    ((EntityPlayerMP) player).updateHeldItem();
                }

                tempInv.setInventorySlotContents(tempSlotIndex, cellStack);

                return true;
            }
        }

        return false;
    }

    private static IInventory getTempCellInventory(ContainerCellTerminalBase container) {
        return container.getTempCellInventory();
    }

    private static IChatComponent getTempCellDisplayName(IInventory tempInv, int tempSlotIndex) {
        if (tempInv == null || tempSlotIndex < 0 || tempSlotIndex >= tempInv.getSizeInventory()) {
            return new ChatComponentTranslation("gui.disk_terminal.cell_empty");
        }

        ItemStack cellStack = tempInv.getStackInSlot(tempSlotIndex);
        if (!ItemStacks.isEmpty(cellStack)) return new ChatComponentText(cellStack.getDisplayName());

        return new ChatComponentTranslation("gui.disk_terminal.cell_empty");
    }

    private static void markDirty(ContainerCellTerminalBase container) {
        container.requestFullRefresh();
    }

    private static InsertResult findAndInsertIntoNetwork(ContainerCellTerminalBase container, ItemStack cellStack) {
        for (ContainerCellTerminalBase.StorageTracker tracker : container.getStorageTrackers()) {
            IInventory cellInv = CellDataHandler.getCellInventory(tracker.storage);
            if (cellInv == null) continue;

            for (int slot = 0; slot < cellInv.getSizeInventory(); slot++) {
                if (!ItemStacks.isEmpty(cellInv.getStackInSlot(slot))) continue;

                ItemStack remainder = InventoryHelper.insert(cellInv, slot, cellStack, false);
                if (ItemStacks.isEmpty(remainder)) {
                    tracker.tile.markDirty();

                    return new InsertResult(true, tracker.id, slot);
                }
            }
        }

        return new InsertResult(false, -1, -1);
    }

    public static class InsertResult {

        public final boolean success;
        public final long storageId;
        public final int slot;

        public InsertResult(boolean success, long storageId, int slot) {
            this.success = success;
            this.storageId = storageId;
            this.slot = slot;
        }
    }
}
