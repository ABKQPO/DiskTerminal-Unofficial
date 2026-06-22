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
import com.hfstudio.diskterminal.util.ItemStacks;
import com.hfstudio.diskterminal.util.PosUtil;

/**
 * Client-side data holder for subnet connection information received from server.
 * <p>
 * A subnet is a SEPARATE ME grid that connects to the main network through the ME Passthrough
 * mechanism. Each connection is one-way (outbound = main-to-subnet, inbound = subnet-to-main),
 * and both directions can exist simultaneously. Written by the subnet scanners.
 */
public class SubnetInfo implements Renameable {

    /**
     * Represents a single connection point between main network and subnet.
     * Multiple connections can exist to the same subnet.
     */
    public static class ConnectionPoint {

        private final BlockPos pos;
        private final int dimension;
        private final EnumFacing side;
        private final boolean isOutbound;
        private final ItemStack localIcon;
        private final ItemStack remoteIcon;
        private final boolean usesSubnetInventory;

        private final List<ItemStack> content;
        private final boolean hasContentKey;

        private final List<ItemStack> partition;
        private final boolean hasPartitionKey;
        private final int maxPartitionSlots;

        public ConnectionPoint(NBTTagCompound nbt) {
            this.pos = PosUtil.fromLong(nbt.getLong("pos"));
            this.dimension = nbt.getInteger("dim");
            this.side = EnumFacing.getFront(nbt.getInteger("side"));
            this.isOutbound = nbt.getBoolean("outbound");
            this.localIcon = nbt.hasKey("localIcon") ? ItemStacks.load(nbt.getCompoundTag("localIcon")) : null;
            this.remoteIcon = nbt.hasKey("remoteIcon") ? ItemStacks.load(nbt.getCompoundTag("remoteIcon")) : null;

            this.hasContentKey = nbt.hasKey("content");
            this.content = new ArrayList<>();
            if (hasContentKey) {
                NBTTagList contentList = nbt.getTagList("content", Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < contentList.tagCount(); i++) {
                    content.add(ItemStacks.load(contentList.getCompoundTagAt(i)));
                }
            }

            this.usesSubnetInventory = nbt.hasKey("usesSubnetInventory") ? nbt.getBoolean("usesSubnetInventory")
                : this.isOutbound && !this.hasContentKey;

            this.hasPartitionKey = nbt.hasKey("filter");
            this.partition = new ArrayList<>();
            if (hasPartitionKey) {
                NBTTagList filterList = nbt.getTagList("filter", Constants.NBT.TAG_COMPOUND);
                for (int i = 0; i < filterList.tagCount(); i++) {
                    partition.add(ItemStacks.load(filterList.getCompoundTagAt(i)));
                }
            }
            this.maxPartitionSlots = nbt.hasKey("maxPartitionSlots") ? nbt.getInteger("maxPartitionSlots") : 63;
        }

        public BlockPos getPos() {
            return pos;
        }

        public EnumFacing getSide() {
            return side;
        }

        /**
         * Dimension of this connection point.
         * May differ from the subnet's primary dimension when Quantum Bridges are involved.
         */
        public int getDimension() {
            return dimension;
        }

        /**
         * True if storage is exposed from the main-network side toward the subnet.
         */
        public boolean isOutbound() {
            return isOutbound;
        }

        /**
         * Whether this connection should mirror the subnet inventory in the overview.
         */
        public boolean usesSubnetInventory() {
            return usesSubnetInventory;
        }

        /**
         * Icon of the block on the main network (Storage Bus for outbound, Interface for inbound).
         */
        public ItemStack getLocalIcon() {
            return localIcon;
        }

        /**
         * Icon of the block on the subnet (Interface for outbound, Storage Bus for inbound).
         */
        public ItemStack getRemoteIcon() {
            return remoteIcon;
        }

        /**
         * Content items flowing through this connection.
         */
        public List<ItemStack> getContent() {
            return content;
        }

        /**
         * Whether the backend sent a "content" key at all.
         */
        public boolean hasContentKey() {
            return hasContentKey;
        }

        /**
         * Partition items (storage bus filter configuration).
         */
        public List<ItemStack> getPartition() {
            return partition;
        }

        /**
         * Whether the backend sent a "filter" key at all.
         */
        public boolean hasPartitionKey() {
            return hasPartitionKey;
        }

        /**
         * Maximum number of partition slots (e.g. 63 for an AE2 storage bus).
         */
        public int getMaxPartitionSlots() {
            return maxPartitionSlots;
        }

        /**
         * Check if this connection has a non-empty filter/partition configured.
         */
        public boolean hasFilter() {
            for (ItemStack stack : partition) {
                if (!ItemStacks.isEmpty(stack)) return true;
            }

            return false;
        }
    }

    private final long id;
    private final int dimension;
    private final BlockPos primaryPos;
    private final String defaultName;
    private String customName;
    private boolean isFavorite;
    private final boolean hasSecurity;
    private final boolean isAccessible;
    private final boolean hasPower;

    private final List<ConnectionPoint> connections = new ArrayList<>();

    private final List<ItemStack> inventory = new ArrayList<>();
    private final List<Long> inventoryCounts = new ArrayList<>();

    private final boolean isMainNetwork;

    /**
     * Create a SubnetInfo representing the main network.
     * This is always displayed at the top of the subnet list.
     */
    public static SubnetInfo createMainNetwork() {
        return new SubnetInfo(
            0,
            new BlockPos(0, 0, 0),
            I18n.format("disk_terminal.subnet.main_network"),
            true,
            true,
            true,
            true);
    }

    private SubnetInfo(long id, BlockPos primaryPos, String defaultName, boolean isFavorite, boolean hasSecurity,
        boolean isAccessible, boolean hasPower) {
        this.id = id;
        this.dimension = 0;
        this.primaryPos = primaryPos;
        this.defaultName = defaultName;
        this.customName = null;
        this.isFavorite = isFavorite;
        this.hasSecurity = hasSecurity;
        this.isAccessible = isAccessible;
        this.hasPower = hasPower;
        this.isMainNetwork = (id == 0);
    }

    public SubnetInfo(NBTTagCompound nbt) {
        this.id = nbt.getLong("id");
        this.dimension = nbt.getInteger("dim");
        this.primaryPos = PosUtil.fromLong(nbt.getLong("primaryPos"));

        int posX = nbt.hasKey("posX") ? nbt.getInteger("posX") : primaryPos.getX();
        int posY = nbt.hasKey("posY") ? nbt.getInteger("posY") : primaryPos.getY();
        int posZ = nbt.hasKey("posZ") ? nbt.getInteger("posZ") : primaryPos.getZ();
        this.defaultName = I18n.format("gui.disk_terminal.subnet.default_name", posX, posY, posZ);

        this.customName = nbt.hasKey("customName") ? nbt.getString("customName") : null;
        this.isFavorite = nbt.getBoolean("favorite");
        this.hasSecurity = nbt.getBoolean("hasSecurity");
        this.isAccessible = nbt.getBoolean("accessible");
        this.hasPower = nbt.getBoolean("hasPower");
        this.isMainNetwork = false;

        if (nbt.hasKey("connections")) {
            NBTTagList connList = nbt.getTagList("connections", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < connList.tagCount(); i++) {
                connections.add(new ConnectionPoint(connList.getCompoundTagAt(i)));
            }
        }

        if (nbt.hasKey("inventory")) {
            NBTTagList invList = nbt.getTagList("inventory", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < invList.tagCount(); i++) {
                NBTTagCompound stackNbt = invList.getCompoundTagAt(i);
                ItemStack stack = ItemStacks.load(stackNbt);
                long count = stackNbt.hasKey("Cnt") ? stackNbt.getLong("Cnt") : (stack == null ? 0 : stack.stackSize);
                inventory.add(stack);
                inventoryCounts.add(count);
            }
        }
    }

    public long getId() {
        return id;
    }

    /**
     * Check if this represents the main network.
     */
    public boolean isMainNetwork() {
        return isMainNetwork;
    }

    public int getDimension() {
        return dimension;
    }

    /**
     * Get the primary position for this subnet (first interface position).
     */
    public BlockPos getPrimaryPos() {
        return primaryPos;
    }

    /**
     * Get the display name for this subnet.
     */
    public String getDisplayName() {
        if (customName != null && !customName.isEmpty()) return customName;

        return defaultName;
    }

    public String getDefaultName() {
        return defaultName;
    }

    @Override
    public String getCustomName() {
        return customName;
    }

    @Override
    public void setCustomName(String name) {
        this.customName = name;
    }

    @Override
    public boolean hasCustomName() {
        return customName != null && !customName.isEmpty();
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        this.isFavorite = favorite;
    }

    /**
     * Whether the subnet has a security station.
     */
    public boolean hasSecurity() {
        return hasSecurity;
    }

    /**
     * Whether the current player can access this subnet.
     */
    public boolean isAccessible() {
        return isAccessible;
    }

    /**
     * Whether the subnet has power.
     */
    public boolean hasPower() {
        return hasPower;
    }

    /**
     * Get all connection points between main network and this subnet.
     */
    public List<ConnectionPoint> getConnections() {
        return connections;
    }

    /**
     * Get the subnet's inventory items (all items/fluids stored in the subnet's ME storage).
     */
    public List<ItemStack> getInventory() {
        return inventory;
    }

    /**
     * Get the count for an inventory item at the given index.
     */
    public long getInventoryCount(int index) {
        if (index < 0 || index >= inventoryCounts.size()) return 0;

        return inventoryCounts.get(index);
    }

    /**
     * Whether this subnet has any inventory data.
     */
    public boolean hasInventory() {
        return !inventory.isEmpty();
    }

    /**
     * Get the number of outbound connections (main network exposes storage to the subnet).
     */
    public int getOutboundCount() {
        int count = 0;
        for (ConnectionPoint cp : connections) {
            if (cp.isOutbound()) count++;
        }

        return count;
    }

    /**
     * Get the number of inbound connections (subnet exposes storage to the main network).
     */
    public int getInboundCount() {
        int count = 0;
        for (ConnectionPoint cp : connections) {
            if (!cp.isOutbound()) count++;
        }

        return count;
    }

    /**
     * Get all unique filter items across all connections.
     */
    public List<ItemStack> getAllFilterItems(int maxItems) {
        List<ItemStack> items = new ArrayList<>();

        for (ConnectionPoint cp : connections) {
            for (ItemStack stack : cp.getPartition()) {
                if (ItemStacks.isEmpty(stack)) continue;
                if (items.size() >= maxItems) return items;

                boolean found = false;
                for (ItemStack existing : items) {
                    if (existing.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(existing, stack)) {
                        found = true;
                        break;
                    }
                }

                if (!found) items.add(stack.copy());
            }
        }

        return items;
    }

    /**
     * Check if any connection has a filter configured.
     */
    public boolean hasAnyFilter() {
        for (ConnectionPoint cp : connections) {
            if (cp.hasFilter()) return true;
        }

        return false;
    }

    /**
     * Create NBT data for this subnet info (for saving custom name and favorite).
     */
    public NBTTagCompound writeActionNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setLong("id", id);
        if (customName != null) nbt.setString("customName", customName);
        nbt.setBoolean("favorite", isFavorite);

        return nbt;
    }

    /**
     * Build content and partition rows for a single connection, following the same layout
     * as Temp Area: content rows first, then partition rows.
     *
     * @param slotsPerRow Number of slots per row (typically 9)
     * @return List of connection rows for this connection
     */
    public static List<SubnetConnectionRow> buildConnectionContentRows(SubnetInfo subnet, ConnectionPoint conn,
        int connIdx, int slotsPerRow, SlotLimit slotLimit) {
        List<SubnetConnectionRow> rows = new ArrayList<>();

        if (conn.usesSubnetInventory() && subnet.hasInventory()) {
            int contentCount = slotLimit.getEffectiveCount(
                subnet.getInventory()
                    .size());
            int contentRows = Math.max(1, (contentCount + slotsPerRow - 1) / slotsPerRow);
            for (int row = 0; row < contentRows; row++) {
                rows.add(new SubnetConnectionRow(subnet, conn, connIdx, row * slotsPerRow, row == 0, false, true));
            }

        } else if (conn.hasContentKey()) {
            int contentCount = slotLimit.getEffectiveCount(
                conn.getContent()
                    .size());
            int contentRows = Math.max(1, (contentCount + slotsPerRow - 1) / slotsPerRow);
            for (int row = 0; row < contentRows; row++) {
                rows.add(new SubnetConnectionRow(subnet, conn, connIdx, row * slotsPerRow, row == 0, false, false));
            }
        }

        if (conn.hasPartitionKey()) {
            int highestSlot = getHighestNonEmptySlot(conn.getPartition());
            int partitionRows = Math.max(1, (highestSlot + slotsPerRow) / slotsPerRow);

            if (highestSlot >= 0 && (highestSlot + 1) % slotsPerRow == 0
                && (highestSlot + 1) < conn.getMaxPartitionSlots()) {
                partitionRows++;
            }

            for (int row = 0; row < partitionRows; row++) {
                rows.add(new SubnetConnectionRow(subnet, conn, connIdx, row * slotsPerRow, row == 0, true, false));
            }
        }

        return rows;
    }

    /**
     * Find the highest non-empty slot index in a list, or -1 if all empty.
     */
    private static int getHighestNonEmptySlot(List<ItemStack> items) {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (!ItemStacks.isEmpty(items.get(i))) return i;
        }

        return -1;
    }

    @Override
    public boolean isRenameable() {
        return !isMainNetwork;
    }

    @Override
    public RenameTargetType getRenameTargetType() {
        return RenameTargetType.SUBNET;
    }

    @Override
    public long getRenameId() {
        return id;
    }
}
