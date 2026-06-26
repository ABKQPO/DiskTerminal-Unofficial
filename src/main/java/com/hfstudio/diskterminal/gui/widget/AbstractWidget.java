package com.hfstudio.diskterminal.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.hfstudio.diskterminal.util.AEStackUtil;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.storage.data.IAEStack;

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

    public static boolean isPointIn(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public static void drawCenteredString(FontRenderer fontRenderer, String text, int x, int y, int width, int height,
        int color) {
        int textX = x + (width - fontRenderer.getStringWidth(text)) / 2;
        int textY = y + (height - fontRenderer.FONT_HEIGHT + 1) / 2;
        fontRenderer.drawString(text, textX, textY, color);
    }

    public static void drawItemOverlayText(FontRenderer fontRenderer, String text, int slotX, int slotY, int slotSize,
        int color) {
        if (text == null || text.isEmpty()) return;

        int textX = slotX + slotSize - 1 - fontRenderer.getStringWidth(text);
        int textY = slotY + slotSize - fontRenderer.FONT_HEIGHT;
        drawOverlayString(fontRenderer, text, textX, textY, color);
    }

    public static void drawPartitionIndicator(FontRenderer fontRenderer, int slotX, int slotY, int color) {
        drawOverlayString(fontRenderer, "P", slotX + 1, slotY + 1, color);
    }

    private static void drawOverlayString(FontRenderer fontRenderer, String text, int x, int y, int color) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glTranslatef(0.0F, 0.0F, 300.0F);
        fontRenderer.drawStringWithShadow(text, x, y, color);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /**
     * Render an IAEStack at the given position using AE2's native rendering.
     * This is the preferred method for rendering storage contents as it properly handles
     * all registered stack types with their custom renderers.
     */
    public static void renderAEStack(IAEStack<?> stack, int renderX, int renderY) {
        if (stack == null) return;

        AEStackUtil.drawStackInGui(stack, renderX, renderY);
    }

    /**
     * Render an item stack at the given position with standard GUI lighting.
     * Uses AE2's rendering approach to properly handle custom item renderers (e.g., ItemFluidDrop).
     * For rendering storage contents, prefer renderAEStack() instead.
     */
    public static void renderItemStack(RenderItem itemRender, ItemStack stack, int renderX, int renderY) {
        if (ItemStacks.isEmpty(stack)) return;

        Minecraft mc = Minecraft.getMinecraft();

        // Save all GL state before rendering to prevent pollution
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        // Set up proper rendering state for items with custom renderers
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        // Translate forward slightly to ensure proper rendering order
        GL11.glTranslatef(0.0F, 0.0F, 32.0F);

        // Enable GUI standard lighting (required for proper item rendering)
        RenderHelper.enableGUIStandardItemLighting();

        // Render the item with effects (enchantment glint, etc.)
        itemRender.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.renderEngine, stack, renderX, renderY);

        // Translate back
        GL11.glTranslatef(0.0F, 0.0F, -32.0F);

        // Restore all GL state
        GL11.glPopAttrib();
    }
}
