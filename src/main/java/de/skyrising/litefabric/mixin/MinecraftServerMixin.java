package de.skyrising.litefabric.mixin;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import de.skyrising.litefabric.runtime.LiteFabric;
import net.minecraft.server.GameProfileCache;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixerUpper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.net.Proxy;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    @Inject(
            method = "<init>",
            at = @At("RETURN")
    )
    private void litefabric$onInit(
            File gameDir,
            Proxy proxy,
            DataFixerUpper dataFixer,
            YggdrasilAuthenticationService authService,
            MinecraftSessionService sessionService,
            GameProfileRepository gameProfileRepository,
            GameProfileCache userCache,
            CallbackInfo ci
    ) {
        LiteFabric.getInstance().onInitServer((MinecraftServer) (Object) this);
    }
}
