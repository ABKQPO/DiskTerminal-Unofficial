package com.hfstudio.diskterminal.gui.widget.header;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import com.hfstudio.diskterminal.client.StorageBusInfo.HeaderModeButtonKind;
import com.hfstudio.diskterminal.gui.GuiConstants;
import com.hfstudio.diskterminal.gui.PriorityFieldManager;
import com.hfstudio.diskterminal.gui.widget.AbstractWidget;
import com.hfstudio.diskterminal.gui.widget.button.ButtonType;
import com.hfstudio.diskterminal.gui.widget.button.SmallButton;
import com.hfstudio.diskterminal.util.ItemStacks;

/**
 * Storage bus header widget for the Cell Terminal (tabs 4-5).
 * <p>
 * Extends the storage header with:
 * <ul>
 * <li>IO mode button (texture-based, cycles through Read/Write/ReadWrite)</li>
 * <li>Upgrade card icons (rendered at the left edge, inherited from AbstractHeader)</li>
 * <li>Selection support for batch operations (quick-add via keybind)</li>
 * </ul>
 *
 * The IO mode button uses textured icons to indicate the current access mode:
 * <ul>
 * <li>{@link ButtonType#READ_ONLY} = Read-only (extract)</li>
 * <li>{@link ButtonType#WRITE_ONLY} = Write-only (insert)</li>
 * <li>{@link ButtonType#READ_WRITE} = Read+Write (bidirectional split)</li>
 * </ul>
 *
 * @see StorageHeader
 * @see AbstractHeader
 */
public class StorageBusHeader extends StorageHeader {

    /** Supplier for the access restriction mode (0=NONE, 1=READ, 2=WRITE, 3=READ_WRITE) */
    private Supplier<Integer> accessModeSupplier;

    /** Whether IO mode switching is supported */
    private Supplier<Boolean> supportsIOModeSupplier;

    /** Which semantic the mode button currently uses */
    private Supplier<HeaderModeButtonKind> modeButtonKindSupplier;

    /** GT5 auto pull enabled flag */
    private Supplier<Boolean> autoPullEnabledSupplier;

    /** Supplier for the target block icon shown as an overlay on bus icons */
    private Supplier<ItemStack> overlayIconSupplier;

    /** Whether the main header icon is the target block rather than the bus itself */
    private Supplier<Boolean> connectedIconTargetSupplier;

    /** Callback when the IO mode button is clicked */
    private Runnable onIOModeClick;

    /** Textured IO mode button (READ_ONLY, WRITE_ONLY, or READ_WRITE) */
    private final SmallButton ioModeButton;

    public StorageBusHeader(int y, FontRenderer fontRenderer, RenderItem itemRender) {
        super(y, fontRenderer, itemRender);
        // IO mode button: type is updated each frame from accessModeSupplier.
        // Default to READ_WRITE since it will be overwritten before drawing.
        this.ioModeButton = new SmallButton(
            GuiConstants.BUTTON_IO_MODE_X,
            y,
            ButtonType.READ_WRITE,
            () -> { if (onIOModeClick != null) onIOModeClick.run(); });
    }

    public void setAccessModeSupplier(Supplier<Integer> supplier) {
        this.accessModeSupplier = supplier;
    }

    public void setSupportsIOModeSupplier(Supplier<Boolean> supplier) {
        this.supportsIOModeSupplier = supplier;
    }

    public void setModeButtonKindSupplier(Supplier<HeaderModeButtonKind> supplier) {
        this.modeButtonKindSupplier = supplier;
    }

    public void setAutoPullEnabledSupplier(Supplier<Boolean> supplier) {
        this.autoPullEnabledSupplier = supplier;
    }

    public void setOverlayIconSupplier(Supplier<ItemStack> supplier) {
        this.overlayIconSupplier = supplier;
    }

    public void setConnectedIconTargetSupplier(Supplier<Boolean> supplier) {
        this.connectedIconTargetSupplier = supplier;
    }

    public void setOnIOModeClick(Runnable callback) {
        this.onIOModeClick = callback;
    }

    @Override
    protected int drawHeaderContent(int mouseX, int mouseY) {
        this.nameMaxWidth = getNameMaxWidth();

        // Draw location text
        drawLocation();

        // Draw expand/collapse indicator
        drawExpandIcon(mouseX, mouseY);

        // Draw IO mode button
        drawIOModeButton(mouseX, mouseY);

        // Draw upgrade cards (from AbstractHeader)
        if (cardsDisplay != null) cardsDisplay.draw(mouseX, mouseY);

        // Register priority field with the singleton (positions it for this frame)
        if (prioritizable != null && prioritizable.supportsPriority()) {
            PriorityFieldManager.getInstance()
                .registerField(prioritizable, y, guiLeft, guiTop, fontRenderer);
        }

        // Return the hover right bound (up to mode button area when present)
        return hasModeButton() ? GuiConstants.BUTTON_IO_MODE_X : GuiConstants.EXPAND_ICON_X;
    }

    private int getNameMaxWidth() {
        int rightEdge = GuiConstants.EXPAND_ICON_X - 4;

        if (prioritizable != null && prioritizable.supportsPriority()) {
            rightEdge = GuiConstants.CONTENT_RIGHT_EDGE - PriorityFieldManager.FIELD_WIDTH
                - PriorityFieldManager.RIGHT_MARGIN
                - 4;
        }

        if (hasModeButton()) {
            rightEdge = GuiConstants.BUTTON_IO_MODE_X - 4;
        }

        return Math.max(0, rightEdge - GuiConstants.HEADER_NAME_X);
    }

    /**
     * Draw the shared header mode button using textured SmallButton.
     * AE buses use access restriction states, while GT5 buses use auto pull on/off.
     */
    private void drawIOModeButton(int mouseX, int mouseY) {
        if (!hasModeButton()) return;

        HeaderModeButtonKind kind = modeButtonKindSupplier != null ? modeButtonKindSupplier.get()
            : HeaderModeButtonKind.NONE;

        if (kind == HeaderModeButtonKind.AUTO_PULL) {
            boolean autoPullEnabled = autoPullEnabledSupplier != null && autoPullEnabledSupplier.get();
            ioModeButton.setType(autoPullEnabled ? ButtonType.AUTO_PULL_ON : ButtonType.AUTO_PULL_OFF);
        } else {
            int accessMode = accessModeSupplier != null ? accessModeSupplier.get() : 3;
            switch (accessMode) {
                case 1:
                    ioModeButton.setType(ButtonType.READ_ONLY);
                    break;
                case 2:
                    ioModeButton.setType(ButtonType.WRITE_ONLY);
                    break;
                default:
                    ioModeButton.setType(ButtonType.READ_WRITE);
                    break;
            }
        }

        // Position at current header Y (since header Y can change per frame)
        ioModeButton.setPosition(GuiConstants.BUTTON_IO_MODE_X, y + 1);
        ioModeButton.draw(mouseX, mouseY);
    }

    @Override
    protected void drawIcon() {
        super.drawIcon();

        boolean connectedIconIsTarget = connectedIconTargetSupplier != null && connectedIconTargetSupplier.get();
        if (!connectedIconIsTarget) return;

        ItemStack targetIcon = overlayIconSupplier != null ? overlayIconSupplier.get() : null;
        if (ItemStacks.isEmpty(targetIcon)) return;

        int iconX = GuiConstants.GUI_INDENT + TARGET_RENDER_X_OFFSET + 1;
        int iconY = y + 1;

        GL11.glPushMatrix();
        GL11.glTranslatef(iconX, iconY, 64.0F);
        GL11.glScalef(0.5F, 0.5F, 1.0F);
        AbstractWidget.renderItemStack(itemRender, targetIcon, 0, 0);
        GL11.glPopMatrix();
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible) return false;

        // Left-click only for IO mode and expand/collapse
        if (button == 0) {
            // IO mode button click (delegated to SmallButton)
            if (hasModeButton() && ioModeButton.handleClick(mouseX, mouseY, button)) {
                return true;
            }

            // Expand/collapse click
            if (expandHovered && onExpandToggle != null) {
                onExpandToggle.run();
                return true;
            }
        }

        // Name click (right-click), cards click, and header selection for quick-add (from base)
        return super.handleClick(mouseX, mouseY, button);
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!visible || !isHovered(mouseX, mouseY)) return Collections.emptyList();

        // IO mode button tooltip
        if (hasModeButton() && ioModeButton.isHovered(mouseX, mouseY)) {
            return ioModeButton.getTooltip(mouseX, mouseY);
        }

        // Cards tooltip (from base)
        return super.getTooltip(mouseX, mouseY);
    }

    private boolean hasModeButton() {
        if (supportsIOModeSupplier != null && supportsIOModeSupplier.get()) return true;

        return modeButtonKindSupplier != null && modeButtonKindSupplier.get() == HeaderModeButtonKind.AUTO_PULL;
    }
}
