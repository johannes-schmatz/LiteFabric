package io.github.zeichenreihe.liteornithe.mixin;

import io.github.zeichenreihe.liteornithe.runtime.LiteFabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.TickTimer;
import net.minecraft.client.resource.pack.ResourcePack;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {
    @Shadow @Final private TickTimer timer;
    @Shadow private boolean paused;
    @Shadow private float f_9101272;

    @Shadow @Final private List<ResourcePack> defaultResourcePacks;

    @Shadow @Final public Profiler profiler;

    @Inject(
            method = "init",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/client/render/GameRenderer"
            )
    )
    private void litefabric$onGameInitStart(CallbackInfo ci) {
        LiteFabric.getInstance().onClientInit();
    }

    @Inject(
            method = "init",
            at = @At("RETURN")
    )
    private void litefabric$onGameInitDone(CallbackInfo ci) {
        LiteFabric.getInstance().onInitCompleted((Minecraft) (Object) this);
    }

    @Inject(
            method = "runGame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/GameRenderer;render(FJ)V",
                    shift = At.Shift.AFTER
            )
    )
    private void litefabric$onClientTick(CallbackInfo ci) {
        profiler.push("litefabric");
        boolean clock = timer.ticksThisFrame > 0;
        float partialTicks = paused ? f_9101272 : timer.tickDelta;
        LiteFabric.getInstance().onTick((Minecraft) (Object) this, clock, partialTicks);
        profiler.pop();
    }

    @Inject(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;add(Ljava/lang/Object;)Z",
                    shift = At.Shift.AFTER,
                    remap = false
            )
    )
    private void litefabric$addResourcePacks(CallbackInfo ci) {
        LiteFabric.getInstance().addResourcePacks(defaultResourcePacks);
    }

    @Inject(
            method = "onResolutionChanged(II)V",
            at = @At("HEAD")
    )
    private void litefabric$onResize(CallbackInfo ci) {
        profiler.push("litefabric");
        LiteFabric.getInstance().onResize();
        profiler.pop();
    }
}
