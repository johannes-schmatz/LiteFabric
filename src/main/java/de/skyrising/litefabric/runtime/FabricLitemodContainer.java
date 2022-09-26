package de.skyrising.litefabric.runtime;

import de.skyrising.litefabric.common.EntryPointType;
import de.skyrising.litefabric.liteloader.LiteMod;
import de.skyrising.litefabric.liteloader.modconfig.ConfigPanel;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.minecraft.client.gui.screen.Screen;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

	public LiteMod init(File configPath) {
		for (String className: this.entryPoints.getOrDefault(EntryPointType.LITEMOD, new HashSet<>())) {
			try {
				@SuppressWarnings("unchecked")
				Class<? extends LiteMod> modClass = (Class<? extends LiteMod>) FabricLauncherBase.getClass(className);
				LiteMod mod = modClass.newInstance();
				mod.init(configPath);
				try {
					this.dynamicDisplayName = mod.getName();
				} catch (Throwable ignored) {}
				try {
					this.dynamicVersion = mod.getVersion();
				} catch (Throwable ignored) {}
				this.instance = mod;
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
				Class<?> cls = FabricLitemodContainer.class.getClassLoader().loadClass(className.replace('/', '.'));
				ConfigPanel panel = (ConfigPanel) cls.newInstance();
				Method getConfigScreenFactory = cls.getMethod("getConfigScreenFactory");

				@SuppressWarnings("unchecked")
				Supplier<Screen> configScreenFactory = (Supplier<Screen>) getConfigScreenFactory.invoke(panel);

				return configScreenFactory.get(); // TODO: not sure if this will actually do what we want
			} catch (ClassNotFoundException | IllegalAccessException | InstantiationException |
					 InvocationTargetException e) {
				throw new RuntimeException(e);
			} catch (NoSuchMethodException ignored) {
				//throw new RuntimeException(e); // TODO: for now ignore it, as masa didn't release new versions yet
			}
		}

		return null;
	}
}
