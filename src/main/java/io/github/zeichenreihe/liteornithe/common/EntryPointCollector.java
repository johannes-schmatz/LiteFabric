package io.github.zeichenreihe.liteornithe.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EntryPointCollector {
	private static final Logger LOGGER = LogManager.getLogger("LiteFabric|EntryPointCollector");
	private static final Map<String, Map<String, Set<String>>> entryPoints = new HashMap<>();
	private static boolean finished = false;
	public static void addEntryPoint(String mod, String type, String clazz) {
		if (finished) {
			throw new RuntimeException("Can't add entry points when the collector is finished.");
		}

		entryPoints
				.computeIfAbsent(mod, key -> new HashMap<>())
				.computeIfAbsent(type, key -> new HashSet<>())
				.add(clazz);

		LOGGER.debug("Added LiteFabric EntryPoint: modId: " + mod + ", type: " + type + ", class: " + clazz);
	}

	public static boolean isFinished() {
		return finished;
	}
	public static Map<String, Map<String, Set<String>>> finish() {
		finished = true;
		return entryPoints;
	}
}
