package com.hfstudio.diskterminal.container.handler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.hfstudio.diskterminal.api.IItemCompactingCell;
import com.hfstudio.diskterminal.network.PacketPartitionAction;
import com.hfstudio.diskterminal.network.PacketTempCellPartitionAction;
import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.InventoryHelper;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.AEApi;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.IterationCounter;

/**
 * Handles cell-related actions: partition modifications, ejection, pickup, insertion, upgrades.
 * <p>
 * Cell config/upgrade inventories are {@link IInventory} in 1.7.10; capability-style operations use
 * {@link InventoryHelper}. Partition data uses AE2's generic stack inventory so every registered stack type is
 * preserved.
 */
public class CellActionHandler {

    /**
     * Handle partition modification for a cell.
     */
    public static boolean handlePartitionAction(IChestOrDrive storage, TileEntity tile, int cellSlot,
        PacketPartitionAction.Action action, int partitionSlot, NBTTagCompound stackData) {
        IInventory cellInventory = CellDataHandler.getCellInventory(storage);
        if (cellInventory == null) return false;

        int inventorySlot = CellDataHandler.toInventorySlot(storage, cellSlot);
        if (!isValidInventorySlot(cellInventory, inventorySlot)) return false;

        ItemStack cellStack = cellInventory.getStackInSlot(inventorySlot);
        if (ItemStacks.isEmpty(cellStack)) return false;

        ICellHandler cellHandler = AEApi.instance()
            .registries()
            .cell()
            .getHandler(cellStack);
        if (cellHandler == null) return false;

        ConfigResult config = getConfigInventory(cellHandler, cellStack);
        if (config.configInv == null && config.configAEInv == null) return false;

        executePartitionAction(config, action, partitionSlot, stackData, cellHandler, cellStack);

        forceCellHandlerRefresh(cellInventory, inventorySlot, cellStack);
        handleCompactingCell(cellStack, action, stackData, config, tile.getWorldObj());

        tile.markDirty();

        return true;
    }

    /**
     * Eject a cell from storage to player's inventory.
     */
    public static boolean ejectCell(IChestOrDrive storage, int cellSlot, EntityPlayer player) {
        IInventory cellInventory = CellDataHandler.getCellInventory(storage);
        if (cellInventory == null) return false;

        int inventorySlot = CellDataHandler.toInventorySlot(storage, cellSlot);
        if (!isValidInventorySlot(cellInventory, inventorySlot)) return false;

        ItemStack cellStack = cellInventory.getStackInSlot(inventorySlot);
        if (ItemStacks.isEmpty(cellStack)) return false;

        ItemStack extracted = InventoryHelper.extract(cellInventory, inventorySlot, 1, false);
        if (ItemStacks.isEmpty(extracted)) return false;

        if (!player.inventory.addItemStackToInventory(extracted)) {
            player.dropPlayerItemWithRandomChoice(extracted, false);
        }

        return true;
    }

    /**
     * Pick up a cell to player's hand or swap with held cell.
     */
    public static boolean pickupCell(IChestOrDrive storage, int cellSlot, EntityPlayer player, boolean toInventory) {
        IInventory cellInventory = CellDataHandler.getCellInventory(storage);
        if (cellInventory == null) return false;

        int inventorySlot = CellDataHandler.toInventorySlot(storage, cellSlot);
        if (!isValidInventorySlot(cellInventory, inventorySlot)) return false;

        ItemStack cellStack = cellInventory.getStackInSlot(inventorySlot);
        ItemStack heldStack = player.inventory.getItemStack();

        if (ItemStacks.isEmpty(cellStack)) {
            if (!toInventory && !ItemStacks.isEmpty(heldStack) && isValidCell(heldStack)) {
                ItemStack remainder = InventoryHelper.insert(cellInventory, inventorySlot, heldStack.copy(), false);
                player.inventory.setItemStack(remainder);
                ((EntityPlayerMP) player).updateHeldItem();

                return true;
            }

            return false;
        }

        if (toInventory) {
            ItemStack extracted = InventoryHelper.extract(cellInventory, inventorySlot, 1, false);
            if (ItemStacks.isEmpty(extracted)) return false;

            if (!player.inventory.addItemStackToInventory(extracted)) {
                player.dropPlayerItemWithRandomChoice(extracted, false);
            }

            return true;
        }

        if (!ItemStacks.isEmpty(heldStack)) {
            if (!isValidCell(heldStack)) return false;

            ItemStack extracted = InventoryHelper.extract(cellInventory, inventorySlot, 1, false);
            if (ItemStacks.isEmpty(extracted)) return false;

            ItemStack remainder = InventoryHelper.insert(cellInventory, inventorySlot, heldStack.copy(), false);
            if (!ItemStacks.isEmpty(remainder)) {
                InventoryHelper.insert(cellInventory, inventorySlot, extracted, false);

                return false;
            }

            player.inventory.setItemStack(extracted);
        } else {
            ItemStack extracted = InventoryHelper.extract(cellInventory, inventorySlot, 1, false);
            if (ItemStacks.isEmpty(extracted)) return false;

            player.inventory.setItemStack(extracted);
        }

        ((EntityPlayerMP) player).updateHeldItem();

        return true;
    }

    /**
     * Insert held cell into storage.
     */
    public static boolean insertCell(IChestOrDrive storage, int targetSlot, EntityPlayer player) {
        ItemStack heldStack = player.inventory.getItemStack();
        if (ItemStacks.isEmpty(heldStack) || !isValidCell(heldStack)) return false;

        IInventory cellInventory = CellDataHandler.getCellInventory(storage);
        if (cellInventory == null) return false;

        int slot = targetSlot >= 0 ? CellDataHandler.toInventorySlot(storage, targetSlot)
            : CellDataHandler.findEmptyCellSlot(cellInventory, storage);
        if (slot < 0) return false;

        ItemStack remainder = InventoryHelper.insert(cellInventory, slot, heldStack.copy(), false);
        int remainderCount = remainder == null ? 0 : remainder.stackSize;
        if (remainderCount < heldStack.stackSize) {
            player.inventory.setItemStack(remainder);
            ((EntityPlayerMP) player).updateHeldItem();

            return true;
        }

        return false;
    }

    /**
     * Insert an upgrade into a cell.
     */
    public static boolean upgradeCell(IChestOrDrive storage, TileEntity tile, int cellSlot, ItemStack upgradeStack,
        EntityPlayer player, int fromSlot) {
        if (ItemStacks.isEmpty(upgradeStack) || !(upgradeStack.getItem() instanceof IUpgradeModule upgradeModule))
            return false;

        Upgrades upgradeType = upgradeModule.getType(upgradeStack);
        if (upgradeType == null) return false;

        IInventory cellInventory = CellDataHandler.getCellInventory(storage);
        if (cellInventory == null) return false;

        int inventorySlot = CellDataHandler.toInventorySlot(storage, cellSlot);
        if (!isValidInventorySlot(cellInventory, inventorySlot)) return false;

        ItemStack cellStack = cellInventory.getStackInSlot(inventorySlot);
        if (ItemStacks.isEmpty(cellStack) || !(cellStack.getItem() instanceof ICellWorkbenchItem cellItem))
            return false;

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

                tile.markDirty();
                forceCellHandlerRefresh(cellInventory, inventorySlot, cellStack);

                return true;
            }
        }

        return false;
    }

    public static ConfigResult getConfigInventory(ICellHandler cellHandler, ItemStack cellStack) {
        ConfigResult result = new ConfigResult();

        if (cellStack.getItem() instanceof ICellWorkbenchItem workbenchItem) {
            result.configAEInv = workbenchItem.getConfigAEInventory(cellStack);
            result.configInv = workbenchItem.getConfigInventory(cellStack);
            result.stackType = workbenchItem.getStackType();
            if (result.configAEInv != null) return result;
        }

        for (IAEStackType<?> type : AEStackTypeRegistry.getSortedTypes()) {
            IMEInventoryHandler<?> rawHandler = cellHandler.getCellInventory(cellStack, null, type);
            if (!(rawHandler instanceof ICellInventoryHandler)) continue;

            ICellInventory<?> cellInv = ((ICellInventoryHandler<?>) rawHandler).getCellInv();
            if (cellInv == null) continue;

            result.configAEInv = cellInv.getConfigAEInventory();
            result.configInv = cellInv.getConfigInventory();
            result.stackType = type;

            return result;
        }

        return result;
    }

    /**
     * Execute a partition action directly (for temp cells).
     */
    public static void executePartitionActionDirect(ConfigResult config, PacketTempCellPartitionAction.Action action,
        int partitionSlot, NBTTagCompound stackData, ICellHandler cellHandler, ItemStack cellStack) {
        PacketPartitionAction.Action mappedAction = switch (action) {
            case ADD_ITEM -> PacketPartitionAction.Action.ADD_ITEM;
            case REMOVE_ITEM -> PacketPartitionAction.Action.REMOVE_ITEM;
            case SET_ALL_FROM_CONTENTS -> PacketPartitionAction.Action.SET_ALL_FROM_CONTENTS;
            case CLEAR_ALL -> PacketPartitionAction.Action.CLEAR_ALL;
            case TOGGLE_ITEM -> PacketPartitionAction.Action.TOGGLE_ITEM;
        };

        executePartitionAction(config, mappedAction, partitionSlot, stackData, cellHandler, cellStack);
    }

    private static void executePartitionAction(ConfigResult config, PacketPartitionAction.Action action,
        int partitionSlot, NBTTagCompound stackData, ICellHandler cellHandler, ItemStack cellStack) {
        IAEStack<?> partitionStack = resolvePartitionStack(config, stackData);
        int slots = getConfigSize(config);

        switch (action) {
            case ADD_ITEM:
                if (partitionSlot >= 0 && partitionSlot < slots && partitionStack != null) {
                    setConfigSlot(config, partitionSlot, partitionStack);
                }
                break;

            case REMOVE_ITEM:
                if (partitionSlot >= 0 && partitionSlot < slots) {
                    setConfigSlot(config, partitionSlot, null);
                }
                break;

            case TOGGLE_ITEM:
                if (partitionStack != null) {
                    int existingSlot = findStackInConfig(config, partitionStack);

                    if (existingSlot >= 0) {
                        setConfigSlot(config, existingSlot, null);
                    } else {
                        int emptySlot = findEmptyConfigSlot(config);
                        if (emptySlot >= 0) setConfigSlot(config, emptySlot, partitionStack);
                    }
                }
                break;

            case SET_ALL_FROM_CONTENTS:
                clearConfig(config);
                setPartitionFromContents(cellHandler, cellStack, config);
                break;

            case CLEAR_ALL:
                clearConfig(config);
                break;
        }
    }

    private static void setPartitionFromContents(ICellHandler cellHandler, ItemStack cellStack, ConfigResult config) {
        for (IAEStackType<?> type : AEStackTypeRegistry.getSortedTypes()) {
            if (config.stackType != null && config.stackType != type) continue;

            IMEInventoryHandler<?> raw = cellHandler.getCellInventory(cellStack, null, type);
            if (!(raw instanceof ICellInventoryHandler)) continue;

            ICellInventory<?> inv = ((ICellInventoryHandler<?>) raw).getCellInv();
            if (inv == null) continue;

            setPartitionFromContentsForUnknownType(inv, config, type);

            return;
        }
    }

    private static void handleCompactingCell(ItemStack cellStack, PacketPartitionAction.Action action,
        NBTTagCompound stackData, ConfigResult config, World world) {
        if (!(cellStack.getItem() instanceof IItemCompactingCell)) return;

        ItemStack partitionItem = null;
        ItemStack displayStack = AEStackUtil.getDisplayStack(resolvePartitionStack(config, stackData));

        if (action == PacketPartitionAction.Action.ADD_ITEM && !ItemStacks.isEmpty(displayStack)) {
            partitionItem = displayStack;
        } else if (action == PacketPartitionAction.Action.TOGGLE_ITEM && !ItemStacks.isEmpty(displayStack)) {
            if (findItemInConfig(config.configInv, displayStack) < 0) partitionItem = displayStack;
        } else if (action == PacketPartitionAction.Action.SET_ALL_FROM_CONTENTS) {
            for (int i = 0; i < getConfigSize(config); i++) {
                ItemStack slot = getConfigDisplayStack(config, i);
                if (!ItemStacks.isEmpty(slot)) {
                    partitionItem = slot;
                    break;
                }
            }
        }

        ((IItemCompactingCell) cellStack.getItem()).initializeCompactingCellChain(cellStack, partitionItem, world);
    }

    public static void forceCellHandlerRefresh(IInventory cellInventory, int cellSlot, ItemStack cellStack) {
        cellInventory.setInventorySlotContents(cellSlot, cellStack);
    }

    public static boolean isValidInventorySlot(IInventory inventory, int slot) {
        return inventory != null && slot >= 0 && slot < inventory.getSizeInventory();
    }

    private static boolean isValidCell(ItemStack stack) {
        return AEApi.instance()
            .registries()
            .cell()
            .getHandler(stack) != null;
    }

    public static int findEmptySlot(IInventory inv) {
        return InventoryHelper.findEmptySlot(inv);
    }

    public static int findItemInConfig(IInventory inv, ItemStack stack) {
        if (inv == null) return -1;

        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (ItemStack.areItemStacksEqual(inv.getStackInSlot(i), stack)) return i;
        }

        return -1;
    }

    public static void setConfigSlot(IInventory inv, int slot, ItemStack stack) {
        InventoryHelper.setSlot(inv, slot, stack);
    }

    public static void clearConfig(IInventory inv) {
        InventoryHelper.clear(inv);
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> void setPartitionFromContentsForUnknownType(ICellInventory<?> inv,
        ConfigResult config, IAEStackType<?> type) {
        setPartitionFromContentsForType((ICellInventory<T>) inv, config, (IAEStackType<T>) type);
    }

    private static <T extends IAEStack<T>> void setPartitionFromContentsForType(ICellInventory<T> inv,
        ConfigResult config, IAEStackType<T> type) {
        IItemList<T> contents = inv.getAvailableItems(type.createList(), IterationCounter.fetchNewId());
        int slot = 0;
        for (T stack : contents) {
            if (slot >= getConfigSize(config)) break;

            T partitionStack = stack.copy();
            partitionStack.setStackSize(1);
            setConfigSlot(config, slot++, partitionStack);
        }
    }

    private static IAEStack<?> resolvePartitionStack(ConfigResult config, NBTTagCompound stackData) {
        IAEStackType<?> targetType = config != null ? config.stackType : null;
        return AEStackUtil.readPartitionStack(stackData, targetType);
    }

    private static int getConfigSize(ConfigResult config) {
        if (config.configAEInv != null) return config.configAEInv.getSizeInventory();
        return config.configInv != null ? config.configInv.getSizeInventory() : 0;
    }

    private static void setConfigSlot(ConfigResult config, int slot, IAEStack<?> stack) {
        if (config.configAEInv != null) {
            config.configAEInv.putAEStackInSlot(slot, stack);
            config.configAEInv.markDirty();
            return;
        }

        ItemStack displayStack = AEStackUtil.getDisplayStack(stack);
        InventoryHelper.setSlot(config.configInv, slot, displayStack);
    }

    private static void clearConfig(ConfigResult config) {
        for (int i = 0; i < getConfigSize(config); i++) {
            setConfigSlot(config, i, null);
        }
    }

    private static int findStackInConfig(ConfigResult config, IAEStack<?> stack) {
        if (stack == null) return -1;

        if (config.configAEInv != null) {
            for (int i = 0; i < config.configAEInv.getSizeInventory(); i++) {
                IAEStack<?> slotStack = config.configAEInv.getAEStackInSlot(i);
                if (slotStack != null && slotStack.isSameType(stack)) return i;
            }

            return -1;
        }

        return findItemInConfig(config.configInv, AEStackUtil.getDisplayStack(stack));
    }

    private static int findEmptyConfigSlot(ConfigResult config) {
        if (config.configAEInv != null) {
            for (int i = 0; i < config.configAEInv.getSizeInventory(); i++) {
                if (config.configAEInv.getAEStackInSlot(i) == null) return i;
            }

            return -1;
        }

        return InventoryHelper.findEmptySlot(config.configInv);
    }

    private static ItemStack getConfigDisplayStack(ConfigResult config, int slot) {
        if (config.configAEInv != null) {
            return AEStackUtil.getDisplayStack(config.configAEInv.getAEStackInSlot(slot));
        }

        return config.configInv != null ? config.configInv.getStackInSlot(slot) : null;
    }

    public static class ConfigResult {

        public IInventory configInv;
        public IAEStackInventory configAEInv;
        public IAEStackType<?> stackType;
    }
}
