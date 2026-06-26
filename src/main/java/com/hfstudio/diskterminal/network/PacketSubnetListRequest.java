package com.hfstudio.diskterminal.network;

import com.hfstudio.diskterminal.container.ContainerCellTerminalBase;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Packet sent from client to server to request subnet list refresh.
 * The server will respond with PacketSubnetListUpdate.
 */
public class PacketSubnetListRequest implements IMessage {

    public PacketSubnetListRequest() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        // No data needed
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // No data needed
    }

    public static class Handler implements IMessageHandler<PacketSubnetListRequest, IMessage> {

        @Override
        public IMessage onMessage(PacketSubnetListRequest message, MessageContext ctx) {
            NetUtil.run(ctx.getServerHandler().playerEntity, () -> {
                if (ctx.getServerHandler().playerEntity.openContainer instanceof ContainerCellTerminalBase container) {
                    // The overview widget may have been recreated client-side (for example by NEI
                    // reinitializing the GUI), so force a full subnet snapshot instead of a delta.
                    container.requestSubnetRefresh(true);
                }
            });

            return null;
        }
    }
}
