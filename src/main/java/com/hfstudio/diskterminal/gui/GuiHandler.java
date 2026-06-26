package com.hfstudio.diskterminal.gui;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.hfstudio.diskterminal.DiskTerminal;
import com.hfstudio.diskterminal.container.ContainerCellTerminal;
import com.hfstudio.diskterminal.container.ContainerWirelessCellTerminal;
import com.hfstudio.diskterminal.integration.BaublesIntegration;
import com.hfstudio.diskterminal.part.PartCellTerminal;

import appeng.api.AEApi;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.container.ContainerOpenContext;
import appeng.helpers.WirelessTerminalGuiObject;
import cpw.mods.fml.common.network.IGuiHandler;

/**
 * GUI handler for the cell terminal (part-based) and wireless cell terminal.
 * <p>
 * GUI ids encode (type &lt;&lt; 4) | side ordinal. The client-side GUI elements are filled in by
 * the GUI layer (Phase 3); until then {@link #getClientGuiElement} returns null.
 */
public class GuiHandler implements IGuiHandler {

    public static final int GUI_CELL_TERMINAL = 0;
    public static final int GUI_WIRELESS_CELL_TERMINAL = 1;

    public static void openCellTerminalGui(EntityPlayer player, TileEntity te, ForgeDirection side) {
        int guiId = (GUI_CELL_TERMINAL << 4) | side.ordinal();
        player.openGui(DiskTerminal.instance, guiId, player.getEntityWorld(), te.xCoord, te.yCoord, te.zCoord);
    }

    public static void openWirelessCellTerminalGui(EntityPlayer player, int slot, boolean isBauble) {
        int guiId = (GUI_WIRELESS_CELL_TERMINAL << 4);
        player.openGui(DiskTerminal.instance, guiId, player.getEntityWorld(), slot, isBauble ? 1 : 0, 0);
    }

    @Nullable
    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        int guiType = id >> 4;
        ForgeDirection side = ForgeDirection.getOrientation(id & 7);

        if (guiType == GUI_CELL_TERMINAL) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof IPartHost) {
                IPart part = ((IPartHost) te).getPart(side);
                if (part instanceof PartCellTerminal) {
                    ContainerCellTerminal container = new ContainerCellTerminal(
                        player.inventory,
                        (PartCellTerminal) part);
                    container.setOpenContext(new ContainerOpenContext(part));
                    container.getOpenContext()
                        .setWorld(world);
                    container.getOpenContext()
                        .setX(x);
                    container.getOpenContext()
                        .setY(y);
                    container.getOpenContext()
                        .setZ(z);
                    container.getOpenContext()
                        .setSide(side);

                    return container;
                }
            }
        } else if (guiType == GUI_WIRELESS_CELL_TERMINAL) {
            boolean isBauble = y == 1;
            ItemStack terminal = getWirelessTerminalStack(player, x, isBauble);
            IWirelessTermHandler handler = AEApi.instance()
                .registries()
                .wireless()
                .getWirelessTerminalHandler(terminal);
            if (handler == null) return null;

            return new ContainerWirelessCellTerminal(
                player.inventory,
                new WirelessTerminalGuiObject(handler, terminal, player, world, x, y, z));
        }

        return null;
    }

    @Nullable
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        int guiType = id >> 4;
        ForgeDirection side = ForgeDirection.getOrientation(id & 7);

        if (guiType == GUI_CELL_TERMINAL) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof IPartHost) {
                IPart part = ((IPartHost) te).getPart(side);
                if (part instanceof PartCellTerminal) {
                    return new GuiCellTerminal(player.inventory, (PartCellTerminal) part);
                }
            }
        } else if (guiType == GUI_WIRELESS_CELL_TERMINAL) {
            boolean isBauble = y == 1;
            ItemStack terminal = getWirelessTerminalStack(player, x, isBauble);
            IWirelessTermHandler handler = AEApi.instance()
                .registries()
                .wireless()
                .getWirelessTerminalHandler(terminal);
            if (handler == null) return null;

            return new GuiWirelessCellTerminal(
                player.inventory,
                new WirelessTerminalGuiObject(handler, terminal, player, world, x, y, z));
        }

        return null;
    }

    private static ItemStack getWirelessTerminalStack(EntityPlayer player, int slot, boolean isBauble) {
        if (!isBauble) {
            if (slot >= 0 && slot < player.inventory.mainInventory.length) {
                return player.inventory.getStackInSlot(slot);
            }

            return null;
        }

        IInventory baubles = BaublesIntegration.getInventory(player);
        if (baubles == null) return null;

        return baubles.getStackInSlot(slot);
    }
}
