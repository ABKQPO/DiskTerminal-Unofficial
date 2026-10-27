package com.hfstudio.diskterminal.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.diskterminal.client.RequestAreaDeviceInfo;
import com.hfstudio.diskterminal.container.ContainerCellTerminalBase;
import com.hfstudio.diskterminal.requestarea.RequestAreaAction;
import com.hfstudio.diskterminal.util.PosUtil;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketRequestAreaAction implements IMessage {

    private long deviceId;
    private long position;
    private int dimension;
    private int side;
    private int slot;
    private RequestAreaAction action;
    private long value;
    private NBTTagCompound stackData;
    private int sourceInventorySlot;

    public PacketRequestAreaAction() {}

    public PacketRequestAreaAction(RequestAreaDeviceInfo device, int slot, RequestAreaAction action, long value,
        NBTTagCompound stackData) {
        this(device, slot, action, value, stackData, -1);
    }

    public PacketRequestAreaAction(RequestAreaDeviceInfo device, int slot, RequestAreaAction action, long value,
        NBTTagCompound stackData, int sourceInventorySlot) {
        this.deviceId = device.getId();
        this.position = PosUtil.toLong(device.getPosition());
        this.dimension = device.getDimension();
        this.side = device.getSideOrdinal();
        this.slot = slot;
        this.action = action;
        this.value = value;
        this.stackData = stackData;
        this.sourceInventorySlot = sourceInventorySlot;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        deviceId = buffer.readLong();
        position = buffer.readLong();
        dimension = buffer.readInt();
        side = buffer.readInt();
        slot = buffer.readInt();
        int actionOrdinal = buffer.readUnsignedByte();
        RequestAreaAction[] actions = RequestAreaAction.values();
        action = actionOrdinal < actions.length ? actions[actionOrdinal] : null;
        value = buffer.readLong();
        stackData = buffer.readBoolean() ? ByteBufUtils.readTag(buffer) : null;
        sourceInventorySlot = buffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeLong(deviceId);
        buffer.writeLong(position);
        buffer.writeInt(dimension);
        buffer.writeInt(side);
        buffer.writeInt(slot);
        buffer.writeByte(action == null ? 255 : action.ordinal());
        buffer.writeLong(value);
        buffer.writeBoolean(stackData != null && !stackData.hasNoTags());
        if (stackData != null && !stackData.hasNoTags()) ByteBufUtils.writeTag(buffer, stackData);
        buffer.writeInt(sourceInventorySlot);
    }

    public static class Handler implements IMessageHandler<PacketRequestAreaAction, IMessage> {

        @Override
        public IMessage onMessage(PacketRequestAreaAction message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            NetUtil.run(player, () -> {
                if (player.openContainer instanceof ContainerCellTerminalBase container) {
                    container.handleRequestAreaAction(
                        player,
                        message.deviceId,
                        message.dimension,
                        message.position,
                        message.side,
                        message.slot,
                        message.action,
                        message.value,
                        message.stackData,
                        message.sourceInventorySlot);
                }
            });
            return null;
        }
    }
}
