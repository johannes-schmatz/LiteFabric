package de.skyrising.litefabric.common;

import de.skyrising.litefabric.remapper.ModFolderRemapper;

public class EntryPointType {
	/**
	 * A {@link EntryPointType} that indicates that it's class is a LiteMod entry point.
	 * (this also indicates that there's a mod)
	 */
	public static final String LITEMOD = "LITEMOD";
	public static final String CONFIG_GUIS = "CONFIG_GUIS";
	/**
	 * A {@link EntryPointType} that indicates that if you create an instance of it's class, that class extends
	 * {@link fi.dy.masa.malilib.gui.config.liteloader.RedirectingConfigPanel}, if you extract the
	 * {@link fi.dy.masa.malilib.gui.config.liteloader.RedirectingConfigPanel#configScreenFactory} from it,
	 * you can run that to open the screen for you.
	 * <p>
	 * masa added a getter in recent versions
	 */
	@SuppressWarnings("JavadocReference")
	public static final String MALILIB_REDIRECTING_CONFIG_PANEL = "MALILIB_REDIRECTING_CONFIG_PANEL";

	public static String getGeneratedPackageName(String modId, char separator) {
		return "litefabric" + separator + "generated" + separator + modId;
	}
	public static String getPreLaunchEntryPoint(String modId, char separator) {
		return getGeneratedPackageName(sanitizeModId(modId), separator) + separator + "PreLaunch";
	}

	private static String sanitizeModId(String modId) {
		return modId.toLowerCase().replaceAll(" ", "_");
	}

	public static String getCollectorClassName() {
		return EntryPointCollector.class.getName().replaceAll("\\.", "/");
	}

	/**
	 * returns the fabric mod file name of the litemod
	 * @param liteModFileName the original liteloader mod file
	 * @return the fabric loader mod file
	 */
	public static String getFabricModName(String liteModFileName) {
		// replace liteloader in filename with fabricloader, litemod with jar
		return liteModFileName.replaceAll("liteloader", "fabricloader").replaceAll("\\.litemod$", ".jar");
	}

	public static String remapCrashLog(String crashLog) {
		return ModFolderRemapper.getRemapper().remapCrashLog(crashLog);
	}
}
