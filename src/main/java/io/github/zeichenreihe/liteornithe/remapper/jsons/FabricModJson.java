package io.github.zeichenreihe.liteornithe.remapper.jsons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.zeichenreihe.liteornithe.common.EntryPointType;
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

	public FabricModJson(String id, String version, String name, String description, List<String> authors,
			Map<String, String> contact, String icon, Map<String, List<String>> entrypoints, List<String> mixins,
			Map<String, String> depends, Map<String, Map<String, List<String>>> custom) {
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
		String id = liteModJson.name.toLowerCase().replaceAll(" ", "_");
		String version = liteModJson.version;

		// convert Carpet.Client-v1.4c to be version 1.4.3
		if (version.startsWith("v")) {
			version = version.substring(1);
			int len = version.length();
			char lastChar = version.charAt(len - 1);
			if ('a' <= lastChar && lastChar <= 'z') {
				version = version.substring(0, len - 1) + "." + (lastChar - 'a' + 1);
			}
		}

		String description;
		List<String> authors;
		HashMap<String, String> contact = new HashMap<>();
		String icon;
		HashMap<String, String> depends = new HashMap<>();
		List<String> badges = new ArrayList<>();

		if (mcmodInfoJson != null) {
			assert Objects.equals(liteModJson.name, mcmodInfoJson.id);
			description = mcmodInfoJson.description;
			authors = mcmodInfoJson.authors;
			contact.put("homepage", mcmodInfoJson.url);
			icon = mcmodInfoJson.logoFile.isEmpty() ? "pack.png" : mcmodInfoJson.logoFile;
		} else {
			description = liteModJson.description;
			authors = Collections.singletonList(liteModJson.author);
			icon = "pack.png";
		}

		String displayName = liteModJson.displayName;
		if (displayName == null)
			displayName = liteModJson.name;

		String preLaunchClass = EntryPointType.getPreLaunchEntryPoint(id, '.');

		// force mc 1.12.2 and the use of liteornithe
		depends.put("minecraft", "1.12.2");
		depends.put("liteornithe", "*");

		// add the old dependencies
		if (liteModJson.dependsOn != null) {
			for (String dependency : liteModJson.dependsOn) {
				depends.put(dependency.toLowerCase(), "*");
			}
		}

		// display the mod as a liteloader mod
		badges.add("liteloader");

		// we consider malilib to be a library
		if ("malilib".equals(id))
			badges.add("library");

		// add in masa's urls
		if (MasaMods.contains(id)) {
			contact.put("homepage", MasaMods.HOMEPAGE);
			contact.put("issues", MasaMods.getMasaModIssues(id));
			contact.put("sources", MasaMods.getMasaModSources(id));
			contact.put("discord", MasaMods.DISCORD);
		}

		return new FabricModJson(
				id,
				version,
				displayName,
				description,
				authors,
				contact,
				icon,
				Collections.singletonMap("preLaunch", Collections.singletonList(preLaunchClass)),
				liteModJson.mixinConfigs,
				depends,
				Collections.singletonMap("modmenu", Collections.singletonMap("badges", badges))
		);
	}

	private static class MasaMods { // TODO: format better
		private static final Set<String> MASA_MODS = new HashSet<>();
		private static final String HOMEPAGE = "https://masa.dy.fi/mcmods/client_mods/?mcver=1.12.2";
		private static final String DISCORD = "https://masa.dy.fi/discord";

		private static boolean contains(String modId) {
			return MASA_MODS.contains(modId);
		}

		private static String getMasaModIssues(String modId) {
			return "https://github.com/maruohon/" + modId + "/issues";
		}

		private static String getMasaModSources(String modId) {
			return "https://github.com/maruohon/" + modId + "/tree/liteloader_1.12.2";
		}

		static {
			MASA_MODS.add("itemscroller");
			MASA_MODS.add("malilib");
			MASA_MODS.add("minihud");
			MASA_MODS.add("litematica");
			MASA_MODS.add("tweakeroo");
		}
	}
}
