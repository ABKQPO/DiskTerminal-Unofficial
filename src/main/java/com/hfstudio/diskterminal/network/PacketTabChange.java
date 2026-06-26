package com.hfstudio.diskterminal.network;

import com.hfstudio.diskterminal.container.ContainerCellTerminalBase;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Packet sent from client to server to notify of tab change.
 * Used to enable/disable storage bus polling based on active tab.
 */
public class PacketTabChange implements IMessage {

    private int tabIndex;

    public PacketTabChange() {}

    public PacketTabChange(int tabIndex) {
        this.tabIndex = tabIndex;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.tabIndex = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(tabIndex);
    }

    public static class Handler implements IMessageHandler<PacketTabChange, IMessage> {

        @Override
        public IMessage onMessage(PacketTabChange message, MessageContext ctx) {
            NetUtil.run(ctx.getServerHandler().playerEntity, () -> {
                if (ctx.getServerHandler().playerEntity.openContainer instanceof ContainerCellTerminalBase container) {
                    container.setActiveTab(message.tabIndex);
                }
            });

            return null;
        }
    }
}
