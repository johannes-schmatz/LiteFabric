package io.github.zeichenreihe.liteornithe.liteloader;

public interface OutboundChatFilter {
    boolean onSendChatMessage(String message);
}
