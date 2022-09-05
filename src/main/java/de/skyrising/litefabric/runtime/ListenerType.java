package de.skyrising.litefabric.runtime;

import de.skyrising.litefabric.liteloader.*;
import de.skyrising.litefabric.runtime.util.ListenerHandle;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListenerType<T> {
	static final Map<Class<?>, ListenerType<?>> LISTENER_TYPES = new HashMap<>();
	static final ListenerType<HUDRenderListener> HUD_RENDER = new ListenerType<>(HUDRenderListener.class);
	static final MethodHandle MH_HUD_RENDER_PRE = HUD_RENDER.createHandle("onPreRenderHUD");
	static final MethodHandle MH_HUD_RENDER_POST = HUD_RENDER.createHandle("onPostRenderHUD");
	static final ListenerType<InitCompleteListener> INIT_COMPLETE = new ListenerType<>(InitCompleteListener.class);
	static final MethodHandle MH_INIT_COMPLETE = INIT_COMPLETE.createHandle("onInitCompleted");
	static final ListenerType<JoinGameListener> JOIN_GAME = new ListenerType<>(JoinGameListener.class);
	static final MethodHandle MH_JOIN_GAME = JOIN_GAME.createHandle("onJoinGame");
	static final ListenerType<PluginChannelListener> PLUGIN_CHANNELS = new ListenerType<>(PluginChannelListener.class);
	static final ListenerType<PostLoginListener> POST_LOGIN = new ListenerType<>(PostLoginListener.class);
	static final MethodHandle MH_POST_LOGIN = POST_LOGIN.createHandle("onPostLogin");
	static final ListenerType<PostRenderListener> POST_RENDER = new ListenerType<>(PostRenderListener.class);
	static final MethodHandle MH_POST_RENDER = POST_RENDER.createHandle("onPostRender");
	static final MethodHandle MH_POST_RENDER_ENTITIES = POST_RENDER.createHandle("onPostRenderEntities");
	static final ListenerType<PreJoinGameListener> PRE_JOIN_GAME = new ListenerType<>(PreJoinGameListener.class);
	static final ListenerType<PreRenderListener> PRE_RENDER = new ListenerType<>(PreRenderListener.class);
	static final MethodHandle MH_RENDER_WORLD = PRE_RENDER.createHandle("onRenderWorld");
	static final MethodHandle MH_SETUP_CAMERA_TRANSFORM = PRE_RENDER.createHandle("onSetupCameraTransform");
	static final MethodHandle MH_RENDER_SKY = PRE_RENDER.createHandle("onRenderSky");
	static final MethodHandle MH_RENDER_CLOUDS = PRE_RENDER.createHandle("onRenderClouds");
	static final MethodHandle MH_RENDER_TERRAIN = PRE_RENDER.createHandle("onRenderTerrain");
	static final ListenerType<ServerCommandProvider> SERVER_COMMAND_PROVIDER = new ListenerType<>(ServerCommandProvider.class);
	static final ListenerType<ShutdownListener> SHUTDOWN = new ListenerType<>(ShutdownListener.class);
	static final MethodHandle MH_SHUTDOWN = SHUTDOWN.createHandle("onShutDown");
	static final ListenerType<ViewportListener> VIEWPORT = new ListenerType<>(ViewportListener.class);
	static final MethodHandle MH_VIEWPORT_RESIZED = VIEWPORT.createHandle("onViewportResized");
	static final MethodHandle MH_FULLSCREEN_TOGGLED = VIEWPORT.createHandle("onFullScreenToggled");
	static final ListenerType<Tickable> TICKABLE = new ListenerType<>(Tickable.class);
	static final MethodHandle MH_TICK = TICKABLE.createHandle("onTick");
	static final ListenerType<ChatFilter> CHAT_FILTER = new ListenerType<>(ChatFilter.class);
	static final ListenerType<OutboundChatFilter> OUTBOUND_CHAT_FILTER = new ListenerType<>(OutboundChatFilter.class);
	final Class<T> cls;
	private final List<T> listeners = new ArrayList<>();
	private final List<ListenerHandle<T>> handles = new ArrayList<>();
	private boolean hasListeners = false;

	ListenerType(Class<T> cls) {
		LISTENER_TYPES.put(cls, this);
		this.cls = cls;
	}

	@SuppressWarnings("unchecked")
	void propose(LiteMod listener) {
		if (cls.isInstance(listener)) addListener((T) listener);
	}

	// consider cleaning this up to one bigger function...
	static void proposeAll(LiteMod instance) {
		for (ListenerType<?> listenerType : LISTENER_TYPES.values()) {
			listenerType.propose(instance);
		}
	}

	void addListener(T listener) {
		listeners.add(listener);
		for (ListenerHandle<T> handle : handles) handle.addListener(listener);
		hasListeners = true;
	}

	boolean hasListeners() {
		return hasListeners;
	}

	List<T> getListeners() {
		return listeners;
	}

	MethodHandle createHandle(String name) {
		for (Method m : cls.getDeclaredMethods()) {
			if (name.equals(m.getName())) {
				return createHandle(name, m.getReturnType(), m.getParameterTypes());
			}
		}
		throw new IllegalStateException("Method '" + name + "' not found in " + cls);
	}

	MethodHandle createHandle(String name, Class<?> retType, Class<?>... argTypes) {
		try {
			ListenerHandle<T> handle = new ListenerHandle<>(cls, name, MethodType.methodType(retType, argTypes));
			for (T listener : listeners) handle.addListener(listener);
			handles.add(handle);
			return handle.callSite.dynamicInvoker();
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	void initHandles() {
		for (ListenerHandle<T> handle : handles) handle.init();
	}
}