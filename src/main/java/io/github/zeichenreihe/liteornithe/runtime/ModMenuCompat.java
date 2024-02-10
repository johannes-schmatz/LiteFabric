package io.github.zeichenreihe.liteornithe.runtime;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
//import com.enderzombie102.modmenu.api.Badge;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ModMenuCompat implements ModMenuApi {
    // TODO: instead of having LiteFabric have one ModMenuCompat for all of the remapped mods, use objectwebs asm api to build ModMenuCompat classes for each
    //  mod (which can then also call into LiteFabric for helper methods), but provide the mods screen themself
    @Override
    public @NotNull Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        Map<String, ConfigScreenFactory<?>> factories = new HashMap<>();
        for (FabricLitemodContainer mod: LiteFabric.getInstance().getMods()) {
            factories.put(mod.modId, mod::getConfigScreen);
        }
        return factories;
    }
/*
    // TODO: badges with ornithe modmenu port?
    @Override
    public void onSetupBadges() {
        Badge.register("modmenu.badge.liteloader", 0xff70531f, 0xff47391e, "liteloader");
    }*/

    static {
        // register the badge only for enders port
        // T_ODO: add registering mod menu badge, once the ported version of modmenu from Legacy ender is used
        //  see https://discord.com/channels/679635419045822474/915114155298676807/1018279263033626685
    }
}
