package io.github.zeichenreihe.liteornithe.mixin;

import com.terraformersmc.modmenu.util.mod.Mod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

@Mixin(Mod.Badge.class)
public class ModMenuBadeFix {
	@Redirect(
			method = "convert",
			at = @At(
					value = "INVOKE",
					target = "Ljava/util/stream/Stream;map(Ljava/util/function/Function;)Ljava/util/stream/Stream;"
			),
			remap = false
	)
	private static <A, B> Stream<B> map(Stream<A> stream, Function<A, B> fun) {
		return stream.map(fun).filter(Objects::nonNull);
	}
}
