package com.hfstudio.diskterminal.network;

import net.minecraft.client.Minecraft;
import net.minecraft.util.IChatComponent;

import com.hfstudio.diskterminal.gui.overlay.MessageHelper;
import com.hfstudio.diskterminal.gui.overlay.MessageType;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server -> Client packet for user-facing feedback messages.
 * <p>
 * This allows server-side code to display messages via the existing GUI overlay
 * and chat path on the client, without referencing client-only classes server-side.
 * <p>
 * Arguments are transmitted as strings or serialized chat components and applied client-side during localization.
 */
public class PacketPlayerFeedback implements IMessage {

    private MessageType type = MessageType.ERROR;
    private boolean raw;
    private String keyOrMessage = "";
    private String[] args = new String[0];
    private boolean[] componentArgs = new boolean[0];

    public PacketPlayerFeedback() {}

    public PacketPlayerFeedback(MessageType type, String translationKey, Object... args) {
        this.type = type;
        this.raw = false;
        this.keyOrMessage = translationKey == null ? "" : translationKey;
        encodeArgs(args);
    }

    public PacketPlayerFeedback(MessageType type, String rawMessage) {
        this.type = type;
        this.raw = true;
        this.keyOrMessage = rawMessage == null ? "" : rawMessage;
        this.args = new String[0];
        this.componentArgs = new boolean[0];
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.raw = buf.readBoolean();

        int typeOrdinal = buf.readInt();
        MessageType[] values = MessageType.values();
        this.type = typeOrdinal >= 0 && typeOrdinal < values.length ? values[typeOrdinal] : MessageType.ERROR;

        this.keyOrMessage = ByteBufUtils.readUTF8String(buf);

        int argCount = buf.readInt();
        if (argCount < 0) {
            this.args = new String[0];

            return;
        }

        this.args = new String[argCount];
        this.componentArgs = new boolean[argCount];
        for (int i = 0; i < argCount; i++) {
            this.componentArgs[i] = buf.readBoolean();
            this.args[i] = ByteBufUtils.readUTF8String(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(this.raw);
        buf.writeInt(this.type.ordinal());
        ByteBufUtils.writeUTF8String(buf, this.keyOrMessage);
        buf.writeInt(this.args.length);

        for (int i = 0; i < this.args.length; i++) {
            boolean componentArg = i < this.componentArgs.length && this.componentArgs[i];
            buf.writeBoolean(componentArg);
            ByteBufUtils.writeUTF8String(buf, this.args[i] == null ? "" : this.args[i]);
        }
    }

    private void encodeArgs(Object[] values) {
        if (values == null || values.length == 0) {
            this.args = new String[0];
            this.componentArgs = new boolean[0];

            return;
        }

        this.args = new String[values.length];
        this.componentArgs = new boolean[values.length];
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (value instanceof IChatComponent component) {
                this.args[i] = IChatComponent.Serializer.func_150696_a(component);
                this.componentArgs[i] = true;
            } else {
                this.args[i] = (value == null) ? "null" : String.valueOf(value);
                this.componentArgs[i] = false;
            }
        }
    }

    private Object[] decodeArgs() {
        if (this.args.length == 0) return new Object[0];

        Object[] decoded = new Object[this.args.length];
        for (int i = 0; i < this.args.length; i++) {
            if (i < this.componentArgs.length && this.componentArgs[i]) {
                decoded[i] = decodeComponent(this.args[i]);
            } else {
                decoded[i] = this.args[i];
            }
        }

        return decoded;
    }

    private static Object decodeComponent(String json) {
        try {
            return IChatComponent.Serializer.func_150699_a(json);
        } catch (RuntimeException ignored) {
            return json == null ? "" : json;
        }
    }

    public static class Handler implements IMessageHandler<PacketPlayerFeedback, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketPlayerFeedback message, MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    if (message.raw) {
                        switch (message.type) {
                            case SUCCESS -> MessageHelper.successRaw(message.keyOrMessage);
                            case WARNING -> MessageHelper.warningRaw(message.keyOrMessage);
                            default -> MessageHelper.errorRaw(message.keyOrMessage);
                        }

                        return;
                    }

                    Object[] castArgs = message.decodeArgs();
                    switch (message.type) {
                        case SUCCESS -> MessageHelper.success(message.keyOrMessage, castArgs);
                        case WARNING -> MessageHelper.warning(message.keyOrMessage, castArgs);
                        default -> MessageHelper.error(message.keyOrMessage, castArgs);
                    }
                });

            return null;
        }
    }
}
