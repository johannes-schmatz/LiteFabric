import io.github.coolcrabs.brachyura.maven.Maven;
import io.github.coolcrabs.brachyura.maven.MavenId;

import java.util.function.BiFunction;

public enum Versions {
	FABRIC_LOADER("https://maven.fabricmc.net", "net.fabricmc", "fabric-loader", "0.14.9"),
	LEGACY_YARN("https://maven.legacyfabric.net", "net.legacyfabric", "yarn", "1.12.2+build.442"), //"1.12.2+build
	// .202206171821"
	LEGACY_INTERMEDIARY("https://maven.legacyfabric.net", "net.fabricmc", "intermediary", "1.12.2"),
	QUILT_FLOWER("https://maven.quiltmc.org/repository/release", "org.quiltmc", "quiltflower", "1.8.1"),
	MODMENU(Maven.MAVEN_LOCAL, "de.skyrising", "modmenu", "1.16.9+1.12.2.76ef206"),
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
