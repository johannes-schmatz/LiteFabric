package de.skyrising.litefabric.mixin;

import de.skyrising.litefabric.runtime.LiteFabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.LoginS2CPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin implements ClientPlayPacketHandler {
    @Inject(
            method = "handleLogin",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/PacketUtils;ensureOnSameThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/handler/PacketHandler;Lnet/minecraft/util/BlockableEventLoop;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void litefabric$handleJoinGame(LoginS2CPacket packet, CallbackInfo ci) {
        try {
            LiteFabric.getInstance().onJoinGame(this, packet, Minecraft.getInstance().getCurrentServerEntry());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Inject(
            method = "handleCustomPayload",
            at = @At("RETURN")
    )
    private void litefabric$handleCustomPayload(CustomPayloadS2CPacket packet, CallbackInfo ci) {
        try {
            LiteFabric.getInstance().getClientPluginChannels().onPluginChannelMessage(packet);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Inject(
            method = "handleChatMessage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GameGui;handleChat(Lnet/minecraft/text/MessageType;Lnet/minecraft/text/Text;)V"
            ),
            cancellable = true
    )
    private void litefabric$handleChat(ChatMessageS2CPacket packet, CallbackInfo ci) {
        Text original = packet.getMessage();
        if (original == null) return;
        Text filtered = LiteFabric.getInstance().filterChat(original);
        if (filtered != original) {
            if (filtered == null) {
                ci.cancel();
                return;
            }
            ((ChatMessageS2CPacketAccessor) packet).setText(filtered);
        }
    }
}
