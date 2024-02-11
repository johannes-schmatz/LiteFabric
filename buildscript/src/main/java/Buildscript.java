import io.github.coolcrabs.brachyura.decompiler.BrachyuraDecompiler;
import io.github.coolcrabs.brachyura.decompiler.fernflower.FernflowerDecompiler;
import io.github.coolcrabs.brachyura.dependency.JavaJarDependency;
import io.github.coolcrabs.brachyura.fabric.*;
import io.github.coolcrabs.brachyura.fabric.FabricContext.ModDependencyCollector;
import io.github.coolcrabs.brachyura.maven.Maven;
import io.github.coolcrabs.brachyura.maven.MavenId;
import io.github.coolcrabs.brachyura.minecraft.Minecraft;
import io.github.coolcrabs.brachyura.minecraft.VersionMeta;
import io.github.coolcrabs.brachyura.processing.sinks.AtomicZipProcessingSink;
import io.github.coolcrabs.brachyura.processing.sources.DirectoryProcessingSource;
import io.github.coolcrabs.brachyura.project.Task;
import net.fabricmc.mappingio.tree.MappingTree;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Buildscript extends SimpleFabricProject {
	@Override
	public String getMavenGroup(){
		return "liteornithe";
	}

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
		//return Versions.LEGACY_YARN.ofMaven(Yarn::ofMaven).tree;
		return Versions.V2_FEATHER.ofMaven(Yarn::ofMaven).tree;
	}

	@Override
	public FabricLoader getLoader() {
		//return Versions.FABRIC_LOADER.ofMaven(FabricLoader::new);
		return Versions.V2_QUILT_LOADER.ofMaven(FabricLoader::new);
	}

	@Override
	public void getModDependencies(ModDependencyCollector d) {
		// Fix for brachyura using the default java User-Agent, which is blocked by cloudflare
		System.setProperty("http.agent", "brachyura http agent");
		d.add(
				//Versions.MODMENU.ofMaven(Maven::getMavenJarDep),
				Versions.V2_MODMENU.ofMaven(Maven::getMavenJarDep),
				FabricContext.ModDependencyFlag.COMPILE,
				FabricContext.ModDependencyFlag.RUNTIME
		);
		// modmenu needs osl-entrypoints for running
		d.add(
				Maven.getMavenJarDep("https://maven.ornithemc.net/releases",
						new MavenId("net.ornithemc.osl", "entrypoints", "0.4.2+mc13w16a-04192037-mc1.14.4")
				),
				FabricContext.ModDependencyFlag.RUNTIME
		);
		// and it also needs osl-resource-loader
		d.add(
				Maven.getMavenJarDep("https://maven.ornithemc.net/releases",
						new MavenId("net.ornithemc.osl", "resource-loader", "0.4.3+mc13w26a-mc1.12.2")
				),
				FabricContext.ModDependencyFlag.RUNTIME
		);

		// the resource loader then needs livecycle-events...
		d.add(
				Maven.getMavenJarDep("https://maven.ornithemc.net/releases",
						new MavenId("net.ornithemc.osl", "lifecycle-events", "0.5.2+mc13w36a-09051446-mc1.13")
				),
				FabricContext.ModDependencyFlag.RUNTIME
		);

		// entry points and resource-loader need osl core..
		d.add(
				Maven.getMavenJarDep("https://maven.ornithemc.net/releases",
						new MavenId("net.ornithemc.osl", "core", "0.5.0")
				),
				FabricContext.ModDependencyFlag.RUNTIME
		);
	}

	@Override
	public BrachyuraDecompiler decompiler() {
		// Uses QuiltFlower instead of CFR
		return new FernflowerDecompiler(
				Versions.QUILT_FLOWER.ofMaven(Maven::getMavenJarDep)
		);
	}

	public class CustomFabricContext extends SimpleFabricContext {
		@Override
		protected MappingTree createIntermediary() {
			// use legacy fabric intermediary
			//return Versions.LEGACY_INTERMEDIARY.ofMaven(Intermediary::ofMaven).tree;
			return Versions.V2_CALAMUS.ofMaven(Intermediary::ofMaven).tree;
		}
	}

	@Override
	protected FabricModule createModule() {
		return new CustomFabricModule(context.get());
	}

	public class CustomFabricModule extends SimpleFabricModule {
		public CustomFabricModule(FabricContext context) {
			super(context);
		}

		@Override
		public List<String> ideVmArgs(boolean client) {
			ArrayList<String> args = new ArrayList<>();

			// for fabric
			args.add("-Dfabric.development=true");
			args.add("-Dfabric.remapClasspathFile=" + context.runtimeRemapClasspath.get());
			args.add("-Dfabric.log.disableAnsi=false");

			// for quilt
			args.add("-Dloader.development=true");
			args.add("-Dloader.remapClasspathFile=" + context.runtimeRemapClasspath.get());
			args.add("-Dloader.log.disableAnsi=false");

			// for both
			try {
				args.add("-Dlog4j.configurationFile=" + writeLog4jXml());
				args.add("-Dlog4j2.formatMsgNoLookups=true");
			} catch (Exception e) {
				throw Util.sneak(e);
			}

			if (client) {
				String natives = context.extractedNatives.get().stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
				args.add("-Djava.library.path=" + natives);
				args.add("-Dtorg.lwjgl.librarypath=" + natives);
				if (OsUtil.OS == OsUtil.Os.OSX) {
					args.add("-XstartOnFirstThread");
				}
			}

			args.add("-D" + "java.awt.headless=true"); // that way fabric loader doesn't open the pop-up screen

			return args;
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

	public static final DateTimeFormatter DEV_VERSION_FORMATTER = DateTimeFormatter.ofPattern(".yyyyMMdd.HHmmss");
	private LocalDateTime buildDate = null; // to save the build date for the version

	@Override
	public String getVersion(){
		String version = super.getVersion();

		// append the build date when it's the dev version
		if(version.endsWith("-dev")){
			Objects.requireNonNull(buildDate, "build date not set");
			version += buildDate.format(DEV_VERSION_FORMATTER);
		}

		return version;
	}

	@Override
	public JavaJarDependency build() {
		// we want to know from when the build is
		this.buildDate = LocalDateTime.now();

		// where the output jars are
		Path outJar = getBuildLibsDir().resolve(getModId() + "-" + getVersion() + ".jar");
		//Path outJarSources = getBuildLibsDir().resolve(getModId() + "-" + getVersion() + "-sources.jar");

		try (
			// create the output sinks
			AtomicZipProcessingSink jarSink = new AtomicZipProcessingSink(outJar)//;
			//AtomicZipProcessingSink jarSourcesSink = new AtomicZipProcessingSink(outJarSources)
		) {
			// make sure all dependencies are loaded
			context.get().modDependencies.get(); // Ugly hack

			// run the toolchain and build it
			resourcesProcessingChain()
					.apply(jarSink,
							Arrays.stream(getResourceDirs())
									.map(DirectoryProcessingSource::new)
									.collect(Collectors.toList())
					);

			// collect the output jar
			context.get().getRemappedClasses(module.get()).values().forEach(s -> s.getInputs(jarSink));
/*
			// collect the sources
			for (Path p : module.get().getSrcDirs()) {
				new DirectoryProcessingSource(p).getInputs(jarSourcesSink);
			}

			FabricModule.FabricCompilationResult compilationResult = module.get().fabricCompilationResult.get();

			try {
				// TODO: with new brachyura version this should be easier
				Class<FabricModule.FabricCompilationResult> clazz = FabricModule.FabricCompilationResult.class;
				Field javaCompilationResult = clazz.getDeclaredField("javaCompilationResult");
				javaCompilationResult.setAccessible(true);

				JavaCompilationResult result = (JavaCompilationResult) javaCompilationResult.get(compilationResult);
				result.getOutputLocation(StandardLocation.SOURCE_OUTPUT, jarSourcesSink);
			} catch (NoSuchFieldException | IllegalAccessException e) {
				throw new RuntimeException(e);
			}*/

			// finish the sinks
			jarSink.commit();
			//jarSourcesSink.commit();
		}

		// return an object containing the output paths
		//return new JavaJarDependency(outJar, outJarSources, getId());
		return new JavaJarDependency(outJar, null, getId());
	}
}
