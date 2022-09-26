package de.skyrising.litefabric.runtime;

import io.github.prospector.modmenu.api.ConfigScreenFactory;
import io.github.prospector.modmenu.api.Mod;
import io.github.prospector.modmenu.api.ModMenuApi;

import java.util.*;

public class ModMenuCompat implements ModMenuApi {
    @Override
    public Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        Map<String, ConfigScreenFactory<?>> factories = new HashMap<>();
        for (FabricLitemodContainer mod: LiteFabric.getInstance().getMods()) {
            factories.put(mod.modId, mod::getConfigScreen);
        }
        return factories;
    }

    static {
        Mod.Badge.register("modmenu.badge.liteloader", 0xff70531f, 0xff47391e, "liteloader");
        // T_ODO: add registering mod menu badge, once the ported version of modmenu from Legacy ender is used
        //  see https://discord.com/channels/679635419045822474/915114155298676807/1018279263033626685
    }
}
