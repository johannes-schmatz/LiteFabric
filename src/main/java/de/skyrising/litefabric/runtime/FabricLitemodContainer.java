package de.skyrising.litefabric.runtime;

import de.skyrising.litefabric.common.EntryPointType;
import de.skyrising.litefabric.liteloader.LiteMod;
import de.skyrising.litefabric.liteloader.modconfig.ConfigPanel;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.minecraft.client.gui.screen.Screen;

import java.io.*;
import java.lang.reflect.Field;
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
		Set<String> configPanelClasses = this.entryPoints.get(EntryPointType.MALILIB_REDIRECTING_CONFIG_PANEL);

		if (configPanelClasses == null)
			return null;

		for (String className: configPanelClasses) {
			try {
				Class<?> cls = FabricLitemodContainer.class.getClassLoader().loadClass(className.replace('/', '.'));
				ConfigPanel panel = (ConfigPanel) cls.newInstance();

				Class<?> superCls = cls.getSuperclass();
				Field configScreenFactoryField = superCls.getField("configScreenFactory");

				//TODO: this stuff doesn't work in the dev env?
				@SuppressWarnings("unchecked")
				Supplier<? extends Screen> configScreenFactory = (Supplier<? extends Screen>) configScreenFactoryField.get(panel);

				return configScreenFactory.get();

				// use a custom Screen class that just opens the malilib screen when initialized
				//return new ModConfigHelperScreen(panel);
			} catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
				throw new RuntimeException(e);
			} catch (NoSuchFieldException e) {
				throw new RuntimeException(e);
			}
		}

		return null;
	}

	private static class ModConfigHelperScreen extends Screen {
		ConfigPanel panel;
		ModConfigHelperScreen(ConfigPanel panel) {
			this.panel = panel;
		}

		@Override
		public void init() {
			panel.onPanelShown(null);
		}
	}
}
