package io.github.zeichenreihe.liteornithe.liteloader.modconfig;

import io.github.zeichenreihe.liteornithe.runtime.LiteFabric;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.nio.file.Path;

public enum ConfigStrategy {
    Unversioned, Versioned;

    @Deprecated
    public File getFileForStrategy(String fileName) {
        return getPathForStrategy(fileName).toFile();
    }

    public Path getPathForStrategy(String fileName) {
        return getDirectory().resolve(fileName);
    }

    public Path getDirectory() {
        FabricLoader loader = FabricLoader.getInstance();
        if (this == Versioned) {
            return loader.getConfigDir().resolve(LiteFabric.getMinecraftVersion().toString());
        }
        return loader.getConfigDir();
    }
}
