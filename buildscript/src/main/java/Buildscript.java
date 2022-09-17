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
	@Override
	public String getMavenGroup(){
		return "litefabric";
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

	public class CustomFabricModule extends SimpleFabricModule {
		public CustomFabricModule(FabricContext context) {
			super(context);
		}

		@Override
		public List<String> ideVmArgs(boolean client) {
			List<String> args = super.ideVmArgs(client);
			args.add("-Djava.awt.headless=true"); // that way fabric loader doesn't open the pop up screen
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
			}

			// finish the sinks
			jarSink.commit();
			jarSourcesSink.commit();
		}

		// return an object containing the output paths
		return new JavaJarDependency(outJar, outJarSources, getId());
	}
}
