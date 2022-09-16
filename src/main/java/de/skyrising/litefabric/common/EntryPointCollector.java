package de.skyrising.litefabric.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EntryPointCollector {
	private static final Logger LOGGER = LogManager.getLogger("LiteFabric|EntryPointCollector");
	private final static Map<String, Map<String, Set<String>>> entryPoints = new HashMap<>();
	private static boolean finished = false;
	public static void addEntryPoint(String mod, String type, String clazz) {
		if (finished) {
			throw new RuntimeException("Can't add entry points when the collector is finished.");
		}

		Map<String, Set<String>> map = entryPoints.getOrDefault(mod, new HashMap<>());

		Set<String> set = map.getOrDefault(type, new HashSet<>());

		set.add(clazz);

		map.put(type, set);

		entryPoints.put(mod, map);

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
