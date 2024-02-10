package de.skyrising.litefabric.liteloader;

import net.minecraft.client.render.Window;

public interface ViewportListener extends LiteMod {
    void onViewportResized(Window resolution, int displayWidth, int displayHeight);
    void onFullScreenToggled(boolean fullScreen);
}
