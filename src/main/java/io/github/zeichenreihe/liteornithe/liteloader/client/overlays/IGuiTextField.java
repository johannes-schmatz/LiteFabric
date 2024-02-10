package io.github.zeichenreihe.liteornithe.liteloader.client.overlays;

public interface IGuiTextField {
	int getXPosition();
	void setXPosition(int xPosition);
	int getYPosition();
	void setYPosition(int yPosition);
	int getInternalWidth();
	void setInternalWidth(int width);
	int getHeight();
	void setHeight(int height);
	boolean isEnabled();
	int getLineScrollOffset();
	int getTextColor();
	int getDisabledTextColor();
}
