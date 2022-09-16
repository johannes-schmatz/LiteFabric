package de.skyrising.litefabric.remapper;

import de.skyrising.litefabric.remapper.util.LitemodRemapper;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class Main {
	private static final String TINY_MAPPINGS = "mappings/mappings.tiny";
	// TODO: probably best to let this call some more abstract "main" function
	//  also let that be called by a mixin in the minecraft's main at the top
	public static void main(String[] args) {
		Path inputLiteMod;
		Path outputIntermediary;
		@Nullable
		Path outputNamed;

		int offset = 1;
		// TODO: remove the + offset here everywhere, when the brachyura bug is fixed, and the _ in the help text
		if (args.length == 1 + offset) {
			// create same file, replace liteloader in filename with fabricloader, litemod with jar
			// also create sources, replace .jar with -sources.jar
			inputLiteMod = Paths.get(args[0 + offset]).toAbsolutePath();

			String fileName = inputLiteMod.getFileName().toString();
			Path parent = inputLiteMod.getParent();

			String intermediaryFileName = fileName.replaceAll("liteloader", "fabricloader").replaceAll("\\.litemod$", ".jar");
			String namedFileName = intermediaryFileName.replaceAll("\\.jar$", "-sources.jar");

			outputIntermediary = parent.resolve(intermediaryFileName).toAbsolutePath();
			outputNamed = parent.resolve(namedFileName).toAbsolutePath();
		} else if (args.length == 2 + offset) {
			inputLiteMod = Paths.get(args[0 + offset]).toAbsolutePath();
			outputIntermediary = Paths.get(args[1 + offset]).toAbsolutePath();
			outputNamed = null;
		} else {
			System.err.println("usage:");
			System.err.println("litefabric.jar _ malilib-liteloader-1.12.2-0.52.0.litemod");
			System.err.println(" this will create two files called malilib-fabricloader-1.12.2-0.52.0.jar and malilib-fabricloader-1.12.2-0.52.0-sources.jar");
			System.err.println("litefabric.jar _ voxelmap-1.7.1.litemod voxelmap-1.7.1.jar");
			System.err.println(" this will create a file called voxelmap-1.7.1.jar only containing the intermediary version");
			System.out.println(Arrays.toString(args));
			return;
		}

		// handle already existing output files
		// TODO: maybe add flag to overwrite the file
		try {
			// for now delete the files
			if (Files.exists(outputIntermediary)) {
				Files.delete(outputIntermediary);
			}
			if (outputNamed != null && Files.exists(outputNamed)) {
				Files.delete(outputNamed);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		//TODO: somehow check if the mc jar is in cp

		// make sure we can get the mappings from the classpath
		TinyTree tree = getMappings();

		// load the mod (the json files)
		LiteMod mod = LiteMod.load(inputLiteMod);

		// create remappers
		LitemodRemapper remapperIntermediary = new LitemodRemapper(tree, "intermediary");
		LitemodRemapper remapperNamed = new LitemodRemapper(tree, "named");

		// remap and write the mods
		mod.write(remapperIntermediary, outputIntermediary);
		if (outputNamed != null) {
			//mod.write(remapperNamed, outputNamed); //TODO: uncomment conde the RefMap remapping replacing itself is fixed
		}
	}

	public static TinyTree getMappings(){
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
