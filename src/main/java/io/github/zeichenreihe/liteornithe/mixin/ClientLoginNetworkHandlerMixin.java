package io.github.zeichenreihe.liteornithe.mixin;

import io.github.zeichenreihe.liteornithe.runtime.LiteFabric;

import net.minecraft.client.network.handler.ClientLoginNetworkHandler;
import net.minecraft.client.network.handler.ClientLoginPacketHandler;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLoginNetworkHandler.class)
public abstract class ClientLoginNetworkHandlerMixin implements ClientLoginPacketHandler {
    @Inject(
            method = "handleLoginSuccess",
            at = @At("HEAD")
    )
    private void litefabric$handleLoginSuccess(LoginSuccessS2CPacket packet, CallbackInfo ci) {
        LiteFabric.getInstance().onPostLogin(this, packet);
    }
}
