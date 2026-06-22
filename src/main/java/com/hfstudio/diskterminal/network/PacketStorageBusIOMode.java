package com.hfstudio.diskterminal.network;

import com.hfstudio.diskterminal.container.ContainerCellTerminalBase;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Packet sent from client to server to toggle storage bus IO mode (access restriction).
 * Cycles through: READ_WRITE -> READ -> WRITE -> READ_WRITE
 */
public class PacketStorageBusIOMode implements IMessage {

    private long storageBusId;

    public PacketStorageBusIOMode() {}

    public PacketStorageBusIOMode(long storageBusId) {
        this.storageBusId = storageBusId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.storageBusId = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(storageBusId);
    }

    public static class Handler implements IMessageHandler<PacketStorageBusIOMode, IMessage> {

        @Override
        public IMessage onMessage(PacketStorageBusIOMode message, MessageContext ctx) {
            NetUtil.run(ctx.getServerHandler().playerEntity, () -> {
                if (ctx.getServerHandler().playerEntity.openContainer instanceof ContainerCellTerminalBase) {
                    ContainerCellTerminalBase container = (ContainerCellTerminalBase) ctx
                        .getServerHandler().playerEntity.openContainer;
                    container.handleStorageBusIOModeToggle(message.storageBusId);
                }
            });

            return null;
        }
    }
}
