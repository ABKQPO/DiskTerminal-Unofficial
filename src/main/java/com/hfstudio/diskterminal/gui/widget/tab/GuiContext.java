package com.hfstudio.diskterminal.gui.widget.tab;

import java.util.Set;

import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.hfstudio.diskterminal.client.CellInfo;
import com.hfstudio.diskterminal.gui.handler.TerminalDataManager;

/**
 * Minimal interface that the parent GUI provides to tab widgets.
 * <p>
 * Tab widgets use this to communicate upward to the GUI for actions that require GUI-level state
 * (scrollbar, popups, modals, network packets), keeping the per-tab API small.
 */
public interface GuiContext {

    // ---- Data access ----

    /** Get the terminal data manager for accessing cell/bus/line data. */
    TerminalDataManager getDataManager();

    /** Get the item stack currently held by the player's cursor. */
    ItemStack getHeldStack();

    /** Get the slot under the mouse cursor, or null if none. */
    Slot getSlotUnderMouse();

    /** Whether shift key is held. */
    boolean isShiftDown();

    // ---- Network packet helpers ----

    /** Send a packet to the server. */
    void sendPacket(Object packet);

    // ---- GUI-level actions ----

    /** Trigger a full rebuild of lines and scrollbar update. */
    void rebuildAndUpdateScrollbar();

    /** Scroll to a specific line index. */
    void scrollToLine(int lineIndex);

    /** Open an inventory preview popup for a cell. */
    void openInventoryPopup(CellInfo cell);

    /** Open a partition preview popup for a cell. */
    void openPartitionPopup(CellInfo cell);

    /** Show an overlay error message. */
    void showError(String translationKey, Object... args);

    /** Show an overlay success message. */
    void showSuccess(String translationKey, Object... args);

    /** Show an overlay warning message. */
    void showWarning(String translationKey, Object... args);

    // ---- Highlight in world ----

    /**
     * Highlight a block position in the world (double-click on headers).
     */
    void highlightInWorld(BlockPos pos, int dimension, String displayName);

    /**
     * Highlight a cell's parent storage in the world (double-click on cells).
     */
    void highlightCellInWorld(CellInfo cell);

    // ---- Selection state (for multi-select keybinds) ----

    /** Get the set of selected storage bus IDs. */
    Set<Long> getSelectedStorageBusIds();

    /** Get the set of selected temp cell slot indices. */
    Set<Integer> getSelectedTempCellSlots();
}
