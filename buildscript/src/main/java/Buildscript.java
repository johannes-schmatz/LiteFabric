import io.github.coolcrabs.brachyura.compiler.java.JavaCompilation;
import io.github.coolcrabs.brachyura.compiler.java.JavaCompilationResult;
import io.github.coolcrabs.brachyura.decompiler.BrachyuraDecompiler;
import io.github.coolcrabs.brachyura.decompiler.fernflower.FernflowerDecompiler;
import io.github.coolcrabs.brachyura.dependency.JavaJarDependency;
import io.github.coolcrabs.brachyura.fabric.*;
import io.github.coolcrabs.brachyura.fabric.FabricContext.ModDependencyCollector;
import io.github.coolcrabs.brachyura.ide.CustomRunConfigBuilder;
import io.github.coolcrabs.brachyura.ide.IdeModule;
import io.github.coolcrabs.brachyura.maven.Maven;
import io.github.coolcrabs.brachyura.maven.MavenId;
import io.github.coolcrabs.brachyura.minecraft.Minecraft;
import io.github.coolcrabs.brachyura.minecraft.VersionMeta;
import io.github.coolcrabs.brachyura.processing.ProcessingId;
import io.github.coolcrabs.brachyura.processing.sinks.AtomicZipProcessingSink;
import io.github.coolcrabs.brachyura.processing.sinks.DirectoryProcessingSink;
import io.github.coolcrabs.brachyura.processing.sources.DirectoryProcessingSource;
import io.github.coolcrabs.brachyura.project.Task;
import io.github.coolcrabs.brachyura.project.java.BuildModule;
import io.github.coolcrabs.brachyura.util.*;
import net.fabricmc.mappingio.tree.MappingTree;

import javax.tools.StandardLocation;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Buildscript extends SimpleFabricProject {
	private static final String MAIN_CLASS = "de.skyrising.litefabric.remapper.Main";
	private static final String MANIFEST_CONTENT = "Manifest-Version: 1.0\r\nMain-Class: " + MAIN_CLASS + "\r\n";

	@Override
	protected FabricContext createContext() {
		return new CustomFabricContext();
	}

	@Override
	public VersionMeta createMcVersion() {
		return Minecraft.getVersion(Versions.MINECRAFT);
	}

	@Override
	public MappingTree createMappings() {
		return Yarn.ofMaven("https://maven.legacyfabric.net", FabricMaven.yarn(Versions.LEGACY_YARN)).tree;
	}

	@Override
	public FabricLoader getLoader() {
		return new FabricLoader(FabricMaven.URL, FabricMaven.loader(Versions.FABRIC_LOADER));
	}

	@Override
	public void getModDependencies(ModDependencyCollector d) {
		d.addMaven(
				Maven.MAVEN_LOCAL,
				new MavenId(
						"de.skyrising",
						"modmenu",
						Versions.MODMENU
				),
				FabricContext.ModDependencyFlag.COMPILE,
				FabricContext.ModDependencyFlag.RUNTIME
		);
	}

	@Override
	public BrachyuraDecompiler decompiler() {
		// Uses QuiltFlower instead of CFR
		return new FernflowerDecompiler(
				Maven.getMavenJarDep(
						"https://maven.quiltmc.org/repository/release",
						new MavenId(
								"org.quiltmc",
								"quiltflower",
								Versions.QUILT_FLOWER
						)
				)
		);
	}

	public class CustomFabricContext extends SimpleFabricContext {
		@Override
		protected MappingTree createIntermediary() {
			// use legacy fabric intermediary
			return Intermediary.ofMaven(
					"https://maven.legacyfabric.net",
					new MavenId(
							"net.fabricmc",
							"intermediary",
							Versions.MINECRAFT
					)
			).tree;
		}
	}

	@Override
	protected FabricModule createModule() {
		return new CustomFabricModule(context.get());
	}

	@Override
	public void getRunConfigTasks(Consumer<Task> p) {
		IdeModule[] ms = getIdeModules();
		for (IdeModule m : ms) {
			for (IdeModule.RunConfig rc : m.runConfigs) {
				String tname = ms.length == 1 ? "run" + rc.name.replace(" ", "") : m.name.replace(" ",
						"") + ":run" + rc.name.replace(" ", "");
				if (rc.cwd.equals(NONE_PATH)) {
					p.accept(Task.of(tname, (String[] args) -> runRunConfigArgs(m, rc, args)));
				} else {
					p.accept(Task.of(tname, () -> runRunConfig(m, rc)));
				}
			}
		}
	}

	public class CustomFabricModule extends SimpleFabricModule {
		public CustomFabricModule(FabricContext context) {
			super(context);
		}

		@Override
		public List<String> ideVmArgs(boolean client) {
			try {
				ArrayList<String> r = new ArrayList<>();
				r.add("-Dfabric.development=true");
				r.add("-Dfabric.remapClasspathFile=" + context.runtimeRemapClasspath.get());
				r.add("-Dlog4j.configurationFile=" + writeLog4jXml());
				r.add("-Dlog4j2.formatMsgNoLookups=true");
				r.add("-Dfabric.log.disableAnsi=false");
				//r.add("-Dfabric.debug.logClassLoad=true"); // without this it's the original
				if (client) {
					String natives = context.extractedNatives.get().stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
					r.add("-Djava.library.path=" + natives);
					r.add("-Dtorg.lwjgl.librarypath=" + natives);
					if (OsUtil.OS == OsUtil.Os.OSX) {
						r.add("-XstartOnFirstThread");
					}
				}
				return r;
			} catch (Exception e) {
				throw Util.sneak(e);
			}
		}

		@Override
		public IdeModule ideModule() {
			Path cwd = PathUtil.resolveAndCreateDir(getModuleRoot(), "run");
			Lazy<List<Path>> classpath = new Lazy<>(() -> {
				Path mappingsClasspath = writeMappings4FabricStuff().getParent().getParent();
				ArrayList<Path> r = new ArrayList<>(context.runtimeDependencies.get().size() + 1);
				for (JavaJarDependency dependency : context.runtimeDependencies.get()) {
					r.add(dependency.jar);
				}
				r.add(mappingsClasspath);
				return r;
			});
			return new IdeModule.IdeModuleBuilder()
					.name(getModuleName())
					.root(getModuleRoot())
					.javaVersion(getJavaVersion())
					.dependencies(context.ideDependencies)
					.sourcePaths(getSrcDirs())
					.resourcePaths(getResourceDirs())
					.dependencyModules(getModuleDependencies().stream().map(BuildModule::ideModule).collect(Collectors.toList()))
					.runConfigs(
							new IdeModule.RunConfigBuilder()
									.name("Minecraft Client")
									.cwd(cwd)
									.mainClass("net.fabricmc.loader.launch.knot.KnotClient")
									.classpath(classpath)
									.resourcePaths(getResourceDirs())
									.vmArgs(() -> this.ideVmArgs(true))
									.args(() -> this.ideArgs(true)),
							new IdeModule.RunConfigBuilder()
									.name("Minecraft Server")
									.cwd(cwd)
									.mainClass("net.fabricmc.loader.launch.knot.KnotServer")
									.classpath(classpath)
									.resourcePaths(getResourceDirs())
									.vmArgs(() -> this.ideVmArgs(false))
									.args(() -> this.ideArgs(false)),
							new IdeModule.RunConfigBuilder() // this is custom
									.name("LiteMod Remapper")
									.cwd(NONE_PATH) // hack, there's an if in the run path
									.mainClass("de.skyrising.litefabric.remapper.Main")
									.classpath(classpath)
									.vmArgs(this::remapperVmArgs)
					)
					.build();
		}

		private List<String> remapperVmArgs(){
			return new ArrayList<>();
		}
	}

	private static final Path NONE_PATH = Paths.get("/").toAbsolutePath();

	public void runRunConfigArgs(IdeModule ideProject, IdeModule.RunConfig rc, String[] args) {
		try {
			LinkedHashSet<IdeModule> toCompile = new LinkedHashSet<>();
			Deque<IdeModule> a = new ArrayDeque<>();
			a.add(ideProject);
			a.addAll(rc.additionalModulesClasspath);
			while (!a.isEmpty()) {
				IdeModule m = a.pop();
				if (!toCompile.contains(m)) {
					a.addAll(m.dependencyModules);
					toCompile.add(m);
				}
			}
			HashMap<IdeModule, Path> mmap = new HashMap<>();
			for (IdeModule m : toCompile) {
				JavaCompilation compilation = new JavaCompilation();
				compilation.addOption(JvmUtil.compileArgs(JvmUtil.CURRENT_JAVA_VERSION, m.javaVersion));
				compilation.addOption("-proc:none");
				for (JavaJarDependency dep : m.dependencies.get()) {
					compilation.addClasspath(dep.jar);
				}
				for (IdeModule m0 : m.dependencyModules) {
					compilation.addClasspath(Objects.requireNonNull(mmap.get(m0), "Bad module dep " + m0.name));
				}
				for (Path srcDir : m.sourcePaths) {
					compilation.addSourceDir(srcDir);
				}
				Path outDir = Files.createTempDirectory("brachyurarun");
				JavaCompilationResult result = compilation.compile();
				Objects.requireNonNull(result);
				result.getInputs(new DirectoryProcessingSink(outDir));
				mmap.put(m, outDir);
			}
			ArrayList<String> command = new ArrayList<>();
			command.add(JvmUtil.CURRENT_JAVA_EXECUTABLE);

			command.addAll(rc.vmArgs.get());

			command.add("-cp");

			ArrayList<Path> cp = new ArrayList<>(rc.classpath.get());
			cp.addAll(ideProject.resourcePaths);
			cp.add(mmap.get(ideProject));
			for (IdeModule m : rc.additionalModulesClasspath) {
				cp.add(mmap.get(m));
			}

			StringBuilder cpStr = new StringBuilder();
			for (Path p : cp) {
				cpStr.append(p.toString());
				cpStr.append(File.pathSeparator);
			}
			cpStr.setLength(cpStr.length() - 1);

			command.add(cpStr.toString());

			command.add(rc.mainClass);

			command.addAll(Arrays.asList(args));

			// run the command
			new ProcessBuilder(command)
					.inheritIO()
					.start()
					.waitFor();
		} catch (Exception e) {
			throw Util.sneak(e);
		}
	}

	@Override
	public void getTasks(Consumer<Task> p){
		super.getTasks(p);

		// patch in our own tasks
		p.accept(Task.of("buildRun", this::buildRun));
	}

	// a task of building and then running mc
	public void buildRun(){
		build();
		runTask("runMinecraftClient");
	}

	public static final SimpleDateFormat DEV_VERSION_FORMAT = new SimpleDateFormat(".yyyyMMdd.HHmmss");
	private Date buildDate = null; // to save the build date for the version

	@Override
	public String getVersion(){
		String version = super.getVersion();

		// append the build date when it's the dev version
		if(version.endsWith("-dev")){
			Objects.requireNonNull(buildDate, "build date not set");
			version += DEV_VERSION_FORMAT.format(buildDate);
		}

		return version;
	}

	static {
		DEV_VERSION_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	@Override
	public JavaJarDependency build() {
		// we want to know from when the build is
		this.buildDate = new Date();

		// where the output jars are
		Path outJar = getBuildLibsDir().resolve(getModId() + "-" + getVersion() + ".jar");
		Path outJarSources = getBuildLibsDir().resolve(getModId() + "-" + getVersion() + "-sources.jar");

		try (
				// create the output sinks
				AtomicZipProcessingSink jarSink = new AtomicZipProcessingSink(outJar);
				AtomicZipProcessingSink jarSourcesSink = new AtomicZipProcessingSink(outJarSources);
		) {
			// make sure all dependencies are loaded
			context.get().modDependencies.get(); // Ugly hack

			// run the toolchain and build it
			resourcesProcessingChain().apply(jarSink, Arrays.stream(getResourceDirs()).map(DirectoryProcessingSource::new).collect(Collectors.toList()));

			// collect the output jar
			context.get().getRemappedClasses(module.get()).values().forEach(s -> s.getInputs(jarSink));

			// add a manifest containing our main class
			jarSink.sink(() -> new StringBufferInputStream(MANIFEST_CONTENT), new ProcessingId("META-INF/MANIFEST.MF", null));

			// collect the sources
			for (Path p : module.get().getSrcDirs()) {
				new DirectoryProcessingSource(p).getInputs(jarSourcesSink);
			}


			FabricModule.FabricCompilationResult compilationResult = module.get().fabricCompilationResult.get();

			try {
				// TODO: write a helper for reflection field access, with class caching!
				Class<FabricModule.FabricCompilationResult> clazz = FabricModule.FabricCompilationResult.class;
				Field javaCompilationResult = clazz.getDeclaredField("javaCompilationResult");
				javaCompilationResult.setAccessible(true);

				JavaCompilationResult result = (JavaCompilationResult) javaCompilationResult.get(compilationResult);
				result.getOutputLocation(StandardLocation.SOURCE_OUTPUT, jarSourcesSink);
			} catch (NoSuchFieldException | IllegalAccessException e) {
				throw new RuntimeException(e);
			}

			// finish the sinks
			jarSink.commit();
			jarSourcesSink.commit();
		}

		// return an object containing the output paths
		return new JavaJarDependency(outJar, outJarSources, getId());
	}

	@Override
	public String getMavenGroup(){
		return "litefabric";
	}
}

class StringBufferInputStream extends InputStream {
	private final String content;
	private final int length;
	private int position = 0;

	public StringBufferInputStream(String content) {
		this.content = content;
		this.length = content.length();
	}

	@Override
	public int read() throws IOException {
		if (this.position < this.length) {
			return this.content.charAt(this.position++);
		} else {
			return -1;
		}
	}

	@Override
	public int available() {
		return this.length - this.position;
	}
}
