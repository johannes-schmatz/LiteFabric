package io.github.zeichenreihe.liteornithe.mixin;

import io.github.zeichenreihe.liteornithe.runtime.LiteFabric;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalClientPlayerEntity.class)
public class ClientPlayerEntityMixin {
    @Inject(
            method = "sendChat",
            at = @At("HEAD"),
            cancellable = true
    )
    private void litefabric$onSendChatMessage(String message, CallbackInfo ci) {
        if (!LiteFabric.getInstance().filterOutboundChat(message)) {
            ci.cancel();
        }
    }
}
