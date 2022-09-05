package de.skyrising.litefabric.remapper.jsons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.skyrising.litefabric.common.EntryPointType;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class FabricModJson {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public final int schemaVersion = 1;
	public final String id;
	public final String version;
	public final String name;
	public final String description;
	public final List<String> authors;
	public final Map<String, String> contact;
	public final String icon;
	public final String environment = "client";
	public final Map<String, List<String>> entrypoints;
	public final List<String> mixins;
	public final Map<String, String> depends;
	public final Map<String, Map<String, List<String>>> custom;

	public FabricModJson(String id, String version, String name, String description, List<String> authors, Map<String, String> contact, String icon, Map<String, List<String>> entrypoints, List<String> mixins, Map<String, String> depends, Map<String, Map<String, List<String>>> custom) {
		this.id = id;
		this.version = version;
		this.name = name;
		this.description = description;
		this.authors = authors;
		this.contact = contact;
		this.icon = icon;
		this.entrypoints = entrypoints;
		this.mixins = mixins;
		this.depends = depends;
		this.custom = custom;
	}

	public void write(FileSystem outputJarFileSystem) throws IOException {
		String fileName = "fabric.mod.json";

		Path output = outputJarFileSystem.getPath(fileName);
		String json = GSON.toJson(this);

		try(Writer writer = Files.newBufferedWriter(output)) {
			writer.write(json);
		}
	}

	public static FabricModJson from(LiteModJson liteModJson, @Nullable McmodInfo mcmodInfoJson) {
		String id = liteModJson.name.toLowerCase();
		String description;
		List<String> authors;
		HashMap<String, String> contact = new HashMap<>();
		HashMap<String, List<String>> entrypoints = new HashMap<>();
		HashMap<String, String> depends = new HashMap<>();
		List<String> badges = new ArrayList<>();

		if (mcmodInfoJson != null) {
			assert Objects.equals(liteModJson.name, mcmodInfoJson.id);
			description = mcmodInfoJson.description;
			authors = mcmodInfoJson.authors;
			contact.put("homepage", mcmodInfoJson.url); // TODO: consider adding masa's github (+ issues) for masa's mods (or changing the url to masa's webpage)
		} else {
			description = liteModJson.description;
			authors = Collections.singletonList(liteModJson.author);
		}

		String displayName = liteModJson.displayName;
		if (displayName == null)
			displayName = liteModJson.name;

		entrypoints.put("preLaunch", Collections.singletonList(EntryPointType.getPreLaunchEntryPoint(liteModJson.name, '.'))); //TODO: somehow get the package + class name here

		depends.put("minecraft", "1.12.2"); // TODO: check if we want to add malilib here maybe

		badges.add("liteloader");

		// TODO: check if we want malilib to be a lib
		//if ("malilib".equals(meta.name))
		//	badges.add("library");

		return new FabricModJson(
				id,
				liteModJson.version,
				displayName,
				description,
				authors,
				contact,
				"assets/" + id + "/icon.png",
				entrypoints,
				liteModJson.mixinConfigs,
				depends,
				Collections.singletonMap("modmenu", Collections.singletonMap("badges", badges))
		);
	}
}
