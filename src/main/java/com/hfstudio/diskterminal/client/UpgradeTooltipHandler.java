package com.hfstudio.diskterminal.client;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.hfstudio.diskterminal.config.DiskTerminalClientConfig;
import com.hfstudio.diskterminal.gui.GuiCellTerminalBase;
import com.hfstudio.diskterminal.gui.GuiConstants;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.implementations.items.IUpgradeModule;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Adds insertion control hints to upgrade item tooltips while the Cell Terminal GUI is open.
 */
@SideOnly(Side.CLIENT)
public class UpgradeTooltipHandler {

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (!(Minecraft.getMinecraft().currentScreen instanceof GuiCellTerminalBase)) return;

        ItemStack stack = event.itemStack;
        if (ItemStacks.isEmpty(stack)) return;

        if (!(stack.getItem() instanceof IUpgradeModule)) return;
        if (((IUpgradeModule) stack.getItem()).getType(stack) == null) return;

        List<String> tooltip = event.toolTip;

        tooltip.add("");
        tooltip.add("§b" + I18n.format("gui.disk_terminal.upgrade.tooltip_hint_click"));
        tooltip.add("§b" + I18n.format("gui.disk_terminal.upgrade.tooltip_hint_shift_click"));

        List<String> availableEntries;

        int tab = DiskTerminalClientConfig.getInstance()
            .getSelectedTab();
        if (tab == GuiConstants.TAB_TERMINAL) {
            availableEntries = Arrays.asList(
                "§a" + I18n.format("gui.disk_terminal.upgrade.tooltip_hint_entry_drive"),
                "§a" + I18n.format("gui.disk_terminal.upgrade.tooltip_hint_entry_cell_lines"));
        } else if (tab == GuiConstants.TAB_INVENTORY || tab == GuiConstants.TAB_PARTITION) {
            availableEntries = Arrays.asList(
                "§a" + I18n.format("gui.disk_terminal.upgrade.tooltip_hint_entry_drive"),
                "§a" + I18n.format("gui.disk_terminal.upgrade.tooltip_hint_entry_cells"));
        } else if (tab == GuiConstants.TAB_TEMP_AREA) {
            availableEntries = List.of("§a" + I18n.format("gui.disk_terminal.upgrade.tooltip_hint_entry_cells"));
        } else if (tab == GuiConstants.TAB_STORAGE_BUS_INVENTORY || tab == GuiConstants.TAB_STORAGE_BUS_PARTITION) {
            availableEntries = List.of("§a" + I18n.format("gui.disk_terminal.upgrade.tooltip_hint_entry_storage_bus"));
        } else {
            availableEntries = List.of();
        }

        if (!availableEntries.isEmpty()) {
            tooltip.add("");
            tooltip.add("§a" + I18n.format("gui.disk_terminal.upgrade.tooltip_hint_available_entries"));
            tooltip.addAll(availableEntries);
        }
    }
}
