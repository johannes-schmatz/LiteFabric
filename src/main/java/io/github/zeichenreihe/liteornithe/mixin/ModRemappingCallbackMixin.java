package io.github.zeichenreihe.liteornithe.mixin;

import io.github.zeichenreihe.liteornithe.Profiler;
import io.github.zeichenreihe.liteornithe.remapper.ModFolderRemapper;
import net.minecraft.client.main.Main;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
public class ModRemappingCallbackMixin {
	@Inject(
			method = "main",
			at = @At("HEAD"),
			cancellable = true
	)
	private static void main(String[] args, CallbackInfo ci) {
		Profiler.enabled = true;
		Profiler.start("main");

		if (ModFolderRemapper.remapModsFolder())
			ci.cancel();

		Profiler.pop();
		Profiler.dump();
		//ci.cancel();
	}
}
