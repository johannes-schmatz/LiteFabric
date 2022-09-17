package de.skyrising.litefabric.mixin;

import de.skyrising.litefabric.common.EntryPointType;
import de.skyrising.litefabric.remapper.LiteMod;
import de.skyrising.litefabric.remapper.Main;
import de.skyrising.litefabric.remapper.util.LitemodRemapper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

@Mixin(net.minecraft.client.main.Main.class)
public class ModRemappingCallbackMixin {
	@Inject(method = "main", at = @At("HEAD"), cancellable = true)
	private static void main(String[] args, CallbackInfo ci) {
		if (remapModsFolder())
			ci.cancel();
	}

	// TODO: create separate class for this stuff...
	private static boolean remapModsFolder() {
		Path modsFolder = FabricLoader.getInstance().getGameDir().resolve("mods");

		if (!(Files.exists(modsFolder) && Files.isDirectory(modsFolder))) {
			return false;
		}

		Set<String> liteMods = new HashSet<>();
		Set<String> fabricMods = new HashSet<>();

		// a file visitor to collect the paths
		FileVisitor<? super Path> fileVisitor = new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				String fileName = file.getFileName().toString();

				boolean notHidden = !fileName.startsWith(".") && !Files.isHidden(file);
				if (fileName.endsWith(".jar") && notHidden) {
					fabricMods.add(fileName);
				} else if (fileName.endsWith(".litemod") && notHidden) {
					liteMods.add(fileName);
				}

				return FileVisitResult.CONTINUE;
			}
		};

		try { // actually run it...
			Files.walkFileTree(modsFolder, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 1, fileVisitor);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		if (liteMods.isEmpty())
			return false;

		TinyTree tree = getMappings();
		String targetNamespace = FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace();
		LitemodRemapper remapper = new LitemodRemapper(tree, targetNamespace);

		int remapped = 0;
		for (String litemod: liteMods) {
			String fabricModFileName = EntryPointType.getFabricModName(litemod);

			if (!fabricMods.contains(fabricModFileName)) {
				// we need to remap

				Path liteMod = modsFolder.resolve(litemod);
				Path fabricMod = modsFolder.resolve(fabricModFileName);

				if (Files.exists(fabricMod))
					throw new IllegalStateException("fabric mod is " + fabricMod);

				LiteMod mod = LiteMod.load(liteMod);
				mod.write(remapper, fabricMod);

				remapped++;
			}
		}

		if (remapped == 0) {
			return false;
		}

		System.out.println("Remapped " + remapped + " liteloader mods to fabric");
		System.out.println("RESTART MC TO LOAD THEM!");

		return true;
	}

	private static final String TINY_MAPPINGS = "mappings/mappings.tiny";
	private static TinyTree getMappings(){
		try (InputStream inputStream = Main.class.getClassLoader().getResourceAsStream(TINY_MAPPINGS)) {
			if (inputStream == null)
				throw new IOException("inputStream is null");

			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
			return TinyMappingFactory.loadWithDetection(reader);
		} catch (IOException e) {
			throw new RuntimeException("Cannot load " + TINY_MAPPINGS + " from classpath!", e);
		}
	}
}
