package com.hfstudio.diskterminal.gui.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.hfstudio.diskterminal.util.ItemStacks;

/**
 * A container that manages a list of child widgets.
 * <p>
 * Provides ordered iteration for drawing (first added = drawn first = background) and reverse
 * iteration for click handling (last added = drawn on top = gets first click). Events propagate to
 * children and stop at the first handler that returns true.
 */
public class WidgetContainer extends AbstractWidget {

    protected final List<IWidget> children = new ArrayList<>();

    public WidgetContainer(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public void addChild(IWidget child) {
        children.add(child);
    }

    public void removeChild(IWidget child) {
        children.remove(child);
    }

    public void clearChildren() {
        children.clear();
    }

    public List<IWidget> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        if (!visible) return;

        for (IWidget child : children) {
            if (child.isVisible()) child.draw(mouseX, mouseY);
        }
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (!visible) return false;

        for (int i = children.size() - 1; i >= 0; i--) {
            IWidget child = children.get(i);

            if (child.isVisible() && child.isHovered(mouseX, mouseY)) {
                if (child.handleClick(mouseX, mouseY, button)) return true;
            }
        }

        return false;
    }

    @Override
    public boolean handleKey(char typedChar, int keyCode) {
        if (!visible) return false;

        for (int i = children.size() - 1; i >= 0; i--) {
            IWidget child = children.get(i);

            if (child.isVisible() && child.handleKey(typedChar, keyCode)) return true;
        }

        return false;
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        if (!visible) return Collections.emptyList();

        for (int i = children.size() - 1; i >= 0; i--) {
            IWidget child = children.get(i);

            if (!child.isVisible() || !child.isHovered(mouseX, mouseY)) continue;

            List<String> tooltip = child.getTooltip(mouseX, mouseY);
            if (!tooltip.isEmpty()) return tooltip;
        }

        return Collections.emptyList();
    }

    @Override
    public ItemStack getHoveredItemStack(int mouseX, int mouseY) {
        if (!visible) return null;

        for (int i = children.size() - 1; i >= 0; i--) {
            IWidget child = children.get(i);

            if (!child.isVisible() || !child.isHovered(mouseX, mouseY)) continue;

            ItemStack stack = child.getHoveredItemStack(mouseX, mouseY);
            if (!ItemStacks.isEmpty(stack)) return stack;
        }

        return null;
    }
}
