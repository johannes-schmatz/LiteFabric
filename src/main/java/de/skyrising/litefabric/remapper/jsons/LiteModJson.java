package de.skyrising.litefabric.remapper.jsons;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class LiteModJson {
    public final String name;
    public final String displayName;
    public final String description;
    public final String version;
    public final String author;
    @SerializedName("mcversion")
    public final String mcVersion;
    public final String revision;
    public final List<String> dependsOn;
    public final List<String> mixinConfigs;

    public LiteModJson(String name, String displayName, String description, String version, String author,
            String mcversion, String revision, List<String> dependsOn, List<String> mixinConfigs) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.version = version;
        this.author = author;
        this.mcVersion = mcversion;
        this.revision = revision;
        this.dependsOn = dependsOn;
        this.mixinConfigs = mixinConfigs;
    }

    public static LiteModJson load(FileSystem jarFileSystem, Path jarPath, Map<String, String> otherFiles){
        String fileName = "litemod.json";

        Path modJson = jarFileSystem.getPath(fileName);

        otherFiles.remove(modJson.toAbsolutePath().toString());

        if (!Files.exists(modJson)) {
            throw new RuntimeException("No litemod.json found in " + jarPath);
        }

        try {
            return new Gson().fromJson(Files.newBufferedReader(modJson), LiteModJson.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read litemod.json in " + jarPath + ": " + e.getMessage());
        } catch (JsonParseException e) {
            throw new RuntimeException("Could not load litemod.json in " + jarPath + ": " + e.getMessage());
        }
    }
}
