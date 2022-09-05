package de.skyrising.litefabric.remapper;

import de.skyrising.litefabric.remapper.util.LitemodRemapper;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
	public static void main(String[] args) {
		if (args.length != 3) {
			System.err.println("usage:");
			System.err.println("the mc jar must be in the classpath.");
			System.err.println("java -cp minecraft.jar:litefabirc.jar de.skyrising.litefabric.remapper");
			System.err.println("java -jar litefabric.jar <input liteloader mod> <tiny mapping> <output fabric mod base name>");
			return;
		}

		Path liteloaderMod = Paths.get(args[0]);
		Path tinyMappings = Paths.get(args[1]);
		Path fabricModIntermediary = Paths.get(args[2]);
		Path fabricModNamed = Paths.get(args[2].replaceFirst(".jar", "-named.jar"));


		if (!Files.exists(tinyMappings)) {
			System.err.printf("TinyMappings File '%s' doesn't exits. Exiting.%n", tinyMappings);
			return;
		}

		if (Files.exists(fabricModIntermediary)) {//TODO: maybe add flag to overwrite the file
			System.err.printf("FabricMod File '%s' exist. Exiting.%n", fabricModIntermediary);
			try {
				Files.delete(fabricModIntermediary);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			//return;
		}

		if (Files.exists(fabricModNamed)) {//TODO: maybe add flag to overwrite the file
			System.err.printf("FabricMod File '%s' exist. Exiting.%n", fabricModNamed);
			try {
				Files.delete(fabricModNamed);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			//return;
		}

		LiteMod mod = LiteMod.load(liteloaderMod);

		TinyTree tree = getMappings(tinyMappings);

		LitemodRemapper remapperIntermediary = new LitemodRemapper(tree, "intermediary");
		LitemodRemapper remapperNamed = new LitemodRemapper(tree, "named");
			/*(cls) -> {
				//System.out.println(cls);
				String classFileName = cls + ".class";
				Path classFilePath = mod.jarFileSystemDelegate.get().getPath(classFileName);
				try {
					InputStream is = Files.newInputStream(classFilePath);
					System.out.println("!= null " + cls);
					return is;
				} catch (NoSuchFileException ignored) {
					System.out.println("== null " + cls);
					return null;
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}*/



		mod.write(remapperIntermediary, fabricModIntermediary);
		mod.write(remapperNamed, fabricModNamed);
	}

	static TinyTree getMappings(Path tinyMappings){
		try {
			BufferedReader reader = Files.newBufferedReader(tinyMappings);
			return TinyMappingFactory.loadWithDetection(reader);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


}
