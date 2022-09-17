package de.skyrising.litefabric.runtime;

import com.mojang.realmsclient.dto.RealmsServer;
import de.skyrising.litefabric.common.EntryPointCollector;
import de.skyrising.litefabric.runtime.modconfig.ConfigManager;
import de.skyrising.litefabric.liteloader.*;
import de.skyrising.litefabric.liteloader.core.ClientPluginChannels;
import de.skyrising.litefabric.liteloader.core.LiteLoader;
import de.skyrising.litefabric.liteloader.core.LiteLoaderEventBroker;
import de.skyrising.litefabric.liteloader.util.Input;
import de.skyrising.litefabric.runtime.util.InputImpl;
import de.skyrising.litefabric.mixin.MinecraftClientAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.util.Window;
import net.minecraft.entity.Entity;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.resource.ResourcePack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.*;

public class LiteFabric {
	private static final Logger LOGGER = LogManager.getLogger("LiteFabric");
	private static final LiteFabric INSTANCE = new LiteFabric();
	final Map<String, FabricLitemodContainer> mods = new LinkedHashMap<>();
	private final ClientPluginChannelsImpl clientPluginChannels = new ClientPluginChannelsImpl();
	private final InputImpl input = new InputImpl();
	public final ConfigManager configManager = new ConfigManager();

	private LiteFabric(){}

	public void addResourcePacks(List<ResourcePack> resourcePacks) {
		addMods();

		for (String modId: mods.keySet()) {
			Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(modId);

			container.ifPresent(modContainer ->
				resourcePacks.add(new ModResourcePack(modId, modContainer))
			);
		}
	}

	/**
	 * Call this as early as possible, after all mods ran the PreLaunch EntryPoint.
	 */
	private void addMods() {
		LOGGER.debug("Finishing collecting all LiteMod entry points.");

		Map<String, Map<String, Set<String>>> entryPoints = EntryPointCollector.finish();
		for (Map.Entry<String, Map<String, Set<String>>> mod: entryPoints.entrySet()) {
			String modId = mod.getKey();
			FabricLitemodContainer modContainer = new FabricLitemodContainer(modId, mod.getValue());
			mods.put(modId, modContainer);
		}
	}


	public static LiteFabric getInstance(){
		return INSTANCE;
	}

	public static Version getMinecraftVersion() {
		return FabricLoader.getInstance().getModContainer("minecraft").orElseThrow(IllegalStateException::new).getMetadata().getVersion();
	}

	public Collection<FabricLitemodContainer> getMods(){
		return Collections.unmodifiableCollection(mods.values());
	}

	public void onClientInit(){
		LOGGER.debug("Initializing litemods");

		onResize();
		input.load();

		File configPath = FabricLoader.getInstance().getConfigDir().toFile();
		for (FabricLitemodContainer mod : mods.values()) {
			LiteMod instance = mod.init(configPath);

			configManager.registerMod(instance);
			configManager.initConfig(instance);
			ListenerType.proposeAll(instance);
		}

		for (PluginChannelListener listener : ListenerType.PLUGIN_CHANNELS.getListeners()) {
			clientPluginChannels.addListener(listener);
		}
		for (ListenerType<?> listener : ListenerType.LISTENER_TYPES.values()) {
			listener.initHandles();
		}
	}

	public void onInitCompleted(MinecraftClient client){
		LiteLoader liteLoader = LiteLoader.getInstance();
		try {
			ListenerType.MH_INIT_COMPLETE.invokeExact(client, liteLoader);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	public void onTick(MinecraftClient client, boolean clock, float partialTicks){
		input.onTick();
		configManager.tick();
		Entity cameraEntity = client.getCameraEntity();
		boolean inGame = cameraEntity != null && cameraEntity.world != null;
		try {
			ListenerType.MH_TICK.invokeExact(client, partialTicks, inGame, clock);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
		if (!((MinecraftClientAccessor) client).isRunning()) onShutdown();
	}

	private void onShutdown() {
		input.save();
		try {
			ListenerType.MH_SHUTDOWN.invokeExact();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	public void onRenderWorld(float partialTicks) {
		try {
			ListenerType.MH_RENDER_WORLD.invokeExact(partialTicks);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	public void onPostRender(float partialTicks) {
		try {
			ListenerType.MH_POST_RENDER.invokeExact(partialTicks);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	public void onPostRenderEntities(float partialTicks) {
		try {
			ListenerType.MH_POST_RENDER_ENTITIES.invokeExact(partialTicks);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	public void onPostLogin(PacketListener packetListener, LoginSuccessS2CPacket loginPacket) {
		clientPluginChannels.onPostLogin();
		try {
			ListenerType.MH_POST_LOGIN.invokeExact(packetListener, loginPacket);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	public void onJoinGame(PacketListener packetListener, GameJoinS2CPacket joinPacket, ServerInfo serverData) {
		ListenerType<PreJoinGameListener> preJoinGame = ListenerType.PRE_JOIN_GAME;
		if (preJoinGame.hasListeners()) {
			for (PreJoinGameListener listener : preJoinGame.getListeners()) {
				if (!listener.onPreJoinGame(packetListener, joinPacket)) {
					LOGGER.warn("Ignoring game join cancellation by {}", listener.getName());
				}
			}
		}
		clientPluginChannels.onJoinGame();
		try {
			ListenerType.MH_JOIN_GAME.invokeExact(packetListener, joinPacket, serverData, (RealmsServer) null);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	public void onInitServer(MinecraftServer server) {
		ListenerType<ServerCommandProvider> serverCommandProviders = ListenerType.SERVER_COMMAND_PROVIDER;
		if (serverCommandProviders.hasListeners()) {
			CommandManager manager = (CommandManager) server.getCommandManager();
			for (ServerCommandProvider provider : serverCommandProviders.getListeners()) {
				provider.provideCommands(manager);
			}
		}
	}

	public void onPreRenderHUD() {
		if (!ListenerType.HUD_RENDER.hasListeners()) return;
		Window window = new Window(MinecraftClient.getInstance());
		try {
			ListenerType.MH_HUD_RENDER_PRE.invokeExact(window.getWidth(), window.getHeight());
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	public void onPostRenderHUD() {
		if (!ListenerType.HUD_RENDER.hasListeners()) return;
		Window window = new Window(MinecraftClient.getInstance());
		try {
			ListenerType.MH_HUD_RENDER_POST.invokeExact(window.getWidth(), window.getHeight());
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	private boolean wasFullscreen;
	public void onResize() {
		MinecraftClient client = MinecraftClient.getInstance();
		boolean fullscreen = client.isWindowFocused(); // incorrect yarn name
		boolean fullscreenChanged = fullscreen != wasFullscreen;
		if (fullscreenChanged) wasFullscreen = fullscreen;
		ListenerType<ViewportListener> viewportListeners = ListenerType.VIEWPORT;
		if (!viewportListeners.hasListeners()) return;
		Window window = new Window(client);
		int width = client.width;
		int height = client.height;
		try {
			if (fullscreenChanged) {
				ListenerType.MH_FULLSCREEN_TOGGLED.invokeExact(fullscreen);
			}
			ListenerType.MH_VIEWPORT_RESIZED.invokeExact(window, width, height);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	public Text filterChat(Text original) {
		ListenerType<ChatFilter> chatFilters = ListenerType.CHAT_FILTER;
		if (!chatFilters.hasListeners()) return original;
		Text result = original;
		String message = original.asFormattedString();
		for (ChatFilter filter : chatFilters.getListeners()) {
			LiteLoaderEventBroker.ReturnValue<Text> retVal = new LiteLoaderEventBroker.ReturnValue<>();
			if (filter.onChat(result, message, retVal)) {
				result = retVal.get();
				if (result == null) result = new LiteralText("");
				message = result.asFormattedString();
			} else {
				return null;
			}
		}
		return result;
	}

	public boolean filterOutboundChat(String message) {
		for (OutboundChatFilter filter : ListenerType.OUTBOUND_CHAT_FILTER.getListeners()) {
			if (!filter.onSendChatMessage(message)) {
				return false;
			}
		}
		return true;
	}

	public ClientPluginChannels getClientPluginChannels() {
		return clientPluginChannels;
	}

	public Input getInput() {
		return input;
	}
}
