package de.skyrising.litefabric.liteloader;

import net.minecraft.network.handler.PacketHandler;
import net.minecraft.network.packet.s2c.play.LoginS2CPacket;

public interface PreJoinGameListener extends LiteMod {
    boolean onPreJoinGame(PacketHandler packetHandler, LoginS2CPacket joinPacket);
}
