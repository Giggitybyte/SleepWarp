package me.thegiggitybyte.sleepwarp.fabriclike;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.thegiggitybyte.sleepwarp.config.ClientConfiguration;

public class ModMenuImpl implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ClientConfiguration::create;
    }
    
}
