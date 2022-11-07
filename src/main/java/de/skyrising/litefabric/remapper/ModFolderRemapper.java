package de.skyrising.litefabric.remapper;

import de.skyrising.litefabric.Profiler;
import de.skyrising.litefabric.common.EntryPointType;
import de.skyrising.litefabric.remapper.util.LitemodRemapper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class ModFolderRemapper {
	private static LitemodRemapper remapper = null;

	public static LitemodRemapper getRemapper() {
		if (remapper == null) {
			String targetNamespace = FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace();
			TinyTree mappings = getMappings();
			remapper = new LitemodRemapper(mappings, targetNamespace);
		}

		return remapper;
	}

	private static TinyTree getMappings() {
		String mappings = "mappings/mappings.tiny";
		try (InputStream inputStream = ModFolderRemapper.class.getClassLoader().getResourceAsStream(mappings)) {
			if (inputStream == null)
				throw new IOException("inputStream is null");

			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
			return TinyMappingFactory.loadWithDetection(reader);
		} catch (IOException e) {
			throw new RuntimeException("Cannot load " + mappings + " from classpath!", e);
		}
	}

	/**
	 * Tries to remap the mods in the users mods folder.
	 * @return true if the user needs to restart mc, false if mc should just continue launching
	 * @throws RuntimeException if it couldn't remap
	 */
	public static boolean remapModsFolder() {
		Path modsFolder = FabricLoader.getInstance().getGameDir().resolve("mods");

		if (!Files.exists(modsFolder) || !Files.isDirectory(modsFolder)) {
			return false; // don't force the user to have a mods folder
		}

		Profiler.push("walkFileTree");
		Map<String, Path> fabricMods = new HashMap<>();
		Map<String, Path> liteMods = new HashMap<>();
		try {
			Files.walkFileTree(modsFolder, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 1, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					String fileName = file.getFileName().toString();
					if (!fileName.startsWith(".") && !Files.isHidden(file)) {
						if (fileName.endsWith(".jar")) {
							fabricMods.put(fileName, file);
						} else if (fileName.endsWith(".litemod")) {
							liteMods.put(fileName, file);
						}
					}

					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		if (liteMods.size() == 0)
			return false; // if there are no litemods, continue

		Profiler.swap("findNeededRemapping");

		Map<Path, Path> liteModsToRemap = new HashMap<>();
		liteMods.forEach((name, litePath) -> {
			String fabricFileName = EntryPointType.getFabricModName(name);

			if (!fabricMods.containsKey(fabricFileName)) {
				// we can safely assume that litePath.getParent() is the same as modsFolder, as all mods come from the mods folder
				Path fabricPath = modsFolder.resolve(fabricFileName);

				liteModsToRemap.put(litePath, fabricPath);
			}
		});

		if (liteModsToRemap.size() == 0)
			return false; // if there are not litemods to remap, continue

		Profiler.swap("preRemap");

		// ensure the remapper exists
		getRemapper();

		Profiler.swap("remap");

		liteModsToRemap.forEach((liteMod, fabricMod) -> {
			{
				String fileName = liteMod.getFileName().toString();
				Profiler.push(fileName.substring(0, fileName.length() - ".litemod".length()));
				System.out.println("Remapping litemod: " + fileName);
			}

			//if (Files.exists(fabricMod)) // this shouldn't trigger
			//	throw new IllegalStateException();

			Profiler.push("load");

			LiteMod mod = LiteMod.load(liteMod);

			Profiler.swap("write");

			mod.write(remapper, fabricMod);

			Profiler.pop();

			Profiler.pop();
		});

		int remapped = liteModsToRemap.size();

		Profiler.pop();

		if (remapped != 0) {
			System.out.println("Remapped " + remapped + " liteloader mods to fabric");
			System.out.println("RESTART MC TO LOAD THEM!");
			return true;
		}
		return false;
	}
}
