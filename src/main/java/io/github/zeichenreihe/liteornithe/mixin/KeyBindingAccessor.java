package io.github.zeichenreihe.liteornithe.mixin;

import net.minecraft.client.options.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(KeyBinding.class)
public interface KeyBindingAccessor {
    @Accessor("CATEGORY_SORT_ORDER")
    static Map<String, Integer> getCategoryMap() { throw new IllegalStateException(); }
}
