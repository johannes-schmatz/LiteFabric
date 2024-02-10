package io.github.zeichenreihe.liteornithe.liteloader.modconfig;

import io.github.zeichenreihe.liteornithe.liteloader.LiteMod;

public interface ConfigPanelHost {
    <T extends LiteMod> T getMod();
    int getWidth();
    int getHeight();
    void close();
}
