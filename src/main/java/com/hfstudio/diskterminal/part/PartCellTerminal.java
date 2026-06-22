package com.hfstudio.diskterminal.part;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Vec3;

import com.hfstudio.diskterminal.gui.GuiHandler;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.client.texture.CableBusTextures;
import appeng.parts.reporting.AbstractPartDisplay;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.Platform;

/**
 * Part representing the Cell Terminal.
 * Can store temporary cells for partitioning before sending them to the network.
 */
public class PartCellTerminal extends AbstractPartDisplay implements IAEAppEngInventory {

    private static final CableBusTextures FRONT_BRIGHT_ICON = CableBusTextures.PartTerminal_Bright;
    private static final CableBusTextures FRONT_DARK_ICON = CableBusTextures.PartTerminal_Dark;
    private static final CableBusTextures FRONT_COLORED_ICON = CableBusTextures.PartTerminal_Colored;

    /** Maximum slots for temporary cell storage (can hold up to 16 cells). */
    private static final int MAX_TEMP_CELLS = 16;

    private final AppEngInternalInventory tempCellInventory = new AppEngInternalInventory(this, MAX_TEMP_CELLS, 1);

    public PartCellTerminal(ItemStack is) {
        super(is);
    }

    @Override
    public CableBusTextures getFrontBright() {
        return FRONT_BRIGHT_ICON;
    }

    @Override
    public CableBusTextures getFrontColored() {
        return FRONT_COLORED_ICON;
    }

    @Override
    public CableBusTextures getFrontDark() {
        return FRONT_DARK_ICON;
    }

    @Override
    public boolean onPartActivate(EntityPlayer player, Vec3 pos) {
        if (!super.onPartActivate(player, pos)) {
            if (Platform.isServer()) {
                GuiHandler.openCellTerminalGui(
                    player,
                    this.getHost()
                        .getTile(),
                    this.getSide());
            }
        }

        return true;
    }

    /**
     * Get the temp cell inventory for GUI access.
     */
    public AppEngInternalInventory getTempCellInventory() {
        return this.tempCellInventory;
    }

    @Override
    public IInventory getInventoryByName(String name) {
        if ("tempCells".equals(name)) return this.tempCellInventory;

        return super.getInventoryByName(name);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.tempCellInventory.readFromNBT(data, "tempCells");
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        this.tempCellInventory.writeToNBT(data, "tempCells");
    }

    @Override
    public void getDrops(List<ItemStack> drops, boolean wrenched) {
        super.getDrops(drops, wrenched);

        for (int i = 0; i < tempCellInventory.getSizeInventory(); i++) {
            ItemStack cell = tempCellInventory.getStackInSlot(i);
            if (!ItemStacks.isEmpty(cell)) drops.add(cell.copy());
        }
    }

    @Override
    public void onChangeInventory(IInventory inv, int slot, InvOperation mc, ItemStack removedStack,
        ItemStack addedStack) {
        if (this.getHost() != null && this.getHost()
            .getTile() != null) {
            this.getHost()
                .markForSave();
        }
    }

    @Override
    public void saveChanges() {
        if (this.getHost() != null) {
            this.getHost()
                .markForSave();
        }
    }
}
