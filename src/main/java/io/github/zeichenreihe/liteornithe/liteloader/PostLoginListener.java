package io.github.zeichenreihe.liteornithe.liteloader;

import net.minecraft.network.handler.PacketHandler;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;

public interface PostLoginListener extends LiteMod {
    void onPostLogin(PacketHandler packetListener, LoginSuccessS2CPacket loginPacket);
}
