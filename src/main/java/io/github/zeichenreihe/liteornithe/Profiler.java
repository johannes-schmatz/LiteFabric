package io.github.zeichenreihe.liteornithe;

import java.util.ArrayList;
import java.util.List;

public class Profiler {
	private static final int MAX_ALLOWED_SECTIONS = 1024;
	public static boolean enabled = false;
	public static final List<ProfilerSection> SECTIONS = new ArrayList<>();
	private static String currentSection = "";
	private static long currentSectionStart = 0;
	private static int level = 0;

	public static void start(String name) {
		if (!enabled) return;

		currentSection = name;

		level++;

		currentSectionStart = System.nanoTime();
	}

	public static void push(String name) {
		long end = System.nanoTime();

		if (!enabled) return;

		addSection(end);

		currentSection = (currentSection.isEmpty() ? "" : currentSection + "/") + name;

		level++;

		currentSectionStart = System.nanoTime();
	}

	public static void pop() {
		long end = System.nanoTime();

		if (!enabled) return;

		addSection(end);

		int lastSlash = currentSection.lastIndexOf("/");
		if (lastSlash != -1) {
			currentSection = currentSection.substring(0, lastSlash);
		}

		level--;

		currentSectionStart = System.nanoTime();
	}

	public static void swap(String name) {
		long end = System.nanoTime();

		if (!enabled) return;

		addSection(end);

		int lastSlash = currentSection.lastIndexOf("/");
		if (lastSlash != -1) {
			currentSection = currentSection.substring(0, lastSlash);
		}

		currentSection = (currentSection.isEmpty() ? "" : currentSection + "/") + name;

		currentSectionStart = System.nanoTime();
	}

	public static void dump() {
		if (enabled) {
			System.out.println("Profiler: current level: " + level);
			for (ProfilerSection section : SECTIONS) {
				System.out.println(section);
			}
		} else {
			System.out.println("Profiler: disabled");
		}
	}

	private static void addSection(long end) {
		SECTIONS.add(new ProfilerSection(currentSection, currentSectionStart, end));
		if (SECTIONS.size() > MAX_ALLOWED_SECTIONS) {
			SECTIONS.remove(0);
		}
	}
}

class ProfilerSection {
	public final String name;
	public final long timeNanos;
	public ProfilerSection(String name, long start, long end) {
		this.name = name;
		this.timeNanos = end - start;
	}

	@Override
	public String toString() {
		return String.format("ProfilerSection: %12.6fms %s", timeNanos / 1e6, name);
	}
}
