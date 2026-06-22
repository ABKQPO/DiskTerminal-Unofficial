package com.hfstudio.diskterminal.items;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import com.hfstudio.diskterminal.Tags;
import com.hfstudio.diskterminal.gui.GuiHandler;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.AEApi;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.features.ILocatable;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.util.IConfigManager;
import appeng.core.AEConfig;
import appeng.core.CreativeTab;
import appeng.core.localization.PlayerMessages;
import appeng.items.tools.powered.powersink.AEBasePoweredItem;
import appeng.util.ConfigManager;
import appeng.util.Platform;
import baubles.api.BaubleType;
import baubles.api.IBauble;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Wireless version of the Cell Terminal.
 * Extends AEBasePoweredItem (battery) and implements IWirelessTermHandler to link with the
 * Security Terminal. Baubles support is provided through Baubles-Expanded.
 */
@Optional.Interface(iface = "baubles.api.IBauble", modid = "Baubles|Expanded")
public class ItemWirelessCellTerminal extends AEBasePoweredItem implements IWirelessTermHandler, IBauble {

    public static final String NBT_TEMP_CELLS = "cellTerminalTempCells";
    public static final int MAX_TEMP_CELLS = 16;

    public ItemWirelessCellTerminal() {
        super(AEConfig.instance.wirelessTerminalBattery, com.google.common.base.Optional.absent());
        this.setUnlocalizedName(Tags.MODID + ".wireless_cell_terminal");
        this.setTextureName(Tags.MODID + ":wireless_cell_terminal");
        this.setCreativeTab(CreativeTab.instance);
        this.setMaxStackSize(1);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack item, World world, EntityPlayer player) {
        if (!world.isRemote) {
            String encKey = getEncryptionKey(item);
            if (encKey == null || encKey.isEmpty()) {
                player.addChatMessage(PlayerMessages.DeviceNotLinked.get());

                return item;
            }

            try {
                long parsedKey = Long.parseLong(encKey);
                ILocatable securityStation = AEApi.instance()
                    .registries()
                    .locatable()
                    .getLocatableBy(parsedKey);

                if (securityStation == null) {
                    player.addChatMessage(PlayerMessages.StationCanNotBeLocated.get());

                    return item;
                }
            } catch (NumberFormatException e) {
                player.addChatMessage(PlayerMessages.DeviceNotLinked.get());

                return item;
            }

            if (!hasPower(player, 0.5, item)) {
                player.addChatMessage(PlayerMessages.DeviceNotPowered.get());

                return item;
            }

            GuiHandler.openWirelessCellTerminalGui(player, player.inventory.currentItem, false);
        }

        return item;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addCheckedInformation(ItemStack stack, EntityPlayer player, List<String> lines,
        boolean displayMoreInfo) {
        super.addCheckedInformation(stack, player, lines, displayMoreInfo);

        lines.add(I18n.format("item.disk_terminal.cell_terminal.tooltip"));

        int tempCellCount = getTempCellCount(stack);
        if (tempCellCount > 0) {
            lines.add(I18n.format("item.disk_terminal.wireless_cell_terminal.temp_cells", tempCellCount));
        }

        String encKey = getEncryptionKey(stack);
        if (encKey == null || encKey.isEmpty()) {
            lines.add(EnumChatFormatting.RED + I18n.format("item.disk_terminal.wireless_cell_terminal.unlinked"));
        } else {
            lines.add(EnumChatFormatting.GREEN + I18n.format("item.disk_terminal.wireless_cell_terminal.linked"));
        }
    }

    @Override
    public boolean canHandle(ItemStack is) {
        return is.getItem() == this;
    }

    @Override
    public boolean usePower(EntityPlayer player, double amount, ItemStack is) {
        return this.extractAEPower(is, amount) >= amount - 0.5;
    }

    @Override
    public boolean hasPower(EntityPlayer player, double amt, ItemStack is) {
        return this.getAECurrentPower(is) >= amt;
    }

    @Override
    public IConfigManager getConfigManager(ItemStack target) {
        ConfigManager out = new ConfigManager((manager, settingName, newValue) -> {
            NBTTagCompound data = Platform.openNbtData(target);
            manager.writeToNBT(data);
        });

        out.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        out.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        out.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);

        out.readFromNBT(
            (NBTTagCompound) Platform.openNbtData(target)
                .copy());

        return out;
    }

    @Override
    public String getEncryptionKey(ItemStack item) {
        return Platform.openNbtData(item)
            .getString("encryptionKey");
    }

    @Override
    public void setEncryptionKey(ItemStack item, String encKey, String name) {
        NBTTagCompound tag = Platform.openNbtData(item);
        tag.setString("encryptionKey", encKey);
        tag.setString("name", name);
    }

    public static int getTempCellCount(ItemStack terminal) {
        NBTTagCompound nbt = Platform.openNbtData(terminal);
        if (!nbt.hasKey(NBT_TEMP_CELLS)) return 0;

        NBTTagList cellList = nbt.getTagList(NBT_TEMP_CELLS, Constants.NBT.TAG_COMPOUND);
        int count = 0;
        for (int i = 0; i < cellList.tagCount(); i++) {
            ItemStack cell = ItemStacks.load(cellList.getCompoundTagAt(i));
            if (!ItemStacks.isEmpty(cell)) count++;
        }

        return count;
    }

    public static ItemStack getTempCell(ItemStack terminal, int slot) {
        if (slot < 0 || slot >= MAX_TEMP_CELLS) return null;

        NBTTagCompound nbt = Platform.openNbtData(terminal);
        if (!nbt.hasKey(NBT_TEMP_CELLS)) return null;

        NBTTagList cellList = nbt.getTagList(NBT_TEMP_CELLS, Constants.NBT.TAG_COMPOUND);
        if (slot >= cellList.tagCount()) return null;

        return ItemStacks.load(cellList.getCompoundTagAt(slot));
    }

    public static void setTempCell(ItemStack terminal, int slot, ItemStack cell) {
        if (slot < 0 || slot >= MAX_TEMP_CELLS) return;

        NBTTagCompound nbt = Platform.openNbtData(terminal);

        NBTTagList cellList = nbt.hasKey(NBT_TEMP_CELLS) ? nbt.getTagList(NBT_TEMP_CELLS, Constants.NBT.TAG_COMPOUND)
            : new NBTTagList();

        while (cellList.tagCount() <= slot) cellList.appendTag(new NBTTagCompound());

        NBTTagCompound cellNbt = new NBTTagCompound();
        if (!ItemStacks.isEmpty(cell)) cell.writeToNBT(cellNbt);
        cellList.func_150304_a(slot, cellNbt);

        nbt.setTag(NBT_TEMP_CELLS, cellList);
    }

    public static int getMaxTempCells() {
        return MAX_TEMP_CELLS;
    }

    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged;
    }

    @Optional.Method(modid = "Baubles|Expanded")
    @Override
    public BaubleType getBaubleType(ItemStack itemStack) {
        return BaubleType.AMULET;
    }

    @Optional.Method(modid = "Baubles|Expanded")
    @Override
    public void onWornTick(ItemStack itemstack, EntityLivingBase player) {}

    @Optional.Method(modid = "Baubles|Expanded")
    @Override
    public void onEquipped(ItemStack itemstack, EntityLivingBase player) {}

    @Optional.Method(modid = "Baubles|Expanded")
    @Override
    public void onUnequipped(ItemStack itemstack, EntityLivingBase player) {}

    @Optional.Method(modid = "Baubles|Expanded")
    @Override
    public boolean canEquip(ItemStack itemstack, EntityLivingBase player) {
        return true;
    }

    @Optional.Method(modid = "Baubles|Expanded")
    @Override
    public boolean canUnequip(ItemStack itemstack, EntityLivingBase player) {
        return true;
    }
}
