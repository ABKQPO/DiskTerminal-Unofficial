package com.hfstudio.diskterminal.network;

import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.diskterminal.container.ContainerCellTerminalBase;
import com.hfstudio.diskterminal.container.handler.SubnetDataHandler;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Packet sent from client to server to perform an action on a subnet.
 * Actions include renaming and toggling favorite status.
 */
public class PacketSubnetAction implements IMessage {

    private long subnetId;
    private SubnetDataHandler.SubnetAction action;
    private NBTTagCompound data;

    public PacketSubnetAction() {
        this.data = new NBTTagCompound();
    }

    public PacketSubnetAction(long subnetId, SubnetDataHandler.SubnetAction action, NBTTagCompound data) {
        this.subnetId = subnetId;
        this.action = action;
        this.data = data != null ? data : new NBTTagCompound();
    }

    /**
     * Create a rename action packet.
     */
    public static PacketSubnetAction rename(long subnetId, String newName) {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("name", newName);

        return new PacketSubnetAction(subnetId, SubnetDataHandler.SubnetAction.RENAME, data);
    }

    /**
     * Create a toggle favorite action packet.
     */
    public static PacketSubnetAction toggleFavorite(long subnetId, boolean favorite) {
        NBTTagCompound data = new NBTTagCompound();
        data.setBoolean("favorite", favorite);

        return new PacketSubnetAction(subnetId, SubnetDataHandler.SubnetAction.TOGGLE_FAVORITE, data);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.subnetId = buf.readLong();
        this.action = SubnetDataHandler.SubnetAction.values()[buf.readInt()];
        this.data = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(subnetId);
        buf.writeInt(action.ordinal());
        ByteBufUtils.writeTag(buf, data);
    }

    public static class Handler implements IMessageHandler<PacketSubnetAction, IMessage> {

        @Override
        public IMessage onMessage(PacketSubnetAction message, MessageContext ctx) {
            NetUtil.run(ctx.getServerHandler().playerEntity, () -> {
                if (ctx.getServerHandler().playerEntity.openContainer instanceof ContainerCellTerminalBase container) {
                    container.handleSubnetAction(message.subnetId, message.action, message.data);
                }
            });

            return null;
        }
    }
}
