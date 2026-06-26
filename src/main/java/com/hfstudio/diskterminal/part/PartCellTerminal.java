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

    @SideOnly(Side.CLIENT)
    private static IIcon partBoardIcon;
    @SideOnly(Side.CLIENT)
    private static IIcon partBrightIcon;
    @SideOnly(Side.CLIENT)
    private static IIcon partColoredIcon;
    @SideOnly(Side.CLIENT)
    private static IIcon partDarkIcon;

    /** Maximum slots for temporary cell storage (can hold up to 16 cells). */
    private static final int MAX_TEMP_CELLS = 16;

    private final AppEngInternalInventory tempCellInventory = new AppEngInternalInventory(this, MAX_TEMP_CELLS, 1);

    public PartCellTerminal(ItemStack is) {
        super(is);
    }

    @Override
    public CableBusTextures getFrontBright() {
        return null;
    }

    @Override
    public CableBusTextures getFrontColored() {
        return null;
    }

    @Override
    public CableBusTextures getFrontDark() {
        return null;
    }

    @Override
    public ItemStack getPrimaryGuiIcon() {
        return this.getItemStack()
            .copy();
    }

    @SideOnly(Side.CLIENT)
    public static void registerPartIcons(TextureMap textureMap) {
        partBoardIcon = textureMap.registerIcon(Tags.MODID + ":parts/cell_terminal_broad");
        partBrightIcon = textureMap.registerIcon(Tags.MODID + ":parts/cell_terminal_bright");
        partColoredIcon = textureMap.registerIcon(Tags.MODID + ":parts/cell_terminal_colored");
        partDarkIcon = textureMap.registerIcon(Tags.MODID + ":parts/cell_terminal_dark");
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

    @Override
    @SideOnly(Side.CLIENT)
    public void renderInventory(final IPartRenderHelper rh, final RenderBlocks renderer) {
        rh.setBounds(2, 2, 14, 14, 14, 16);

        final IIcon sideTexture = CableBusTextures.PartMonitorSides.getIcon();
        final IIcon backTexture = CableBusTextures.PartMonitorBack.getIcon();

        rh.setTexture(sideTexture, sideTexture, backTexture, getFrontBoardIcon(), sideTexture, sideTexture);
        rh.renderInventoryBox(renderer);

        rh.setInvColor(this.getColor().whiteVariant);
        rh.renderInventoryFace(this.getFrontBrightIcon(), ForgeDirection.SOUTH, renderer);

        rh.setInvColor(this.getColor().mediumVariant);
        rh.renderInventoryFace(this.getFrontDarkIcon(), ForgeDirection.SOUTH, renderer);

        rh.setInvColor(this.getColor().blackVariant);
        rh.renderInventoryFace(this.getFrontColoredIcon(), ForgeDirection.SOUTH, renderer);

        rh.setBounds(4, 4, 13, 12, 12, 14);
        rh.renderInventoryBox(renderer);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderStatic(final int x, final int y, final int z, final IPartRenderHelper rh,
        final RenderBlocks renderer) {
        this.setRenderCache(rh.useSimplifiedRendering(x, y, z, this, this.getRenderCache()));

        final IIcon sideTexture = CableBusTextures.PartMonitorSides.getIcon();
        final IIcon backTexture = CableBusTextures.PartMonitorBack.getIcon();

        rh.setTexture(sideTexture, sideTexture, backTexture, getFrontBoardIcon(), sideTexture, sideTexture);

        rh.setBounds(2, 2, 14, 14, 14, 16);
        rh.renderBlock(x, y, z, renderer);

        final Tessellator tess = Tessellator.instance;
        if (this.getLightLevel() > 0) {
            final int l = 13;
            tess.setBrightness(l << 20 | l << 4);
        }

        renderer.uvRotateBottom = renderer.uvRotateEast = renderer.uvRotateNorth = renderer.uvRotateSouth = renderer.uvRotateTop = renderer.uvRotateWest = this
            .getSpin();

        tess.setColorOpaque_I(this.getColor().whiteVariant);
        rh.renderFace(x, y, z, this.getFrontBrightIcon(), ForgeDirection.SOUTH, renderer);

        tess.setColorOpaque_I(this.getColor().mediumVariant);
        rh.renderFace(x, y, z, this.getFrontDarkIcon(), ForgeDirection.SOUTH, renderer);

        tess.setColorOpaque_I(this.getColor().blackVariant);
        rh.renderFace(x, y, z, this.getFrontColoredIcon(), ForgeDirection.SOUTH, renderer);

        renderer.uvRotateBottom = renderer.uvRotateEast = renderer.uvRotateNorth = renderer.uvRotateSouth = renderer.uvRotateTop = renderer.uvRotateWest = 0;

        final IIcon sideStatusTexture = CableBusTextures.PartMonitorSidesStatus.getIcon();

        rh.setTexture(
            sideStatusTexture,
            sideStatusTexture,
            backTexture,
            getFrontBoardIcon(),
            sideStatusTexture,
            sideStatusTexture);

        rh.setBounds(4, 4, 13, 12, 12, 14);
        rh.renderBlock(x, y, z, renderer);

        final boolean hasChan = (this.getClientFlags() & (PartPanel.POWERED_FLAG | PartPanel.CHANNEL_FLAG))
            == (PartPanel.POWERED_FLAG | PartPanel.CHANNEL_FLAG);
        final boolean hasPower = (this.getClientFlags() & PartPanel.POWERED_FLAG) == PartPanel.POWERED_FLAG;

        if (hasChan) {
            final int l = 14;
            tess.setBrightness(l << 20 | l << 4);
            tess.setColorOpaque_I(this.getColor().blackVariant);
        } else if (hasPower) {
            final int l = 9;
            tess.setBrightness(l << 20 | l << 4);
            tess.setColorOpaque_I(this.getColor().whiteVariant);
        } else {
            tess.setBrightness(0);
            tess.setColorOpaque_I(0x000000);
        }

        final IIcon sideStatusLightTexture = CableBusTextures.PartMonitorSidesStatusLights.getIcon();

        rh.renderFace(x, y, z, sideStatusLightTexture, ForgeDirection.EAST, renderer);
        rh.renderFace(x, y, z, sideStatusLightTexture, ForgeDirection.WEST, renderer);
        rh.renderFace(x, y, z, sideStatusLightTexture, ForgeDirection.UP, renderer);
        rh.renderFace(x, y, z, sideStatusLightTexture, ForgeDirection.DOWN, renderer);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getBreakingTexture() {
        return partBoardIcon;
    }

    @SideOnly(Side.CLIENT)
    private IIcon getFrontBoardIcon() {
        return partBoardIcon;
    }

    @SideOnly(Side.CLIENT)
    private IIcon getFrontBrightIcon() {
        return partBrightIcon;
    }

    @SideOnly(Side.CLIENT)
    private IIcon getFrontColoredIcon() {
        return partColoredIcon;
    }

    @SideOnly(Side.CLIENT)
    private IIcon getFrontDarkIcon() {
        return partDarkIcon;
    }
}
