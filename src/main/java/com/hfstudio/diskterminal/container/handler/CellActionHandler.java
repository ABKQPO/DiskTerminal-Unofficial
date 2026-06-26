package com.hfstudio.diskterminal.container.handler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.glodblock.github.common.item.ItemFluidDrop;
import com.hfstudio.diskterminal.api.IItemCompactingCell;
import com.hfstudio.diskterminal.integration.ThaumicEnergisticsIntegration;
import com.hfstudio.diskterminal.network.PacketPartitionAction;
import com.hfstudio.diskterminal.network.PacketTempCellPartitionAction;
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
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.IterationCounter;
import appeng.util.item.AEFluidStackType;
import appeng.util.item.AEItemStackType;

/**
 * Handles cell-related actions: partition modifications, ejection, pickup, insertion, upgrades.
 * <p>
 * Cell config/upgrade inventories are {@link IInventory} in 1.7.10; capability-style operations use
 * {@link InventoryHelper}. Item/fluid channels use the fork's {@code IAEStackType} singletons.
 */
public class CellActionHandler {

    /**
     * Handle partition modification for a cell.
     */
    public static boolean handlePartitionAction(IChestOrDrive storage, TileEntity tile, int cellSlot,
        PacketPartitionAction.Action action, int partitionSlot, ItemStack itemStack) {
        IInventory cellInventory = CellDataHandler.getCellInventory(storage);
        if (cellInventory == null) return false;

        ItemStack cellStack = cellInventory.getStackInSlot(cellSlot);
        if (ItemStacks.isEmpty(cellStack)) return false;

        ICellHandler cellHandler = AEApi.instance()
            .registries()
            .cell()
            .getHandler(cellStack);
        if (cellHandler == null) return false;

        ConfigResult config = getConfigInventory(cellHandler, cellStack);
        if (config.configInv == null) return false;

        executePartitionAction(
            config.configInv,
            action,
            partitionSlot,
            itemStack,
            cellHandler,
            cellStack,
            config.isFluidCell,
            config.essentiaData);

        forceCellHandlerRefresh(cellInventory, cellSlot, cellStack);
        handleCompactingCell(cellStack, action, itemStack, config.configInv, tile.getWorldObj());

        tile.markDirty();

        return true;
    }

    /**
     * Eject a cell from storage to player's inventory.
     */
    public static boolean ejectCell(IChestOrDrive storage, int cellSlot, EntityPlayer player) {
        IInventory cellInventory = CellDataHandler.getCellInventory(storage);
        if (cellInventory == null) return false;

        ItemStack cellStack = cellInventory.getStackInSlot(cellSlot);
        if (ItemStacks.isEmpty(cellStack)) return false;

        ItemStack extracted = InventoryHelper.extract(cellInventory, cellSlot, 1, false);
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

        ItemStack cellStack = cellInventory.getStackInSlot(cellSlot);
        ItemStack heldStack = player.inventory.getItemStack();

        if (ItemStacks.isEmpty(cellStack)) {
            if (!toInventory && !ItemStacks.isEmpty(heldStack) && isValidCell(heldStack)) {
                ItemStack remainder = InventoryHelper.insert(cellInventory, cellSlot, heldStack.copy(), false);
                player.inventory.setItemStack(remainder);
                ((EntityPlayerMP) player).updateHeldItem();

                return true;
            }

            return false;
        }

        if (toInventory) {
            ItemStack extracted = InventoryHelper.extract(cellInventory, cellSlot, 1, false);
            if (ItemStacks.isEmpty(extracted)) return false;

            if (!player.inventory.addItemStackToInventory(extracted)) {
                player.dropPlayerItemWithRandomChoice(extracted, false);
            }

            return true;
        }

        if (!ItemStacks.isEmpty(heldStack)) {
            if (!isValidCell(heldStack)) return false;

            ItemStack extracted = InventoryHelper.extract(cellInventory, cellSlot, 1, false);
            if (ItemStacks.isEmpty(extracted)) return false;

            ItemStack remainder = InventoryHelper.insert(cellInventory, cellSlot, heldStack.copy(), false);
            if (!ItemStacks.isEmpty(remainder)) {
                InventoryHelper.insert(cellInventory, cellSlot, extracted, false);

                return false;
            }

            player.inventory.setItemStack(extracted);
        } else {
            ItemStack extracted = InventoryHelper.extract(cellInventory, cellSlot, 1, false);
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

        int slot = targetSlot >= 0 ? targetSlot : InventoryHelper.findEmptySlot(cellInventory);
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
        if (ItemStacks.isEmpty(upgradeStack) || !(upgradeStack.getItem() instanceof IUpgradeModule)) return false;

        IUpgradeModule upgradeModule = (IUpgradeModule) upgradeStack.getItem();
        Upgrades upgradeType = upgradeModule.getType(upgradeStack);
        if (upgradeType == null) return false;

        IInventory cellInventory = CellDataHandler.getCellInventory(storage);
        if (cellInventory == null) return false;

        ItemStack cellStack = cellInventory.getStackInSlot(cellSlot);
        if (ItemStacks.isEmpty(cellStack) || !(cellStack.getItem() instanceof ICellWorkbenchItem)) return false;

        ICellWorkbenchItem cellItem = (ICellWorkbenchItem) cellStack.getItem();
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
                forceCellHandlerRefresh(cellInventory, cellSlot, cellStack);

                return true;
            }
        }

        return false;
    }

    // --- Helper methods ---

    public static ConfigResult getConfigInventory(ICellHandler cellHandler, ItemStack cellStack) {
        ConfigResult result = new ConfigResult();

        IMEInventoryHandler<?> rawItem = cellHandler.getCellInventory(cellStack, null, AEItemStackType.ITEM_STACK_TYPE);
        if (rawItem instanceof ICellInventoryHandler) {
            @SuppressWarnings("unchecked")
            ICellInventoryHandler<IAEItemStack> itemHandler = (ICellInventoryHandler<IAEItemStack>) rawItem;
            if (itemHandler.getCellInv() != null) {
                result.configInv = itemHandler.getCellInv()
                    .getConfigInventory();
                result.itemHandler = itemHandler;

                return result;
            }

            if (cellStack.getItem() instanceof ICellWorkbenchItem) {
                result.configInv = ((ICellWorkbenchItem) cellStack.getItem()).getConfigInventory(cellStack);
                result.itemHandler = itemHandler;

                return result;
            }
        }

        IMEInventoryHandler<?> rawFluid = cellHandler
            .getCellInventory(cellStack, null, AEFluidStackType.FLUID_STACK_TYPE);
        if (rawFluid instanceof ICellInventoryHandler) {
            @SuppressWarnings("unchecked")
            ICellInventoryHandler<IAEFluidStack> fluidHandler = (ICellInventoryHandler<IAEFluidStack>) rawFluid;
            if (fluidHandler.getCellInv() != null) {
                result.configInv = fluidHandler.getCellInv()
                    .getConfigInventory();
                result.fluidHandler = fluidHandler;
                result.isFluidCell = true;

                return result;
            }

            if (cellStack.getItem() instanceof ICellWorkbenchItem) {
                result.configInv = ((ICellWorkbenchItem) cellStack.getItem()).getConfigInventory(cellStack);
                result.fluidHandler = fluidHandler;
                result.isFluidCell = true;

                return result;
            }
        }

        result.essentiaData = ThaumicEnergisticsIntegration.tryGetEssentiaConfigInventory(cellHandler, cellStack);
        if (result.essentiaData != null) result.configInv = (IInventory) result.essentiaData[0];

        return result;
    }

    /**
     * Execute a partition action directly (for temp cells).
     */
    public static void executePartitionActionDirect(IInventory configInv, PacketTempCellPartitionAction.Action action,
        int partitionSlot, ItemStack itemStack, ICellHandler cellHandler, ItemStack cellStack, boolean isFluidCell,
        Object[] essentiaData) {
        PacketPartitionAction.Action mappedAction = switch (action) {
            case ADD_ITEM -> PacketPartitionAction.Action.ADD_ITEM;
            case REMOVE_ITEM -> PacketPartitionAction.Action.REMOVE_ITEM;
            case SET_ALL_FROM_CONTENTS -> PacketPartitionAction.Action.SET_ALL_FROM_CONTENTS;
            case CLEAR_ALL -> PacketPartitionAction.Action.CLEAR_ALL;
            case TOGGLE_ITEM -> PacketPartitionAction.Action.TOGGLE_ITEM;
        };

        executePartitionAction(
            configInv,
            mappedAction,
            partitionSlot,
            itemStack,
            cellHandler,
            cellStack,
            isFluidCell,
            essentiaData);
    }

    private static void executePartitionAction(IInventory configInv, PacketPartitionAction.Action action,
        int partitionSlot, ItemStack itemStack, ICellHandler cellHandler, ItemStack cellStack, boolean isFluidCell,
        Object[] essentiaData) {
        ItemStack normalizedStack = isFluidCell ? normalizeFluidStack(itemStack) : itemStack;

        switch (action) {
            case ADD_ITEM:
                if (partitionSlot >= 0 && partitionSlot < configInv.getSizeInventory()
                    && !ItemStacks.isEmpty(normalizedStack)) {
                    setConfigSlot(configInv, partitionSlot, normalizedStack);
                }
                break;

            case REMOVE_ITEM:
                if (partitionSlot >= 0 && partitionSlot < configInv.getSizeInventory()) {
                    setConfigSlot(configInv, partitionSlot, null);
                }
                break;

            case TOGGLE_ITEM:
                if (!ItemStacks.isEmpty(normalizedStack)) {
                    int existingSlot = isFluidCell ? findFluidInConfig(configInv, normalizedStack)
                        : findItemInConfig(configInv, normalizedStack);

                    if (existingSlot >= 0) {
                        setConfigSlot(configInv, existingSlot, null);
                    } else {
                        int emptySlot = InventoryHelper.findEmptySlot(configInv);
                        if (emptySlot >= 0) setConfigSlot(configInv, emptySlot, normalizedStack);
                    }
                }
                break;

            case SET_ALL_FROM_CONTENTS:
                InventoryHelper.clear(configInv);
                setPartitionFromContents(cellHandler, cellStack, configInv, isFluidCell, essentiaData);
                break;

            case CLEAR_ALL:
                InventoryHelper.clear(configInv);
                break;
        }
    }

    private static void setPartitionFromContents(ICellHandler cellHandler, ItemStack cellStack, IInventory configInv,
        boolean isFluidCell, Object[] essentiaData) {
        if (essentiaData != null) {
            ThaumicEnergisticsIntegration.setAllFromEssentiaContents(configInv, essentiaData);

            return;
        }

        if (!isFluidCell) {
            IMEInventoryHandler<?> raw = cellHandler.getCellInventory(cellStack, null, AEItemStackType.ITEM_STACK_TYPE);
            if (!(raw instanceof ICellInventoryHandler)) return;

            @SuppressWarnings("unchecked")
            ICellInventory<IAEItemStack> inv = ((ICellInventoryHandler<IAEItemStack>) raw).getCellInv();
            if (inv == null) return;

            IItemList<IAEItemStack> contents = inv
                .getAvailableItems(AEItemStackType.ITEM_STACK_TYPE.createList(), IterationCounter.fetchNewId());
            int slot = 0;
            for (IAEItemStack stack : contents) {
                if (slot >= configInv.getSizeInventory()) break;
                setConfigSlot(configInv, slot++, stack.getItemStack());
            }
        } else {
            IMEInventoryHandler<?> raw = cellHandler
                .getCellInventory(cellStack, null, AEFluidStackType.FLUID_STACK_TYPE);
            if (!(raw instanceof ICellInventoryHandler)) return;

            @SuppressWarnings("unchecked")
            ICellInventory<IAEFluidStack> inv = ((ICellInventoryHandler<IAEFluidStack>) raw).getCellInv();
            if (inv == null) return;

            IItemList<IAEFluidStack> contents = inv
                .getAvailableItems(AEFluidStackType.FLUID_STACK_TYPE.createList(), IterationCounter.fetchNewId());
            int slot = 0;
            for (IAEFluidStack stack : contents) {
                if (slot >= configInv.getSizeInventory()) break;
                setConfigSlot(configInv, slot++, normalizeFluidStack(ItemFluidDrop.newStack(stack.getFluidStack())));
            }
        }
    }

    /**
     * Normalize a fluid ItemStack so partition entries compare by fluid type only.
     */
    private static ItemStack normalizeFluidStack(ItemStack stack) {
        FluidStack fluid = extractFluidFromStack(stack);
        if (fluid == null || fluid.getFluid() == null) return stack;

        FluidStack normalized = new FluidStack(fluid.getFluid(), 1000);

        return ItemFluidDrop.newStack(normalized);
    }

    private static void handleCompactingCell(ItemStack cellStack, PacketPartitionAction.Action action,
        ItemStack itemStack, IInventory configInv, World world) {
        if (!(cellStack.getItem() instanceof IItemCompactingCell)) return;

        ItemStack partitionItem = null;

        if (action == PacketPartitionAction.Action.ADD_ITEM && !ItemStacks.isEmpty(itemStack)) {
            partitionItem = itemStack;
        } else if (action == PacketPartitionAction.Action.TOGGLE_ITEM && !ItemStacks.isEmpty(itemStack)) {
            if (findItemInConfig(configInv, itemStack) < 0) partitionItem = itemStack;
        } else if (action == PacketPartitionAction.Action.SET_ALL_FROM_CONTENTS) {
            for (int i = 0; i < configInv.getSizeInventory(); i++) {
                ItemStack slot = configInv.getStackInSlot(i);
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
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (ItemStack.areItemStacksEqual(inv.getStackInSlot(i), stack)) return i;
        }

        return -1;
    }

    public static int findFluidInConfig(IInventory inv, ItemStack stack) {
        FluidStack targetFluid = extractFluidFromStack(stack);
        if (targetFluid == null || targetFluid.getFluid() == null) return -1;

        for (int i = 0; i < inv.getSizeInventory(); i++) {
            FluidStack slotFluid = extractFluidFromStack(inv.getStackInSlot(i));

            if (slotFluid != null && slotFluid.getFluid() == targetFluid.getFluid()) return i;
        }

        return -1;
    }

    private static FluidStack extractFluidFromStack(ItemStack stack) {
        if (ItemStacks.isEmpty(stack)) return null;

        if (stack.getItem() instanceof ItemFluidDrop) return ItemFluidDrop.getFluidStack(stack);

        return FluidContainerRegistry.getFluidForFilledItem(stack);
    }

    public static void setConfigSlot(IInventory inv, int slot, ItemStack stack) {
        InventoryHelper.setSlot(inv, slot, stack);
    }

    public static void clearConfig(IInventory inv) {
        InventoryHelper.clear(inv);
    }

    public static class ConfigResult {

        public IInventory configInv;
        public ICellInventoryHandler<IAEItemStack> itemHandler;
        public ICellInventoryHandler<IAEFluidStack> fluidHandler;
        public boolean isFluidCell;
        /** Essentia cell data: [configInv, ...] or null if not an essentia cell */
        public Object[] essentiaData;
    }
}
