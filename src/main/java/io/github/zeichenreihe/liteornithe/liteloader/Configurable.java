package io.github.zeichenreihe.liteornithe.liteloader;

import io.github.zeichenreihe.liteornithe.liteloader.modconfig.ConfigPanel;

public interface Configurable {
    Class<? extends ConfigPanel> getConfigPanelClass();
}
