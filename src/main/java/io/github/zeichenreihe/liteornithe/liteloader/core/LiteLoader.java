package io.github.zeichenreihe.liteornithe.liteloader.core;

import io.github.zeichenreihe.liteornithe.runtime.modconfig.ConfigManager;
import io.github.zeichenreihe.liteornithe.liteloader.modconfig.Exposable;
import io.github.zeichenreihe.liteornithe.liteloader.util.Input;
import io.github.zeichenreihe.liteornithe.runtime.LiteFabric;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;

public final class LiteLoader {
    private static final LiteLoader INSTANCE = new LiteLoader();
    private LiteLoader() {}

    public static LiteLoader getInstance() {
        return INSTANCE;
    }

    public static Input getInput() {
        return LiteFabric.getInstance().getInput();
    }

    public static File getCommonConfigFolder() {
        return FabricLoader.getInstance().getConfigDir().toFile();
    }

    public static ClientPluginChannels getClientPluginChannels() {
        return LiteFabric.getInstance().getClientPluginChannels();
    }

    public void registerExposable(Exposable exposable, String fileName) {
        ConfigManager configManager = LiteFabric.getInstance().configManager;
        configManager.registerExposable(exposable, fileName, true);
        configManager.initConfig(exposable);
    }

    public void writeConfig(Exposable exposable) {
        LiteFabric.getInstance().configManager.invalidateConfig(exposable);
    }
}
