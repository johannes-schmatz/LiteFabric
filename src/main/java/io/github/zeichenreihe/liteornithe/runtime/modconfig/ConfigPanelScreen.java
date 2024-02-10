package io.github.zeichenreihe.liteornithe.runtime.modconfig;

import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.client.gui.screen.Screen;

import io.github.zeichenreihe.liteornithe.liteloader.LiteMod;
import io.github.zeichenreihe.liteornithe.liteloader.modconfig.ConfigPanel;
import io.github.zeichenreihe.liteornithe.liteloader.modconfig.ConfigPanelHost;

public class ConfigPanelScreen extends Screen implements ConfigPanelHost {
    private static final int MARGIN_LEFT = 40;
    private static final int MARGIN_RIGHT = 40;
    private static final int MARGIN_TOP = 40;
    private static final int MARGIN_BOTTOM = 0;
    private final Screen parent;
    private final LiteMod mod;
    private final ConfigPanel panel;
    protected String title;

    public ConfigPanelScreen(Screen parent, LiteMod mod, ConfigPanel panel) {
        this.parent = parent;
        this.mod = mod;
        this.title = mod.getName();
        this.panel = panel;
    }

    @Override
    public void init() {
        title = panel.getPanelTitle();
        panel.onPanelShown(this);
    }

    @Override
    public void tick() {
        panel.onTick(this);
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        renderBackground();
        drawCenteredString(textRenderer, title, width / 2, 15, 0xffffff);
        GlStateManager.pushMatrix();
        GlStateManager.translated(MARGIN_LEFT, MARGIN_TOP, 0);
        panel.drawPanel(this, mouseX - MARGIN_LEFT, mouseY - MARGIN_TOP, delta);
        GlStateManager.popMatrix();
        super.render(mouseX, mouseY, delta);
    }

    @Override
    protected void mouseClicked(int x, int y, int mouseButton) {
        panel.mousePressed(this, x - MARGIN_LEFT, y - MARGIN_TOP, mouseButton);
    }

    @Override
    protected void mouseReleased(int x, int y, int mouseButton) {
        panel.mouseReleased(this, x - MARGIN_LEFT, y - MARGIN_TOP, mouseButton);
    }

    @Override
    protected void keyPressed(char chr, int keyCode) {
        panel.keyPressed(this, chr, keyCode);
    }

    @Override
    public void removed() {
        panel.onPanelHidden();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends LiteMod> T getMod() {
        return (T) mod;
    }

    @Override
    public int getWidth() {
        return width - MARGIN_LEFT - MARGIN_RIGHT;
    }

    @Override
    public int getHeight() {
        return height - MARGIN_TOP - MARGIN_BOTTOM;
    }

    @Override
    public void close() {
        minecraft.openScreen(parent);
    }
}
