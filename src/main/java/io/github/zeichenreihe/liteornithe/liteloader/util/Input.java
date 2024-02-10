package io.github.zeichenreihe.liteornithe.liteloader.util;

import net.minecraft.client.options.KeyBinding;

public abstract class Input {
    public abstract void registerKeyBinding(KeyBinding binding);
    public abstract void unRegisterKeyBinding(KeyBinding binding);
}
