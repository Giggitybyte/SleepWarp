package me.thegiggitybyte.sleepwarp.forge;

import dev.architectury.platform.forge.EventBuses;
import me.thegiggitybyte.sleepwarp.WarpDrive;
import me.thegiggitybyte.sleepwarp.config.ClientConfiguration;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(WarpDrive.MOD_ID)
public class SleepWarpForge {
    public SleepWarpForge() {
        EventBuses.registerModEventBus(WarpDrive.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> ClientConfiguration.create(parent))
        );
        
        
        
        WarpDrive.init();
    }
}
