package io.github.zeichenreihe.liteornithe.liteloader;

import io.github.zeichenreihe.liteornithe.liteloader.core.LiteLoader;

import net.minecraft.client.Minecraft;

public interface InitCompleteListener extends LiteMod {
    void onInitCompleted(Minecraft client, LiteLoader loader);
}
