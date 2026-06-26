package com.hfstudio.diskterminal.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
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
 * Packet for modifying partition of a temp cell in the temp area.
 */
public class PacketTempCellPartitionAction implements IMessage {

    public enum Action {
        ADD_ITEM,
        REMOVE_ITEM,
        SET_ALL_FROM_CONTENTS,
        CLEAR_ALL,
        TOGGLE_ITEM
    }

    private int tempSlotIndex;
    private Action action;
    private int partitionSlot;
    private NBTTagCompound stackData;

    public PacketTempCellPartitionAction() {
        this.stackData = null;
    }

    /**
     * Create a packet for actions that don't need a specific partition slot (CLEAR_ALL, SET_ALL_FROM_CONTENTS).
     */
    public PacketTempCellPartitionAction(int tempSlotIndex, Action action) {
        this.tempSlotIndex = tempSlotIndex;
        this.action = action;
        this.partitionSlot = -1;
        this.stackData = null;
    }

    /**
     * Create a packet for REMOVE_ITEM action on a specific partition slot.
     */
    public PacketTempCellPartitionAction(int tempSlotIndex, Action action, int partitionSlot) {
        this.tempSlotIndex = tempSlotIndex;
        this.action = action;
        this.partitionSlot = partitionSlot;
        this.stackData = null;
    }

    /**
     * Create a packet for ADD_ITEM or TOGGLE_ITEM action.
     */
    public PacketTempCellPartitionAction(int tempSlotIndex, Action action, int partitionSlot, ItemStack itemStack) {
        this.tempSlotIndex = tempSlotIndex;
        this.action = action;
        this.partitionSlot = partitionSlot;
        this.stackData = AEStackUtil.writeItemLikePartitionStack(itemStack);
    }

    /**
     * Create a packet for TOGGLE_ITEM without specific partition slot.
     */
    public PacketTempCellPartitionAction(int tempSlotIndex, Action action, ItemStack itemStack) {
        this.tempSlotIndex = tempSlotIndex;
        this.action = action;
        this.partitionSlot = -1;
        this.stackData = AEStackUtil.writeItemLikePartitionStack(itemStack);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.tempSlotIndex = buf.readInt();
        this.action = Action.values()[buf.readByte()];
        this.partitionSlot = buf.readInt();

        this.stackData = buf.readBoolean() ? ByteBufUtils.readTag(buf) : null;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(tempSlotIndex);
        buf.writeByte(action.ordinal());
        buf.writeInt(partitionSlot);

        if (stackData != null && !stackData.hasNoTags()) {
            buf.writeBoolean(true);
            ByteBufUtils.writeTag(buf, stackData);
        } else {
            buf.writeBoolean(false);
        }
    }

    public static class Handler implements IMessageHandler<PacketTempCellPartitionAction, IMessage> {

        @Override
        public IMessage onMessage(PacketTempCellPartitionAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;

            NetUtil.run(player, () -> {
                Container container = player.openContainer;
                if (!(container instanceof ContainerCellTerminalBase)) return;

                ContainerCellTerminalBase cellContainer = (ContainerCellTerminalBase) container;
                cellContainer.handleTempCellPartitionAction(
                    message.tempSlotIndex,
                    message.action,
                    message.partitionSlot,
                    message.stackData);
            });

            return null;
        }
    }
}
