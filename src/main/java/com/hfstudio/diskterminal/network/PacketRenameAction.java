package com.hfstudio.diskterminal.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.hfstudio.diskterminal.DiskTerminal;
import com.hfstudio.diskterminal.container.ContainerCellTerminalBase;
import com.hfstudio.diskterminal.container.handler.CellActionHandler;
import com.hfstudio.diskterminal.container.handler.CellDataHandler;
import com.hfstudio.diskterminal.container.handler.StorageBusDataHandler.StorageBusTracker;
import com.hfstudio.diskterminal.gui.rename.RenameTargetType;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.parts.IPart;
import appeng.api.parts.PartItemStack;
import appeng.helpers.ICustomNameObject;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Packet sent from client to server to rename a storage device, cell, or storage bus.
 */
public class PacketRenameAction implements IMessage {

    private RenameTargetType targetType;
    private long primaryId;
    private int secondaryId;
    private String newName;

    public PacketRenameAction() {}

    public PacketRenameAction(RenameTargetType targetType, long primaryId, int secondaryId, String newName) {
        this.targetType = targetType;
        this.primaryId = primaryId;
        this.secondaryId = secondaryId;
        this.newName = newName != null ? newName : "";
    }

    public static PacketRenameAction renameStorage(long storageId, String newName) {
        return new PacketRenameAction(RenameTargetType.STORAGE, storageId, -1, newName);
    }

    public static PacketRenameAction renameCell(long storageId, int cellSlot, String newName) {
        return new PacketRenameAction(RenameTargetType.CELL, storageId, cellSlot, newName);
    }

    public static PacketRenameAction renameStorageBus(long storageBusId, String newName) {
        return new PacketRenameAction(RenameTargetType.STORAGE_BUS, storageBusId, -1, newName);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.targetType = RenameTargetType.values()[buf.readInt()];
        this.primaryId = buf.readLong();
        this.secondaryId = buf.readInt();
        this.newName = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(targetType.ordinal());
        buf.writeLong(primaryId);
        buf.writeInt(secondaryId);
        ByteBufUtils.writeUTF8String(buf, newName);
    }

    public static class Handler implements IMessageHandler<PacketRenameAction, IMessage> {

        @Override
        public IMessage onMessage(PacketRenameAction message, MessageContext ctx) {
            EntityPlayer player = ctx.getServerHandler().playerEntity;

            NetUtil.run(ctx.getServerHandler().playerEntity, () -> {
                if (!(player.openContainer instanceof ContainerCellTerminalBase)) return;

                ContainerCellTerminalBase container = (ContainerCellTerminalBase) player.openContainer;

                switch (message.targetType) {
                    case STORAGE:
                        handleStorageRename(container, message.primaryId, message.newName);
                        break;
                    case CELL:
                        handleCellRename(container, message.primaryId, message.secondaryId, message.newName);
                        break;
                    case STORAGE_BUS:
                        handleStorageBusRename(container, message.primaryId, message.newName);
                        break;
                    default:
                        break;
                }
            });

            return null;
        }

        private void handleStorageRename(ContainerCellTerminalBase container, long storageId, String newName) {
            ContainerCellTerminalBase.StorageTracker tracker = container.getStorageTracker(storageId);
            if (tracker == null) return;

            TileEntity tile = tracker.tile;

            if (!(tile instanceof ICustomNameObject)) return;

            ICustomNameObject nameable = (ICustomNameObject) tile;
            String trimmed = newName.trim();
            nameable.setCustomName(trimmed.isEmpty() ? null : trimmed);
            tile.markDirty();

            container.requestFullRefresh();
        }

        private void handleCellRename(ContainerCellTerminalBase container, long storageId, int cellSlot,
            String newName) {
            ContainerCellTerminalBase.StorageTracker tracker = container.getStorageTracker(storageId);
            if (tracker == null) return;

            IChestOrDrive storage = tracker.storage;
            IInventory cellInventory = CellDataHandler.getCellInventory(storage);
            if (cellInventory == null) return;
            int inventorySlot = CellDataHandler.toInventorySlot(storage, cellSlot);
            if (!CellActionHandler.isValidInventorySlot(cellInventory, inventorySlot)) return;

            ItemStack cellStack = cellInventory.getStackInSlot(inventorySlot);
            if (ItemStacks.isEmpty(cellStack)) return;

            String trimmed = newName.trim();
            if (trimmed.isEmpty()) {
                ItemStacks.clearCustomName(cellStack);
            } else {
                cellStack.setStackDisplayName(trimmed);
            }

            CellActionHandler.forceCellHandlerRefresh(cellInventory, inventorySlot, cellStack);

            ((TileEntity) storage).markDirty();

            container.requestFullRefresh();
        }

        private void handleStorageBusRename(ContainerCellTerminalBase container, long storageBusId, String newName) {
            StorageBusTracker tracker = container.getStorageBusTracker(storageBusId);
            if (tracker == null) return;

            String trimmed = newName.trim();

            if (trimmed.isEmpty()) {
                if (!clearStorageBusCustomName(tracker, storageBusId)) return;
            } else {
                if (!(tracker.storageBus instanceof ICustomNameObject nameable)) {
                    DiskTerminal.LOG.debug("Storage bus {} does not implement ICustomNameObject", storageBusId);
                    return;
                }

                nameable.setCustomName(trimmed);
            }

            if (tracker.hostTile != null) tracker.hostTile.markDirty();

            container.requestStorageBusRefresh();
        }

        private boolean clearStorageBusCustomName(StorageBusTracker tracker, long storageBusId) {
            ItemStack partStack = getMutableStorageBusStack(tracker.storageBus);
            if (ItemStacks.isEmpty(partStack)) {
                DiskTerminal.LOG
                    .debug("Storage bus {} does not expose a mutable ItemStack for clearing name", storageBusId);
                return false;
            }

            ItemStacks.clearCustomName(partStack);
            return true;
        }

        private ItemStack getMutableStorageBusStack(Object storageBus) {
            if (!(storageBus instanceof IPart part)) return null;

            return part.getItemStack(PartItemStack.World);
        }
    }
}
