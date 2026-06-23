package com.hfstudio.diskterminal.network;

import net.minecraft.client.Minecraft;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.hfstudio.diskterminal.client.BlockHighlightRenderer;
import com.hfstudio.diskterminal.config.DiskTerminalClientConfig;
import com.hfstudio.diskterminal.util.PosUtil;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Packet sent from server to client to highlight a block with a glowing outline.
 */
public class PacketHighlightBlockClient implements IMessage {

    private BlockPos pos;
    private int dimension;

    public PacketHighlightBlockClient() {}

    public PacketHighlightBlockClient(BlockPos pos, int dimension) {
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

    public static class Handler implements IMessageHandler<PacketHighlightBlockClient, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketHighlightBlockClient message, MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    if (Minecraft.getMinecraft().thePlayer.dimension == message.dimension) {
                        long duration = DiskTerminalClientConfig.getInstance()
                            .getHighlightDurationMs();
                        BlockHighlightRenderer.addHighlight(message.pos, duration);
                    }
                });

            return null;
        }
    }
}
