package com.hfstudio.diskterminal.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.util.Constants;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.hfstudio.diskterminal.gui.rename.RenameTargetType;
import com.hfstudio.diskterminal.gui.rename.Renameable;
import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.ItemStacks;
import com.hfstudio.diskterminal.util.PosUtil;

import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.storage.data.IAEStack;

/**
 * Client-side data holder for storage bus information received from server.
 * Similar to CellInfo but for storage buses which connect to external inventories.
 */
public class StorageBusInfo implements Renameable, Prioritizable {

    /**
     * Base number of config slots without any capacity upgrades.
     */
    public static final int BASE_CONFIG_SLOTS = 18;

    /**
     * Additional config slots per capacity upgrade.
     */
    public static final int SLOTS_PER_CAPACITY_UPGRADE = 9;

    /**
     * Maximum number of config slots (with 5 capacity upgrades).
     */
    public static final int MAX_CONFIG_SLOTS = 63;

    /**
     * Calculate the number of available config slots for a given capacity upgrade count.
     */
    public static int calculateAvailableSlots(int capacityUpgrades) {
        return Math.min(BASE_CONFIG_SLOTS + SLOTS_PER_CAPACITY_UPGRADE * capacityUpgrades, MAX_CONFIG_SLOTS);
    }

    private long parentStorageId;
    private final long id;
    private final BlockPos pos;
    private final int dimension;
    private final EnumFacing side;
    private final int priority;
    private final int baseConfigSlots;
    private final int slotsPerUpgrade;
    private final int maxConfigSlots;
    private final StorageType storageType;
    private final BusRole busRole;
    private final String stackTypeId;
    private final int accessRestriction; // 0=NO_ACCESS, 1=READ, 2=WRITE, 3=READ_WRITE
    private final String customName; // Storage bus custom name (takes priority over connectedName)
    private final String namePrefixKey; // Optional translated prefix prepended to the resolved display name
    private final String connectedName;
    private final ItemStack connectedIcon;
    private final ItemStack busIcon;
    private final boolean connectedIconIsTarget;
    private final List<ItemStack> partition = new ArrayList<>();
    private final List<ItemStack> contents = new ArrayList<>();
    private final List<Long> contentCounts = new ArrayList<>();
    private final List<ItemStack> upgrades = new ArrayList<>();
    private final List<Integer> upgradeSlotIndices = new ArrayList<>();
    private final boolean supportsPriorityFlag;
    private final boolean supportsIOModeFlag;
    private final int upgradeSlotCount;

    public StorageBusInfo(NBTTagCompound nbt) {
        this.id = nbt.getLong("id");
        this.pos = PosUtil.fromLong(nbt.getLong("pos"));
        this.dimension = nbt.getInteger("dim");
        this.side = EnumFacing.getFront(nbt.getInteger("side"));
        this.priority = nbt.getInteger("priority");
        this.storageType = StorageType.fromNBT(nbt);
        this.busRole = BusRole.fromNBT(nbt);
        this.stackTypeId = nbt.hasKey("stackType") ? nbt.getString("stackType") : stackTypeIdFrom(storageType);
        this.accessRestriction = nbt.hasKey("access") ? nbt.getInteger("access") : 3; // Default READ_WRITE

        // Per-implementation slot parameters (optional; default to AE2 values)
        this.baseConfigSlots = nbt.hasKey("baseConfigSlots") ? nbt.getInteger("baseConfigSlots") : BASE_CONFIG_SLOTS;
        this.slotsPerUpgrade = nbt.hasKey("slotsPerUpgrade") ? nbt.getInteger("slotsPerUpgrade")
            : SLOTS_PER_CAPACITY_UPGRADE;
        this.maxConfigSlots = nbt.hasKey("maxConfigSlots") ? nbt.getInteger("maxConfigSlots") : MAX_CONFIG_SLOTS;

        // Capability flags provided by scanners
        this.supportsPriorityFlag = nbt.getBoolean("supportsPriority");
        this.supportsIOModeFlag = nbt.getBoolean("supportsIOMode");

        // Upgrade slot count from server; fallback to 5 if not provided
        this.upgradeSlotCount = nbt.hasKey("upgradeSlotCount") ? nbt.getInteger("upgradeSlotCount") : 5;

        // Storage bus custom name (takes priority over connected block name)
        this.customName = nbt.hasKey("customName") ? nbt.getString("customName") : null;
        this.namePrefixKey = nbt.hasKey("namePrefixKey") ? nbt.getString("namePrefixKey") : null;

        // Connected inventory info
        this.connectedName = nbt.hasKey("connectedName") ? nbt.getString("connectedName") : null;
        this.connectedIcon = nbt.hasKey("connectedIcon") ? ItemStacks.load(nbt.getCompoundTag("connectedIcon")) : null;
        this.connectedIconIsTarget = nbt.getBoolean("connectedIconIsTarget");
        this.busIcon = nbt.hasKey("busIcon") ? ItemStacks.load(nbt.getCompoundTag("busIcon"))
            : connectedIconIsTarget ? null : connectedIcon;

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

        fillPartitionSlots(getAvailableConfigSlots());

        // Parse partition (config inventory)
        if (nbt.hasKey("partition")) {
            NBTTagList partList = nbt.getTagList("partition", Constants.NBT.TAG_COMPOUND);

            // Place items at their correct slot positions
            for (int i = 0; i < partList.tagCount(); i++) {
                NBTTagCompound partNbt = partList.getCompoundTagAt(i);
                int slot = partNbt.hasKey("slot") ? partNbt.getInteger("slot") : i;

                IAEStack<?> aeStack = AEStackUtil.readStackFromNBT(partNbt);
                ItemStack stack = AEStackUtil.getDisplayStack(aeStack);
                if (ItemStacks.isEmpty(stack) && partNbt.hasKey("id")) stack = ItemStacks.load(partNbt);
                if (!ItemStacks.isEmpty(stack)) {
                    fillPartitionSlots(slot + 1);
                    this.partition.set(slot, stack);
                }
            }
        }

        // Parse contents (inventory preview)
        if (nbt.hasKey("contents")) {
            NBTTagList contentList = nbt.getTagList("contents", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < contentList.tagCount(); i++) {
                NBTTagCompound stackNbt = contentList.getCompoundTagAt(i);
                IAEStack<?> aeStack = AEStackUtil.readStackFromNBT(stackNbt);
                ItemStack stack = AEStackUtil.getDisplayStack(aeStack);
                if (ItemStacks.isEmpty(stack)) stack = ItemStacks.load(stackNbt);

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

    @Override
    public long getId() {
        return id;
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getDimension() {
        return dimension;
    }

    public EnumFacing getSide() {
        return side;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    /**
     * Get the number of available config slots based on capacity upgrades.
     * Formula: 18 + 9 * capacityUpgrades, capped at 63.
     * Essentia buses always have 63 slots (they don't use capacity upgrades).
     */
    public int getAvailableConfigSlots() {
        int raw = baseConfigSlots + slotsPerUpgrade * Math.max(0, getInstalledUpgrades(Upgrades.CAPACITY));

        return Math.min(raw, maxConfigSlots);
    }

    public StorageType getStorageType() {
        return storageType;
    }

    public BusRole getBusRole() {
        return busRole;
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

    /**
     * Check if this storage bus supports IO mode toggling.
     */
    public boolean supportsIOMode() {
        return supportsIOModeFlag;
    }

    /**
     * Check if this storage bus supports priority editing.
     */
    @Override
    public boolean supportsPriority() {
        return supportsPriorityFlag;
    }

    /**
     * Get the access restriction mode.
     *
     * @return 0=NO_ACCESS, 1=READ, 2=WRITE, 3=READ_WRITE
     */
    public int getAccessRestriction() {
        return accessRestriction;
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

    public String getDisplayName() {
        return I18n.format(busRole.getNameKey());
    }

    public String getWarningDisplayName() {
        if (customName != null && !customName.isEmpty()) return customName;

        String baseName = getDisplayName();
        String prefixKey = getResolvedPrefixKey();
        if (prefixKey == null || prefixKey.isEmpty()) return baseName;

        return I18n.format(prefixKey) + " " + baseName;
    }

    /**
     * Get localized name for display.
     * Priority: custom name &gt; connected inventory name &gt; bus role name.
     */
    public String getLocalizedName() {
        String baseName;

        // Custom name takes highest priority
        if (customName != null && !customName.isEmpty()) {
            baseName = customName;

            // Fall back to connected inventory name
        } else if (!ItemStacks.isEmpty(connectedIcon)) {
            baseName = connectedIcon.getDisplayName();

        } else if (connectedName != null && !connectedName.isEmpty()) {
            baseName = connectedName;

        } else {
            baseName = getDisplayName();
        }

        String prefixKey = getResolvedPrefixKey();
        if (prefixKey == null || prefixKey.isEmpty()) return baseName;

        return I18n.format(prefixKey) + " " + baseName;
    }

    /**
     * Check if this storage bus has a connected inventory.
     */
    public boolean hasConnectedInventory() {
        return connectedName != null && !connectedName.isEmpty();
    }

    /**
     * Get the icon of the connected inventory.
     */
    public ItemStack getConnectedInventoryIcon() {
        return connectedIcon;
    }

    /**
     * Get the storage bus part icon used as an overlay when the main icon is the target block.
     */
    public ItemStack getBusIcon() {
        return busIcon;
    }

    public boolean isConnectedIconTarget() {
        return connectedIconIsTarget;
    }

    public String getLocationString() {
        return I18n.format("gui.disk_terminal.location_format", pos.getX(), pos.getY(), pos.getZ(), dimension);
    }

    /**
     * Check if this storage bus has any partition configured.
     */
    public boolean hasPartition() {
        for (ItemStack stack : partition) {
            if (!ItemStacks.isEmpty(stack)) return true;
        }

        return false;
    }

    /**
     * Get count of non-empty partition slots.
     */
    public int getPartitionCount() {
        int count = 0;
        for (ItemStack stack : partition) {
            if (!ItemStacks.isEmpty(stack)) count++;
        }

        return count;
    }

    /**
     * Get count of unique items in the connected inventory.
     */
    public int getContentTypeCount() {
        return contents.size();
    }

    /**
     * Get total item count in the connected inventory.
     */
    public long getTotalItemCount() {
        long total = 0;
        for (Long count : contentCounts) total += count;

        return total;
    }

    /**
     * Check if this storage bus has space for more upgrades.
     * Storage buses have 5 upgrade slots.
     */
    public boolean hasUpgradeSpace() {
        return upgrades.size() < getUpgradeSlotCount();
    }

    /**
     * Get the total number of upgrade slots available.
     * Standard AE2 storage buses have 5 upgrade slots.
     *
     * @return The total upgrade slot count
     */
    public int getUpgradeSlotCount() {
        return upgradeSlotCount;
    }

    /**
     * Get the current installed count of a specific upgrade type.
     *
     * @param upgradeType The upgrade type to count
     * @return The number currently installed
     */
    public int getInstalledUpgrades(Upgrades upgradeType) {
        if (upgradeType == null) return 0;

        int count = 0;
        for (ItemStack upgrade : upgrades) {
            if (upgrade.getItem() instanceof IUpgradeModule) {
                Upgrades type = ((IUpgradeModule) upgrade.getItem()).getType(upgrade);
                if (type == upgradeType) count++;
            }
        }

        return count;
    }

    /**
     * Check if this storage bus can potentially accept the given upgrade item.
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
        return true;
    }

    @Override
    public String getCustomName() {
        return customName;
    }

    @Override
    public boolean hasCustomName() {
        return customName != null && !customName.isEmpty();
    }

    @Override
    public void setCustomName(String name) {
        // Client-side optimistic update not supported; server sends refresh.
    }

    @Override
    public RenameTargetType getRenameTargetType() {
        return RenameTargetType.STORAGE_BUS;
    }

    @Override
    public long getRenameId() {
        return id;
    }

    private static String stackTypeIdFrom(StorageType storageType) {
        if (storageType.isFluid()) return "fluid";
        if (storageType.isEssentia()) return "essentia";

        return "item";
    }

    private String getResolvedPrefixKey() {
        if (namePrefixKey != null && !namePrefixKey.isEmpty()) return namePrefixKey;

        return busRole.getPrefixKey();
    }

}
