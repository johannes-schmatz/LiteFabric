package de.skyrising.litefabric.runtime;

import de.skyrising.litefabric.common.EntryPointType;
import de.skyrising.litefabric.liteloader.Configurable;
import de.skyrising.litefabric.liteloader.LiteMod;
import de.skyrising.litefabric.liteloader.modconfig.ConfigPanel;
import de.skyrising.litefabric.runtime.modconfig.ConfigPanelScreen;

import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.screen.Screen;

import java.io.*;
import java.lang.reflect.Constructor;
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

	public @Nullable LiteMod init(Path configPath) {
		File configPathFile = configPath.toFile();
		Set<String> classes = entryPoints.get(EntryPointType.LITEMOD);

		if (classes == null) return null;

		for (String className: classes) {
			try {
				@SuppressWarnings("unchecked")
				Class<? extends LiteMod> modClass = (Class<? extends LiteMod>) FabricLauncherBase.getClass(className);

				LiteMod mod = modClass.getConstructor().newInstance();
				mod.init(configPathFile);
				try {
					dynamicDisplayName = mod.getName();
				} catch (Throwable ignored) {}
				try {
					dynamicVersion = mod.getVersion();
				} catch (Throwable ignored) {}
				instance = mod;
				return mod;
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException |
					InvocationTargetException e) {
				throw new RuntimeException("Failed to initialize LiteMod " + this.modId, e);
			}
		}
		return null;
	}

	public @Nullable Screen getConfigScreen(Screen parent) {
		Set<String> configPanelClasses = this.entryPoints.get(EntryPointType.MALILIB_REDIRECTING_CONFIG_PANEL);

		if (configPanelClasses == null)
			return getConfigScreenNonMalilib(parent);

		if (configPanelClasses.size() != 1)
			throw new RuntimeException(configPanelClasses.toString());

		for (String className: configPanelClasses) {
			ClassLoader loader = FabricLitemodContainer.class.getClassLoader();
			try {
				Class<?> panelClass = loader.loadClass(className.replace('/', '.'));
				Class<?> fieldClass = loader.loadClass("fi.dy.masa.malilib.gui.config.liteloader.RedirectingConfigPanel");

				Field factory = fieldClass.getDeclaredField("configScreenFactory");
				factory.setAccessible(true);

				ConfigPanel panel = (ConfigPanel) panelClass.getConstructor().newInstance();

				@SuppressWarnings("unchecked")
				Supplier<Screen> configScreenFactory = (Supplier<Screen>) factory.get(panel);

				Class<?> baseScreen = loader.loadClass("fi.dy.masa.malilib.gui.BaseScreen");
				Method setParent = baseScreen.getDeclaredMethod("setParent", Screen.class);

				Screen configScreen = configScreenFactory.get();

				setParent.invoke(configScreen, parent);

				return configScreen;
			} catch (InstantiationException | IllegalAccessException | ClassNotFoundException | NoSuchFieldException |
					NoSuchMethodException | InvocationTargetException e) {
				e.printStackTrace();
				//throw new RuntimeException(e);
			}

			System.err.println(className + " is sus!");
			try {
				Class<?> cls = FabricLitemodContainer.class.getClassLoader().loadClass(className.replace('/', '.'));
				ConfigPanel panel = (ConfigPanel) cls.getConstructor().newInstance();
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

	private @Nullable Screen getConfigScreenNonMalilib(Screen parent) {
		// try the "normal" LiteLoader configs
		if (instance instanceof Configurable) {
			Configurable configurable = (Configurable) instance;
			Class<? extends ConfigPanel> cls = configurable.getConfigPanelClass();
			if (cls != null) {
				try {
					Constructor<? extends ConfigPanel> panelConstructor = cls.getConstructor();
					ConfigPanel panel = panelConstructor.newInstance();
					return new ConfigPanelScreen(parent, instance, panel);
				} catch (ReflectiveOperationException e) {
					throw new RuntimeException(e);
				}
			}
		}

		// we handle voxelmap here, because it's special, and we want it
		if ("voxelmap".equals(modId)) {
			ClassLoader loader = FabricLitemodContainer.class.getClassLoader();
			try {
				// get the IVoxelMap, yes it's a singleton
				Class<?> abstractVoxelMapClass = loader.loadClass("com.mamiyaotaru.voxelmap.interfaces.AbstractVoxelMap");
				Method getInstance = abstractVoxelMapClass.getDeclaredMethod("getInstance");
				Object iVoxelMap = getInstance.invoke(null);

				// the class that is actually the config screen
				@SuppressWarnings("unchecked")
				Class<? extends Screen> configScreenClass = (Class<? extends Screen>)
						loader.loadClass("com.mamiyaotaru.voxelmap.a.f");

				// the IVoxelMap passed to the config screen constructor
				Class<?> iVoxelMapClass = loader.loadClass("com.mamiyaotaru.voxelmap.interfaces.IVoxelMap");

				// get the constructor
				Constructor<? extends Screen> configScreenConstructor =
						configScreenClass.getDeclaredConstructor(Screen.class, iVoxelMapClass);

				// create the config screen
				return configScreenConstructor.newInstance(parent, iVoxelMap);
			} catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException |
					IllegalAccessException | InstantiationException e) {
				throw new RuntimeException("Make sure you have voxelmap version 1.7.1!", e);
			}
		}

		return null;
	}
}
