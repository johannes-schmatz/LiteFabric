package io.github.zeichenreihe.liteornithe.liteloader.messaging;

import java.util.List;

public interface Messenger {
    List<String> getMessageChannels();
    void receiveMessage(Message message);
}
