package io.github.zeichenreihe.liteornithe.mixin;

import io.github.zeichenreihe.liteornithe.runtime.LiteFabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow @Final private Minecraft minecraft;

    @Shadow protected abstract void setupCamera(float tickDelta, int anaglyphFilter);

    @Inject(
            method = "renderWorld(FJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiler/Profiler;push(Ljava/lang/String;)V"
            )
    )
    private void litefabric$onRenderWorld(float partialTicks, long timeSlice, CallbackInfo ci) {
        minecraft.profiler.push("litefabric");
        LiteFabric.getInstance().onRenderWorld(partialTicks);
        minecraft.profiler.pop();
    }

    @Inject(
            method = "renderWorld(FJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiler/Profiler;pop()V"
            )
    )
    private void litefabric$onPostRender(float partialTicks, long timeSlice, CallbackInfo ci) {
        minecraft.profiler.push("litefabric");
        setupCamera(partialTicks, 0);
        LiteFabric.getInstance().onPostRender(partialTicks);
        minecraft.profiler.pop();
    }

    @Inject(
            method = "render(IFJ)V",
            at = @At(
                    value = "INVOKE_STRING",
                    target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V",
                    args = "ldc=litParticles"
            )
    )
    private void litefabric$onPostRenderEntities(int pass, float partialTicks, long timeSlice, CallbackInfo ci) {
        minecraft.profiler.push("litefabric");
        LiteFabric.getInstance().onPostRenderEntities(partialTicks);
        minecraft.profiler.pop();
    }

    @Inject(
            method = "render(FJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GameGui;render(F)V"
            )
    )
    private void litefabric$onPreRenderHud(float partialTicks, long timeSlice, CallbackInfo ci) {
        minecraft.profiler.push("litefabric");
        LiteFabric.getInstance().onPreRenderHUD();
        minecraft.profiler.pop();
    }

    @Inject(
            method = "render(FJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GameGui;render(F)V",
                    shift = At.Shift.AFTER
            )
    )
    private void litefabric$onPostRenderHud(float partialTicks, long timeSlice, CallbackInfo ci) {
        minecraft.profiler.push("litefabric");
        LiteFabric.getInstance().onPostRenderHUD();
        minecraft.profiler.pop();
    }
}
