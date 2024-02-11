import io.github.coolcrabs.brachyura.maven.Maven;
import io.github.coolcrabs.brachyura.maven.MavenId;

import java.util.function.BiFunction;

public enum Versions {
	FABRIC_LOADER("https://maven.fabricmc.net", "net.fabricmc", "fabric-loader", "0.14.9"),
	LEGACY_YARN("https://maven.legacyfabric.net", "net.legacyfabric", "yarn", "1.12.2+build.442"),
	LEGACY_INTERMEDIARY("https://maven.legacyfabric.net", "net.fabricmc", "intermediary", "1.12.2"),
	QUILT_FLOWER("https://maven.quiltmc.org/repository/release", "org.quiltmc", "quiltflower", "1.8.1"),
	MODMENU(Maven.MAVEN_LOCAL, "de.skyrising", "modmenu", "1.16.9+1.12.2.76ef206"),

	V2_QUILT_LOADER("https://maven.quiltmc.org/repository/release", "org.quiltmc", "quilt-loader", "0.23.1"),
	V2_FEATHER("https://maven.ornithemc.net/releases", "net.ornithemc", "feather", "1.12.2+build.19"),
	V2_CALAMUS("https://maven.ornithemc.net/releases", "net.ornithemc", "calamus-intermediary", "1.12.2"),
	V2_MODMENU("https://maven.ornithemc.net/releases", "com.terraformersmc", "modmenu", "0.2.0+mc1.12.2")
	;

	public static final String MINECRAFT = "1.12.2";

	private final String repo;
	private final String group;
	private final String artifact;
	private final String version;

	Versions(String repo, String group, String artifact, String version) {
		this.repo = repo;
		this.group = group;
		this.artifact = artifact;
		this.version = version;
	}

	private MavenId toMavenId() {
		return new MavenId(this.group, this.artifact, this.version);
	}

	public <T> T ofMaven(BiFunction<String, MavenId, T> function) {
		return function.apply(this.repo, this.toMavenId());
	}
}
