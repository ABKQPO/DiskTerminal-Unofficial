package com.hfstudio.diskterminal.part;

import java.util.List;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

import com.hfstudio.diskterminal.ItemRegistry;
import com.hfstudio.diskterminal.Tags;
import com.hfstudio.diskterminal.gui.GuiHandler;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.parts.IPartRenderHelper;
import appeng.client.texture.CableBusTextures;
import appeng.helpers.IPrimaryGuiIconProvider;
import appeng.parts.reporting.AbstractPartDisplay;
import appeng.parts.reporting.PartPanel;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEAppEngInventory;
import appeng.tile.inventory.InvOperation;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Part representing the Cell Terminal.
 * Can store temporary cells for partitioning before sending them to the network.
 */
public class PartCellTerminal extends AbstractPartDisplay implements IAEAppEngInventory, IPrimaryGuiIconProvider {

    private static final CableBusTextures FRONT_BRIGHT_ICON = CableBusTextures.PartTerminal_Bright;
    private static final CableBusTextures FRONT_DARK_ICON = CableBusTextures.PartTerminal_Dark;
    private static final CableBusTextures FRONT_COLORED_ICON = CableBusTextures.PartTerminal_Colored;

    @SideOnly(Side.CLIENT)
    private static IIcon partBackgroundIcon;

    @SideOnly(Side.CLIENT)
    private static IIcon partOverlayIcon;

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

    @SideOnly(Side.CLIENT)
    @Override
    public void renderInventory(IPartRenderHelper rh, RenderBlocks renderer) {
        rh.setBounds(2, 2, 14, 14, 14, 16);

        IIcon sideTexture = CableBusTextures.PartMonitorSides.getIcon();
        IIcon backTexture = CableBusTextures.PartMonitorBack.getIcon();

        rh.setTexture(
            sideTexture,
            sideTexture,
            backTexture,
            this.getItemStack()
                .getIconIndex(),
            sideTexture,
            sideTexture);
        rh.renderInventoryBox(renderer);

        IIcon background = getPartBackgroundIcon();
        IIcon overlay = getPartOverlayIcon();
        if (background != null) {
            rh.setInvColor(this.getColor().whiteVariant);
            rh.renderInventoryFace(background, ForgeDirection.SOUTH, renderer);
        }

        if (overlay != null) {
            rh.setInvColor(this.getColor().blackVariant);
            rh.renderInventoryFace(overlay, ForgeDirection.SOUTH, renderer);
        }

        rh.setBounds(4, 4, 13, 12, 12, 14);
        rh.renderInventoryBox(renderer);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void renderStatic(int x, int y, int z, IPartRenderHelper rh, RenderBlocks renderer) {
        this.setRenderCache(rh.useSimplifiedRendering(x, y, z, this, this.getRenderCache()));

        IIcon sideTexture = CableBusTextures.PartMonitorSides.getIcon();
        IIcon backTexture = CableBusTextures.PartMonitorBack.getIcon();

        rh.setTexture(
            sideTexture,
            sideTexture,
            backTexture,
            this.getItemStack()
                .getIconIndex(),
            sideTexture,
            sideTexture);
        rh.setBounds(2, 2, 14, 14, 14, 16);
        rh.renderBlock(x, y, z, renderer);

        Tessellator tessellator = Tessellator.instance;
        if (this.getLightLevel() > 0) {
            int light = 13;
            tessellator.setBrightness(light << 20 | light << 4);
        }

        renderer.uvRotateBottom = renderer.uvRotateEast = renderer.uvRotateNorth = renderer.uvRotateSouth = renderer.uvRotateTop = renderer.uvRotateWest = this
            .getSpin();

        IIcon background = getPartBackgroundIcon();
        IIcon overlay = getPartOverlayIcon();
        if (background != null) {
            tessellator.setColorOpaque_I(this.getColor().whiteVariant);
            rh.renderFace(x, y, z, background, ForgeDirection.SOUTH, renderer);
        }

        if (overlay != null) {
            tessellator.setColorOpaque_I(this.getColor().blackVariant);
            rh.renderFace(x, y, z, overlay, ForgeDirection.SOUTH, renderer);
        }

        renderer.uvRotateBottom = renderer.uvRotateEast = renderer.uvRotateNorth = renderer.uvRotateSouth = renderer.uvRotateTop = renderer.uvRotateWest = 0;

        IIcon sideStatusTexture = CableBusTextures.PartMonitorSidesStatus.getIcon();

        rh.setTexture(
            sideStatusTexture,
            sideStatusTexture,
            backTexture,
            this.getItemStack()
                .getIconIndex(),
            sideStatusTexture,
            sideStatusTexture);

        rh.setBounds(4, 4, 13, 12, 12, 14);
        rh.renderBlock(x, y, z, renderer);

        boolean hasChannel = (this.getClientFlags() & (PartPanel.POWERED_FLAG | PartPanel.CHANNEL_FLAG))
            == (PartPanel.POWERED_FLAG | PartPanel.CHANNEL_FLAG);
        boolean hasPower = (this.getClientFlags() & PartPanel.POWERED_FLAG) == PartPanel.POWERED_FLAG;

        if (hasChannel) {
            int light = 14;
            tessellator.setBrightness(light << 20 | light << 4);
            tessellator.setColorOpaque_I(this.getColor().blackVariant);
        } else if (hasPower) {
            int light = 9;
            tessellator.setBrightness(light << 20 | light << 4);
            tessellator.setColorOpaque_I(this.getColor().whiteVariant);
        } else {
            tessellator.setBrightness(0);
            tessellator.setColorOpaque_I(0x000000);
        }

        IIcon sideStatusLightTexture = CableBusTextures.PartMonitorSidesStatusLights.getIcon();

        rh.renderFace(x, y, z, sideStatusLightTexture, ForgeDirection.EAST, renderer);
        rh.renderFace(x, y, z, sideStatusLightTexture, ForgeDirection.WEST, renderer);
        rh.renderFace(x, y, z, sideStatusLightTexture, ForgeDirection.UP, renderer);
        rh.renderFace(x, y, z, sideStatusLightTexture, ForgeDirection.DOWN, renderer);
    }

    @Override
    public ItemStack getPrimaryGuiIcon() {
        return new ItemStack(ItemRegistry.CELL_TERMINAL);
    }

    @SideOnly(Side.CLIENT)
    public static void registerPartIcons(TextureMap textureMap) {
        partBackgroundIcon = textureMap.registerIcon(Tags.MODID + ":parts/cell_terminal_background");
        partOverlayIcon = textureMap.registerIcon(Tags.MODID + ":parts/cell_terminal_overlay");
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

    @SideOnly(Side.CLIENT)
    private IIcon getPartBackgroundIcon() {
        return partBackgroundIcon != null ? partBackgroundIcon : getFrontBright().getIcon();
    }

    @SideOnly(Side.CLIENT)
    private IIcon getPartOverlayIcon() {
        return partOverlayIcon != null ? partOverlayIcon : getFrontColored().getIcon();
    }
}
