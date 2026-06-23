package com.hfstudio.diskterminal.gui.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import com.hfstudio.diskterminal.util.ItemStacks;

/**
 * Widget that displays cell upgrade cards (small 8x8 icons) in a 2-column grid.
 * Supports hover tracking for tooltips and click-to-extract behavior.
 */
public class CardsDisplay extends AbstractWidget {

    private static final int CARD_ICON_SIZE = 8;
    private static final int CARD_STRIDE = 9;
    private static final int COLUMNS = 2;

    private final Supplier<List<CardEntry>> cardsSupplier;
    private final RenderItem itemRender;

    private int hoveredCardIndex = -1;
    private ItemStack hoveredCardStack = null;

    /**
     * A single card entry with its item and slot position.
     */
    public static class CardEntry {

        public final ItemStack stack;
        public final int slotIndex;

        public CardEntry(ItemStack stack, int slotIndex) {
            this.stack = stack;
            this.slotIndex = slotIndex;
        }
    }

    /**
     * Callback for card click events (upgrade extraction).
     */
    @FunctionalInterface
    public interface CardClickCallback {

        void onCardClicked(int slotIndex);
    }

    private CardClickCallback clickCallback;

    public CardsDisplay(int x, int y, Supplier<List<CardEntry>> cardsSupplier, RenderItem itemRender) {
        super(x, y, 0, CARD_ICON_SIZE);
        this.cardsSupplier = cardsSupplier;
        this.itemRender = itemRender;
    }

    public void setClickCallback(CardClickCallback callback) {
        this.clickCallback = callback;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        List<CardEntry> cards = cardsSupplier.get();
        if (cards.isEmpty()) return;

        hoveredCardIndex = -1;
        hoveredCardStack = null;

        int rows = (cards.size() + COLUMNS - 1) / COLUMNS;
        this.width = COLUMNS * CARD_STRIDE;
        this.height = rows * CARD_STRIDE;

        for (int i = 0; i < cards.size(); i++) {
            CardEntry entry = cards.get(i);
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int iconX = x + col * CARD_STRIDE;
            int iconY = y + row * CARD_STRIDE;

            if (!ItemStacks.isEmpty(entry.stack)) {
                renderSmallItemStack(entry.stack, iconX, iconY);

                if (mouseX >= iconX && mouseX < iconX + CARD_ICON_SIZE
                    && mouseY >= iconY
                    && mouseY < iconY + CARD_ICON_SIZE) {
                    hoveredCardIndex = entry.slotIndex;
                    hoveredCardStack = entry.stack;
                }
            }
        }
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible || button != 0 || hoveredCardIndex < 0) return false;
        if (clickCallback == null) return false;

        clickCallback.onCardClicked(hoveredCardIndex);

        return true;
    }

    @Override
    public boolean isHovered(int mouseX, int mouseY) {
        if (!visible) return false;

        List<CardEntry> cards = cardsSupplier.get();
        if (cards.isEmpty()) return false;

        for (int i = 0; i < cards.size(); i++) {
            CardEntry entry = cards.get(i);
            if (ItemStacks.isEmpty(entry.stack)) continue;

            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int iconX = x + col * CARD_STRIDE;
            int iconY = y + row * CARD_STRIDE;
            if (mouseX >= iconX && mouseX < iconX + CARD_ICON_SIZE
                && mouseY >= iconY
                && mouseY < iconY + CARD_ICON_SIZE) {
                return true;
            }
        }

        return false;
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (hoveredCardIndex < 0 || ItemStacks.isEmpty(hoveredCardStack)) return Collections.emptyList();

        List<String> lines = new ArrayList<>();
        lines.add("§6" + hoveredCardStack.getDisplayName());
        lines.add("");
        lines.add("§b" + I18n.format("gui.disk_terminal.upgrade.click_extract"));
        lines.add("§b" + I18n.format("gui.disk_terminal.upgrade.shift_click_inventory"));

        return lines;
    }

    private void renderSmallItemStack(ItemStack stack, int renderX, int renderY) {
        if (ItemStacks.isEmpty(stack)) return;

        Minecraft mc = Minecraft.getMinecraft();

        GL11.glPushMatrix();
        GL11.glTranslatef(renderX, renderY, 0);
        GL11.glScalef(0.5f, 0.5f, 1.0f);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.renderEngine, stack, 0, 0);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);

        GL11.glPopMatrix();

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
}
