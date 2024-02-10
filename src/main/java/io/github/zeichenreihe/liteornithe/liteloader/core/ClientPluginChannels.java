package io.github.zeichenreihe.liteornithe.liteloader.core;

import io.github.zeichenreihe.liteornithe.liteloader.PluginChannelListener;

import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.network.PacketByteBuf;

public abstract class ClientPluginChannels extends PluginChannels<PluginChannelListener> {
    protected static ClientPluginChannels instance;

    protected ClientPluginChannels() {
        if (instance != null) throw new IllegalStateException("only one instance of ClientPluginChannels is allowed.");
        instance = this;
    }

    public abstract void onPluginChannelMessage(CustomPayloadS2CPacket customPayload);

    protected abstract boolean send(String channel, PacketByteBuf data, ChannelPolicy policy);

    public static boolean sendMessage(String channel, PacketByteBuf data, ChannelPolicy policy) {
        if (instance == null) return false;
        return instance.send(channel, data, policy);
    }
}
