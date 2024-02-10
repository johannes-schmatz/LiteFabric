package io.github.zeichenreihe.liteornithe.liteloader;

import io.github.zeichenreihe.liteornithe.liteloader.api.Listener;
import io.github.zeichenreihe.liteornithe.liteloader.modconfig.Exposable;

import java.io.File;

public interface LiteMod extends Exposable, Listener {
    String getVersion();
    void init(File configPath);
    void upgradeSettings(String version, File configPath, File oldConfigPath);
}
