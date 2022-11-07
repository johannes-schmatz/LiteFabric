package de.skyrising.litefabric.runtime;

import de.skyrising.litefabric.common.EntryPointType;
import de.skyrising.litefabric.liteloader.LiteMod;
import de.skyrising.litefabric.liteloader.modconfig.ConfigPanel;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.minecraft.client.gui.screen.Screen;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

public class FabricLitemodContainer {
	private LiteMod instance = null;
	public final String modId;
	private final Map<String, Set<String>> entryPoints;
	private String dynamicDisplayName = null;
	private String dynamicVersion = null;
	public FabricLitemodContainer(String modId, Map<String, Set<String>> entryPoints) {
		this.modId = modId;
		this.entryPoints = entryPoints;
	}

	public String getDynamicDisplayName() {
		return dynamicDisplayName;
	}

	public String getDynamicVersion() {
		return dynamicVersion;
	}

	public LiteMod getInstance() {
		return instance;
	}

	public LiteMod init(Path configPath) {
		File configPathFile = configPath.toFile();
		Set<String> classes = entryPoints.get(EntryPointType.LITEMOD);

		if (classes == null) return null;

		for (String className: classes) {
			try {
				@SuppressWarnings("unchecked")
				Class<? extends LiteMod> modClass = (Class<? extends LiteMod>) FabricLauncherBase.getClass(className);

				LiteMod mod = modClass.newInstance();
				mod.init(configPathFile);
				try {
					dynamicDisplayName = mod.getName();
				} catch (Throwable ignored) {}
				try {
					dynamicVersion = mod.getVersion();
				} catch (Throwable ignored) {}
				instance = mod;
				return mod;
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
				throw new RuntimeException("Failed to initialize LiteMod " + this.modId, e);
			}
		}
		return null;
	}

	public Screen getConfigScreen(Screen parent) {
		// TODO: voxelmap: this.instance instanceof Configurable, then cast, then get the panel class...

		Set<String> configPanelClasses = this.entryPoints.get(EntryPointType.MALILIB_REDIRECTING_CONFIG_PANEL);

		if (configPanelClasses == null)
			return null;

		if (configPanelClasses.size() != 1)
			throw new RuntimeException(configPanelClasses.toString());

		for (String className: configPanelClasses) {
			try {
				ClassLoader loader = FabricLitemodContainer.class.getClassLoader();

				Class<?> panelClass = loader.loadClass(className.replace('/', '.'));
				Class<?> fieldClass = loader.loadClass("fi.dy.masa.malilib.gui.config.liteloader.RedirectingConfigPanel");

				Field factory = fieldClass.getDeclaredField("configScreenFactory");
				factory.setAccessible(true);

				ConfigPanel panel = (ConfigPanel) panelClass.newInstance();

				@SuppressWarnings("unchecked")
				Supplier<Screen> configScreenFactory = (Supplier<Screen>) factory.get(panel);

				return configScreenFactory.get();
			} catch (InstantiationException | IllegalAccessException | ClassNotFoundException | NoSuchFieldException e) {
				e.printStackTrace();
				//throw new RuntimeException(e);
			}


			System.err.println(className + " is sus!");
			try {
				Class<?> cls = FabricLitemodContainer.class.getClassLoader().loadClass(className.replace('/', '.'));
				ConfigPanel panel = (ConfigPanel) cls.newInstance();
				Method getConfigScreenFactory = cls.getMethod("getConfigScreenFactory");

				@SuppressWarnings("unchecked")
				Supplier<Screen> configScreenFactory = (Supplier<Screen>) getConfigScreenFactory.invoke(panel);

				return configScreenFactory.get(); // TODO: not sure if this will actually do what we want
			} catch (ClassNotFoundException | IllegalAccessException | InstantiationException |
					 InvocationTargetException | NoSuchMethodException e) {
				e.printStackTrace();
				//throw new RuntimeException(e); // TODO: for now ignore it, as masa didn't release new versions yet
			}
		}

		return null;
	}
}
