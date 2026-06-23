package com.hfstudio.diskterminal.gui.handler;

import java.lang.reflect.Field;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

/**
 * Reads the slot currently under the mouse from a {@link GuiContainer}.
 * <p>
 * The {@code theSlot} field's access modifier varies between the 1.7.10 MC variants on the
 * dependency classpath, so it is read reflectively and cached. Returns null on any failure.
 */
public final class SlotAccess {

    private static Field theSlotField;
    private static boolean resolved;

    private SlotAccess() {}

    public static Slot slotUnderMouse(GuiContainer gui) {
        try {
            if (!resolved) {
                resolved = true;
                theSlotField = GuiContainer.class.getDeclaredField("theSlot");
                theSlotField.setAccessible(true);
            }
            if (theSlotField == null) return null;

            return (Slot) theSlotField.get(gui);
        } catch (Exception e) {
            return null;
        }
    }
}
