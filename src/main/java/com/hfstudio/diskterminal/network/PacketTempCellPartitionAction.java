package com.hfstudio.diskterminal.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

import com.hfstudio.diskterminal.container.ContainerCellTerminalBase;


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
    private ItemStack itemStack;

    public PacketTempCellPartitionAction() {
        this.itemStack = null;
    }

    /**
     * Create a packet for actions that don't need a specific partition slot (CLEAR_ALL, SET_ALL_FROM_CONTENTS).
     */
    public PacketTempCellPartitionAction(int tempSlotIndex, Action action) {
        this.tempSlotIndex = tempSlotIndex;
        this.action = action;
        this.partitionSlot = -1;
        this.itemStack = null;
    }

    /**
     * Create a packet for REMOVE_ITEM action on a specific partition slot.
     */
    public PacketTempCellPartitionAction(int tempSlotIndex, Action action, int partitionSlot) {
        this.tempSlotIndex = tempSlotIndex;
        this.action = action;
        this.partitionSlot = partitionSlot;
        this.itemStack = null;
    }

    /**
     * Create a packet for ADD_ITEM or TOGGLE_ITEM action.
     */
    public PacketTempCellPartitionAction(int tempSlotIndex, Action action, int partitionSlot, ItemStack itemStack) {
        this.tempSlotIndex = tempSlotIndex;
        this.action = action;
        this.partitionSlot = partitionSlot;
        this.itemStack = itemStack != null ? itemStack : null;
    }

    /**
     * Create a packet for TOGGLE_ITEM without specific partition slot.
     */
    public PacketTempCellPartitionAction(int tempSlotIndex, Action action, ItemStack itemStack) {
        this.tempSlotIndex = tempSlotIndex;
        this.action = action;
        this.partitionSlot = -1;
        this.itemStack = itemStack != null ? itemStack : null;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.tempSlotIndex = buf.readInt();
        this.action = Action.values()[buf.readByte()];
        this.partitionSlot = buf.readInt();

        if (buf.readBoolean()) {
            NBTTagCompound nbt = ByteBufUtils.readTag(buf);
            this.itemStack = ItemStack.loadItemStackFromNBT(nbt);
        } else {
            this.itemStack = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(tempSlotIndex);
        buf.writeByte(action.ordinal());
        buf.writeInt(partitionSlot);

        if (itemStack != null && itemStack.stackSize > 0) {
            buf.writeBoolean(true);
            NBTTagCompound nbt = new NBTTagCompound();
            itemStack.writeToNBT(nbt);
            ByteBufUtils.writeTag(buf, nbt);
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
                cellContainer.handleTempCellPartitionAction(message.tempSlotIndex, message.action,
                    message.partitionSlot, message.itemStack);
            });

            return null;
        }
    }
}
