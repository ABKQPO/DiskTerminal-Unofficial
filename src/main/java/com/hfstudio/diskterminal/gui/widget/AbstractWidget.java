package com.hfstudio.diskterminal.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import com.hfstudio.diskterminal.util.ItemStacks;

/**
 * Base implementation of {@link IWidget} providing common fields and behavior.
 * <p>
 * Subclasses must implement {@link #draw(int, int)} and {@link #handleClick(int, int, int)}.
 * Hover detection is provided by default based on the widget's bounding rectangle.
 */
public abstract class AbstractWidget implements IWidget {

    protected static int ICON_SIZE = 16;

    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean visible = true;

    protected AbstractWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public boolean isHovered(int mouseX, int mouseY) {
        if (!visible) return false;

        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // ---- Shared utilities ----

    /**
     * Truncate a string to fit within a pixel width, appending "..." if needed.
     * Correctly handles formatting codes via fontRenderer.getStringWidth.
     */
    public static String trimTextToWidth(FontRenderer fontRenderer, String text, int maxWidth) {
        if (fontRenderer.getStringWidth(text) <= maxWidth) return text;

        String ellipsis = "...";
        int ellipWidth = fontRenderer.getStringWidth(ellipsis);

        for (int i = text.length() - 1; i > 0; i--) {
            String trimmed = text.substring(0, i);
            if (fontRenderer.getStringWidth(trimmed) + ellipWidth <= maxWidth) {
                return trimmed + ellipsis;
            }
        }

        return ellipsis;
    }

    /**
     * Render an item stack at the given position with standard GUI lighting.
     * Restores GL state (lighting, blend) after rendering.
     */
    public static void renderItemStack(RenderItem itemRender, ItemStack stack, int renderX, int renderY) {
        if (ItemStacks.isEmpty(stack)) return;

        Minecraft mc = Minecraft.getMinecraft();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.renderEngine, stack, renderX, renderY);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
}
