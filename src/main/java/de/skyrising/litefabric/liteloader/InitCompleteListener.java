package de.skyrising.litefabric.liteloader;

import de.skyrising.litefabric.liteloader.core.LiteLoader;

import net.minecraft.client.Minecraft;

public interface InitCompleteListener extends LiteMod {
    void onInitCompleted(Minecraft client, LiteLoader loader);
}
