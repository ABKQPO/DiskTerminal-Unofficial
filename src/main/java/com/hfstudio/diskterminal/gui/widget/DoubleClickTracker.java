package com.hfstudio.diskterminal.gui.widget;

import com.hfstudio.diskterminal.gui.GuiConstants;

/**
 * Centralized double-click tracking for widgets that are recreated each frame.
 * <p>
 * Since widgets (headers, lines) are recreated in {@code buildVisibleRows()} every frame,
 * storing {@code lastClickTime} in the widget instance doesn't work - the state is lost
 * when a new widget object is created. This static tracker stores the last click time and
 * target ID, allowing widgets to detect double-clicks across instance recreations.
 */
public final class DoubleClickTracker {

    private DoubleClickTracker() {}

    private static long lastClickTargetId = -1;
    private static long lastClickTime = 0;

    /**
     * Check if the current click is a double-click on the given target.
     *
     * @param targetId A unique identifier for the click target (storage ID, cell slot, etc.)
     * @return true if this is a double-click, false otherwise
     */
    public static boolean isDoubleClick(long targetId) {
        long currentTime = System.currentTimeMillis();

        if (lastClickTargetId == targetId && currentTime - lastClickTime < GuiConstants.DOUBLE_CLICK_TIME_MS) {
            lastClickTargetId = -1;
            lastClickTime = 0;
            return true;
        }

        lastClickTargetId = targetId;
        lastClickTime = currentTime;
        return false;
    }

    public static long storageTargetId(long storageId) {
        return storageId;
    }

    public static long cellTargetId(long storageId, int slot) {
        return (storageId << 8) | (slot & 0xFF);
    }

    public static long storageBusTargetId(long busId) {
        return -busId - 1;
    }

    public static long subnetTargetId(long subnetId) {
        return Long.MIN_VALUE / 2 - subnetId;
    }

    public static void reset() {
        lastClickTargetId = -1;
        lastClickTime = 0;
    }
}
