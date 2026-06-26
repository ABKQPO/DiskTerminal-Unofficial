package com.hfstudio.diskterminal.gui.widget.tab;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import com.hfstudio.diskterminal.gui.handler.TerminalDataManager;
import com.hfstudio.diskterminal.gui.networktools.INetworkTool;
import com.hfstudio.diskterminal.gui.networktools.NetworkToolRegistry;
import com.hfstudio.diskterminal.gui.widget.IWidget;
import com.hfstudio.diskterminal.gui.widget.NetworkToolRowWidget;

import appeng.api.AEApi;

/**
 * Tab widget for the Network Tools tab (Tab 6).
 * <p>
 * Displays a scrollable list of network tools, each rendered as an independent
 * {@link NetworkToolRowWidget}. Each row is 36px tall (2x the standard 18px row height),
 * so this tab overrides scroll calculations accordingly.
 * <p>
 * Tools come from {@link NetworkToolRegistry} rather than the data manager.
 * The tool context (filters, storages) is fresh-fetched from the GUI context each frame.
 */
public class NetworkToolsTabWidget extends AbstractTabWidget {

    /** Cached tab icon (lazy initialized) */
    private ItemStack tabIcon = null;

    public NetworkToolsTabWidget(FontRenderer fontRenderer, RenderItem itemRender) {
        super(fontRenderer, itemRender);
    }

    @Override
    protected int getRowStep(List<?> lines, int index) {
        return NetworkToolRowWidget.ROW_HEIGHT;
    }

    @Override
    protected void propagateTreeLines(List<?> allLines, int scrollOffset) {
        bottomContinuationFromY = -1;
    }

    @Override
    protected IWidget createRowWidget(Object lineData, int y, List<?> allLines, int lineIndex) {
        if (!(lineData instanceof INetworkTool tool)) return null;

        NetworkToolRowWidget row = new NetworkToolRowWidget(tool, y, fontRenderer, itemRender);

        // Wire up context supplier. Lazily fetches tool context each frame
        row.setContextSupplier(
            () -> guiContext != null ? ((NetworkToolGuiContext) guiContext).createNetworkToolContext() : null);

        // Wire up run button click → show confirmation modal
        row.setOnRunClicked(() -> {
            if (guiContext != null) {
                ((NetworkToolGuiContext) guiContext).showNetworkToolConfirmation(tool);
            }
        });

        return row;
    }

    @Override
    protected boolean isContentLine(List<?> allLines, int index) {
        // All lines are tools (no headers/content distinction)
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object> getLines(TerminalDataManager dataManager) {
        List<INetworkTool> tools = NetworkToolRegistry.getAllTools();

        return (List<Object>) (List<?>) tools;
    }

    @Override
    public boolean showSearchModeButton() {
        return true;
    }

    @Override
    public List<String> getHelpLines() {
        List<String> lines = new ArrayList<>();

        lines.add(I18n.format("gui.disk_terminal.networktools.warning.caution"));
        lines.add(I18n.format("gui.disk_terminal.networktools.warning.irreversible"));
        lines.add("");
        lines.add(I18n.format("gui.disk_terminal.networktools.help.read_tooltip"));

        return lines;
    }

    @Override
    public ItemStack getTabIcon() {
        if (tabIcon == null) {
            tabIcon = AEApi.instance()
                .definitions()
                .items()
                .networkTool()
                .maybeStack(1)
                .orNull();
        }

        return tabIcon;
    }

    @Override
    public String getTabTooltip() {
        return I18n.format("gui.disk_terminal.tab.network_tools.tooltip");
    }

    /**
     * Extended GUI context interface for network tools.
     * The parent GUI (GuiCellTerminalBase) implements this to provide
     * tool-specific callbacks that the tab widget needs.
     */
    public interface NetworkToolGuiContext extends GuiContext {

        /** Create a fresh ToolContext for tool preview and execution. */
        INetworkTool.ToolContext createNetworkToolContext();

        /** Show the confirmation modal for a network tool. */
        void showNetworkToolConfirmation(INetworkTool tool);
    }
}
