package io.github.zeichenreihe.liteornithe.liteloader;

public interface PostRenderListener extends LiteMod {
    void onPostRenderEntities(float partialTicks);
    void onPostRender(float partialTicks);
}
