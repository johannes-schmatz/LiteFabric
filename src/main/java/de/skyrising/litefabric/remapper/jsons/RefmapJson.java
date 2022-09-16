package de.skyrising.litefabric.remapper.jsons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import de.skyrising.litefabric.remapper.util.LitemodRemapper;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RefmapJson {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public final Map<String, Map<String, String>> mappings;
	public final Map<String, Map<String, Map<String, String>>> data;

	public RefmapJson(Map<String, Map<String, String>> mappings, Map<String, Map<String, Map<String, String>>> data) {
		this.mappings = mappings;
		this.data = data;
	}

	public static Map<Path, RefmapJson> load(FileSystem jarFileSystem, Path jarPath, Map<MixinsJson, Path> mixinConfigs, Map<String, String> otherFiles) {
		if (mixinConfigs.size() == 0) {
			return Collections.emptyMap();
		}

		Map<Path, RefmapJson> refmaps = new HashMap<>();

		mixinConfigs.forEach((mixinsJson, path) -> {
			if (mixinsJson.refMap == null) {
				throw new RuntimeException("No RefMap in MixinsJson " + path + " in " + jarPath + " specified.");
			}

			Path refmap = jarFileSystem.getPath(mixinsJson.refMap);
			otherFiles.remove(refmap.toAbsolutePath().toString());

			if (!Files.exists(refmap)) {
				throw new RuntimeException("RefMap " + refmap + " not found in " + jarPath);
			}

			try {
				refmaps.put(refmap, GSON.fromJson(Files.newBufferedReader(refmap, StandardCharsets.UTF_8), RefmapJson.class));
			} catch (IOException e) {
				throw new RuntimeException("Failed to read " + refmap + " in " + jarPath + ": " + e.getMessage());
			} catch (JsonParseException e) {
				throw new RuntimeException("Could not read " + refmap + " in " + jarPath + ": " + e.getMessage());
			}
		});

		return refmaps;
	}

	public void remap(LitemodRemapper remapper) {
		this.data.clear();

		// TODO: let this also change the descriptor? maybe not
		// for every class entry we need to remap the mappings of references
		this.mappings.forEach((mixinClass, refmapEntryMap) -> {
			// TODO: don't let this write back here, we want to use the original here again!
			//  also we should remap the map only once, not once for named and intermediary, but once for both
			refmapEntryMap.replaceAll((originalName, oldMappedName) -> remapper.mapRefMapEntry(mixinClass, oldMappedName));
			//refmapEntryMap.replaceAll((c, v) -> remapper.mapRefMapEntry(mixinClass, refmapEntryMap.get(c)));
			// ^ this should be the same as the uncommented above
		});

		this.data.put("named:intermediary", this.mappings);

	}

	public void write(Path path, FileSystem outputJarFileSystem) throws IOException {
		String fileName = path.toString();

		Path output = outputJarFileSystem.getPath(fileName);

		String json = GSON.toJson(this);

		if (output.getParent() != null)
			Files.createDirectories(output.getParent());

		try (Writer writer = Files.newBufferedWriter(output)) {
			writer.write(json);
		}
	}
}
