package de.skyrising.litefabric.liteloader;

import net.minecraft.client.Minecraft;

public interface Tickable extends LiteMod {
    void onTick(Minecraft client, float partialTicks, boolean inGame, boolean clock);
}
