package com.hfstudio.diskterminal.gui;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.hfstudio.diskterminal.client.CellContentRow;
import com.hfstudio.diskterminal.client.CellInfo;
import com.hfstudio.diskterminal.client.KeyBindings;
import com.hfstudio.diskterminal.client.SearchFilterMode;
import com.hfstudio.diskterminal.client.StorageBusContentRow;
import com.hfstudio.diskterminal.client.StorageInfo;
import com.hfstudio.diskterminal.client.SubnetVisibility;
import com.hfstudio.diskterminal.client.TabStateManager;
import com.hfstudio.diskterminal.config.DiskTerminalClientConfig;
import com.hfstudio.diskterminal.config.DiskTerminalClientConfig.TerminalStyle;
import com.hfstudio.diskterminal.config.DiskTerminalServerConfig;
import com.hfstudio.diskterminal.container.ContainerCellTerminalBase;
import com.hfstudio.diskterminal.gui.buttons.FilterPanelManager;
import com.hfstudio.diskterminal.gui.buttons.GuiBackButton;
import com.hfstudio.diskterminal.gui.buttons.GuiFilterButton;
import com.hfstudio.diskterminal.gui.buttons.GuiSearchHelpButton;
import com.hfstudio.diskterminal.gui.buttons.GuiSearchModeButton;
import com.hfstudio.diskterminal.gui.buttons.GuiSlotLimitButton;
import com.hfstudio.diskterminal.gui.buttons.GuiSubnetVisibilityButton;
import com.hfstudio.diskterminal.gui.buttons.GuiTerminalStyleButton;
import com.hfstudio.diskterminal.gui.handler.GhostTarget;
import com.hfstudio.diskterminal.gui.handler.SlotAccess;
import com.hfstudio.diskterminal.gui.handler.TabManager;
import com.hfstudio.diskterminal.gui.handler.TabRenderingHandler;
import com.hfstudio.diskterminal.gui.handler.TerminalDataManager;
import com.hfstudio.diskterminal.gui.handler.TooltipHandler;
import com.hfstudio.diskterminal.gui.networktools.GuiToolConfirmationModal;
import com.hfstudio.diskterminal.gui.networktools.INetworkTool;
import com.hfstudio.diskterminal.gui.overlay.MessageHelper;
import com.hfstudio.diskterminal.gui.overlay.OverlayMessageRenderer;
import com.hfstudio.diskterminal.gui.rename.InlineRenameManager;
import com.hfstudio.diskterminal.gui.widget.tab.AbstractTabWidget;
import com.hfstudio.diskterminal.gui.widget.tab.NetworkToolsTabWidget;
import com.hfstudio.diskterminal.gui.widget.tab.SubnetOverviewTabWidget;
import com.hfstudio.diskterminal.integration.NEIIntegration;
import com.hfstudio.diskterminal.network.DiskTerminalNetwork;
import com.hfstudio.diskterminal.network.PacketHighlightBlock;
import com.hfstudio.diskterminal.network.PacketSlotLimitChange;
import com.hfstudio.diskterminal.network.PacketSubnetListRequest;
import com.hfstudio.diskterminal.network.PacketSwitchNetwork;
import com.hfstudio.diskterminal.network.PacketTabChange;
import com.hfstudio.diskterminal.network.chunked.PayloadDispatcher;
import com.hfstudio.diskterminal.network.chunked.PayloadMode;
import com.hfstudio.diskterminal.network.chunked.TerminalChannels;
import com.hfstudio.diskterminal.util.ItemStacks;

import appeng.api.implementations.items.IUpgradeModule;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.slot.AppEngSlot;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/**
 * Base GUI for Cell Terminal variants.
 * Contains shared functionality for displaying storage drives/chests with their cells.
 * Supports three tabs: Terminal (list view), Inventory (cell slots with contents), Partition (cell slots with
 * partition).
 */
public abstract class GuiCellTerminalBase extends AEBaseGui implements NetworkToolsTabWidget.NetworkToolGuiContext,
    SubnetOverviewTabWidget.SubnetOverviewContext, TabManager.TabSwitchListener {

    // Layout constants
    protected static final int ROW_HEIGHT = GuiConstants.ROW_HEIGHT;
    protected static final int MIN_ROWS = GuiConstants.MIN_ROWS;
    protected static final int DEFAULT_ROWS = GuiConstants.DEFAULT_ROWS;

    // Magic height number for tall mode calculation (header + footer heights)
    private static final int MAGIC_HEIGHT_NUMBER = GuiConstants.MAGIC_HEIGHT_NUMBER;

    // Dynamic row count (computed based on terminal style)
    protected int rowsVisible = DEFAULT_ROWS;

    // Tab management (widget lifecycle, rendering, clicking, switching)
    protected TabManager tabManager;

    // Handlers
    protected TerminalDataManager dataManager;

    // Terminal style button
    protected GuiTerminalStyleButton terminalStyleButton;

    // Filter panel manager
    protected FilterPanelManager filterPanelManager;
    protected int nextButtonId = 10; // Starting button ID for filter buttons

    // Search field and mode button
    protected MEGuiTextField searchField;
    protected GuiSearchModeButton searchModeButton;
    protected GuiSearchHelpButton searchHelpButton;
    protected GuiBackButton subnetBackButton;
    protected GuiSubnetVisibilityButton subnetVisibilityButton;
    protected SearchFilterMode currentSearchMode = SearchFilterMode.MIXED;
    protected SubnetVisibility currentSubnetVisibility = SubnetVisibility.DONT_SHOW;

    // Popup states
    protected PopupCellInventory inventoryPopup = null;
    protected PopupCellPartition partitionPopup = null;

    // Storage bus and temp area selection tracking (for quick-add keybinds)
    protected final Set<Long> selectedStorageBusIds = new HashSet<>();
    protected final Set<Integer> selectedTempCellSlots = new HashSet<>();

    // Modal search bar for expanded editing
    protected GuiModalSearchBar modalSearchBar = null;

    // Search field click handler (right-click clear, double-click modal)
    protected SearchFieldHandler searchFieldHandler = null;

    /**
     * Tracks whether AE2's NEI bookmark ghost handler consumed the current click via
     * handleMouseClick. When this is true, mouseClicked skips the activeTab.handleClick
     * call to prevent double-fire: AE2's bookmark mechanism sends an ADD_ITEM via accept()
     * using the current mouse position, while our click callback would send a conflicting
     * action (potentially REMOVE_ITEM) using the stale hoveredSlotIndex from the previous
     * frame's draw. If the mouse moved vertically between frames, these target different rows
     * in the same column, setting one slot while clearing another in a neighboring row.
     */
    private boolean ghostDropConsumedClick = false;

    // Guard to prevent style button from being retriggered while mouse is still down
    private long lastStyleButtonClickTime = 0;
    private static final long STYLE_BUTTON_COOLDOWN = 100; // ms

    // Whether we've restored the saved scroll after the first data update
    private boolean initialScrollRestored = false;

    // Subnet view state (network routing, kept here since it affects data updates)
    protected long currentNetworkId = 0; // 0 = main network, >0 = subnet ID
    // When true, we're waiting for a network switch response - ignore incoming data until confirmed
    protected boolean awaitingNetworkSwitch = false;

    // RenderItem instance for rendering items in GUI (1.7.10 doesn't have this in GuiContainer)
    protected final RenderItem itemRender = new RenderItem();

    public GuiCellTerminalBase(Container container) {
        super(container);

        this.xSize = GuiConstants.GUI_WIDTH;
        this.rowsVisible = calculateRowsCount();
        this.ySize = MAGIC_HEIGHT_NUMBER + this.rowsVisible * ROW_HEIGHT;
        this.setScrollBar(new GuiScrollbar());

        this.dataManager = new TerminalDataManager();
        this.filterPanelManager = new FilterPanelManager();

        // Load persisted settings
        DiskTerminalClientConfig config = DiskTerminalClientConfig.getInstance();
        this.tabManager = new TabManager(config.getSelectedTab(), this);
        this.currentSearchMode = config.getSearchMode();
        this.currentSubnetVisibility = config.getSubnetVisibility();

        // Restore the last viewed subnet for this client connection. ClientProxy clears the
        // saved ID on connect/disconnect so ephemeral subnet IDs do not leak across sessions.
        this.currentNetworkId = config.getLastViewedNetworkId();

        registerPayloadHandlers();
    }

    /**
     * Register chunked-payload handlers for all data channels this GUI consumes.
     * <p>
     * Handlers are registered statically in {@link PayloadDispatcher} but the registration is
     * idempotent: re-registering with the same channel name overwrites the previous binding.
     * Since only one Cell Terminal GUI can be open at a time, this is safe.
     * <p>
     * The {@code networkId} field embedded in every section payload is used to gate updates
     * during a network switch: payloads from the previous network (still in flight when the
     * client requested the switch) are discarded.
     */
    protected void registerPayloadHandlers() {
        // META: terminalPos / terminalDim / networkId. Used to confirm network-switch completion.
        PayloadDispatcher.register(TerminalChannels.META, this::onMetaPayload);

        // STORAGES / BUSES / TEMP_CELLS: data sections, gated by networkId.
        PayloadDispatcher.register(TerminalChannels.STORAGES, (mode, data) -> {
            if (!acceptForCurrentNetwork(data)) return;
            dataManager.applyStorages(mode, data);
            updateScrollbarForCurrentTab();
            restoreInitialScrollIfNeeded();
        });
        PayloadDispatcher.register(TerminalChannels.BUSES, (mode, data) -> {
            if (!acceptForCurrentNetwork(data)) return;
            dataManager.applyBuses(mode, data);
            updateScrollbarForCurrentTab();
            restoreInitialScrollIfNeeded();
        });
        PayloadDispatcher.register(TerminalChannels.TEMP_CELLS, (mode, data) -> {
            if (!acceptForCurrentNetwork(data)) return;
            dataManager.applyTempCells(mode, data);
            updateScrollbarForCurrentTab();
            restoreInitialScrollIfNeeded();
        });

        // SUBNETS: routed to the subnet overview tab widget. Not gated by networkId since the
        // subnet list is global per main grid, not per current view.
        PayloadDispatcher.register(TerminalChannels.SUBNETS, (mode, data) -> {
            tabManager.getSubnetTab()
                .applySubnetPayload(mode, data);
            updateScrollbarForCurrentTab();
        });
    }

    /**
     * Handle the META channel payload. Mirrors the network-switch verification logic that used
     * to live in the old monolithic postUpdate().
     */
    protected void onMetaPayload(PayloadMode mode, NBTTagCompound data) {
        if (data.hasKey("networkId")) {
            long incomingNetworkId = data.getLong("networkId");

            if (this.awaitingNetworkSwitch) {
                // Stale META from the old network: drop and keep waiting.
                if (incomingNetworkId != this.currentNetworkId) return;

                // Server has acknowledged the switch.
                this.awaitingNetworkSwitch = false;
            }
        }

        dataManager.applyMeta(mode, data);
    }

    /**
     * Returns true if a section payload should be applied given the current network gating state.
     * Drops payloads stamped with a different network ID than what the client is currently
     * showing (or, when awaiting a switch, anything that doesn't match the requested ID).
     */
    protected boolean acceptForCurrentNetwork(NBTTagCompound data) {
        if (!data.hasKey("networkId")) return true;
        long incoming = data.getLong("networkId");

        if (this.awaitingNetworkSwitch) return incoming == this.currentNetworkId;
        return incoming == this.currentNetworkId;
    }

    /**
     * Restore saved scroll position once we have at least one section payload.
     * Centralized so each section handler can call it without duplicating the flag logic.
     */
    protected void restoreInitialScrollIfNeeded() {
        if (this.initialScrollRestored) return;

        TabStateManager.TabType tabType = TabStateManager.TabType.fromIndex(tabManager.getCurrentTab());
        int saved = TabStateManager.getInstance()
            .getScrollPosition(tabType);
        scrollToLine(saved);
        this.initialScrollRestored = true;
    }

    /**
     * Calculate the number of rows to display based on terminal style and screen height.
     */
    protected int calculateRowsCount() {
        TerminalStyle style = DiskTerminalClientConfig.getInstance()
            .getTerminalStyle();

        if (style == TerminalStyle.SMALL) return DEFAULT_ROWS;

        // TALL mode: expand to fill available screen space
        // Use ScaledResolution to properly get the scaled screen height (handles auto GUI scale)
        ScaledResolution res = new ScaledResolution(
            Minecraft.getMinecraft(),
            Minecraft.getMinecraft().displayWidth,
            Minecraft.getMinecraft().displayHeight);
        int screenHeight = res.getScaledHeight();

        // Leave some padding at top and bottom
        int availableHeight = screenHeight - 24;
        int extraSpace = availableHeight - MAGIC_HEIGHT_NUMBER;

        return Math.max(MIN_ROWS, extraSpace / ROW_HEIGHT);
    }

    protected abstract String getGuiTitle();

    @Override
    public void initGui() {
        // NEI can close and later reopen the same GUI instance. onGuiClosed() unregisters the
        // chunked payload handlers, so re-register them here before any refresh packets are sent.
        registerPayloadHandlers();

        // Reset button ID counter on each initGui call
        this.nextButtonId = 10;

        // Recalculate rows based on screen size and terminal style
        this.rowsVisible = calculateRowsCount();
        this.ySize = MAGIC_HEIGHT_NUMBER + this.rowsVisible * ROW_HEIGHT;

        super.initGui();

        // Center GUI with appropriate spacing based on terminal style
        TerminalStyle style = DiskTerminalClientConfig.getInstance()
            .getTerminalStyle();
        int unusedSpace = this.height - this.ySize;

        if (style == TerminalStyle.SMALL) {
            // Small mode: center vertically with tab space consideration
            int tabSpace = 24;
            this.guiTop = Math.max(tabSpace, (this.height - this.ySize) / 2);
        } else if (unusedSpace < 0) {
            // Tall mode: GUI is larger than screen - push it up so bottom content is more visible
            this.guiTop = (int) Math.floor(unusedSpace / 3.8f);
        } else {
            // Tall mode: GUI fits on screen - position it to extend to the bottom with minimal margin
            int bottomMargin = 4;
            this.guiTop = this.height - this.ySize - bottomMargin;

            // Ensure there's enough space at the top for tabs (TAB_HEIGHT = 22, plus buffer)
            int tabSpace = 24;
            if (this.guiTop < tabSpace) this.guiTop = tabSpace;
        }

        this.getScrollBar()
            .setTop(18)
            .setLeft(189)
            .setHeight(this.rowsVisible * ROW_HEIGHT - 2);
        this.repositionSlots();
        initTabWidgets();
        initTerminalStyleButton();
        initSubnetBackButton();
        initFilterButtons();
        initSearchField();

        // Apply persisted filter states to data manager
        applyFiltersToDataManager();

        // Update scrollbar range for current tab
        updateScrollbarForCurrentTab();

        // Restore saved scroll position for the current tab (in-memory TabStateManager)
        TabStateManager.TabType initialTabType = TabStateManager.TabType.fromIndex(tabManager.getCurrentTab());
        int initialSavedScroll = TabStateManager.getInstance()
            .getScrollPosition(initialTabType);
        scrollToLine(initialSavedScroll);

        // We'll also re-apply the saved scroll once when data arrives, in case
        // the initial scrollbar range was limited during initGui().
        this.initialScrollRestored = false;

        // Notify server of the current tab so it can start sending appropriate data
        // This is especially important for storage bus tabs which require server polling
        DiskTerminalNetwork.INSTANCE.sendToServer(new PacketTabChange(tabManager.getCurrentTab()));

        // Send current slot limit preferences to server
        DiskTerminalClientConfig config = DiskTerminalClientConfig.getInstance();
        DiskTerminalNetwork.INSTANCE.sendToServer(
            new PacketSlotLimitChange(
                config.getCellSlotLimit()
                    .getLimit(),
                config.getBusSlotLimit()
                    .getLimit(),
                config.getSubnetSlotLimit()
                    .getLimit()));

        // If a subnet was previously being viewed, tell the server to switch to it
        // Also reset data manager to avoid showing stale data from a previous session
        if (this.currentNetworkId != 0) {
            this.awaitingNetworkSwitch = true;
            this.dataManager.resetForNetworkSwitch();
            DiskTerminalNetwork.INSTANCE.sendToServer(new PacketSwitchNetwork(this.currentNetworkId));
        }

        // Reopening the GUI directly onto the subnet overview (for example after leaving via NEI)
        // does not fire a tab-switch event, so trigger the overview enter hook manually.
        if (isInSubnetOverviewMode()) tabManager.getSubnetTab()
            .onEnterOverview();
    }

    protected void initTabWidgets() {
        // Provide scroll access so TabManager can save/restore scroll positions on tab switch
        tabManager.setScrollAccessor(new TabManager.ScrollAccessor() {

            @Override
            public int getCurrentScroll() {
                return getScrollBar().getCurrentScroll();
            }

            @Override
            public void scrollToLine(int lineIndex) {
                GuiCellTerminalBase.this.scrollToLine(lineIndex);
            }
        });

        // Delegate widget creation and initialization to the TabManager
        tabManager
            .initWidgets(this.fontRendererObj, this.itemRender, this.guiLeft, this.guiTop, this.rowsVisible, this);

        // Wire subnet context after widget init
        tabManager.getSubnetTab()
            .setSubnetContext(this);
    }

    protected void initSubnetBackButton() {
        if (this.subnetBackButton != null) this.buttonList.remove(this.subnetBackButton);

        // Position the back button at the left side of the header, before the title
        int buttonX = this.guiLeft + 4;
        int buttonY = this.guiTop + 4;
        this.subnetBackButton = new GuiBackButton(3, buttonX, buttonY);
        this.subnetBackButton.setInOverviewMode(isInSubnetOverviewMode());
        this.buttonList.add(this.subnetBackButton);
    }

    protected void initTerminalStyleButton() {
        if (this.terminalStyleButton != null) this.buttonList.remove(this.terminalStyleButton);

        // Calculate button Y like in SMALL mode (for consistent positioning across style changes)
        int smallModeYSize = MAGIC_HEIGHT_NUMBER + DEFAULT_ROWS * ROW_HEIGHT;
        int buttonY = Math.max(8, (this.height - smallModeYSize) / 2 + 8);
        this.terminalStyleButton = new GuiTerminalStyleButton(
            0,
            this.guiLeft - 18,
            buttonY,
            DiskTerminalClientConfig.getInstance()
                .getTerminalStyle());
        this.buttonList.add(this.terminalStyleButton);
    }

    protected void initFilterButtons() {
        nextButtonId = filterPanelManager.initButtons(this.buttonList, nextButtonId, tabManager.getCurrentTab());
        updateFilterButtonPositions();
    }

    protected void updateFilterButtonPositions() {
        int styleButtonY = (this.terminalStyleButton != null) ? this.terminalStyleButton.yPosition : this.guiTop + 8;
        int styleButtonBottom = styleButtonY + 16; // BUTTON_SIZE = 16
        Rectangle controlsHelpBounds = getControlsHelpBounds();
        filterPanelManager.updatePositions(
            this.guiLeft,
            this.guiTop,
            this.ySize,
            styleButtonY,
            styleButtonBottom,
            controlsHelpBounds);
    }

    protected void applyFiltersToDataManager() {
        dataManager.updateFiltersQuiet(filterPanelManager.getAllFilterStates());
    }

    protected void initSearchField() {
        // Search help button: positioned at the start of the search area
        int titleWidth = this.fontRendererObj.getStringWidth(getGuiTitle());
        int helpButtonX = 22 + titleWidth + 4;
        int helpButtonY = 5;

        if (this.searchHelpButton != null) this.buttonList.remove(this.searchHelpButton);
        this.searchHelpButton = new GuiSearchHelpButton(2, this.guiLeft + helpButtonX, this.guiTop + helpButtonY);
        this.buttonList.add(this.searchHelpButton);

        // Search field: positioned after help button
        int searchX = helpButtonX + GuiSearchHelpButton.SIZE + 2;
        int searchY = 4;
        int availableWidth = 189 - searchX;

        String existingSearch = (this.searchField != null) ? this.searchField.getText()
            : DiskTerminalClientConfig.getInstance()
                .getSearchFilter();

        this.searchField = new MEGuiTextField(availableWidth, 12) {

            @Override
            public void onTextChange(String oldText) {
                onSearchTextChanged();
            }
        };
        this.searchField.x = this.guiLeft + searchX;
        this.searchField.y = this.guiTop + searchY;
        this.searchField.setMaxStringLength(512);
        this.searchField.setText(existingSearch, true);

        if (this.searchModeButton != null) this.buttonList.remove(this.searchModeButton);

        // Search mode button: positioned above the scrollbar (top-right corner)
        this.searchModeButton = new GuiSearchModeButton(1, this.guiLeft + 189, this.guiTop + 5, currentSearchMode);
        this.buttonList.add(this.searchModeButton);
        updateSearchModeButtonVisibility();

        // Subnet visibility button: positioned next to search mode button
        // if (this.subnetVisibilityButton != null) this.buttonList.remove(this.subnetVisibilityButton);
        // TODO: enable when it's working
        // this.subnetVisibilityButton = new GuiSubnetVisibilityButton(4, this.guiLeft + 189 - 14, this.guiTop + 4,
        // currentSubnetVisibility);
        // this.buttonList.add(this.subnetVisibilityButton);

        // Initialize modal search bar and search field handler
        this.modalSearchBar = new GuiModalSearchBar(this.fontRendererObj, this.searchField, this::onSearchTextChanged);
        this.searchFieldHandler = new SearchFieldHandler(this.searchField, this.modalSearchBar);

        if (!existingSearch.isEmpty()) dataManager.setSearchFilter(existingSearch, getEffectiveSearchMode());
    }

    protected void updateSearchModeButtonVisibility() {
        if (this.searchModeButton == null) return;

        this.searchModeButton.visible = tabManager.isSearchModeButtonVisible();
    }

    protected void onSearchTextChanged() {
        SearchFilterMode effectiveMode = getEffectiveSearchMode();
        dataManager.setSearchFilter(searchField.getText(), effectiveMode);
        updateScrollbarForCurrentTab();

        // Persist the search filter text
        DiskTerminalClientConfig.getInstance()
            .setSearchFilter(searchField.getText());
    }

    /**
     * Get the effective search mode based on current tab.
     * Delegates to the TabManager.
     */
    protected SearchFilterMode getEffectiveSearchMode() {
        return tabManager.getEffectiveSearchMode(currentSearchMode);
    }

    protected void repositionSlots() {
        for (Object obj : this.inventorySlots.inventorySlots) {
            if (obj instanceof AppEngSlot) {
                AppEngSlot slot = (AppEngSlot) obj;
                slot.yDisplayPosition = this.ySize + slot.getY() - 82;
                slot.xDisplayPosition = slot.getX() + 14;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Draw popups on top
        if (inventoryPopup != null) {
            inventoryPopup.draw(mouseX, mouseY);
            drawPopupHoverTooltip(
                inventoryPopup.getHoveredTooltip(),
                inventoryPopup.getHoveredTooltipX(),
                inventoryPopup.getHoveredTooltipY());
        }

        if (partitionPopup != null) {
            partitionPopup.draw(mouseX, mouseY);
            drawPopupHoverTooltip(
                partitionPopup.getHoveredTooltip(),
                partitionPopup.getHoveredTooltipX(),
                partitionPopup.getHoveredTooltipY());
        }

        drawPopupTooltipTail(mouseX, mouseY, partialTicks);
    }

    /**
     * Render a popup's hovered-item tooltip via the GUI's (protected) tooltip helper.
     */
    private void drawPopupHoverTooltip(List<String> lines, int x, int y) {
        if (lines != null && !lines.isEmpty()) this.drawHoveringText(lines, x, y);
    }

    @Override
    public void drawHoveringText(List<String> textLines, int x, int y) {
        drawHoveringText(textLines, x, y, fontRendererObj);
    }

    @Override
    public void drawHoveringText(List<String> textLines, int x, int y, FontRenderer font) {
        if (textLines == null || textLines.isEmpty()) return;

        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        int tooltipWidth = 0;
        for (String line : textLines) {
            tooltipWidth = Math.max(tooltipWidth, font.getStringWidth(line));
        }

        int tooltipX = x + 12;
        int tooltipY = y - 12;
        int tooltipHeight = 8;
        if (textLines.size() > 1) tooltipHeight += 2 + (textLines.size() - 1) * 10;

        if (tooltipX + tooltipWidth + 4 > this.width) tooltipX = x - 16 - tooltipWidth;
        if (tooltipX < 4) tooltipX = 4;
        if (tooltipX + tooltipWidth + 4 > this.width) tooltipX = Math.max(4, this.width - tooltipWidth - 4);

        if (tooltipY + tooltipHeight + 6 > this.height) tooltipY = this.height - tooltipHeight - 6;
        if (tooltipY < 4) tooltipY = 4;

        this.zLevel = 300.0F;
        itemRender.zLevel = 300.0F;

        int backgroundColor = -267386864;
        this.drawGradientRect(
            tooltipX - 3,
            tooltipY - 4,
            tooltipX + tooltipWidth + 3,
            tooltipY - 3,
            backgroundColor,
            backgroundColor);
        this.drawGradientRect(
            tooltipX - 3,
            tooltipY + tooltipHeight + 3,
            tooltipX + tooltipWidth + 3,
            tooltipY + tooltipHeight + 4,
            backgroundColor,
            backgroundColor);
        this.drawGradientRect(
            tooltipX - 3,
            tooltipY - 3,
            tooltipX + tooltipWidth + 3,
            tooltipY + tooltipHeight + 3,
            backgroundColor,
            backgroundColor);
        this.drawGradientRect(
            tooltipX - 4,
            tooltipY - 3,
            tooltipX - 3,
            tooltipY + tooltipHeight + 3,
            backgroundColor,
            backgroundColor);
        this.drawGradientRect(
            tooltipX + tooltipWidth + 3,
            tooltipY - 3,
            tooltipX + tooltipWidth + 4,
            tooltipY + tooltipHeight + 3,
            backgroundColor,
            backgroundColor);

        int borderColor = 1347420415;
        int borderColorEnd = (borderColor & 16711422) >> 1 | borderColor & -16777216;
        this.drawGradientRect(
            tooltipX - 3,
            tooltipY - 2,
            tooltipX - 2,
            tooltipY + tooltipHeight + 2,
            borderColor,
            borderColorEnd);
        this.drawGradientRect(
            tooltipX + tooltipWidth + 2,
            tooltipY - 2,
            tooltipX + tooltipWidth + 3,
            tooltipY + tooltipHeight + 2,
            borderColor,
            borderColorEnd);
        this.drawGradientRect(
            tooltipX - 3,
            tooltipY - 3,
            tooltipX + tooltipWidth + 3,
            tooltipY - 2,
            borderColor,
            borderColor);
        this.drawGradientRect(
            tooltipX - 3,
            tooltipY + tooltipHeight + 2,
            tooltipX + tooltipWidth + 3,
            tooltipY + tooltipHeight + 3,
            borderColorEnd,
            borderColorEnd);

        for (int i = 0; i < textLines.size(); i++) {
            font.drawStringWithShadow(textLines.get(i), tooltipX, tooltipY, -1);
            if (i == 0) tooltipY += 2;
            tooltipY += 10;
        }

        this.zLevel = 0.0F;
        itemRender.zLevel = 0.0F;
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderHelper.enableStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
    }

    @Override
    public Slot getSlotUnderMouse() {
        return SlotAccess.slotUnderMouse(this);
    }

    private void drawPopupTooltipTail(int mouseX, int mouseY, float partialTicks) {

        // Draw hover preview from terminal tab widget
        // TODO: we can assume that the popups are only open in the terminal tab, as outside click or Esc closes them
        if (tabManager.getCurrentTab() == GuiConstants.TAB_TERMINAL && inventoryPopup == null
            && partitionPopup == null) {
            CellInfo previewCell = tabManager.getTerminalWidget()
                .getPreviewCell();
            int previewType = tabManager.getTerminalWidget()
                .getPreviewType();

            if (previewCell != null) {
                int previewX = mouseX + 10, previewY = mouseY + 10;
                if (previewType == 1)
                    new PopupCellInventory(this, previewCell, previewX, previewY).draw(mouseX, mouseY);
                else if (previewType == 2)
                    new PopupCellPartition(this, previewCell, previewX, previewY).draw(mouseX, mouseY);
                else if (previewType == 3) this.drawHoveringText(
                    Collections.singletonList(I18n.format("gui.disk_terminal.eject_cell")),
                    mouseX,
                    mouseY);
            }
        }

        drawTooltips(mouseX, mouseY);

        // Draw back button tooltip
        if (this.subnetBackButton != null && this.subnetBackButton.isMouseOver()) {
            List<String> tooltip = this.subnetBackButton.getTooltip();
            if (!tooltip.isEmpty()) this.drawHoveringText(tooltip, mouseX, mouseY);
        }

        // Render modal search bar on top of everything else
        if (modalSearchBar != null && modalSearchBar.isVisible()) modalSearchBar.draw(mouseX, mouseY);

        // Render network tool confirmation modal on top of everything else
        if (networkToolModal != null) networkToolModal.draw(mouseX, mouseY);

        // Render overlay messages last (on top of everything)
        OverlayMessageRenderer.render();
    }

    protected void drawTooltips(int mouseX, int mouseY) {
        int relMouseX = mouseX - guiLeft;
        int relMouseY = mouseY - guiTop;

        // Widget-based tooltips for tabs 0-5
        AbstractTabWidget activeTab = tabManager.getActiveTab();
        if (activeTab != null) {
            // Get tooltip from hovered widget row
            List<String> widgetTooltip = activeTab.getTooltip(relMouseX, relMouseY);
            if (!widgetTooltip.isEmpty()) {
                this.drawHoveringText(widgetTooltip, mouseX, mouseY);

                return;
            }

            // Get hovered item stack for vanilla item tooltip
            ItemStack hoveredStack = activeTab.getHoveredItemStack(relMouseX, relMouseY);
            if (!ItemStacks.isEmpty(hoveredStack)) {
                this.renderToolTip(hoveredStack, mouseX, mouseY);

                return;
            }
        }

        // Tab button tooltips (via TabManager)
        int hoveredTab = tabManager.getHoveredTab();
        if (hoveredTab >= 0 && inventoryPopup == null && partitionPopup == null) {
            String tabTooltip = tabManager.getTabTooltip(hoveredTab);
            if (!tabTooltip.isEmpty()) {
                this.drawHoveringText(Collections.singletonList(tabTooltip), mouseX, mouseY);

                return;
            }
        }

        // Legacy tooltip context for non-widget elements (buttons, search field)
        TooltipHandler.TooltipContext ctx = new TooltipHandler.TooltipContext();
        ctx.terminalStyleButton = terminalStyleButton;
        ctx.searchModeButton = searchModeButton;
        ctx.searchHelpButton = searchHelpButton;
        // ctx.subnetVisibilityButton = subnetVisibilityButton;
        ctx.filterPanelManager = filterPanelManager;

        // Search error state
        ctx.hasSearchError = dataManager.hasAdvancedSearchError();
        ctx.searchErrorMessage = dataManager.getAdvancedSearchError();
        if (searchField != null) {
            ctx.searchFieldX = searchField.x - 2;
            ctx.searchFieldY = searchField.y - 2;
            ctx.searchFieldWidth = searchField.w + 4;
            ctx.searchFieldHeight = searchField.h + 4;
        }

        TooltipHandler.drawTooltips(ctx, new TooltipHandler.TooltipRenderer() {

            @Override
            public void drawHoveringText(List<String> lines, int x, int y) {
                GuiCellTerminalBase.this.drawHoveringText(lines, x, y);
            }

        }, mouseX, mouseY);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRendererObj.drawString(getGuiTitle(), 22, 6, 0x404040);
        this.fontRendererObj.drawString(I18n.format("container.inventory"), 22, this.ySize - 93, 0x404040);

        // Draw search field - translate back since field uses absolute coords but we're in translated context
        if (this.searchField != null) {
            GL11.glPushMatrix();
            GL11.glTranslatef(-this.guiLeft, -this.guiTop, 0);

            // Draw red border if there's a parse error
            if (dataManager.hasAdvancedSearchError()) {
                int fx = this.searchField.x - 3;
                int fy = this.searchField.y - 3;
                int fw = this.searchField.w + 6;
                int fh = this.searchField.h + 6;
                drawRect(fx, fy, fx + fw, fy + fh, 0xFFFF0000);
            }

            this.searchField.drawTextBox();
            GL11.glPopMatrix();
        }

        int relMouseX = mouseX - offsetX;
        int relMouseY = mouseY - offsetY;
        final int currentScroll = this.getScrollBar()
            .getCurrentScroll();

        // Reset priority field visibility before rendering (fields are re-registered by headers during draw)
        PriorityFieldManager.getInstance()
            .resetVisibility();

        // Draw based on current tab using widgets
        AbstractTabWidget activeTab = tabManager.getActiveTab();
        boolean isSubnetTab = isInSubnetOverviewMode();
        if (activeTab != null) {
            activeTab.buildVisibleRows(tabManager.getActiveLines(dataManager), currentScroll);
            activeTab.draw(relMouseX, relMouseY);
        }

        // FIXME: is the guard necessary? They won't trigger unless we call them from headers
        // Priority fields are only relevant for real tabs, not subnet overview
        if (!isSubnetTab) {
            // Draw priority fields (positioned by headers during their draw pass, rendered in absolute coords)
            PriorityFieldManager pfm = PriorityFieldManager.getInstance();
            pfm.drawFieldsRelative(guiLeft, guiTop);

            // Cleanup fields for storages/buses that no longer exist in the data
            Set<Long> activeIds = new HashSet<>(
                dataManager.getStorageMap()
                    .keySet());
            activeIds.addAll(
                dataManager.getStorageBusMap()
                    .keySet());
            pfm.cleanupStaleFields(activeIds);
        }

        // Draw inline rename field overlay on top of everything else (applies to all tabs)
        InlineRenameManager.getInstance()
            .drawRenameField(this.fontRendererObj);

        // Draw controls help - delegate to tab controller
        drawControlsHelpForCurrentTab();
    }

    // Constants for controls help widget positioning
    // NEI buttons are at guiLeft - 18, with ~4px margin from screen edge
    // We position the panel to leave similar margins on both sides
    protected static final int CONTROLS_HELP_RIGHT_MARGIN = GuiConstants.CONTROLS_HELP_RIGHT_MARGIN;
    protected static final int CONTROLS_HELP_LEFT_MARGIN = GuiConstants.CONTROLS_HELP_LEFT_MARGIN;
    protected static final int CONTROLS_HELP_PADDING = GuiConstants.CONTROLS_HELP_PADDING;
    protected static final int CONTROLS_HELP_LINE_HEIGHT = GuiConstants.CONTROLS_HELP_LINE_HEIGHT;

    /**
     * Draw controls help for the current tab using the handler.
     * Works for both real tabs and subnet overlay (the active tab provides getHelpLines()).
     */
    protected void drawControlsHelpForCurrentTab() {
        int tabIndex = tabManager.getCurrentTab();

        TabRenderingHandler.ControlsHelpContext ctx = new TabRenderingHandler.ControlsHelpContext(
            this.guiLeft,
            this.guiTop,
            this.ySize,
            this.height,
            tabIndex,
            this.fontRendererObj);

        // Get help lines from the active tab widget (works for subnet tab too)
        AbstractTabWidget activeTab = tabManager.getActiveTab();
        List<String> helpLines = activeTab != null ? activeTab.getHelpLines() : Collections.emptyList();

        TabRenderingHandler.ControlsHelpResult result = TabRenderingHandler.drawControlsHelpWidget(ctx, helpLines);
        tabManager.setCachedControlsHelp(result.wrappedLines, result.cachedTab);

        // Update filter button positions after controls help bounds are known
        updateFilterButtonPositions();
    }

    /**
     * Get the bounding rectangle for the controls help widget.
     * Uses cached wrapped lines from the last render for accurate sizing.
     * Used for NEI exclusion areas.
     */
    protected Rectangle getControlsHelpBounds() {
        List<String> wrappedLines = tabManager.getCachedControlsHelpLines();
        if (wrappedLines.isEmpty()) return new Rectangle(0, 0, 0, 0);

        int lineCount = wrappedLines.size();
        int contentHeight = lineCount * CONTROLS_HELP_LINE_HEIGHT;
        int panelHeight = contentHeight + (CONTROLS_HELP_PADDING * 2);

        // Calculate panel width and position (same logic as in drawControlsHelpWidget)
        int panelWidth = Math.min(118, this.guiLeft - CONTROLS_HELP_RIGHT_MARGIN - CONTROLS_HELP_LEFT_MARGIN - 18);
        if (panelWidth < 60) panelWidth = 60;

        int panelRight = -CONTROLS_HELP_RIGHT_MARGIN - 18;
        int panelLeft = panelRight - panelWidth;

        // Position relative to screen bottom
        // Leave margin for NEI bookmarks button at screen bottom
        int bottomOffset = 28;
        int panelBottom = this.height - this.guiTop - bottomOffset;
        int panelTop = panelBottom - panelHeight;

        return new Rectangle(this.guiLeft + panelLeft, this.guiTop + panelTop, panelWidth, panelHeight);
    }

    /**
     * Get NEI exclusion areas to prevent overlap with controls help widget and filter buttons.
     */
    public List<Rectangle> getNEIExclusionArea() {
        List<Rectangle> areas = new ArrayList<>();
        Rectangle controlsHelp = getControlsHelpBounds();

        areas.add(new Rectangle(this.guiLeft, this.guiTop, this.xSize, this.ySize));

        if (controlsHelp.width > 0) areas.add(controlsHelp);

        // Add filter panel bounds
        Rectangle filterBounds = filterPanelManager.getBounds();
        if (filterBounds.width > 0) areas.add(filterBounds);

        // Add terminal style button bounds
        // TODO: move terminal style to the filterPanelManager
        if (this.terminalStyleButton != null && this.terminalStyleButton.visible) {
            areas.add(
                new Rectangle(
                    this.terminalStyleButton.xPosition,
                    this.terminalStyleButton.yPosition,
                    this.terminalStyleButton.width,
                    this.terminalStyleButton.height));
        }

        if (hasToolbox()) {
            areas.add(new Rectangle(this.guiLeft + this.xSize + 1, this.guiTop + this.ySize - 90, 68, 68));
        }

        if (inventoryPopup != null) {
            areas.add(
                new Rectangle(
                    inventoryPopup.getX() - 1,
                    inventoryPopup.getY() - 1,
                    inventoryPopup.getWidth() + 2,
                    inventoryPopup.getHeight() + 2));
        }

        if (partitionPopup != null) {
            areas.add(
                new Rectangle(
                    partitionPopup.getX() - 1,
                    partitionPopup.getY() - 1,
                    partitionPopup.getWidth() + 2,
                    partitionPopup.getHeight() + 2));
        }

        return areas;
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        Rectangle checkedArea = new Rectangle(x, y, w, h);
        for (Rectangle area : getNEIExclusionArea()) {
            if (area.width <= 0 || area.height <= 0) continue;
            if (area.intersects(checkedArea) || area.contains(x, y)) return true;
        }

        return false;
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);

        this.bindTexture("guis/newinterfaceterminal.png");

        // Draw top section
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, 18);

        // Draw middle section (repeated rows)
        for (int i = 0; i < this.rowsVisible; i++) {
            this.drawTexturedModalRect(offsetX, offsetY + 18 + i * ROW_HEIGHT, 0, 52, this.xSize, ROW_HEIGHT);
        }

        // Draw upper border for the main area (as the texture has a gap)
        drawRect(offsetX + 21, offsetY + 17, offsetX + 182, offsetY + 18, 0xFF373737);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        // Draw bottom section
        int bottomY = offsetY + 18 + this.rowsVisible * ROW_HEIGHT;
        this.drawTexturedModalRect(offsetX, bottomY, 0, 158, this.xSize, 98);

        tabManager.drawTabs(this.guiLeft, offsetX, offsetY, mouseX, mouseY, this.itemRender, this.mc);
        this.bindTexture("guis/bus.png");
        if (this.hasToolbox()) {
            this.drawTexturedModalRect(offsetX + this.xSize + 1, offsetY + this.ySize - 90, 178, 184 - 90, 68, 68);
        }
    }

    public boolean hasToolbox() {
        return ((ContainerCellTerminalBase) this.inventorySlots).hasToolbox();
    }

    @Override
    protected void handleMouseClick(Slot slot, int slotIdx, int mouseButton, int clickType) {
        // Intercept shift-clicks on upgrade items in player inventory to insert into
        // the first visible cell/bus. Delegates to the active tab widget for tab-specific logic.
        if (clickType == 1 && slot != null && slot.getHasStack()) {
            ItemStack slotStack = slot.getStack();

            if (slotStack.getItem() instanceof IUpgradeModule
                && ((IUpgradeModule) slotStack.getItem()).getType(slotStack) != null) {
                // Check if upgrade insertion is enabled
                if (DiskTerminalServerConfig.isInitialized() && !DiskTerminalServerConfig.getInstance()
                    .isUpgradeInsertEnabled()) {
                    MessageHelper.error("disk_terminal.error.upgrade_insert_disabled");

                    return;
                }

                AbstractTabWidget activeTab = tabManager.getActiveTab();
                if (activeTab != null && activeTab.handleInventorySlotShiftClick(slotStack, slot.getSlotIndex())) {
                    return;
                }
            }
        }

        // Detect when AE2's NEI bookmark ghost handler consumes the click.
        // AE2 processes bookmark drops in handleMouseClick when clicking outside real slots
        // (slot == null). If it consumed the click, the cursor item changes (cleared or replaced
        // with next bookmark). We must suppress our subsequent activeTab.handleClick to prevent
        // sending a conflicting REMOVE_ITEM for a stale hoveredSlotIndex from the previous draw.
        ItemStack cursorBefore = slot == null && mc.thePlayer.inventory.getItemStack() != null
            ? mc.thePlayer.inventory.getItemStack()
                .copy()
            : null;

        super.handleMouseClick(slot, slotIdx, mouseButton, clickType);

        ItemStack cursorAfter = mc.thePlayer.inventory.getItemStack();
        if (slot == null && cursorBefore != null && !ItemStack.areItemStacksEqual(cursorBefore, cursorAfter)) {
            ghostDropConsumedClick = true;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        // Handle modal search bar clicks first
        if (modalSearchBar != null && modalSearchBar.isVisible()) {
            if (modalSearchBar.handleMouseClick(mouseX, mouseY, mouseButton)) return;
        }

        // Handle network tool confirmation modal first (blocks all other clicks)
        if (networkToolModal != null) {
            if (networkToolModal.handleClick(mouseX, mouseY, mouseButton)) return;

            // Click outside modal cancels it
            networkToolModal = null;

            return;
        }

        // Handle inline rename: clicking outside the rename field saves and closes it
        // (does not consume the click, let it propagate to potentially start a new rename)
        InlineRenameManager.getInstance()
            .handleClickOutside(mouseX - guiLeft, mouseY - guiTop);

        // Handle search field clicks (right-click clear, double-click modal, regular focus)
        if (this.searchFieldHandler != null && this.searchFieldHandler.handleClick(mouseX, mouseY, mouseButton)) return;

        // Handle priority field clicks (only visible in certain tabs)
        if (PriorityFieldManager.getInstance()
            .handleClick(mouseX, mouseY, mouseButton)) return;

        // Handle upgrade insertion (player holding upgrade + left-click on cell/bus)
        if (mouseButton == 0 && handleWidgetUpgradeClick(mouseX, mouseY)) return;

        if (inventoryPopup != null) {
            if (inventoryPopup.handleClick(mouseX, mouseY, mouseButton)) return;

            // Click outside popup closes it
            inventoryPopup = null;

            return;
        }

        if (partitionPopup != null) {
            if (partitionPopup.handleClick(mouseX, mouseY, mouseButton)) return;

            // Keep partition editing open so NEI ghost drags and held-item marking can target it.
            return;
        }

        // Handle tab header clicks via TabManager
        if (tabManager.handleClick(mouseX, mouseY, guiLeft, guiTop)) return;

        ghostDropConsumedClick = false;

        super.mouseClicked(mouseX, mouseY, mouseButton);

        // If AE2's NEI bookmark ghost handler already processed this click (via handleMouseClick),
        // skip our widget handler to avoid sending a conflicting partition action
        if (ghostDropConsumedClick) return;

        // Delegate content area clicks to the active tab widget
        int relMouseX = mouseX - guiLeft;
        int relMouseY = mouseY - guiTop;
        AbstractTabWidget activeTab = tabManager.getActiveTab();
        if (activeTab != null) activeTab.handleClick(relMouseX, relMouseY, mouseButton);
    }

    @Override
    public void onPreSwitch(int oldTab) {
        // No special action needed here for tab transitions.
    }

    @Override
    public void onPostSwitch(int newTab) {
        getScrollBar().setRange(0, 0, 1); // Reset scrollbar
        updateScrollbarForCurrentTab(); // Set new scrollbar range
        updateSearchModeButtonVisibility(); // Show/hide search mode button
        initFilterButtons(); // Filter buttons differ per tab
        applyFiltersToDataManager(); // ^ which means we need to apply them
        onSearchTextChanged(); // Then apply the search filter

        // Update back button appearance based on whether we're in subnet overview
        if (this.subnetBackButton != null) {
            this.subnetBackButton.setInOverviewMode(isInSubnetOverviewMode());
        }

        // Only notify server of real tab changes, not subnet overlay transitions
        if (newTab >= 0) {
            DiskTerminalNetwork.INSTANCE.sendToServer(new PacketTabChange(newTab));
        }
    }

    /**
     * Handle upgrade insertion when the player is holding an upgrade item and left-clicks.
     * Delegates to the active tab widget for tab-specific logic.
     */
    protected boolean handleWidgetUpgradeClick(int mouseX, int mouseY) {
        ItemStack heldStack = mc.thePlayer.inventory.getItemStack();
        if (ItemStacks.isEmpty(heldStack)) return false;
        if (!(heldStack.getItem() instanceof IUpgradeModule)) return false;

        // Distinguish real upgrades from storage components that also implement IUpgradeModule
        if (((IUpgradeModule) heldStack.getItem()).getType(heldStack) == null) return false;

        // Check if upgrade insertion is enabled
        if (DiskTerminalServerConfig.isInitialized() && !DiskTerminalServerConfig.getInstance()
            .isUpgradeInsertEnabled()) {
            MessageHelper.error("disk_terminal.error.upgrade_insert_disabled");

            return true; // Consume click to prevent other handlers
        }

        AbstractTabWidget activeTab = tabManager.getActiveTab();
        if (activeTab == null) return false;

        boolean isShiftClick = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (isShiftClick) return activeTab.handleShiftUpgradeClick(heldStack);

        int relMouseX = mouseX - guiLeft;
        int relMouseY = mouseY - guiTop;
        Object hoveredData = activeTab.getDataForHoveredRow(relMouseX, relMouseY);

        // When hovering a content/partition row, only skip upgrade insertion if the mouse
        // is over the actual slot grid (where partition clicks take priority). If the mouse
        // is in the pre-slot area (cell icon, upgrade cards), allow the upgrade click.
        if (hoveredData instanceof CellContentRow || hoveredData instanceof StorageBusContentRow) {
            if (activeTab.isMouseOverSlotGrid(relMouseX, relMouseY)) return false;
        }

        return activeTab.handleUpgradeClick(hoveredData, heldStack, false);
    }

    public void rebuildAndUpdateScrollbar() {
        dataManager.rebuildLines();
        updateScrollbarForCurrentTab();
    }

    @Override
    protected void actionPerformed(GuiButton btn) {
        if (btn == terminalStyleButton) {
            // TODO: maybe add the guard to actionPerformed (or click) as a whole
            // Guard against repeated clicks while mouse is still down after initGui recreates buttons
            long now = System.currentTimeMillis();
            if (now - lastStyleButtonClickTime < STYLE_BUTTON_COOLDOWN) return;

            lastStyleButtonClickTime = now;
            terminalStyleButton.setStyle(
                DiskTerminalClientConfig.getInstance()
                    .cycleTerminalStyle());
            this.buttonList.clear();
            this.initGui();

            return;
        }

        if (btn == searchModeButton) {
            // Cycle search mode, persist it, and reapply filter
            currentSearchMode = searchModeButton.cycleMode();
            DiskTerminalClientConfig.getInstance()
                .setSearchMode(currentSearchMode);
            onSearchTextChanged();

            return;
        }

        if (btn == subnetBackButton) {
            handleSubnetBackButtonClick();

            return;
        }

        /*
         * if (btn == subnetVisibilityButton) {
         * // Cycle subnet visibility mode and persist it
         * currentSubnetVisibility = subnetVisibilityButton.cycleMode();
         * DiskTerminalClientConfig.getInstance().setSubnetVisibility(currentSubnetVisibility);
         * return;
         * }
         */

        // Handle filter button clicks
        if (btn instanceof GuiFilterButton) {
            GuiFilterButton filterBtn = (GuiFilterButton) btn;
            if (filterPanelManager.handleClick(filterBtn)) {
                applyFiltersToDataManager();
                rebuildAndUpdateScrollbar();

                return;
            }
        }

        // Handle slot limit button clicks
        if (btn instanceof GuiSlotLimitButton) {
            GuiSlotLimitButton slotLimitBtn = (GuiSlotLimitButton) btn;
            if (filterPanelManager.handleSlotLimitClick(slotLimitBtn)) {
                rebuildAndUpdateScrollbar();

                return;
            }
        }

        super.actionPerformed(btn);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // Handle inline rename keys (Esc cancels, Enter confirms, typing updates field)
        if (InlineRenameManager.getInstance()
            .handleKey(typedChar, keyCode)) return;

        // Handle network tool confirmation modal (blocks all other input)
        if (networkToolModal != null) {
            if (networkToolModal.handleKeyTyped(keyCode)) return;
        }

        // Handle modal search bar keyboard
        if (modalSearchBar != null && modalSearchBar.isVisible()) {
            if (modalSearchBar.handleKeyTyped(typedChar, keyCode)) return;
        }

        // Handle priority field keyboard
        if (PriorityFieldManager.getInstance()
            .handleKeyTyped(typedChar, keyCode)) return;

        // Esc key should close modals
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (inventoryPopup != null) {
                inventoryPopup = null;
                return;
            }

            if (partitionPopup != null) {
                partitionPopup = null;
                return;
            }

            if (this.searchField != null && this.searchField.isFocused()) {
                this.searchField.setFocused(false);
                return;
            }
        }

        // Handle search field keyboard input
        if (this.searchField != null && this.searchField.textboxKeyTyped(typedChar, keyCode)) return;

        if (handleNEIVirtualStackKey(keyCode)) return;

        // Delegate key handling to the active tab widget via TabManager
        if (tabManager.handleKey(keyCode)) return;

        // Toggle subnet overview: available everywhere and should not take priority over other handlers
        if (KeyBindings.SUBNET_OVERVIEW_TOGGLE.isActiveAndMatches(keyCode)) {
            handleSubnetBackButtonClick();
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    private boolean handleNEIVirtualStackKey(int keyCode) {
        if (!NEIIntegration.isModLoaded()) return false;
        if (keyCode != Keyboard.KEY_R && keyCode != Keyboard.KEY_U) return false;

        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int mouseX = Mouse.getX() * resolution.getScaledWidth() / mc.displayWidth;
        int mouseY = resolution.getScaledHeight() - Mouse.getY() * resolution.getScaledHeight() / mc.displayHeight - 1;
        ItemStack hoveredStack = getVirtualHoveredItemStack(mouseX, mouseY);
        if (ItemStacks.isEmpty(hoveredStack)) return false;

        return keyCode == Keyboard.KEY_R ? NEIIntegration.showRecipe(hoveredStack)
            : NEIIntegration.showUsage(hoveredStack);
    }

    /**
     * Scroll to a specific line index.
     */
    public void scrollToLine(int lineIndex) {
        int currentScroll = this.getScrollBar()
            .getCurrentScroll();

        // wheel() clamps delta to -1/+1, so we need to call it multiple times
        // Positive delta in wheel() scrolls up (towards 0), negative scrolls down
        while (currentScroll != lineIndex) {
            int delta = currentScroll < lineIndex ? -1 : 1;
            this.getScrollBar()
                .wheel(delta);
            int newScroll = this.getScrollBar()
                .getCurrentScroll();
            if (newScroll == currentScroll) break;
            currentScroll = newScroll;
        }
    }

    public void postUpdate(NBTTagCompound data) {
        updateScrollbarForCurrentTab();
        restoreInitialScrollIfNeeded();
    }
    // All subnet interaction logic now lives in SubnetOverviewTabWidget.
    // These methods implement SubnetOverviewContext and provide thin wrappers for the GUI.

    /**
     * Handle subnet list update from server.
     * Delegates to the subnet tab widget for parsing and display.
     */
    public void handleSubnetListUpdate(NBTTagCompound data) {
        tabManager.getSubnetTab()
            .handleSubnetListUpdate(data);
        updateScrollbarForCurrentTab();
    }

    /**
     * Check if currently in subnet overview mode.
     */
    public boolean isInSubnetOverviewMode() {
        return tabManager.getCurrentTab() == GuiConstants.TAB_SUBNETS;
    }

    /**
     * Handle back button click - toggle subnet overview mode.
     */
    protected void handleSubnetBackButtonClick() {
        if (isInSubnetOverviewMode()) {
            // Exiting subnet overview: return to previous tab and refresh network data
            tabManager.switchToTab(tabManager.getPreviousRealTab());
            switchToNetwork(currentNetworkId);
        } else {
            // Entering subnet overview
            tabManager.switchToTab(GuiConstants.TAB_SUBNETS);
        }
    }

    @Override
    public void switchToNetwork(long networkId) {
        this.currentNetworkId = networkId;
        DiskTerminalClientConfig.getInstance()
            .setLastViewedNetworkId(networkId);

        // Exit subnet overview if it was active (e.g. clicking a Load button in overview)
        if (isInSubnetOverviewMode()) tabManager.switchToTab(tabManager.getPreviousRealTab());

        // Reset data manager so the next update does a full rebuild with proper filters
        // instead of using snapshots from the old network context
        this.dataManager.resetForNetworkSwitch();

        // Update back button state - now we're in normal view, not overview
        if (this.subnetBackButton != null) this.subnetBackButton.setInOverviewMode(false);

        // Tell server to switch network context
        DiskTerminalNetwork.INSTANCE.sendToServer(new PacketSwitchNetwork(networkId));
    }

    @Override
    public void requestSubnetList() {
        DiskTerminalNetwork.INSTANCE.sendToServer(new PacketSubnetListRequest());
    }

    @Override
    public void onGuiClosed() {
        // Persist the current scroll position for the active tab so it is restored when the GUI is reopened.
        tabManager.saveCurrentScrollPosition();
        DiskTerminalClientConfig.getInstance()
            .setLastViewedNetworkId(this.currentNetworkId);

        // Unregister our chunked-payload handlers so any straggling packets after the GUI closes
        // are dropped instead of being applied to a stale data manager / tab widget.
        PayloadDispatcher.unregister(TerminalChannels.META);
        PayloadDispatcher.unregister(TerminalChannels.STORAGES);
        PayloadDispatcher.unregister(TerminalChannels.BUSES);
        PayloadDispatcher.unregister(TerminalChannels.TEMP_CELLS);
        PayloadDispatcher.unregister(TerminalChannels.SUBNETS);

        super.onGuiClosed();
    }

    protected void updateScrollbarForCurrentTab() {
        List<Object> lines = tabManager.getActiveLines(dataManager);
        int lineCount = lines.size();

        // Use the tab widget's visible item count (accounts for non-standard row heights)
        int visibleItems = tabManager.getActiveVisibleItemCount();

        this.getScrollBar()
            .setRange(0, Math.max(0, lineCount - visibleItems), 1);
    }

    // NEI Ghost Ingredient support (NEI drag-and-drop)

    /**
     * NEI drag-and-drop handler. Dispatches to our GhostTarget widgets (partition popups and tab widgets).
     * Without this override, NEI drags fall through to AEBaseGui's default handler, which only handles
     * SlotFake slots and returns false for anything else — causing the GUI to close on unhandled drags.
     */
    @Override
    public boolean handleDragNDrop(GuiContainer gui, int mouseX, int mouseY, ItemStack draggedStack, int button) {
        // Check partition popups first (they overlay tabs)
        List<GhostTarget<?>> targets = getPhantomTargets(draggedStack);

        for (GhostTarget<?> target : targets) {
            Rectangle area = target.getArea();
            if (area.contains(mouseX, mouseY)) {
                // GhostTarget<Object> accepts any ingredient type; cast to raw to call accept
                ((GhostTarget) target).accept(draggedStack);
                return true;
            }
        }

        // Fall through to AEBaseGui's SlotFake handler
        return super.handleDragNDrop(gui, mouseX, mouseY, draggedStack, button);
    }

    public List<GhostTarget<?>> getPhantomTargets(Object ingredient) {
        if (partitionPopup != null) return partitionPopup.getGhostTargets();

        AbstractTabWidget activeTab = tabManager.getActiveTab();
        if (activeTab == null) return Collections.emptyList();

        return activeTab.getPhantomTargets(ingredient);
    }

    // Accessors for popups and renderers

    public Map<Long, StorageInfo> getStorageMap() {
        return dataManager.getStorageMap();
    }

    /**
     * Create a ToolContext for the Network Tools tab.
     */
    public INetworkTool.ToolContext createNetworkToolContext() {
        return new INetworkTool.ToolContext(
            dataManager.getStorageMap(),
            dataManager.getStorageBusMap(),
            filterPanelManager.getAllFilterStates(),
            getEffectiveSearchMode(),
            new INetworkTool.NetworkToolCallback() {

                @Override
                public void showError(String message) {
                    MessageHelper.error(message);
                }

                @Override
                public void showSuccess(String message) {
                    MessageHelper.success(message);
                }
            },
            dataManager.getSearchFilter(),
            dataManager.isUsingAdvancedSearch(),
            dataManager.getAdvancedMatcher());
    }

    // Network Tools modal support
    protected GuiToolConfirmationModal networkToolModal = null;

    /**
     * Show the confirmation modal for a network tool.
     */
    public void showNetworkToolConfirmation(INetworkTool tool) {
        INetworkTool.ToolContext ctx = createNetworkToolContext();
        String error = tool.getExecutionError(ctx);
        if (error != null) {
            MessageHelper.error(error);

            return;
        }

        networkToolModal = new GuiToolConfirmationModal(
            tool,
            ctx,
            this.fontRendererObj,
            this.width,
            this.height,
            () -> {
                tool.execute(ctx);
                networkToolModal = null;
            },
            () -> networkToolModal = null);
    }

    @Override
    public TerminalDataManager getDataManager() {
        return dataManager;
    }

    @Override
    public ItemStack getHeldStack() {
        return mc.thePlayer.inventory.getItemStack();
    }

    @Override
    public boolean isShiftDown() {
        return isShiftKeyDown();
    }

    @Override
    public void sendPacket(Object packet) {
        DiskTerminalNetwork.INSTANCE.sendToServer((IMessage) packet);
    }

    @Override
    public void openInventoryPopup(CellInfo cell) {
        inventoryPopup = new PopupCellInventory(this, cell, 0, 0);
    }

    @Override
    public void openPartitionPopup(CellInfo cell) {
        partitionPopup = new PopupCellPartition(this, cell, 0, 0);
    }

    @Override
    public void showError(String translationKey, Object... args) {
        MessageHelper.error(translationKey, args);
    }

    @Override
    public void showSuccess(String translationKey, Object... args) {
        MessageHelper.success(translationKey, args);
    }

    @Override
    public void showWarning(String translationKey, Object... args) {
        MessageHelper.warning(translationKey, args);
    }

    @Override
    public void highlightInWorld(BlockPos pos, int dimension, String displayName) {
        if (pos == null || pos.equals(new BlockPos(0, 0, 0))) return;

        // Check if in different dimension
        if (dimension != Minecraft.getMinecraft().thePlayer.dimension) {
            MessageHelper.error("disk_terminal.error.different_dimension");
            return;
        }

        DiskTerminalNetwork.INSTANCE.sendToServer(new PacketHighlightBlock(pos, dimension));

        // Send green chat message with block name and coordinates
        MessageHelper.success("gui.disk_terminal.highlighted", pos.getX(), pos.getY(), pos.getZ(), displayName);
    }

    @Override
    public void highlightCellInWorld(CellInfo cell) {
        if (cell == null) return;

        // Find the parent storage to get its position
        StorageInfo storage = dataManager.findStorageForCell(cell);
        if (storage == null) return;

        // Use storage name (block name) since we're highlighting the block, not the cell
        highlightInWorld(storage.getPos(), storage.getDimension(), storage.getName());
    }

    @Override
    public Set<Long> getSelectedStorageBusIds() {
        return selectedStorageBusIds;
    }

    @Override
    public Set<Integer> getSelectedTempCellSlots() {
        return selectedTempCellSlots;
    }

    /**
     * Get the ItemStack under the mouse from our virtual slot widgets.
     * Used by the NEI plugin to support recipe/usage lookups (R/U keybinds)
     * on items displayed in virtual slots that NEI cannot detect normally.
     *
     * @param screenMouseX Mouse X in screen coordinates
     * @param screenMouseY Mouse Y in screen coordinates
     * @return The hovered ItemStack, or null if none
     */
    public ItemStack getVirtualHoveredItemStack(int screenMouseX, int screenMouseY) {
        AbstractTabWidget activeTab = tabManager != null ? tabManager.getActiveTab() : null;
        if (activeTab == null) return null;

        int relMouseX = screenMouseX - guiLeft;
        int relMouseY = screenMouseY - guiTop;

        return activeTab.getHoveredItemStack(relMouseX, relMouseY);
    }
}
