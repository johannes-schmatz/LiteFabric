package de.skyrising.litefabric.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.client.gui.widget.TextFieldWidget;

import de.skyrising.litefabric.liteloader.client.overlays.IGuiTextField;

@Mixin(TextFieldWidget.class)
public class TextFieldWidgetMixin implements IGuiTextField {
	@Shadow
	public int x;
	@Shadow
	public int y;
	@Mutable
	@Final
	@Shadow
	private int height;
	@Mutable
	@Final
	@Shadow
	private int width;
	@Shadow
	private boolean editable;
	@Shadow
	private int firstCharacterIndex;
	@Shadow
	private int editableColor;
	@Shadow
	private int uneditableColor;

	@Override
	public int getXPosition() {
		return x;
	}

	@Override
	public void setXPosition(int xPosition) {
		x = xPosition;
	}

	@Override
	public int getYPosition() {
		return y;
	}

	@Override
	public void setYPosition(int yPosition) {
		y = yPosition;
	}

	@Override
	public int getInternalWidth() {
		return width;
	}

	@Override
	public void setInternalWidth(int width) {
		this.width = width;
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public void setHeight(int height) {
		this.height = height;
	}

	@Override
	public boolean isEnabled() {
		return editable;
	}

	@Override
	public int getLineScrollOffset() {
		return firstCharacterIndex;
	}

	@Override
	public int getTextColor() {
		return editableColor;
	}

	@Override
	public int getDisabledTextColor() {
		return uneditableColor;
	}
}
