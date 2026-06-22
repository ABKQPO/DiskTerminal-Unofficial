package com.hfstudio.diskterminal.items;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.hfstudio.diskterminal.Tags;
import com.hfstudio.diskterminal.part.PartCellTerminal;

import appeng.api.AEApi;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.core.CreativeTab;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Item form of the Cell Terminal part. Placing it on a cable bus creates a {@link PartCellTerminal}.
 */
public class ItemCellTerminal extends Item implements IPartItem {

    public ItemCellTerminal() {
        this.setUnlocalizedName(Tags.MODID + ".cell_terminal");
        this.setTextureName(Tags.MODID + ":cell_terminal");
        this.setCreativeTab(CreativeTab.instance);
        this.setMaxStackSize(64);
    }

    @Override
    public IPart createPartFromItemStack(ItemStack stack) {
        return new PartCellTerminal(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        this.itemIcon = register.registerIcon(Tags.MODID + ":cell_terminal");
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("item.disk_terminal.cell_terminal.tooltip"));
    }

    @Override
    public boolean onItemUse(ItemStack is, EntityPlayer player, World world, int x, int y, int z, int side, float hitX,
        float hitY, float hitZ) {
        return AEApi.instance()
            .partHelper()
            .placeBus(is, x, y, z, side, player, world);
    }
}
