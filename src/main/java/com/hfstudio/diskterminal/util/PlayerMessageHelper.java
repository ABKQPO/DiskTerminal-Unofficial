package com.hfstudio.diskterminal.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import com.hfstudio.diskterminal.gui.overlay.MessageType;
import com.hfstudio.diskterminal.network.DiskTerminalNetwork;
import com.hfstudio.diskterminal.network.PacketPlayerFeedback;

/**
 * Server-safe helper for sending feedback messages to players.
 * <p>
 * This class never calls client-only rendering directly. Instead, it forwards
 * feedback via packets so the client can render messages through MessageHelper,
 * preserving overlay behavior while GUIs are open.
 */
public final class PlayerMessageHelper {

    private PlayerMessageHelper() {}

    /**
     * Sends a success message (green, localized key).
     */
    public static void success(EntityPlayer player, String translationKey, Object... args) {
        send(player, new PacketPlayerFeedback(MessageType.SUCCESS, translationKey, args));
    }

    /**
     * Sends an error message (red, localized key).
     */
    public static void error(EntityPlayer player, String translationKey, Object... args) {
        send(player, new PacketPlayerFeedback(MessageType.ERROR, translationKey, args));
    }

    /**
     * Sends a warning message (yellow, localized key).
     */
    public static void warning(EntityPlayer player, String translationKey, Object... args) {
        send(player, new PacketPlayerFeedback(MessageType.WARNING, translationKey, args));
    }

    /**
     * Sends a raw (non-localized) message with color formatting.
     */
    public static void successRaw(EntityPlayer player, String message) {
        send(player, new PacketPlayerFeedback(MessageType.SUCCESS, message));
    }

    /**
     * Sends a raw (non-localized) error message.
     */
    public static void errorRaw(EntityPlayer player, String message) {
        send(player, new PacketPlayerFeedback(MessageType.ERROR, message));
    }

    /**
     * Sends a raw (non-localized) warning message.
     */
    public static void warningRaw(EntityPlayer player, String message) {
        send(player, new PacketPlayerFeedback(MessageType.WARNING, message));
    }

    private static void send(EntityPlayer player, PacketPlayerFeedback packet) {
        if (!(player instanceof EntityPlayerMP)) return;

        DiskTerminalNetwork.INSTANCE.sendTo(packet, (EntityPlayerMP) player);
    }
}
