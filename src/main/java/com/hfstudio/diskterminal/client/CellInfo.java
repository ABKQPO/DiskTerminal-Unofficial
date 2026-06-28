package com.hfstudio.diskterminal.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.hfstudio.diskterminal.gui.rename.RenameTargetType;
import com.hfstudio.diskterminal.gui.rename.Renameable;
import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.storage.data.IAEStack;

/**
 * Client-side data holder for cell information received from server.
 * <p>
 * Written by {@code CellDataHandler#createCellData}.
 */
public class CellInfo implements Renameable {

    private long parentStorageId;
    private final int slot;
    private final int status;
    private final StorageType storageType;
    private final String stackTypeId;
    private final ItemStack cellItem;
    private final long usedBytes;
    private final long totalBytes;
    private final long usedTypes;
    private final long totalTypes;
    private final int configSlotCount;
    private final long storedItemCount;
    private final List<ItemStack> partition = new ArrayList<>();
    private final List<ItemStack> contents = new ArrayList<>();
    private final List<Long> contentCounts = new ArrayList<>();

    // Upgrade tracking
    private final List<ItemStack> upgrades = new ArrayList<>();
    private final List<Integer> upgradeSlotIndices = new ArrayList<>();
    private final int upgradeSlotCount;

    public CellInfo(NBTTagCompound nbt) {
        this.slot = nbt.getInteger("slot");
        this.status = nbt.getInteger("status");
        this.storageType = StorageType.fromNBT(nbt);
        this.stackTypeId = nbt.hasKey("stackType") ? nbt.getString("stackType") : stackTypeIdFrom(storageType);
        this.cellItem = nbt.hasKey("cellItem") ? ItemStacks.load(nbt.getCompoundTag("cellItem")) : null;
        this.usedBytes = nbt.getLong("usedBytes");
        this.totalBytes = nbt.getLong("totalBytes");
        this.usedTypes = nbt.getLong("usedTypes");
        this.totalTypes = nbt.getLong("totalTypes");
        this.configSlotCount = nbt.hasKey("configSlotCount") ? nbt.getInteger("configSlotCount") : (int) totalTypes;
        this.storedItemCount = nbt.getLong("storedItemCount");

        // Parse upgrade items for display
        if (nbt.hasKey("upgrades")) {
            NBTTagList upgradeList = nbt.getTagList("upgrades", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < upgradeList.tagCount(); i++) {
                NBTTagCompound upgradeNbt = upgradeList.getCompoundTagAt(i);
                ItemStack upgrade = ItemStacks.load(upgradeNbt);
                if (!ItemStacks.isEmpty(upgrade)) {
                    this.upgrades.add(upgrade);
                    // Read actual slot index, fallback to iteration index for backwards compatibility
                    int slotIndex = upgradeNbt.hasKey("slot") ? upgradeNbt.getInteger("slot") : i;
                    this.upgradeSlotIndices.add(slotIndex);
                }
            }
        }

        // Upgrade slot count from server; fallback to 2 when not provided
        this.upgradeSlotCount = nbt.hasKey("upgradeSlotCount") ? nbt.getInteger("upgradeSlotCount") : 2;

        if (nbt.hasKey("partition")) {
            NBTTagList partList = nbt.getTagList("partition", Constants.NBT.TAG_COMPOUND);

            // Place items at their correct slot positions
            for (int i = 0; i < partList.tagCount(); i++) {
                NBTTagCompound partNbt = partList.getCompoundTagAt(i);
                int slot = partNbt.hasKey("slot") ? partNbt.getInteger("slot") : i;

                IAEStack<?> aeStack = AEStackUtil.readStackFromNBT(partNbt);
                ItemStack stack = AEStackUtil.getDisplayStack(aeStack);
                if (ItemStacks.isEmpty(stack) && partNbt.hasKey("id")) stack = ItemStacks.loadDisplay(partNbt);
                if (!ItemStacks.isEmpty(stack)) {
                    fillPartitionSlots(slot + 1);
                    this.partition.set(slot, stack);
                }
            }
        }

        if (nbt.hasKey("contents")) {
            NBTTagList contentList = nbt.getTagList("contents", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < contentList.tagCount(); i++) {
                NBTTagCompound stackNbt = contentList.getCompoundTagAt(i);
                IAEStack<?> aeStack = AEStackUtil.readStackFromNBT(stackNbt);
                ItemStack stack = AEStackUtil.getDisplayStack(aeStack);
                if (ItemStacks.isEmpty(stack)) stack = ItemStacks.loadDisplay(stackNbt);

                long count;
                if (stackNbt.hasKey("Cnt")) {
                    count = stackNbt.getLong("Cnt");
                } else {
                    count = aeStack != null ? aeStack.getStackSize() : stack == null ? 0 : stack.stackSize;
                }

                this.contents.add(stack);
                this.contentCounts.add(count);
            }
        }
    }

    public void setParentStorageId(long parentStorageId) {
        this.parentStorageId = parentStorageId;
    }

    public long getParentStorageId() {
        return parentStorageId;
    }

    public int getSlot() {
        return slot;
    }

    public int getStatus() {
        return status;
    }

    public StorageType getStorageType() {
        return storageType;
    }

    public String getStackTypeId() {
        return stackTypeId;
    }

    public boolean isFluid() {
        return storageType.isFluid();
    }

    public boolean isEssentia() {
        return storageType.isEssentia();
    }

    public boolean isItem() {
        return storageType.isItem();
    }

    public ItemStack getCellItem() {
        return cellItem;
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public long getUsedTypes() {
        return usedTypes;
    }

    public long getTotalTypes() {
        return totalTypes;
    }

    public int getConfigSlotCount() {
        return configSlotCount;
    }

    public long getStoredItemCount() {
        return storedItemCount;
    }

    public List<ItemStack> getPartition() {
        return partition;
    }

    private void fillPartitionSlots(int size) {
        while (this.partition.size() < size) this.partition.add(null);
    }

    public List<ItemStack> getContents() {
        return contents;
    }

    public long getContentCount(int index) {
        if (index < 0 || index >= contentCounts.size()) return 0;

        return contentCounts.get(index);
    }

    public float getByteUsagePercent() {
        if (totalBytes == 0) return 0;

        return (float) usedBytes / totalBytes;
    }

    public String getDisplayName() {
        if (!ItemStacks.isEmpty(cellItem)) return cellItem.getDisplayName();

        return I18n.format("gui.disk_terminal.cell_empty");
    }

    public List<ItemStack> getUpgrades() {
        return upgrades;
    }

    /**
     * Get the actual slot index for an upgrade in the upgrade inventory.
     *
     * @param index The index in the upgrades list (0 to upgrades.size()-1)
     * @return The actual slot index in the upgrade inventory
     */
    public int getUpgradeSlotIndex(int index) {
        if (index < 0 || index >= upgradeSlotIndices.size()) return index;

        return upgradeSlotIndices.get(index);
    }

    public int getUpgradeSlotCount() {
        return upgradeSlotCount;
    }

    public boolean hasUpgradeSpace() {
        return upgrades.size() < getUpgradeSlotCount();
    }

    /**
     * Check if this cell can potentially accept the given upgrade item.
     * This is a client-side heuristic only - actual validation happens server-side.
     *
     * @param upgradeStack The upgrade item to check
     * @return true if the upgrade might be insertable
     */
    public boolean canAcceptUpgrade(ItemStack upgradeStack) {
        if (ItemStacks.isEmpty(upgradeStack)) return false;
        if (!(upgradeStack.getItem() instanceof IUpgradeModule)) return false;

        // Distinguish real upgrades from storage components that also implement IUpgradeModule
        // Real upgrades (speed card, capacity card, etc.) return a non-null Upgrades type
        if (((IUpgradeModule) upgradeStack.getItem()).getType(upgradeStack) == null) return false;

        return hasUpgradeSpace();
    }

    @Override
    public boolean isRenameable() {
        return !ItemStacks.isEmpty(cellItem);
    }

    @Override
    public String getCustomName() {
        if (ItemStacks.isEmpty(cellItem)) return null;
        if (cellItem.hasDisplayName()) return cellItem.getDisplayName();

        return null;
    }

    @Override
    public boolean hasCustomName() {
        return !ItemStacks.isEmpty(cellItem) && cellItem.hasDisplayName();
    }

    @Override
    public void setCustomName(String name) {
        // Client-side optimistic update for the ItemStack display name
        if (ItemStacks.isEmpty(cellItem)) return;

        if (name == null || name.isEmpty()) {
            ItemStacks.clearCustomName(cellItem);
        } else {
            cellItem.setStackDisplayName(name);
        }
    }

    @Override
    public RenameTargetType getRenameTargetType() {
        return RenameTargetType.CELL;
    }

    private static String stackTypeIdFrom(StorageType storageType) {
        if (storageType.isFluid()) return "fluid";
        if (storageType.isEssentia()) return "essentia";

        return "item";
    }

    @Override
    public long getRenameId() {
        return parentStorageId;
    }

    @Override
    public int getRenameSecondaryId() {
        return slot;
    }
}
