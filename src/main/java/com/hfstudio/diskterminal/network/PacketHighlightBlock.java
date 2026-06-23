package com.hfstudio.diskterminal.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.hfstudio.diskterminal.util.PosUtil;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Packet sent from client to server, then broadcast back to the requesting client to highlight a
 * block with a glowing outline for a configured duration.
 */
public class PacketHighlightBlock implements IMessage {

    private BlockPos pos;
    private int dimension;

    public PacketHighlightBlock() {}

    public PacketHighlightBlock(BlockPos pos, int dimension) {
        this.pos = pos;
        this.dimension = dimension;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = PosUtil.fromLong(buf.readLong());
        this.dimension = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(PosUtil.toLong(pos));
        buf.writeInt(dimension);
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getDimension() {
        return dimension;
    }

    public static class Handler implements IMessageHandler<PacketHighlightBlock, IMessage> {

        @Override
        public IMessage onMessage(PacketHighlightBlock message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;

            NetUtil.run(player, () -> {
                if (player.dimension != message.dimension) return;

                DiskTerminalNetwork.INSTANCE
                    .sendTo(new PacketHighlightBlockClient(message.pos, message.dimension), player);
            });

            return null;
        }
    }
}
