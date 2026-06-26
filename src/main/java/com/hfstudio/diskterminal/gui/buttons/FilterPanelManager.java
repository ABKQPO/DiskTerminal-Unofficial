package com.hfstudio.diskterminal.gui.buttons;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;

import com.hfstudio.diskterminal.client.CellFilter;
import com.hfstudio.diskterminal.client.CellFilter.State;
import com.hfstudio.diskterminal.client.SlotLimit;
import com.hfstudio.diskterminal.config.DiskTerminalClientConfig;
import com.hfstudio.diskterminal.gui.GuiConstants;
import com.hfstudio.diskterminal.integration.ModIntegration;
import com.hfstudio.diskterminal.network.DiskTerminalNetwork;
import com.hfstudio.diskterminal.network.PacketSlotLimitChange;

/**
 * Manages the layout and positioning of filter buttons.
 * Handles dynamic placement based on available space and controls help widget.
 * <p>
 * Positioning strategy:
 * - Buttons start below the terminal style button
 * - Stack vertically in a single column by default
 * - If not enough space, stack in 2 columns
 * - If still not enough space, push upwards from controls help widget
 */
public class FilterPanelManager {

    private static final int BUTTON_SIZE = GuiFilterButton.SIZE;
    private static final int BUTTON_SPACING = 2;
    private static final int BUTTON_WITH_SPACING = BUTTON_SIZE + BUTTON_SPACING;

    // Margin from edges
    private static final int TOP_MARGIN = 4;
    private static final int BOTTOM_MARGIN = 8;

    private final List<GuiFilterButton> filterButtons = new ArrayList<>();
    private final Map<CellFilter, GuiFilterButton> filterButtonMap = new EnumMap<>(CellFilter.class);

    private GuiSlotLimitButton slotLimitButton = null;

    private boolean forStorageBus = false;
    private int currentTab = 0;

    /**
     * Initialize filter buttons for the given tab.
     * Creates buttons based on which filters are applicable to the current tab.
     *
     * @param buttonList    The GUI's button list to add buttons to
     * @param startButtonId Starting button ID
     * @param currentTab    The current tab (0-4)
     * @return The next available button ID
     */
    public int initButtons(List<GuiButton> buttonList, int startButtonId, int currentTab) {
        // Remove old buttons from list
        buttonList.removeAll(filterButtons);
        filterButtons.clear();
        filterButtonMap.clear();

        // Remove old slot limit button
        if (slotLimitButton != null) buttonList.remove(slotLimitButton);

        this.forStorageBus = currentTab >= GuiConstants.TAB_TEMP_AREA;
        this.currentTab = currentTab;
        DiskTerminalClientConfig config = DiskTerminalClientConfig.getInstance();

        int buttonId = startButtonId;

        // Create slot limit button for tabs that show content (inventory, storage bus inventory, subnet overview)
        if (currentTab == GuiConstants.TAB_INVENTORY || currentTab == GuiConstants.TAB_STORAGE_BUS_INVENTORY
            || currentTab == GuiConstants.TAB_SUBNETS) {
            SlotLimit limit = config.getSlotLimitForTab(currentTab);
            slotLimitButton = new GuiSlotLimitButton(buttonId++, 0, 0, limit);
            buttonList.add(slotLimitButton);
        } else {
            slotLimitButton = null;
        }

        List<CellFilter> applicableFilters = getApplicableFilters(currentTab);

        for (CellFilter filter : applicableFilters) {
            State state = config.getFilterState(filter, forStorageBus);
            GuiFilterButton button = new GuiFilterButton(buttonId++, 0, 0, filter, state);
            filterButtons.add(button);
            filterButtonMap.put(filter, button);
            buttonList.add(button);
        }

        return buttonId;
    }

    /**
     * Get the list of filters applicable to the current tab.
     */
    private List<CellFilter> getApplicableFilters(int tab) {
        List<CellFilter> filters = new ArrayList<>();

        // Tabs 0-2 are cell tabs, 3-4 are storage bus tabs
        boolean isCellTab = tab <= GuiConstants.TAB_PARTITION;
        boolean isStorageBusTab = tab >= GuiConstants.TAB_TEMP_AREA;

        // Stack type filters are only shown when the backing AE2 stack type is registered.
        if (isCellTab || isStorageBusTab) {
            if (ModIntegration.hasItemStorage()) {
                filters.add(CellFilter.ITEM_CELLS);
            }

            if (ModIntegration.hasFluidStorage()) {
                filters.add(CellFilter.FLUID_CELLS);
            }

            if (ModIntegration.hasEssentiaStorage()) {
                filters.add(CellFilter.ESSENTIA_CELLS);
            }
        }

        // Content-based filters
        filters.add(CellFilter.HAS_ITEMS);
        filters.add(CellFilter.PARTITIONED);

        return filters;
    }

    /**
     * Update button positions based on available space.
     *
     * @param guiLeft           GUI left edge X coordinate
     * @param guiTop            GUI top edge Y coordinate
     * @param ySize             GUI height
     * @param styleButtonY      Y coordinate of the terminal style button (top)
     * @param styleButtonBottom Y coordinate of the terminal style button (bottom)
     */
    public void updatePositions(int guiLeft, int guiTop, int ySize, int styleButtonY, int styleButtonBottom) {
        // Count all buttons (slot limit + filters)
        int buttonCount = filterButtons.size();
        if (slotLimitButton != null) buttonCount++;

        if (buttonCount == 0) return;

        // Available space calculation
        int panelX = guiLeft - BUTTON_SIZE - 2; // Same X as terminal style button
        int availableTop = styleButtonBottom + TOP_MARGIN;
        int availableBottom = guiTop + ySize - BOTTOM_MARGIN;

        int availableHeight = availableBottom - availableTop;

        // Determine layout strategy
        LayoutResult layout = calculateLayout(buttonCount, availableHeight, guiLeft);
        applyLayout(layout, panelX, availableTop, availableBottom, styleButtonY, guiLeft);
    }

    private static class LayoutResult {

        int columns;
        int rows;
        boolean pushUp;
        int requiredHeight;
    }

    private LayoutResult calculateLayout(int buttonCount, int availableHeight, int guiLeft) {
        LayoutResult result = new LayoutResult();

        // Strategy 1: Single column vertical
        int singleColHeight = buttonCount * BUTTON_WITH_SPACING - BUTTON_SPACING;
        if (singleColHeight <= availableHeight) {
            result.columns = 1;
            result.rows = buttonCount;
            result.pushUp = false;
            result.requiredHeight = singleColHeight;

            return result;
        }

        // Strategy 2: Two columns vertical
        int twoColRows = (buttonCount + 1) / 2;
        int twoColHeight = twoColRows * BUTTON_WITH_SPACING - BUTTON_SPACING;
        if (twoColHeight <= availableHeight) {
            result.columns = 2;
            result.rows = twoColRows;
            result.pushUp = false;
            result.requiredHeight = twoColHeight;

            return result;
        }

        // Strategy 3: Push upward (use two columns, start from bottom)
        result.columns = 2;
        result.rows = twoColRows;
        result.pushUp = true;
        result.requiredHeight = twoColHeight;

        return result;
    }

    private void applyLayout(LayoutResult layout, int panelX, int availableTop, int availableBottom, int styleButtonY,
        int guiLeft) {
        int startY;
        int startX;

        if (layout.pushUp) {
            // Start from bottom, going up
            startY = availableBottom - layout.requiredHeight;
            startX = panelX;

            // For 2 columns in pushUp mode, also adjust X
            if (layout.columns == 2) {
                startX = panelX - BUTTON_WITH_SPACING;
            }
        } else {
            // Normal vertical layout
            startX = panelX;
            startY = availableTop;

            // For 2 columns, adjust X to fit both columns
            if (layout.columns == 2) {
                startX = panelX - BUTTON_WITH_SPACING;
            }
        }

        // Style button forbidden zone (with margin)
        int styleButtonTop = styleButtonY - BUTTON_SPACING;
        int styleButtonBottom = styleButtonY + BUTTON_SIZE + BUTTON_SPACING;

        int index = 0;

        // Position slot limit button first (if present)
        if (slotLimitButton != null) {
            int[] pos = calculateButtonPosition(
                index,
                layout,
                startX,
                startY,
                guiLeft,
                styleButtonTop,
                styleButtonBottom);
            slotLimitButton.xPosition = pos[0];
            slotLimitButton.yPosition = pos[1];
            index++;
        }

        // Position filter buttons
        for (GuiFilterButton button : filterButtons) {
            int[] pos = calculateButtonPosition(
                index,
                layout,
                startX,
                startY,
                guiLeft,
                styleButtonTop,
                styleButtonBottom);
            button.xPosition = pos[0];
            button.yPosition = pos[1];
            index++;
        }
    }

    /**
     * Calculate the position for a button at the given index.
     * 
     * @return array of [x, y]
     */
    private int[] calculateButtonPosition(int index, LayoutResult layout, int startX, int startY, int guiLeft,
        int styleButtonTop, int styleButtonBottom) {
        int col, row;

        col = index % layout.columns;
        row = index / layout.columns;

        int buttonX, buttonY;

        // For vertical layout with 2 columns, right column is col 0
        if (layout.columns == 2) {
            buttonX = startX + (1 - col) * BUTTON_WITH_SPACING;
        } else {
            buttonX = startX;
        }
        buttonY = startY + row * BUTTON_WITH_SPACING;

        // Check if button overlaps with style button forbidden zone
        // If so, shift it above the style button
        if (buttonY >= styleButtonTop && buttonY < styleButtonBottom) {
            buttonY = styleButtonTop - BUTTON_SIZE;
        } else if (buttonY + BUTTON_SIZE > styleButtonTop && buttonY + BUTTON_SIZE <= styleButtonBottom) {
            buttonY = styleButtonTop - BUTTON_SIZE;
        }

        return new int[] { buttonX, buttonY };
    }

    /**
     * Get the bounding rectangle of the filter panel for NEI exclusion.
     */
    public Rectangle getBounds() {
        if (filterButtons.isEmpty() && slotLimitButton == null) return new Rectangle(0, 0, 0, 0);

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        // Include slot limit button
        if (slotLimitButton != null && slotLimitButton.visible) {
            minX = slotLimitButton.xPosition;
            minY = slotLimitButton.yPosition;
            maxX = Math.max(maxX, slotLimitButton.xPosition + BUTTON_SIZE);
            maxY = Math.max(maxY, slotLimitButton.yPosition + BUTTON_SIZE);
        }

        for (GuiFilterButton button : filterButtons) {
            if (!button.visible) continue;
            minX = Math.min(minX, button.xPosition);
            minY = Math.min(minY, button.yPosition);
            maxX = Math.max(maxX, button.xPosition + BUTTON_SIZE);
            maxY = Math.max(maxY, button.yPosition + BUTTON_SIZE);
        }

        if (minX == Integer.MAX_VALUE) return new Rectangle(0, 0, 0, 0);

        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * Get the filter state for a given filter type.
     */
    public State getFilterState(CellFilter filter) {
        GuiFilterButton button = filterButtonMap.get(filter);
        if (button != null) return button.getState();

        return State.SHOW_ALL;
    }

    /**
     * Get all current filter states as a map.
     */
    public Map<CellFilter, State> getAllFilterStates() {
        Map<CellFilter, State> states = new EnumMap<>(CellFilter.class);

        for (Map.Entry<CellFilter, GuiFilterButton> entry : filterButtonMap.entrySet()) {
            states.put(
                entry.getKey(),
                entry.getValue()
                    .getState());
        }

        return states;
    }

    /**
     * Handle a filter button click.
     * 
     * @return true if a filter button was clicked
     */
    public boolean handleClick(GuiFilterButton button) {
        if (!filterButtons.contains(button)) return false;

        State newState = button.cycleState();
        DiskTerminalClientConfig.getInstance()
            .setFilterState(button.getFilter(), newState, forStorageBus);

        return true;
    }

    /**
     * Get the filter button being hovered, if any.
     */
    public GuiFilterButton getHoveredButton(int mouseX, int mouseY) {
        for (GuiFilterButton button : filterButtons) {
            if (button.visible && mouseX >= button.xPosition
                && mouseX < button.xPosition + BUTTON_SIZE
                && mouseY >= button.yPosition
                && mouseY < button.yPosition + BUTTON_SIZE) {
                return button;
            }
        }

        return null;
    }

    /**
     * Get the slot limit button, if present.
     */
    public GuiSlotLimitButton getSlotLimitButton() {
        return slotLimitButton;
    }

    /**
     * Handle a slot limit button click.
     * 
     * @param button The clicked button
     * @return true if it was the slot limit button
     */
    public boolean handleSlotLimitClick(GuiSlotLimitButton button) {
        if (slotLimitButton != button) return false;

        SlotLimit newLimit = button.cycleLimit();
        DiskTerminalClientConfig config = DiskTerminalClientConfig.getInstance();

        if (currentTab < 0) {
            config.setSubnetSlotLimit(newLimit);
        } else if (forStorageBus) {
            config.setBusSlotLimit(newLimit);
        } else {
            config.setCellSlotLimit(newLimit);
        }

        // Send updated slot limits to server
        DiskTerminalNetwork.INSTANCE.sendToServer(
            new PacketSlotLimitChange(
                config.getCellSlotLimit()
                    .getLimit(),
                config.getBusSlotLimit()
                    .getLimit(),
                config.getSubnetSlotLimit()
                    .getLimit()));

        return true;
    }

    /**
     * Get the current slot limit based on the current tab.
     */
    public SlotLimit getCurrentSlotLimit() {
        return DiskTerminalClientConfig.getInstance()
            .getSlotLimitForTab(currentTab);
    }

    public List<GuiFilterButton> getButtons() {
        return filterButtons;
    }
}
