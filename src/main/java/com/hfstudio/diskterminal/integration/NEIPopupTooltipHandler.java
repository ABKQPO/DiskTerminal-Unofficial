package com.hfstudio.diskterminal.integration;

import java.awt.Point;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import com.hfstudio.diskterminal.gui.GuiCellTerminalBase;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.guihook.IContainerObjectHandler;

public class NEIPopupTooltipHandler implements IContainerObjectHandler {

    public static final NEIPopupTooltipHandler INSTANCE = new NEIPopupTooltipHandler();

    private NEIPopupTooltipHandler() {}

    @Override
    public void guiTick(GuiContainer gui) {}

    @Override
    public void refresh(GuiContainer gui) {}

    @Override
    public void load(GuiContainer gui) {}

    @Override
    public ItemStack getStackUnderMouse(GuiContainer gui, int mouseX, int mouseY) {
        return null;
    }

    @Override
    public boolean objectUnderMouse(GuiContainer gui, int mouseX, int mouseY) {
        return false;
    }

    @Override
    public boolean shouldShowTooltip(GuiContainer gui) {
        if (!(gui instanceof GuiCellTerminalBase terminalGui)) return true;

        Point mouse = GuiDraw.getMousePosition();
        return !terminalGui.isBlockingPopupAt(mouse.x, mouse.y);
    }
}
