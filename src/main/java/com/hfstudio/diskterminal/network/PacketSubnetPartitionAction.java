package com.hfstudio.diskterminal.network;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.hfstudio.diskterminal.container.ContainerCellTerminalBase;
import com.hfstudio.diskterminal.util.AEStackUtil;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Packet for modifying partition of a storage bus connection in the subnet overview.
 * Identifies the target by subnet ID + connection position + side.
 */
public class PacketSubnetPartitionAction implements IMessage {

    public enum Action {
        ADD_ITEM,
        REMOVE_ITEM,
        SET_ALL_FROM_CONTENTS,
        CLEAR_ALL,
        TOGGLE_ITEM,
        /** Set partition from the subnet's entire ME storage inventory. */
        SET_ALL_FROM_SUBNET_INVENTORY
    }

    private long subnetId;
    private long pos;
    private int side;
    private Action action;
    private int partitionSlot;
    private NBTTagCompound stackData;

    public PacketSubnetPartitionAction() {
        this.stackData = null;
    }

    /**
     * Actions that don't need a specific partition slot or item (CLEAR_ALL, SET_ALL_FROM_CONTENTS).
     */
    public PacketSubnetPartitionAction(long subnetId, long pos, int side, Action action) {
        this(subnetId, pos, side, action, -1, (NBTTagCompound) null);
    }

    /**
     * REMOVE_ITEM action on a specific partition slot.
     */
    public PacketSubnetPartitionAction(long subnetId, long pos, int side, Action action, int partitionSlot) {
        this(subnetId, pos, side, action, partitionSlot, (NBTTagCompound) null);
    }

    /**
     * ADD_ITEM action on a specific partition slot with an item.
     */
    public PacketSubnetPartitionAction(long subnetId, long pos, int side, Action action, int partitionSlot,
        ItemStack itemStack) {
        this(subnetId, pos, side, action, partitionSlot, AEStackUtil.writeItemLikePartitionStack(itemStack));
    }

    public PacketSubnetPartitionAction(long subnetId, long pos, int side, Action action, int partitionSlot,
        NBTTagCompound stackData) {
        this.subnetId = subnetId;
        this.pos = pos;
        this.side = side;
        this.action = action;
        this.partitionSlot = partitionSlot;
        this.stackData = stackData;
    }

    /**
     * TOGGLE_ITEM action without specific partition slot.
     */
    public PacketSubnetPartitionAction(long subnetId, long pos, int side, Action action, ItemStack itemStack) {
        this(subnetId, pos, side, action, -1, itemStack);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.subnetId = buf.readLong();
        this.pos = buf.readLong();
        this.side = buf.readInt();
        this.action = Action.values()[buf.readByte()];
        this.partitionSlot = buf.readInt();

        if (buf.readBoolean()) {
            this.stackData = ByteBufUtils.readTag(buf);
        } else {
            this.stackData = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(subnetId);
        buf.writeLong(pos);
        buf.writeInt(side);
        buf.writeByte(action.ordinal());
        buf.writeInt(partitionSlot);

        if (stackData != null && !stackData.hasNoTags()) {
            buf.writeBoolean(true);
            ByteBufUtils.writeTag(buf, stackData);
        } else {
            buf.writeBoolean(false);
        }
    }

    public static class Handler implements IMessageHandler<PacketSubnetPartitionAction, IMessage> {

        @Override
        public IMessage onMessage(PacketSubnetPartitionAction message, MessageContext ctx) {
            NetUtil.run(ctx.getServerHandler().playerEntity, () -> {
                if (ctx.getServerHandler().playerEntity.openContainer instanceof ContainerCellTerminalBase container) {
                    container.handleSubnetPartitionAction(
                        message.subnetId,
                        message.pos,
                        message.side,
                        message.action,
                        message.partitionSlot,
                        message.stackData);
                }
            });

            return null;
        }
    }
}
