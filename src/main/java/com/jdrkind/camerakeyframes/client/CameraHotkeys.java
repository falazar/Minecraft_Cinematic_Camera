package com.jdrkind.camerakeyframes.client;

import com.jdrkind.camerakeyframes.CameraKeyframeProMod;
import com.jdrkind.camerakeyframes.CameraPlayback;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = CameraKeyframeProMod.MODID, value = Dist.CLIENT)
public final class CameraHotkeys {
    private CameraHotkeys() {
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS) {
            onPress(InputConstants.getKey(event.getKey(), event.getScanCode()));
        }
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Post event) {
        if (event.getAction() == GLFW.GLFW_PRESS) {
            onPress(InputConstants.Type.MOUSE.getOrCreate(event.getButton()));
        }
    }

    private static void onPress(InputConstants.Key key) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || minecraft.isPaused() || minecraft.getSingleplayerServer() == null
                || minecraft.getConnection() == null || CameraPlayback.clientView() != null) {
            return;
        }
        if (CameraKeyMappings.CAPTURE.isActiveAndMatches(key)) {
            minecraft.getConnection().sendCommand("camkey add");
        } else if (CameraKeyMappings.PLAY.isActiveAndMatches(key)) {
            minecraft.getConnection().sendCommand("camkey play");
        }
    }
}
