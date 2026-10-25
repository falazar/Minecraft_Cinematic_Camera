package com.jdrkind.camerakeyframes;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CameraKeyframeProMod.MODID)
public class CameraKeyframeProMod {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "camerakeyframepro";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public CameraKeyframeProMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Camera Keyframe Pro is loaded. Use /camkey help for capture and storage commands.");
        var playback = new CameraPlayback();
        var controller = new CameraController(playback);
        var commands = new CameraCommands(controller);
        NeoForge.EVENT_BUS.addListener(commands::register);
        NeoForge.EVENT_BUS.addListener(controller::onLogout);
        NeoForge.EVENT_BUS.addListener(controller::onServerStopped);
        NeoForge.EVENT_BUS.addListener(playback::onTick);
        NeoForge.EVENT_BUS.addListener(playback::onServerStopping);
        NeoForge.EVENT_BUS.addListener(playback::onDeath);
    }
}
