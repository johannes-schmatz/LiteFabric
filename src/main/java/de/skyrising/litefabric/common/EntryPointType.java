package de.skyrising.litefabric.common;

public class EntryPointType {
	/**
	 * A {@link EntryPointType} that indicates that it's class is a LiteMod entry point. (this also indicates that there's a mod)
	 */
	public static final String LITEMOD = "LITEMOD";
	public static final String CONFIG_GUIS = "CONFIG_GUIS";
	/**
	 * A {@link EntryPointType} that indicates that if you create an instance of it's class, that class extends
	 * {@link fi.dy.masa.malilib.gui.config.liteloader.RedirectingConfigPanel}, if you extract the
	 * {@link fi.dy.masa.malilib.gui.config.liteloader.RedirectingConfigPanel#configScreenFactory} from it,
	 * you can run that to open the screen for you.
	 */
	public static final String MALILIB_REDIRECTING_CONFIG_PANEL = "MALILIB_REDIRECTING_CONFIG_PANEL";

	public static String getGeneratedPackageName(String modId, char separator) {
		return "litefabric" + separator + "generated" + separator + modId;
	}
	public static String getPreLaunchEntryPoint(String modId, char separator) {
		return getGeneratedPackageName(modId.toLowerCase(), separator) + separator + "PreLaunch";
	}

	public static String getCollectorClassName() {
		//TODO: also change this when refactoring EntryPointCollector (moving it) [fix package]
		return "de/skyrising/litefabric/common/EntryPointCollector";
	}
}
