package com.hfstudio.diskterminal.gui.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

/**
 * Utility for sending messages to both the chat and the GUI overlay.
 * <p>
 * Messages are:
 * 1. Sent to chat (for history and when GUI is closed)
 * 2. Displayed in the overlay (for immediate visual feedback)
 * <p>
 * This ensures players get both persistent feedback (chat) and
 * immediate visual feedback (overlay) with appropriate colors.
 */
public final class MessageHelper {

    private MessageHelper() {}

    /**
     * Sends a success message (green).
     *
     * @param translationKey The localization key
     * @param args           Optional format arguments
     */
    public static void success(String translationKey, Object... args) {
        send(translationKey, MessageType.SUCCESS, EnumChatFormatting.GREEN, args);
    }

    /**
     * Sends an error message (red).
     *
     * @param translationKey The localization key
     * @param args           Optional format arguments
     */
    public static void error(String translationKey, Object... args) {
        send(translationKey, MessageType.ERROR, EnumChatFormatting.RED, args);
    }

    /**
     * Sends a warning message (yellow).
     *
     * @param translationKey The localization key
     * @param args           Optional format arguments
     */
    public static void warning(String translationKey, Object... args) {
        send(translationKey, MessageType.WARNING, EnumChatFormatting.YELLOW, args);
    }

    /**
     * Sends a raw string message (not localized) as an error.
     *
     * @param message The message text
     */
    public static void errorRaw(String message) {
        sendRaw(message, MessageType.ERROR, EnumChatFormatting.RED);
    }

    /**
     * Sends a raw string message (not localized) as a success.
     *
     * @param message The message text
     */
    public static void successRaw(String message) {
        sendRaw(message, MessageType.SUCCESS, EnumChatFormatting.GREEN);
    }

    /**
     * Sends a raw string message (not localized) as a warning.
     *
     * @param message The message text
     */
    public static void warningRaw(String message) {
        sendRaw(message, MessageType.WARNING, EnumChatFormatting.YELLOW);
    }

    /**
     * Sends a message using an existing IChatComponent (for compatibility).
     * The component is sent to chat, and its unformatted text is sent to overlay.
     *
     * @param component The text component to send
     * @param type      The message type for overlay coloring
     */
    public static void sendComponent(IChatComponent component, MessageType type) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;

        // Send to chat
        player.addChatMessage(component);

        // Send to overlay using unformatted text
        OverlayMessageRenderer.setMessage(component.getUnformattedText(), type);
    }

    private static void send(String translationKey, MessageType type, EnumChatFormatting chatColor, Object... args) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;

        // Create colored chat message
        ChatComponentTranslation chatMessage = new ChatComponentTranslation(translationKey, args);
        chatMessage.setChatStyle(new ChatStyle().setColor(chatColor));
        player.addChatMessage(chatMessage);

        // Create overlay message using localized text
        String overlayText = StatCollector.translateToLocalFormatted(translationKey, args);
        OverlayMessageRenderer.setMessage(overlayText, type);
    }

    private static void sendRaw(String message, MessageType type, EnumChatFormatting chatColor) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;

        // Create colored chat message
        ChatComponentText chatMessage = new ChatComponentText(message);
        chatMessage.setChatStyle(new ChatStyle().setColor(chatColor));
        player.addChatMessage(chatMessage);

        // Send to overlay
        OverlayMessageRenderer.setMessage(message, type);
    }
}
