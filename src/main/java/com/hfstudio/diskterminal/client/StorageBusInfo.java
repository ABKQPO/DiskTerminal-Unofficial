package com.hfstudio.diskterminal.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.hfstudio.diskterminal.gui.ComparisonUtils;
import com.hfstudio.diskterminal.gui.rename.RenameTargetType;
import com.hfstudio.diskterminal.gui.rename.Renameable;
import com.hfstudio.diskterminal.storagebus.model.StorageBusId;
import com.hfstudio.diskterminal.storagebus.runtime.StorageBusCapabilityIds;
import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.FluidStacks;
import com.hfstudio.diskterminal.util.ItemStacks;
import com.hfstudio.diskterminal.util.PosUtil;

import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.AEStackTypeRegistry;

/**
 * Client-side data holder for storage bus information received from server.
 * Similar to CellInfo but for storage buses which connect to external inventories.
 */
public class StorageBusInfo implements Renameable, Prioritizable {

    public enum HeaderModeButtonKind {
        NONE,
        IO_ACCESS,
        AUTO_PULL
    }

    /**
     * Base number of config slots without any capacity upgrades.
     */
    public static final int BASE_CONFIG_SLOTS = 18;

    private static final String CAPABILITY_RENAME = StorageBusCapabilityIds.RENAME.toString();
    private static final String CAPABILITY_PRIORITY = StorageBusCapabilityIds.PRIORITY.toString();

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
    private final int sideOrdinal;
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
    private final String displayName;
    private final String namePrefixKey; // Optional translated prefix prepended to the resolved display name
    private final String connectedName;
    private final ItemStack connectedIcon;
    private final ItemStack busIcon;
    private final boolean connectedIconIsTarget;
    private final boolean preferDisplayName;
    private final List<ItemStack> partition = new ArrayList<>();
    private final List<String> partitionSlotTypeIds = new ArrayList<>();
    private final List<ItemStack> contents = new ArrayList<>();
    private final List<Long> contentCounts = new ArrayList<>();
    private final List<String> contentTypeIds = new ArrayList<>();
    private final List<ItemStack> upgrades = new ArrayList<>();
    private final List<Integer> upgradeSlotIndices = new ArrayList<>();
    private final boolean supportsPriorityFlag;
    private final boolean supportsIOModeFlag;
    private final boolean supportsRenameFlag;
    private final boolean supportsAutoPullFlag;
    private final boolean autoPullEnabledFlag;
    private final int upgradeSlotCount;
    private final Set<String> availableCapabilities = new HashSet<>();

    public StorageBusInfo(NBTTagCompound nbt) {
        this.id = nbt.getLong("id");
        this.pos = PosUtil.fromLong(nbt.getLong("pos"));
        this.dimension = nbt.getInteger("dim");
        this.sideOrdinal = nbt.getInteger("side");
        this.side = readSide(sideOrdinal);
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
        this.supportsRenameFlag = !nbt.hasKey("supportsRename") || nbt.getBoolean("supportsRename");
        this.supportsAutoPullFlag = nbt.getBoolean("supportsAutoPull");
        this.autoPullEnabledFlag = nbt.getBoolean("autoPullEnabled");

        // Formal capability metadata: the authoritative set of capability ids the server can resolve
        // for this bus. Drives button visibility without inspecting concrete bus types.
        if (nbt.hasKey("availableCapabilities")) {
            NBTTagList capabilityList = nbt.getTagList("availableCapabilities", Constants.NBT.TAG_STRING);
            for (int i = 0; i < capabilityList.tagCount(); i++) {
                availableCapabilities.add(capabilityList.getStringTagAt(i));
            }
        }

        // Upgrade slot count from server; fallback to 5 if not provided
        this.upgradeSlotCount = nbt.hasKey("upgradeSlotCount") ? nbt.getInteger("upgradeSlotCount") : 5;

        // Storage bus custom name (takes priority over connected block name)
        this.customName = nbt.hasKey("customName") ? nbt.getString("customName") : null;
        this.displayName = nbt.hasKey("displayName") ? nbt.getString("displayName") : null;
        this.namePrefixKey = nbt.hasKey("namePrefixKey") ? nbt.getString("namePrefixKey") : null;
        this.preferDisplayName = nbt.getBoolean("preferDisplayName");

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

        fillPartitionSlotTypeIds(getAvailableConfigSlots());
        if (nbt.hasKey("slotTypes")) {
            NBTTagList slotTypes = nbt.getTagList("slotTypes", Constants.NBT.TAG_STRING);
            for (int i = 0; i < slotTypes.tagCount(); i++) {
                fillPartitionSlotTypeIds(i + 1);
                partitionSlotTypeIds.set(i, normalizeStackTypeId(slotTypes.getStringTagAt(i)));
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
                String partTypeId = normalizeStackTypeId(
                    partNbt.hasKey("stackTypeId") ? partNbt.getString("stackTypeId") : this.stackTypeId);

                IAEStack<?> aeStack = AEStackUtil.readStackFromNBT(partNbt);
                ItemStack stack = readClientDisplayStack(partNbt, aeStack, partTypeId);
                if (ItemStacks.isEmpty(stack) && partNbt.hasKey("id")) stack = ItemStacks.load(partNbt);
                if (!ItemStacks.isEmpty(stack)) {
                    fillPartitionSlots(slot + 1);
                    this.partition.set(slot, stack);
                    fillPartitionSlotTypeIds(slot + 1);
                    this.partitionSlotTypeIds.set(slot, partTypeId);
                }
            }
        }

        // Parse contents (inventory preview)
        if (nbt.hasKey("contents")) {
            NBTTagList contentList = nbt.getTagList("contents", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < contentList.tagCount(); i++) {
                NBTTagCompound stackNbt = contentList.getCompoundTagAt(i);
                String contentTypeId = normalizeStackTypeId(
                    stackNbt.hasKey("stackTypeId") ? stackNbt.getString("stackTypeId") : this.stackTypeId);
                IAEStack<?> aeStack = AEStackUtil.readStackFromNBT(stackNbt);
                ItemStack stack = readClientDisplayStack(stackNbt, aeStack, contentTypeId);
                if (ItemStacks.isEmpty(stack)) stack = ItemStacks.load(stackNbt);

                long count;
                if (stackNbt.hasKey("Cnt")) {
                    count = stackNbt.getLong("Cnt");
                } else {
                    count = aeStack != null ? aeStack.getStackSize() : stack == null ? 0 : stack.stackSize;
                }

                this.contents.add(stack);
                this.contentCounts.add(count);
                this.contentTypeIds.add(contentTypeId);
            }
        }

        while (this.contentTypeIds.size() < this.contents.size()) {
            this.contentTypeIds.add(this.stackTypeId);
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

    /**
     * Build the stable capability target identity for this bus from its location, side, role and
     * storage type. Used by the GUI to address unified capability actions.
     */
    public StorageBusId toTargetId() {
        return new StorageBusId(dimension, pos, sideOrdinal, busRole, storageType);
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

    public int getSideOrdinal() {
        return sideOrdinal;
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
        return supportsStackType("fluid");
    }

    public boolean isEssentia() {
        return supportsStackType("essentia");
    }

    public boolean isItem() {
        return supportsStackType("item");
    }

    /**
     * Check if this storage bus supports IO mode toggling.
     */
    public boolean supportsIOMode() {
        return supportsIOModeFlag;
    }

    public boolean supportsAutoPull() {
        return supportsAutoPullFlag;
    }

    public boolean isAutoPullEnabled() {
        return autoPullEnabledFlag;
    }

    public boolean hasHeaderModeButton() {
        return getHeaderModeButtonKind() != HeaderModeButtonKind.NONE;
    }

    public HeaderModeButtonKind getHeaderModeButtonKind() {
        if (supportsIOModeFlag) return HeaderModeButtonKind.IO_ACCESS;
        if (supportsAutoPullFlag) return HeaderModeButtonKind.AUTO_PULL;

        return HeaderModeButtonKind.NONE;
    }

    /**
     * Check if this storage bus supports priority editing.
     */
    @Override
    public boolean supportsPriority() {
        if (!availableCapabilities.isEmpty()) return availableCapabilities.contains(CAPABILITY_PRIORITY);

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

    public boolean applyOptimisticFilterSet(int slot, ItemStack stack, String stackTypeId) {
        if (slot < 0 || slot >= getAvailableConfigSlots() || ItemStacks.isEmpty(stack)) return false;

        String normalizedType = normalizeStackTypeId(stackTypeId);
        if (!normalizedType.equals(getPartitionSlotTypeId(slot))) return false;

        fillPartitionSlots(slot + 1);
        ItemStack displayStack = createOptimisticFilterDisplayStack(stack, normalizedType);
        this.partition.set(slot, displayStack);
        return true;
    }

    public boolean applyOptimisticFilterClear(int slot) {
        if (slot < 0 || slot >= this.partition.size()) return false;
        if (ItemStacks.isEmpty(this.partition.get(slot))) return false;

        this.partition.set(slot, null);
        return true;
    }

    public boolean applyOptimisticFilterClearAll() {
        boolean changed = false;
        for (int i = 0; i < this.partition.size(); i++) {
            if (ItemStacks.isEmpty(this.partition.get(i))) continue;

            this.partition.set(i, null);
            changed = true;
        }

        return changed;
    }

    public boolean applyOptimisticFilterToggle(ItemStack stack, String stackTypeId) {
        if (ItemStacks.isEmpty(stack)) return false;

        String normalizedType = normalizeStackTypeId(stackTypeId);
        int existingSlot = findPartitionSlot(stack, normalizedType);
        if (existingSlot >= 0) {
            this.partition.set(existingSlot, null);
            return true;
        }

        int emptySlot = findFirstEmptyPartitionSlot(normalizedType);
        if (emptySlot < 0) return false;

        return applyOptimisticFilterSet(emptySlot, stack, normalizedType);
    }

    private void fillPartitionSlots(int size) {
        while (this.partition.size() < size) this.partition.add(null);
    }

    private void fillPartitionSlotTypeIds(int size) {
        while (this.partitionSlotTypeIds.size() < size) this.partitionSlotTypeIds.add(this.stackTypeId);
    }

    public List<ItemStack> getContents() {
        return contents;
    }

    public long getContentCount(int index) {
        if (index < 0 || index >= contentCounts.size()) return 0;

        return contentCounts.get(index);
    }

    public String getPartitionSlotTypeId(int slot) {
        if (slot < 0 || slot >= partitionSlotTypeIds.size()) return stackTypeId;

        return partitionSlotTypeIds.get(slot);
    }

    public String getContentTypeId(int index) {
        if (index < 0 || index >= contentTypeIds.size()) return stackTypeId;

        return contentTypeIds.get(index);
    }

    public boolean hasMixedPartitionSlotTypes() {
        String first = null;
        for (String slotTypeId : partitionSlotTypeIds) {
            if (first == null) {
                first = slotTypeId;
                continue;
            }

            if (!first.equals(slotTypeId)) return true;
        }

        return false;
    }

    public boolean supportsStackType(String typeId) {
        String normalized = normalizeStackTypeId(typeId);
        for (String slotTypeId : partitionSlotTypeIds) {
            if (normalized.equals(slotTypeId)) return true;
        }

        if (!partitionSlotTypeIds.isEmpty()) return false;

        return normalized.equals(stackTypeId);
    }

    public String resolvePreferredStackTypeId(ItemStack stack) {
        if (ItemStacks.isEmpty(stack)) return stackTypeId;

        FluidStack fluid = FluidStacks.extract(stack);
        if (fluid != null && supportsStackType("fluid")) return "fluid";

        if (supportsStackType("essentia")) return "essentia";
        if (supportsStackType("item")) return "item";

        return stackTypeId;
    }

    public boolean isContentInPartition(int index) {
        if (index < 0 || index >= contents.size()) return false;

        return ComparisonUtils
            .isInPartition(contents.get(index), getContentTypeId(index), partition, this::getPartitionSlotTypeId);
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
        if (displayName != null && !displayName.isEmpty()) return displayName;

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
        String baseName = getResolvedDisplayName();

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

    private static EnumFacing readSide(int ordinal) {
        EnumFacing[] values = EnumFacing.values();
        if (ordinal >= 0 && ordinal < values.length) return EnumFacing.getFront(ordinal);

        return EnumFacing.NORTH;
    }

    private static ItemStack readClientDisplayStack(NBTTagCompound stackNbt, IAEStack<?> aeStack, String stackTypeId) {
        if ("item".equals(stackTypeId)) {
            ItemStack display = AEStackUtil.readDisplayStack(stackNbt);
            if (!ItemStacks.isEmpty(display)) return display;
        }

        return AEStackUtil.getDisplayStack(aeStack);
    }

    private static ItemStack createOptimisticFilterDisplayStack(ItemStack stack, String stackTypeId) {
        IAEStackType<?> targetType = AEStackTypeRegistry.getType(stackTypeId);
        IAEStack<?> convertedStack = AEStackUtil.convertItemForType(stack, targetType);
        ItemStack displayStack = AEStackUtil.getDisplayStack(convertedStack);
        if (!ItemStacks.isEmpty(displayStack)) return displayStack;

        if ("fluid".equals(stackTypeId)) {
            FluidStack fluid = FluidStacks.extract(stack);
            ItemStack fluidDisplayStack = FluidStacks.toDisplayStack(fluid);
            if (!ItemStacks.isEmpty(fluidDisplayStack)) return fluidDisplayStack;
        }

        ItemStack fallback = stack.copy();
        fallback.stackSize = 1;
        return fallback;
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
        if (upgradeSlotCount <= 0) return false;

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
        if (upgradeSlotCount <= 0) return false;
        if (ItemStacks.isEmpty(upgradeStack)) return false;
        if (!(upgradeStack.getItem() instanceof IUpgradeModule)) return false;

        // Distinguish real upgrades from storage components that also implement IUpgradeModule
        // Real upgrades (speed card, capacity card, etc.) return a non-null Upgrades type
        if (((IUpgradeModule) upgradeStack.getItem()).getType(upgradeStack) == null) return false;

        return hasUpgradeSpace();
    }

    @Override
    public boolean isRenameable() {
        if (!availableCapabilities.isEmpty()) return availableCapabilities.contains(CAPABILITY_RENAME);

        return supportsRenameFlag;
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

    private static String normalizeStackTypeId(String typeId) {
        if ("fluid".equals(typeId)) return "fluid";
        if ("essentia".equals(typeId)) return "essentia";

        return "item";
    }

    private String getResolvedDisplayName() {
        if (customName != null && !customName.isEmpty()) return customName;

        if (preferDisplayName) {
            String ownDisplayName = getDisplayName();
            if (ownDisplayName != null && !ownDisplayName.isEmpty()) return ownDisplayName;
        }

        String connectedDisplayName = getConnectedDisplayName();
        if (connectedDisplayName != null && !connectedDisplayName.isEmpty()) return connectedDisplayName;

        return getDisplayName();
    }

    private String getConnectedDisplayName() {
        if (!ItemStacks.isEmpty(connectedIcon)) return connectedIcon.getDisplayName();
        if (connectedName != null && !connectedName.isEmpty()) return connectedName;

        return null;
    }

    private String getResolvedPrefixKey() {
        if (namePrefixKey != null && !namePrefixKey.isEmpty()) return namePrefixKey;

        return busRole.getPrefixKey();
    }

    private int findPartitionSlot(ItemStack stack, String stackTypeId) {
        for (int i = 0; i < this.partition.size(); i++) {
            ItemStack partitionStack = this.partition.get(i);
            if (ItemStacks.isEmpty(partitionStack)) continue;
            if (!stackTypeId.equals(getPartitionSlotTypeId(i))) continue;

            if (ComparisonUtils.isInPartition(stack, stackTypeId, List.of(partitionStack), ignored -> stackTypeId)) {
                return i;
            }
        }

        return -1;
    }

    private int findFirstEmptyPartitionSlot(String stackTypeId) {
        int availableSlots = getAvailableConfigSlots();
        for (int i = 0; i < availableSlots; i++) {
            if (!stackTypeId.equals(getPartitionSlotTypeId(i))) continue;
            if (i >= this.partition.size() || ItemStacks.isEmpty(this.partition.get(i))) return i;
        }

        return -1;
    }

}
