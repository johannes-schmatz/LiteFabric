package de.skyrising.litefabric.remapper;

import de.skyrising.litefabric.Profiler;
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
import java.util.stream.Stream;
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
		Profiler.push("openFs");
		FileSystemDelegate outputJarFileSystemDelegate;
		try {
			outputJarFileSystemDelegate = FileSystemDelegate.writeFileSystem(fabricMod);
		} catch (IOException e) {
			throw new RuntimeException("Failed to open new mod JAR at " + fabricMod + ": " + e.getMessage());
		}

		FileSystem outputJarFileSystem = outputJarFileSystemDelegate.get();

		Profiler.swap("otherFiles");
		try {
			writeOtherFiles(this.jarFileSystemDelegate.get(), outputJarFileSystem);
			Profiler.swap("remappedClasses");
			writeRemappedClasses(remapper, this.jarFileSystemDelegate.get(), outputJarFileSystem);
			Profiler.swap("fmj");
			this.fabricModJson.write(outputJarFileSystem);

			// write this after the classes, as it needs the classes to be remapped to figure out the super classes specified in the mixin annotation
			for (Map.Entry<Path, RefmapJson> entry: refmaps.entrySet()) {
				RefmapJson refmap = entry.getValue();
				Profiler.swap("remap " + entry.getKey().getFileName());
				refmap.remap(remapper);
				Profiler.swap("write " + entry.getKey().getFileName());
				refmap.write(entry.getKey(), outputJarFileSystem);
			}
			Profiler.swap("settingsClass");
			writeSettingsClass(outputJarFileSystem);
		} catch (IOException e) {
			throw new RuntimeException("IOException while trying to write JAR at " + fabricMod + ": " + e.getMessage(), e);
		}
		Profiler.swap("close");

		try {
			outputJarFileSystemDelegate.close();
		} catch (Exception e) {
			throw new RuntimeException("Failed to close new mod JAR at " + fabricMod + ": " + e.getMessage());
		}
		Profiler.pop();
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
			Path input = inputJarFileSystem.getPath(entry.getKey());
			Path output = outputJarFileSystem.getPath(entry.getValue());

			if (!Files.exists(output.getParent()))
				Files.createDirectories(output.getParent());

			if (!Files.exists(output))
				Files.copy(input, output);
		}
	}

	static class ClassStorage {
		public final String className;
		public final Path inputPath;
		public final Path outputPath;
		public ClassNode inputNode;
		public ClassNode remapped;

		ClassStorage(String className, Path inputPath, Path outputPath) {
			this.className = className;
			this.inputPath = inputPath;
			this.outputPath = outputPath;
		}

		public void remap(LitemodRemapper remapper) {
			remapped = new ClassNode();
			ClassRemapper clsRemapper = new ClassRemapper(remapped, remapper);
			inputNode.accept(clsRemapper);
		}

		public void read() {
			byte [] raw;
			try {
				raw = Files.readAllBytes(inputPath);
			} catch (IOException e) {
				throw new RuntimeException("Can not read bytes of class " + className, e);
			}

			ClassReader reader = new ClassReader(raw);
			inputNode = new ClassNode();
			reader.accept(inputNode, ClassReader.EXPAND_FRAMES);
		}

		public void write() {
			ClassWriter writer = new ClassWriter(Opcodes.ASM9);
			remapped.accept(writer);
			byte[] raw = writer.toByteArray();

			try {
				// creating folders isn't needed, those are copied with the other files stuff
				Files.write(outputPath, raw);
			} catch (IOException e) {
				throw new RuntimeException("Failed to write bytes of class " + className, e);
			}
		}
	}

	private void writeRemappedClasses(LitemodRemapper remapper, FileSystem inputFileSystem, FileSystem outputFileSystem) {
		Set<ClassStorage> storages = new LinkedHashSet<>(classes.size());

		Profiler.push("read");
		for (String className: classes) {
			String classNameInternal = className.replace('.', '/') + ".class";

			Path inputPath = inputFileSystem.getPath(classNameInternal);
			Path outputPath = outputFileSystem.getPath(classNameInternal);

			ClassStorage storage = new ClassStorage(className, inputPath, outputPath);

			storage.read();

			remapper.addClass(storage.inputNode);

			storages.add(storage);
		}
		Profiler.swap("remap");
		for (ClassStorage storage: storages) {
			storage.remap(remapper);

			// it's a malilib config redirect panel
			if ("fi/dy/masa/malilib/gui/config/liteloader/RedirectingConfigPanel".equals(storage.remapped.superName)) {
				this.preLaunchClassBuilder.addEntryPoint(EntryPointType.MALILIB_REDIRECTING_CONFIG_PANEL, storage.remapped.name);
			}

			//if (isConfigGuiCandidate(remapped))
			//	System.out.println("config gui candidate! " + remapped.name);
			//	mod.configGuiCandidates.add(remapped.name);
		}
		Profiler.swap("write");
		for (ClassStorage storage: storages) {
			storage.write();
		}
		Profiler.pop();
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
		Profiler.push("openFs");
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

		Profiler.swap("otherFiles");
		Path rootDir = jarFileSystem.getRootDirectories().iterator().next();
		Map<String, String> otherFiles = new LinkedHashMap<>();
		try (Stream<Path> stream = Files.walk(rootDir)) {
			stream.filter(path -> {
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

		Profiler.swap("entrypoints");

		List<String> entryPoints = new ArrayList<>();
		try (Stream<Path> stream = Files.walk(rootDir)){
			stream.filter(path -> {
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

		Profiler.swap("classes");

		Set<String> classes = new LinkedHashSet<>();
		try (Stream<Path> stream = Files.walk(rootDir)){
			stream.filter(path -> {
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

		Profiler.swap("liteJson");

		LiteModJson liteModJson = LiteModJson.load(jarFileSystem, jarPath, otherFiles);

		Profiler.swap("mcModJson");

		@Nullable McmodInfo mcmodInfoJson = McmodInfo.load(jarFileSystem, jarPath, otherFiles);

		Profiler.swap("fabricJson");

		FabricModJson fabricModJson = FabricModJson.from(liteModJson, mcmodInfoJson);

		Profiler.swap("mixinJson");

		Map<MixinsJson, Path> mixinConfigs = MixinsJson.load(jarFileSystem, jarPath, liteModJson);

		Profiler.swap("refmaps");

		Map<Path, RefmapJson> refmaps = RefmapJson.load(jarFileSystem, jarPath, mixinConfigs, otherFiles);

		Profiler.pop();

		return new LiteMod(fabricModJson, refmaps, entryPoints, classes, otherFiles, jarFileSystemDelegate);
	}
}
