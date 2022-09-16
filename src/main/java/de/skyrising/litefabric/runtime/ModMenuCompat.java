package de.skyrising.litefabric.runtime;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
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
        // TODO: add registering mod menu badge, once the ported version of modmenu from Legacy ender is used
        //  see https://discord.com/channels/679635419045822474/915114155298676807/1018279263033626685
    }
}
