package de.skyrising.litefabric.remapper.jsons;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class McmodInfo {
    @SerializedName("modid")
    public final String id;
    public final String name;
    public final String description;
    public final String version;
    @SerializedName("mcversion")
    public final String mcVersion;
    public final String url;
    public final String updateUrl;
    @SerializedName("authorList")
    public final List<String> authors;
    public final String credits;
    public final String logoFile;

    public McmodInfo(String id, String name, String description, String version, String mcVersion, String url,
            String updateUrl, List<String> authors, String credits, String logoFile) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.version = version;
        this.mcVersion = mcVersion;
        this.url = url;
        this.updateUrl = updateUrl;
        this.authors = authors;
        this.credits = credits;
        this.logoFile = logoFile;
    }

    @Nullable
    public static McmodInfo load(FileSystem jarFileSystem, Path jarPath, Map<String, String> otherFiles) {
        String fileName = "mcmod.info";

        Path mcmodInfo = jarFileSystem.getPath(fileName);

        otherFiles.remove(mcmodInfo.toAbsolutePath().toString());

        if (Files.exists(mcmodInfo)) {
            try {
                List<McmodInfo> mcmodInfos = new Gson().fromJson(
                        Files.newBufferedReader(mcmodInfo, StandardCharsets.UTF_8),
                        new TypeToken<List<McmodInfo>>(){}.getType()
                );

                // actually we should return a Map<String, McmodInfo> (modId to McmodInfo), but so far all LiteMods
                // contain only one mcmod.info entry.
                // map.put(info.id, info)
                if (mcmodInfos.size() != 1) {
                    throw new RuntimeException("Expected mcmod.info in " + jarPath + " to only contain one entry, found " + mcmodInfos.size() + " instead.");
                }

                return mcmodInfos.get(0);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read mcmod.info in " + jarPath + ": " + e.getMessage());
            } catch (JsonParseException e) {
                throw new RuntimeException("Could not load mcmod.info in " + jarPath + ": " + e.getMessage());
            }
        }
        // return an empty set in case of rewrite
        return null;
    }
}
