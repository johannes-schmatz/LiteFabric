package io.github.zeichenreihe.liteornithe.liteloader;

import net.minecraft.network.PacketByteBuf;

public interface PluginChannelListener extends LiteMod, CommonPluginChannelListener {
    void onCustomPayload(String channel, PacketByteBuf data);
}