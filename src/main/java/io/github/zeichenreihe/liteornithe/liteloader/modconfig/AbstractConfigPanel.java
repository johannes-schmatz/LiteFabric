package io.github.zeichenreihe.liteornithe.liteloader.modconfig;

import io.github.zeichenreihe.liteornithe.mixin.ButtonWidgetAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.LabelWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;

import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractConfigPanel implements ConfigPanel {
    protected final Minecraft mc;
    private final List<ConfigOption> options = new ArrayList<>();
    private int height = 0;
    private ConfigOption selected;

    public AbstractConfigPanel() {
        this.mc = Minecraft.getInstance();
    }

    @Override
    public void onPanelShown(ConfigPanelHost host) {
        this.clearOptions();
        this.addOptions(host);
    }

    protected abstract void addOptions(ConfigPanelHost host);

    protected void clearOptions() {
        options.clear();
        height = 0;
    }

    protected void addLabel(int id, int x, int y, int width, int height, int color, String... lines) {
        //TODO: implement
    }

    protected ConfigTextField addTextField(int id, int x, int y, int width, int height) {
        //TODO: implement
        return new ConfigTextField() {
            private String text = "";
            private TextFieldWidget field = new TextFieldWidget(-1, Minecraft.getInstance().textRenderer, 100,
                    100, 100, 100);
            @Override
            public TextFieldWidget getNativeTextField() {
                return field;
            }

            @Override
            public String getText() {
                return text;
            }

            @Override
            public ConfigTextField setText(String text) {
                this.text = text;
                field.setText(text);
                return this;
            }

            @Override
            public ConfigTextField setRegex(String regex, boolean force) {
                return this;
            }

            @Override
            public boolean isValid() {
                return true;
            }

            @Override
            public ConfigTextField setMaxLength(int maxLength) {
                return this;
            }
        };
    }

    protected void drawHoveringText(List<String> lines, int x, int y) {
        Screen s = Minecraft.getInstance().screen;
        if (s != null) s.renderTooltip(lines, x, y);
        // TODO: implement
    }

    public int getContentHeight() {
        // TODO: implement
        return 200;
    }

    protected <T extends ButtonWidget> T addControl(T control, ConfigOptionListener<T> listener) {
        if (control == null) return null;
        height = Math.max(control.y + ((ButtonWidgetAccessor) control).getHeight(), height);
        options.add(new ConfigOptionButton<>(control, listener));
        return control;
    }

    @Override
    public void onPanelResize(ConfigPanelHost host) {

    }

    @Override
    public void onTick(ConfigPanelHost host) {
        for (ConfigOption option : options) option.onTick();
    }

    @Override
    public void drawPanel(ConfigPanelHost host, int mouseX, int mouseY, float partialTicks) {
        for (ConfigOption option : options) option.draw(mc, mouseX, mouseY, partialTicks);
    }

    @Override
    public void mousePressed(ConfigPanelHost host, int mouseX, int mouseY, int mouseButton) {
        selected = null;
        if (mouseButton != 0) return;
        for (ConfigOption option : options) {
            if (option.mousePressed(mc, mouseX, mouseY)) {
                selected = option;
            }
        }
    }

    @Override
    public void mouseReleased(ConfigPanelHost host, int mouseX, int mouseY, int mouseButton) {
        if (selected != null && mouseButton == 0) {
            selected.mouseReleased(mc, mouseX, mouseY);
        }
        selected = null;
    }

    @Override
    public void mouseMoved(ConfigPanelHost host, int mouseX, int mouseY) {
    }

    @Override
    public void keyPressed(ConfigPanelHost host, char keyChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            host.close();
            return;
        }
        for (ConfigOption option : options) {
            if (option.keyPressed(mc, keyChar, keyCode)) {
                break;
            }
        }
    }

    public interface ConfigOptionListener<T extends ButtonWidget> {
        void actionPerformed(T control);
    }

    public interface ConfigTextField {
        TextFieldWidget getNativeTextField();
        String getText();
        ConfigTextField setText(String text);
        ConfigTextField setRegex(String regex, boolean force);
        boolean isValid();
        ConfigTextField setMaxLength(int maxLength);
    }

    abstract static class ConfigOption {
        void onTick() {}
        abstract void draw(Minecraft client, int mouseX, int mouseY, float partialTicks);
        void mouseReleased(Minecraft client, int mouseX, int mouseY) {}
        boolean mousePressed(Minecraft client, int mouseX, int mouseY) { return false; }
        boolean keyPressed(Minecraft client, char keyChar, int keyCode) { return false; }
    }

    static class ConfigOptionLabel extends ConfigOption {
        private final LabelWidget label;

        ConfigOptionLabel(LabelWidget label) {
            this.label = label;
        }

        @Override
        void draw(Minecraft client, int mouseX, int mouseY, float partialTicks) {
            label.render(client, mouseX, mouseY);
        }
    }

    static class ConfigOptionButton<T extends ButtonWidget> extends ConfigOption {
        private final T button;
        private final ConfigOptionListener<T> listener;

        ConfigOptionButton(T button, ConfigOptionListener<T> listener) {
            this.button = button;
            this.listener = listener;
        }

        @Override
        void draw(Minecraft client, int mouseX, int mouseY, float partialTicks) {
            button.render(client, mouseX, mouseY, partialTicks);
        }

        @Override
        boolean mousePressed(Minecraft client, int mouseX, int mouseY) {
            if (button.isMouseOver(client, mouseX, mouseY)) {
                button.playDownSound(client.getSoundManager());
                if (listener != null) {
                    listener.actionPerformed(button);
                }
                return true;
            }
            return false;
        }
    }
}
