package io.github.zeichenreihe.liteornithe.liteloader;

import com.mojang.realmsclient.dto.RealmsServer;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.network.handler.PacketHandler;
import net.minecraft.network.packet.s2c.play.LoginS2CPacket;

public interface JoinGameListener extends LiteMod {
    void onJoinGame(PacketHandler netHandler, LoginS2CPacket loginPacket, ServerListEntry serverData,
            RealmsServer realmsServer);
}
