package com.hfstudio.diskterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

import com.hfstudio.diskterminal.container.ContainerCellTerminalBase;


/**
 * Packet sent from client to server to pick up a cell from a drive.
 * Used for tab 2/3 slot interactions (clicking cells to pick them up/swap).
 * If toInventory is true (shift-click), the cell goes to player's inventory.
 * If toInventory is false (regular click), the cell goes to player's hand for reorganization.
 */
public class PacketPickupCell implements IMessage {

    private long storageId;
    private int cellSlot;
    private boolean toInventory;

    public PacketPickupCell() {
    }

    public PacketPickupCell(long storageId, int cellSlot, boolean toInventory) {
        this.storageId = storageId;
        this.cellSlot = cellSlot;
        this.toInventory = toInventory;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.storageId = buf.readLong();
        this.cellSlot = buf.readInt();
        this.toInventory = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(storageId);
        buf.writeInt(cellSlot);
        buf.writeBoolean(toInventory);
    }

    public static class Handler implements IMessageHandler<PacketPickupCell, IMessage> {

        @Override
        public IMessage onMessage(PacketPickupCell message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;

            NetUtil.run(player, () -> {
                Container container = player.openContainer;

                if (container instanceof ContainerCellTerminalBase) {
                    ContainerCellTerminalBase cellContainer = (ContainerCellTerminalBase) container;
                    cellContainer.handlePickupCell(message.storageId, message.cellSlot, player, message.toInventory);
                }
            });

            return null;
        }
    }
}
