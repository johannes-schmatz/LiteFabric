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
}
