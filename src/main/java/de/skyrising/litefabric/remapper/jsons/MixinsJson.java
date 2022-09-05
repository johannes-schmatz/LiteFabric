package de.skyrising.litefabric.remapper.jsons;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MixinsJson {
	public final boolean required;
	@SerializedName("package")
	public final String packageString;
	public final String compatibilityLevel;
	public final List<String> mixins;
	public final List<String> client;
	public final Map<String, String> injectors;
	@SerializedName("refmap")
	public final String refMap;
	public final String minVersion;

	public MixinsJson(boolean required, String packageString, String compatibilityLevel, List<String> mixins,
					  List<String> client, Map<String, String> injectors, String refMap, String minVersion) {
		this.required = required;
		this.packageString = packageString;
		this.compatibilityLevel = compatibilityLevel;
		this.mixins = mixins;
		this.client = client;
		this.injectors = injectors;
		this.refMap = refMap;
		this.minVersion = minVersion;
	}

	public static Map<MixinsJson, Path> load(FileSystem jarFileSystem, Path jarPath, LiteModJson liteModJson){
		if (liteModJson.mixinConfigs == null) {
			return Collections.emptyMap();
		}

		Gson gson = new Gson();
		Map<MixinsJson, Path> mixinConfigs = new HashMap<>();

		liteModJson.mixinConfigs.forEach(name -> {
			Path mixinConfigFile = jarFileSystem.getPath(name);

			if (!Files.exists(mixinConfigFile)) {
				throw new RuntimeException("Mixin Config File " + mixinConfigFile + " not found in " + jarPath);
			}

			try {
				mixinConfigs.put(gson.fromJson(Files.newBufferedReader(mixinConfigFile), MixinsJson.class), mixinConfigFile);
			} catch (IOException e) {
				throw new RuntimeException("Failed to read " + mixinConfigFile + " in " + jarPath + ": " + e.getMessage());
			} catch (JsonParseException e) {
				throw new RuntimeException("Could not load " + mixinConfigFile + " in " + jarPath + ": " + e.getMessage());
			}
		});

		return mixinConfigs;
	}
}
