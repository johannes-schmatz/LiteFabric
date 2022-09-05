package de.skyrising.litefabric.runtime;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.resource.ResourceMetadataProvider;
import net.minecraft.client.texture.TextureUtil;
import net.minecraft.resource.ResourcePack;
import net.minecraft.util.Identifier;
import net.minecraft.util.MetadataSerializer;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModResourcePack implements ResourcePack {
	private final ModContainer container;
	private final Path root;
	public ModResourcePack(String modId, ModContainer container) {
		this.container = container;
		this.root = container.getRootPaths().get(0);
	}

	@Override
	public InputStream open(Identifier id) throws IOException {
		return Files.newInputStream(getPath(id));
	}

	@Override
	public boolean contains(Identifier id) {
		return Files.exists(getPath(id));
	}

	@Override
	public Set<String> getNamespaces() {
		try (Stream<Path> stream = Files.list(getPath("assets"))) {
			return stream.filter(Files::isDirectory)
					.map(Path::getFileName)
					.map(Path::toString)
					.map(s -> s.endsWith("/") ? s.substring(0, s.length() - 1) : s)
					.collect(Collectors.toSet());
		} catch (IOException e) {
			return Collections.emptySet();
		}
	}

	@Nullable
	@Override
	public <T extends ResourceMetadataProvider> T parseMetadata(MetadataSerializer serializer,
																String key) throws IOException {
		InputStream packMcmeta;
		try {
			packMcmeta = this.openFile("pack.mcmeta");
		} catch (NoSuchFileException ignored) {
			return null;
		}

		BufferedReader bufferedReader = null;
		try {
			bufferedReader = new BufferedReader(new InputStreamReader(packMcmeta, StandardCharsets.UTF_8));
			JsonObject jsonObject = new JsonParser().parse(bufferedReader).getAsJsonObject();
			return serializer.fromJson(key, jsonObject);
		} finally {
			IOUtils.closeQuietly(bufferedReader);
		}

	}

	@Override
	public BufferedImage getIcon() throws IOException {
		return TextureUtil.create(this.openFile("pack.png"));
	}

	@Override
	public String getName() {
		return this.container.getMetadata().getName();
	}

	private InputStream openFile(String file) throws IOException {
		return Files.newInputStream(getPath(file));
	}

	private Path getPath(String file) {
		return this.root.resolve(file);
	}

	private Path getPath(Identifier id) {
		return this.root.resolve("assets").resolve(id.getNamespace()).resolve(id.getPath());
	}
}
