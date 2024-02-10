package io.github.zeichenreihe.liteornithe.liteloader;

import io.github.zeichenreihe.liteornithe.liteloader.core.LiteLoaderEventBroker;
import net.minecraft.text.Text;

public interface ChatFilter {
    boolean onChat(Text chat, String message, LiteLoaderEventBroker.ReturnValue<Text> newMessage);
}
