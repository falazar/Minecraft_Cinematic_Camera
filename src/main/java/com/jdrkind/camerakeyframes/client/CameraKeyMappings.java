package com.jdrkind.camerakeyframes.client;

import com.jdrkind.camerakeyframes.CameraKeyframeProMod;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = CameraKeyframeProMod.MODID, value = Dist.CLIENT)
public final class CameraKeyMappings {
    public static final KeyMapping CAPTURE = new KeyMapping("key.camerakeyframepro.capture",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.camerakeyframepro");
    public static final KeyMapping PLAY = new KeyMapping("key.camerakeyframepro.play",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAUSE, "key.categories.camerakeyframepro");

    private CameraKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CAPTURE);
        event.register(PLAY);
    }
}
