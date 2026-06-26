package com.hfstudio.diskterminal.gui.handler;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.hfstudio.diskterminal.config.DiskTerminalServerConfig;
import com.hfstudio.diskterminal.gui.GuiConstants;
import com.hfstudio.diskterminal.util.ItemStacks;

/**
 * Handler for rendering tabs and the controls help widget.
 * Extracted from GuiCellTerminalBase to reduce complexity.
 */
public class TabRenderingHandler {

    /** The location of the creative inventory tabs texture */
    private static final ResourceLocation CREATIVE_INVENTORY_TABS = new ResourceLocation("textures/gui/container/creative_inventory/tabs.png");

    private TabRenderingHandler() {}

    /**
     * Context for tab rendering operations.
     */
    public static class TabRenderContext {

        public final int guiLeft;
        public final int offsetX;
        public final int offsetY;
        public final int mouseX;
        public final int mouseY;
        public final int tabWidth;
        public final int tabHeight;
        public final int tabYOffset;
        public final int currentTab;
        public final RenderItem itemRender;
        public final Minecraft mc;

        public TabRenderContext(int guiLeft, int offsetX, int offsetY, int mouseX, int mouseY, int tabWidth,
            int tabHeight, int tabYOffset, int currentTab, RenderItem itemRender, Minecraft mc) {
            this.guiLeft = guiLeft;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.tabWidth = tabWidth;
            this.tabHeight = tabHeight;
            this.tabYOffset = tabYOffset;
            this.currentTab = currentTab;
            this.itemRender = itemRender;
            this.mc = mc;
        }
    }

    /**
     * Callback interface for getting tab icons.
     */
    public interface TabIconProvider {

        ItemStack getTabIcon(int tab);

        ItemStack getStorageBusIcon();

        ItemStack getInventoryIcon();

        ItemStack getPartitionIcon();
    }

    /**
     * Result of rendering tabs, containing the hovered tab index.
     */
    public static class TabRenderResult {

        public final int hoveredTab;

        public TabRenderResult(int hoveredTab) {
            this.hoveredTab = hoveredTab;
        }
    }

    /**
     * Draw all tabs with proper hover highlighting.
     *
     * @param ctx          The rendering context
     * @param iconProvider Provider for tab icons
     * @param tabCount     Total number of tabs to draw
     * @return Result containing hovered tab index (-1 if none)
     */
    public static TabRenderResult drawTabs(TabRenderContext ctx, TabIconProvider iconProvider, int tabCount) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);

        int tabY = ctx.offsetY + ctx.tabYOffset;
        int hoveredTab = -1;

        for (int i = 0; i < tabCount; i++) {
            int tabX = ctx.offsetX + 4 + (i * (ctx.tabWidth + 2));
            boolean isSelected = (i == ctx.currentTab);
            boolean isHovered = ctx.mouseX >= tabX && ctx.mouseX < tabX + ctx.tabWidth
                && ctx.mouseY >= tabY
                && ctx.mouseY < tabY + ctx.tabHeight;
            boolean isDisabled = DiskTerminalServerConfig.isInitialized() && !DiskTerminalServerConfig.getInstance()
                .isTabEnabled(i);

            if (isHovered) hoveredTab = i;

            // Draw the tab using the vanilla creative-inventory tab sprite for a native MC look.
            // The creative tabs.png lays out top-row tabs as 28x32 sprites: unselected at uv
            // (28,0), selected at uv (28,32) (the selected sprite has the dark notch that merges
            // with the panel). We render at the configured tabWidth/tabHeight so layout is unchanged.
            ctx.mc.getTextureManager()
                .bindTexture(CREATIVE_INVENTORY_TABS);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            int spriteU = 28;
            int spriteV = isSelected ? 32 : 0;
            int spriteW = 28;
            int spriteH = 32;

            drawScaledTexturedRect(tabX, tabY, spriteU, spriteV, spriteW, spriteH, ctx.tabWidth, ctx.tabHeight);

            // Gray-out overlay for disabled tabs (the vanilla sprite has no disabled variant).
            if (isDisabled) {
                Gui.drawRect(tabX, tabY, tabX + ctx.tabWidth, tabY + ctx.tabHeight, 0x99303030);
            } else if (isHovered && !isSelected) {
                Gui.drawRect(tabX, tabY, tabX + ctx.tabWidth, tabY + ctx.tabHeight, 0x33FFFFFF);
            }

            // Draw icon (composite for storage bus tabs)
            // Gray out icons for disabled tabs
            float iconAlpha = isDisabled ? 0.4f : 1.0f;
            if (i == GuiConstants.TAB_STORAGE_BUS_INVENTORY || i == GuiConstants.TAB_STORAGE_BUS_PARTITION) {
                drawCompositeTabIcon(ctx, tabX + 3, tabY + 3, i, iconProvider, isDisabled);
            } else {
                ItemStack icon = iconProvider.getTabIcon(i);
                if (!ItemStacks.isEmpty(icon)) {
                    GL11.glColor4f(iconAlpha, iconAlpha, iconAlpha, 1.0F);
                    RenderHelper.enableGUIStandardItemLighting();
                    ctx.itemRender
                        .renderItemAndEffectIntoGUI(ctx.mc.fontRenderer, ctx.mc.renderEngine, icon, tabX + 3, tabY + 3);
                    RenderHelper.disableStandardItemLighting();
                    GL11.glDisable(GL11.GL_LIGHTING);
                }
            }
        }

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);

        return new TabRenderResult(hoveredTab);
    }

    /**
     * Draw a composite icon for storage bus tabs using diagonal cut view.
     * Shows top-left half of one icon and bottom-right half of the storage bus icon.
     * 
     * @param ctx          The rendering context
     * @param x            The x position to draw at
     * @param y            The y position to draw at
     * @param tab          The tab index
     * @param iconProvider Provider for tab icons
     * @param isDisabled   Whether the tab is disabled (will render grayed out)
     */
    private static void drawCompositeTabIcon(TabRenderContext ctx, int x, int y, int tab, TabIconProvider iconProvider,
        boolean isDisabled) {
        ItemStack topLeftIcon = (tab == GuiConstants.TAB_STORAGE_BUS_INVENTORY) ? iconProvider.getInventoryIcon()
            : iconProvider.getPartitionIcon();
        ItemStack storageBusIcon = iconProvider.getStorageBusIcon();

        float colorMod = isDisabled ? 0.4f : 1.0f;
        GL11.glColor4f(colorMod, colorMod, colorMod, 1.0F);

        int scaleFactor = new ScaledResolution(ctx.mc, ctx.mc.displayWidth, ctx.mc.displayHeight).getScaleFactor();
        int offset = 4;

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        RenderHelper.enableGUIStandardItemLighting();

        // Top-left icon
        if (!ItemStacks.isEmpty(topLeftIcon)) {
            for (int row = 0; row < 16; row++) {
                int stripWidth = Math.max(0, 15 - row - 1);
                if (stripWidth == 0) continue;

                int scissorX = x * scaleFactor;
                int scissorY = (ctx.mc.displayHeight) - ((y + row + 1) * scaleFactor);
                int scissorWidth = stripWidth * scaleFactor;
                int scissorHeight = scaleFactor;

                GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
                ctx.itemRender.renderItemAndEffectIntoGUI(
                    ctx.mc.fontRenderer,
                    ctx.mc.renderEngine,
                    topLeftIcon,
                    x - offset,
                    y - offset);
            }
        }

        // Bottom-right icon (storage bus)
        if (!ItemStacks.isEmpty(storageBusIcon)) {
            for (int row = 0; row < 16; row++) {
                int clipStart = Math.min(16, 17 - row);
                int stripWidth = 16 - clipStart;
                if (stripWidth <= 0) continue;

                int scissorX = (x + clipStart) * scaleFactor;
                int scissorY = (ctx.mc.displayHeight) - ((y + row + 1) * scaleFactor);
                int scissorWidth = stripWidth * scaleFactor;
                int scissorHeight = scaleFactor;

                GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
                ctx.itemRender.renderItemAndEffectIntoGUI(
                    ctx.mc.fontRenderer,
                    ctx.mc.renderEngine,
                    storageBusIcon,
                    x + offset,
                    y + offset);
            }
        }
        RenderHelper.disableStandardItemLighting();

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);

        // Draw diagonal separator line
        float lineColor = isDisabled ? 0.15f : 0.3f;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(lineColor, lineColor, lineColor, 1.0f);
        GL11.glLineWidth(1.5f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x + 15, y + 1);
        GL11.glVertex2f(x + 1, y + 15);
        GL11.glEnd();
        GL11.glLineWidth(1.0f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Draw a textured rectangle with scaling support.
     * Renders a texture region (spriteU, spriteV, spriteW, spriteH) at screen position (x, y)
     * scaled to (targetW, targetH).
     *
     * @param x       Screen X position
     * @param y       Screen Y position
     * @param spriteU Texture U coordinate
     * @param spriteV Texture V coordinate
     * @param spriteW Texture width in pixels
     * @param spriteH Texture height in pixels
     * @param targetW Target width on screen
     * @param targetH Target height on screen
     */
    private static void drawScaledTexturedRect(int x, int y, int spriteU, int spriteV, int spriteW, int spriteH,
        int targetW, int targetH) {
        float texScale = 1.0F / 256.0F;
        float u0 = spriteU * texScale;
        float v0 = spriteV * texScale;
        float u1 = (spriteU + spriteW) * texScale;
        float v1 = (spriteV + spriteH) * texScale;

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + targetH, 0, u0, v1);
        tessellator.addVertexWithUV(x + targetW, y + targetH, 0, u1, v1);
        tessellator.addVertexWithUV(x + targetW, y, 0, u1, v0);
        tessellator.addVertexWithUV(x, y, 0, u0, v0);
        tessellator.draw();
    }

    /**
     * Context for controls help widget rendering.
     */
    public static class ControlsHelpContext {

        public final int guiLeft;
        public final int guiTop;
        public final int ySize;
        public final int screenHeight;
        public final int currentTab;
        public final FontRenderer fontRenderer;

        public ControlsHelpContext(int guiLeft, int guiTop, int ySize, int screenHeight, int currentTab,
            FontRenderer fontRenderer) {
            this.guiLeft = guiLeft;
            this.guiTop = guiTop;
            this.ySize = ySize;
            this.screenHeight = screenHeight;
            this.currentTab = currentTab;
            this.fontRenderer = fontRenderer;
        }
    }

    /**
     * Result of rendering controls help widget, containing the wrapped lines.
     */
    public static class ControlsHelpResult {

        public final List<String> wrappedLines;
        public final int cachedTab;

        public ControlsHelpResult(List<String> wrappedLines, int cachedTab) {
            this.wrappedLines = wrappedLines;
            this.cachedTab = cachedTab;
        }
    }

    // Constants for controls help layout
    private static final int CONTROLS_HELP_LEFT_MARGIN = GuiConstants.CONTROLS_HELP_LEFT_MARGIN;
    private static final int CONTROLS_HELP_RIGHT_MARGIN = GuiConstants.CONTROLS_HELP_RIGHT_MARGIN;
    private static final int CONTROLS_HELP_PADDING = GuiConstants.CONTROLS_HELP_PADDING;
    private static final int CONTROLS_HELP_LINE_HEIGHT = GuiConstants.CONTROLS_HELP_LINE_HEIGHT;

    /**
     * Draw the controls help widget for the current tab.
     *
     * @param ctx       The rendering context
     * @param helpLines The help text lines from the active tab widget
     * @return Result containing wrapped lines and cached tab for exclusion area calculation
     */
    public static ControlsHelpResult drawControlsHelpWidget(ControlsHelpContext ctx, List<String> helpLines) {
        if (helpLines == null || helpLines.isEmpty()) {
            return new ControlsHelpResult(new ArrayList<>(), ctx.currentTab);
        }

        // Calculate panel width
        int panelWidth = ctx.guiLeft - CONTROLS_HELP_RIGHT_MARGIN - CONTROLS_HELP_LEFT_MARGIN;
        if (panelWidth < 60) panelWidth = 60;

        int textWidth = panelWidth - (CONTROLS_HELP_PADDING * 2);

        // Wrap all lines
        List<String> wrappedLines = new ArrayList<>();
        for (String line : helpLines) {
            if (line.isEmpty()) {
                wrappedLines.add("");
            } else {
                wrappedLines.addAll(ctx.fontRenderer.listFormattedStringToWidth(line, textWidth));
            }
        }

        // Calculate positions
        int panelRight = -CONTROLS_HELP_RIGHT_MARGIN;
        int panelLeft = -ctx.guiLeft + CONTROLS_HELP_LEFT_MARGIN;
        int contentHeight = wrappedLines.size() * CONTROLS_HELP_LINE_HEIGHT;
        int panelHeight = contentHeight + (CONTROLS_HELP_PADDING * 2);

        // Position relative to screen bottom
        // Leave margin for JEI bookmarks button at screen bottom
        int bottomOffset = 28;
        int panelBottom = ctx.screenHeight - ctx.guiTop - bottomOffset;
        int panelTop = panelBottom - panelHeight;

        // Draw AE2-style panel background
        Gui.drawRect(panelLeft, panelTop, panelRight, panelBottom, 0xC0000000);

        // Border
        Gui.drawRect(panelLeft, panelTop, panelRight, panelTop + 1, 0xFF606060);
        Gui.drawRect(panelLeft, panelTop, panelLeft + 1, panelBottom, 0xFF606060);
        Gui.drawRect(panelLeft, panelBottom - 1, panelRight, panelBottom, 0xFF303030);
        Gui.drawRect(panelRight - 1, panelTop, panelRight, panelBottom, 0xFF303030);

        // Draw text
        int textX = panelLeft + CONTROLS_HELP_PADDING;
        int textY = panelTop + CONTROLS_HELP_PADDING;
        for (int i = 0; i < wrappedLines.size(); i++) {
            String line = wrappedLines.get(i);
            if (!line.isEmpty()) {
                ctx.fontRenderer.drawString(line, textX, textY + (i * CONTROLS_HELP_LINE_HEIGHT), 0xCCCCCC);
            }
        }

        return new ControlsHelpResult(wrappedLines, ctx.currentTab);
    }
}
