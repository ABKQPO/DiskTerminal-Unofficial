package com.hfstudio.diskterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

import com.hfstudio.diskterminal.container.ContainerCellTerminalBase;


/**
 * Packet sent from client to server to modify storage priority.
 * Can be sent from the Cell Terminal GUI.
 */
public class PacketSetPriority implements IMessage {

    private long storageId;
    private int priority;

    public PacketSetPriority() {
    }

    /**
     * Constructor for Cell Terminal GUI priority change.
     */
    public PacketSetPriority(long storageId, int priority) {
        this.storageId = storageId;
        this.priority = priority;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.storageId = buf.readLong();
        this.priority = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(storageId);
        buf.writeInt(priority);
    }

    public static class Handler implements IMessageHandler<PacketSetPriority, IMessage> {

        @Override
        public IMessage onMessage(PacketSetPriority message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;

            NetUtil.run(player, () -> handleGuiPriority(player, message));

            return null;
        }

        private void handleGuiPriority(EntityPlayerMP player, PacketSetPriority message) {
            Container container = player.openContainer;

            if (!(container instanceof ContainerCellTerminalBase)) return;

            ContainerCellTerminalBase cellContainer = (ContainerCellTerminalBase) container;
            cellContainer.handleSetPriority(message.storageId, message.priority);
        }
    }
}
