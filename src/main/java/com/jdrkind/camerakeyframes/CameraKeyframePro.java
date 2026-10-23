package com.jdrkind.camerakeyframes;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CameraKeyframePro.MODID)
public class CameraKeyframePro {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "camerakeyframepro";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public CameraKeyframePro(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Hello World! Camera Keyframe Pro is loaded.");
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        event.getEntity().sendSystemMessage(Component.literal("Hello World! Camera Keyframe Pro is working."));
    }
}
