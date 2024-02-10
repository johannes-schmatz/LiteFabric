package io.github.zeichenreihe.liteornithe.remapper.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.Collections;
import java.util.Map;

public class FileSystemDelegate implements AutoCloseable {
	private final FileSystem fs;
	private final boolean opened;

	public FileSystemDelegate(FileSystem fs, boolean opened) {
		this.fs = fs;
		this.opened = opened;
	}

	public FileSystem get() {
		return this.fs;
	}

	@Override
	public void close() throws Exception {
		if (this.opened) {
			fs.close();
		}
	}

	public static FileSystemDelegate getFileSystem(Path path) throws IOException {
		return getFileSystem(path, Collections.emptyMap());
	}

	public static FileSystemDelegate writeFileSystem(Path path) throws IOException {
		Map<String, ?> create = Collections.singletonMap("create", "true");
		return getFileSystem(path, create);
	}

	public static FileSystemDelegate getFileSystem(Path path, Map<String, ?> createArguments) throws IOException {
		URI uri = path.toUri();
		URI jarURI;
		try {
			jarURI = new URI("jar:" + uri.getScheme(), uri.getHost(), uri.getPath(), uri.getFragment());
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}

		FileSystem fs;
		boolean opened = false;
		try {
			fs = FileSystems.getFileSystem(jarURI);
		} catch (FileSystemNotFoundException ignored) {
			opened = true;
			fs = FileSystems.newFileSystem(jarURI, createArguments);
		}
		return new FileSystemDelegate(fs, opened);
	}
}