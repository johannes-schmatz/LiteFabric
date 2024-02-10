package io.github.zeichenreihe.liteornithe.runtime;

import com.mojang.realmsclient.dto.RealmsServer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.client.render.Window;
import net.minecraft.client.resource.pack.ResourcePack;
import net.minecraft.entity.Entity;
import net.minecraft.network.handler.PacketHandler;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.network.packet.s2c.play.LoginS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.handler.CommandManager;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

import io.github.zeichenreihe.liteornithe.common.EntryPointCollector;
import io.github.zeichenreihe.liteornithe.liteloader.*;
import io.github.zeichenreihe.liteornithe.liteloader.core.ClientPluginChannels;
import io.github.zeichenreihe.liteornithe.liteloader.core.LiteLoader;
import io.github.zeichenreihe.liteornithe.liteloader.core.LiteLoaderEventBroker;
import io.github.zeichenreihe.liteornithe.liteloader.util.Input;
import io.github.zeichenreihe.liteornithe.mixin.MinecraftClientAccessor;
import io.github.zeichenreihe.liteornithe.runtime.modconfig.ConfigManager;
import io.github.zeichenreihe.liteornithe.runtime.util.InputImpl;

import java.nio.file.Path;
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

		for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
			String modId = container.getMetadata().getId();

			// java doesn't have resources, mcs are already added
			if ("java".equals(modId) || "minecraft".equals(modId)) continue;

			resourcePacks.add(new ModResourcePack(modId, container));
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
		return FabricLoader.getInstance()
				.getModContainer("minecraft")
				.orElseThrow(IllegalStateException::new)
				.getMetadata()
				.getVersion();
	}

	public Collection<FabricLitemodContainer> getMods(){
		return Collections.unmodifiableCollection(mods.values());
	}

	public void onClientInit(){
		LOGGER.debug("Initializing litemods");

		onResize();
		input.load();

		Path configPath = FabricLoader.getInstance().getConfigDir();
		for (FabricLitemodContainer mod : mods.values()) {
			LiteMod instance = mod.init(configPath);

			configManager.registerMod(instance);
			configManager.initConfig(instance);

			// set up the method handle callbacks
			ListenerType.proposeAll(instance);
		}

		for (PluginChannelListener listener : ListenerType.PLUGIN_CHANNELS.getListeners()) {
			clientPluginChannels.addListener(listener);
		}
		for (ListenerType<?> listener : ListenerType.LISTENER_TYPES.values()) {
			listener.initHandles();
		}
	}

	public void onInitCompleted(Minecraft client){
		LiteLoader liteLoader = LiteLoader.getInstance();
		try {
			ListenerType.MH_INIT_COMPLETE.invokeExact(client, liteLoader);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	public void onTick(Minecraft client, boolean clock, float partialTicks){
		input.onTick();
		configManager.tick();
		Entity cameraEntity = client.getCamera();
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

	public void onPostLogin(PacketHandler packetListener, LoginSuccessS2CPacket loginPacket) {
		clientPluginChannels.onPostLogin();
		try {
			ListenerType.MH_POST_LOGIN.invokeExact(packetListener, loginPacket);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	public void onJoinGame(PacketHandler packetListener, LoginS2CPacket joinPacket, ServerListEntry serverData) {
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
			CommandManager manager = (CommandManager) server.getCommandHandler();
			for (ServerCommandProvider provider : serverCommandProviders.getListeners()) {
				provider.provideCommands(manager);
			}
		}
	}

	public void onPreRenderHUD() {
		if (!ListenerType.HUD_RENDER.hasListeners()) return;
		Window window = new Window(Minecraft.getInstance());
		try {
			ListenerType.MH_HUD_RENDER_PRE.invokeExact(window.getWidth(), window.getHeight());
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	public void onPostRenderHUD() {
		if (!ListenerType.HUD_RENDER.hasListeners()) return;
		Window window = new Window(Minecraft.getInstance());
		try {
			ListenerType.MH_HUD_RENDER_POST.invokeExact(window.getWidth(), window.getHeight());
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	private boolean wasFullscreen;
	public void onResize() {
		Minecraft client = Minecraft.getInstance();

		boolean fullscreen = client.isWindowFocused();
		boolean fullscreenChanged = fullscreen != wasFullscreen;

		if (fullscreenChanged) {
			wasFullscreen = fullscreen;
		}

		if (!ListenerType.VIEWPORT.hasListeners()) {
			return;
		}

		try {
			Window window = new Window(client);
			if (fullscreenChanged) {
				ListenerType.MH_FULLSCREEN_TOGGLED.invokeExact(fullscreen);
			}
			ListenerType.MH_VIEWPORT_RESIZED.invokeExact(window, client.width, client.height);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	public Text filterChat(Text original) {
		ListenerType<ChatFilter> chatFilters = ListenerType.CHAT_FILTER;
		if (!chatFilters.hasListeners()) return original;
		Text result = original;
		String message = original.getFormattedContent();
		for (ChatFilter filter : chatFilters.getListeners()) {
			LiteLoaderEventBroker.ReturnValue<Text> retVal = new LiteLoaderEventBroker.ReturnValue<>();
			if (filter.onChat(result, message, retVal)) {
				result = retVal.get();
				if (result == null) result = new LiteralText("");
				message = result.getFormattedContent();
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
