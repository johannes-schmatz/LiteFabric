package de.skyrising.litefabric.remapper;

import de.skyrising.litefabric.common.EntryPointType;
import de.skyrising.litefabric.remapper.jsons.*;
import de.skyrising.litefabric.remapper.util.FileSystemDelegate;
import de.skyrising.litefabric.remapper.util.LitemodRemapper;
import de.skyrising.litefabric.remapper.util.PreLaunchClassBuilder;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.util.Annotations;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipError;

public class LiteMod {
	private final FabricModJson fabricModJson;
	public final Map<Path, RefmapJson> refmaps;

	public final Set<String> classes;
	private final Map<String, String> otherFiles;
	public final FileSystemDelegate jarFileSystemDelegate;
	private final PreLaunchClassBuilder preLaunchClassBuilder;

	public LiteMod(FabricModJson fabricModJson, Map<Path, RefmapJson> refmaps, List<String> entryPoints, Set<String> classes,
				   Map<String, String> otherFiles, FileSystemDelegate jarFileSystemDelegate) {

		this.fabricModJson = fabricModJson;
		this.refmaps = refmaps;

		this.classes = classes;
		this.otherFiles = otherFiles;
		this.jarFileSystemDelegate = jarFileSystemDelegate;

		this.preLaunchClassBuilder = new PreLaunchClassBuilder(fabricModJson.id);

		for (String entryPoint: entryPoints) {
			preLaunchClassBuilder.addEntryPoint(EntryPointType.LITEMOD, entryPoint);
		}
	}

	public void write(LitemodRemapper remapper, Path fabricMod) {
		FileSystemDelegate outputJarFileSystemDelegate;
		try {
			outputJarFileSystemDelegate = FileSystemDelegate.writeFileSystem(fabricMod);
		} catch (IOException e) {
			throw new RuntimeException("Failed to open new mod JAR at " + fabricMod + ": " + e.getMessage());
		}

		FileSystem outputJarFileSystem = outputJarFileSystemDelegate.get();

		try {
			writeOtherFiles(this.jarFileSystemDelegate.get(), outputJarFileSystem);

			writeRemappedClasses(remapper, this.jarFileSystemDelegate.get(), outputJarFileSystem);

			this.fabricModJson.write(outputJarFileSystem);

			// write this after the classes, as it needs the classes to be remapped to figure out the super classes specified in the mixin annotation
			for (Map.Entry<Path, RefmapJson> entry: refmaps.entrySet()) {
				RefmapJson refmap = entry.getValue();
				refmap.remap(remapper);
				refmap.write(entry.getKey(), outputJarFileSystem);
			}

			writeSettingsClass(outputJarFileSystem);
		} catch (IOException e) {
			throw new RuntimeException("IOException while trying to write JAR at " + fabricMod + ": " + e.getMessage(), e);
		}

		try {
			outputJarFileSystemDelegate.close();
		} catch (Exception e) {
			throw new RuntimeException("Failed to close new mod JAR at " + fabricMod + ": " + e.getMessage());
		}
	}

	private void writeSettingsClass(FileSystem outputJarFileSystem) throws IOException {
		byte[] bytes = this.preLaunchClassBuilder.build();

		Path path = outputJarFileSystem.getPath(this.preLaunchClassBuilder.className + ".class");

		if (path.getParent() != null)
			Files.createDirectories(path.getParent());

		Files.write(path, bytes);
	}

	private void writeOtherFiles(FileSystem inputJarFileSystem, FileSystem outputJarFileSystem) throws IOException {
		for (Map.Entry<String, String> entry: otherFiles.entrySet()) {
			String oldFileName = entry.getKey();
			String newFileName = entry.getValue();

			//if ("/pack.mcmeta".equals(newFileName))
			//	newFileName = "/assets" + newFileName;

			Path input = inputJarFileSystem.getPath(oldFileName);
			Path output = outputJarFileSystem.getPath(newFileName);

			if (!Files.exists(output.getParent()))
				Files.createDirectories(output.getParent());

			if (!Files.exists(output))
				Files.copy(input, output);
		}
	}

	static class ClassStorage {
		public String className;
		public String classNameInternal;
		public byte[] inputBytes;//rename? rawBytes?
		public byte[] outputBytes;//rename?
		public ClassNode inputNode;
		public ClassNode outputNode;
		public Path inputPath;
		public Path outputPath;
	}

	private void writeRemappedClasses(LitemodRemapper remapper, FileSystem inputFileSystem, FileSystem outputFileSystem) throws IOException {
		Set<ClassStorage> storages = new LinkedHashSet<>(classes.size());

		//TODO: improve this stuff here, clean up, only store needed values in ClassStorage
		for (String className: classes) {
			ClassStorage storage = new ClassStorage();
			storage.className = className;
			storage.classNameInternal = className.replace('.', '/') + ".class";
			storage.inputPath = inputFileSystem.getPath(storage.classNameInternal);
			storage.outputPath = outputFileSystem.getPath(storage.classNameInternal);

			try {//TODO: refactor, move to ClassStorage
				storage.inputBytes = Files.readAllBytes(storage.inputPath);
			} catch (IOException e) {
				throw new RuntimeException("Can not read bytes of class " + className, e);
			}

			ClassReader reader = new ClassReader(storage.inputBytes);
			ClassNode raw = new ClassNode();
			reader.accept(raw, ClassReader.EXPAND_FRAMES);

			remapper.addClass(raw); //TODO: [done] first run this for all classes, then remap all classes

			storage.inputNode = raw;

			storages.add(storage);
		}

		for (ClassStorage storage: storages) {
			storage.outputBytes = remapClass(storage.inputNode, remapper);
		}

		for (ClassStorage storage: storages) {
			// creating folders isn't needed, those are copied with the other files stuff
			Files.write(storage.outputPath, storage.outputBytes);
		}
	}

	private byte[] remapClass(ClassNode raw, LitemodRemapper remapper) {
		ClassNode remapped = new ClassNode();
		ClassRemapper clsRemapper = new ClassRemapper(remapped, remapper);
		raw.accept(clsRemapper);

		// it's a malilib config redirect panel
		if ("fi/dy/masa/malilib/gui/config/liteloader/RedirectingConfigPanel".equals(remapped.superName)) {
			this.preLaunchClassBuilder.addEntryPoint(EntryPointType.MALILIB_REDIRECTING_CONFIG_PANEL, remapped.name);
		}

		//if (isConfigGuiCandidate(remapped))
		//	System.out.println("config gui candidate! " + remapped.name);
		//	mod.configGuiCandidates.add(remapped.name);

		// fix non-public overwrite methods in dev
		// most likely fabric launcher already does this for us
		/*if (FabricLoader.getInstance().isDevelopmentEnvironment()) { //doesn't fabric loader already do that for us?
			if (Annotations.getInvisible(remapped, Mixin.class) != null) {
				for (MethodNode method : remapped.methods) {
					if ((method.access & Opcodes.ACC_STATIC) != 0) continue;
					if (method.visibleAnnotations == null || Annotations.getVisible(method, Overwrite.class) != null) {
						Bytecode.setVisibility(method, Opcodes.ACC_PUBLIC);
					}
				}
			}
		}*/

		// also move this step to later??
		ClassWriter writer = new ClassWriter(Opcodes.ASM9);
		remapped.accept(writer);

		return writer.toByteArray();
	}

	private static boolean isConfigGuiCandidate(ClassNode node) {
		if (Annotations.getInvisible(node, Mixin.class) != null) return false;
		if ((node.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT)) != Opcodes.ACC_PUBLIC) return false;
		return CONFIG_GUI_SUPER_CLASSES.contains(node.superName);
	}

	private static final Set<String> CONFIG_GUI_SUPER_CLASSES = new HashSet<>(Arrays.asList(
			"fi/dy/masa/malilib/gui/GuiConfigsBase",
			"fi/dy/masa/malilib/gui/config/BaseConfigScreen",
			"net/minecraft/client/gui/screen/Screen"
	));

	public static LiteMod load(Path jarPath) {
		if (!Files.exists(jarPath)) {
			throw new RuntimeException("LiteMod at " + jarPath + " doesn't exist.");
		}

		FileSystemDelegate jarFileSystemDelegate;

		try {
			jarFileSystemDelegate = FileSystemDelegate.getFileSystem(jarPath);
		} catch (IOException e) {
			throw new RuntimeException("Failed to open mod JAR at " + jarPath + ": " + e.getMessage());
		} catch (ZipError e) {
			throw new RuntimeException("Jar at " + jarPath + " is corrupted, please redownload it: " + e.getMessage());
		}

		FileSystem jarFileSystem = jarFileSystemDelegate.get();

		Path rootDir = jarFileSystem.getRootDirectories().iterator().next();
		Map<String, String> otherFiles = new LinkedHashMap<>();
		try {
			Files.walk(rootDir).filter(path -> {
				if (path.getNameCount() == 0) return false;
				String fileName = path.getFileName().toString();
				return !(fileName.endsWith(".class") || fileName.endsWith(".java"));
			}).map(Path::toAbsolutePath).map(Path::toString).forEach(path -> otherFiles.put(path, path));
		} catch (IOException e) {
			throw new RuntimeException("Could not search for files", e);
		}

		// remove the forge cache stuff
		otherFiles.remove("/META-INF/fml_cache_class_versions.json");
		otherFiles.remove("/META-INF/fml_cache_annotation.json");

		List<String> entryPoints = new ArrayList<>();
		try {
			Files.walk(rootDir).filter(path -> {
				if (path.getNameCount() == 0) return false;
				String fileName = path.getFileName().toString();
				return fileName.startsWith("LiteMod") && fileName.endsWith(".class");
			}).map(path -> {
				Path relative = rootDir.relativize(path);
				String fullString = relative.toString();
				return fullString.substring(0, fullString.length() - 6).replace('/', '.');
			}).forEach(entryPoints::add);
		} catch (IOException e) {
			throw new RuntimeException("Could not search for LiteMod entry point", e);
		}

		Set<String> classes = new LinkedHashSet<>();
		try {
			Files.walk(rootDir).filter(path -> {
				if (path.getNameCount() == 0) return false;
				String fileName = path.getFileName().toString();
				return fileName.endsWith(".class");
			}).map(path -> {
				Path relative = rootDir.relativize(path);
				String fullString = relative.toString();
				return fullString.substring(0, fullString.length() - 6).replace('/', '.');
			}).forEach(classes::add);
		} catch (IOException e) {
			throw new RuntimeException("Could not search for classes", e);
		}

		LiteModJson liteModJson = LiteModJson.load(jarFileSystem, jarPath, otherFiles);

		@Nullable McmodInfo mcmodInfoJson = McmodInfo.load(jarFileSystem, jarPath, otherFiles);

		FabricModJson fabricModJson = FabricModJson.from(liteModJson, mcmodInfoJson);

		Map<MixinsJson, Path> mixinConfigs = MixinsJson.load(jarFileSystem, jarPath, liteModJson);

		Map<Path, RefmapJson> refmaps = RefmapJson.load(jarFileSystem, jarPath, mixinConfigs, otherFiles);

		return new LiteMod(fabricModJson, refmaps, entryPoints, classes, otherFiles, jarFileSystemDelegate);
	}
}
