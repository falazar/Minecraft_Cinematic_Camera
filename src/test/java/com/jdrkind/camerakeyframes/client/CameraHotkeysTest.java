package com.jdrkind.camerakeyframes.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jdrkind.camerakeyframes.CameraPlayback;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.mockito.MockedStatic;

class CameraHotkeysTest {
    private final Minecraft minecraft = mock(Minecraft.class);
    private final ClientPacketListener connection = mock(ClientPacketListener.class);
    private MockedStatic<Minecraft> minecraftAccess;
    private MockedStatic<CameraPlayback> playbackAccess;

    @BeforeEach
    void setup() {
        minecraftAccess = mockStatic(Minecraft.class);
        minecraftAccess.when(Minecraft::getInstance).thenReturn(minecraft);
        playbackAccess = mockStatic(CameraPlayback.class);
        minecraft.player = mock(LocalPlayer.class);
        minecraft.level = mock(ClientLevel.class);
        when(minecraft.getSingleplayerServer()).thenReturn(mock(IntegratedServer.class));
        when(minecraft.getConnection()).thenReturn(connection);
        CameraKeyMappings.CAPTURE.setToDefault();
        CameraKeyMappings.PLAY.setToDefault();
    }

    @AfterEach
    void cleanup() {
        CameraKeyMappings.CAPTURE.setToDefault();
        CameraKeyMappings.PLAY.setToDefault();
        playbackAccess.close();
        minecraftAccess.close();
    }

    private void key(int key, int action) {
        CameraHotkeys.onKey(new InputEvent.Key(key, 0, action, 0));
    }

    @Test
    void registersRebindableCaptureAndPlayDefaults() {
        var event = mock(RegisterKeyMappingsEvent.class);
        CameraKeyMappings.register(event);
        verify(event).register(CameraKeyMappings.CAPTURE);
        verify(event).register(CameraKeyMappings.PLAY);
        assertEquals(GLFW.GLFW_KEY_K, CameraKeyMappings.CAPTURE.getDefaultKey().getValue());
        assertEquals(GLFW.GLFW_KEY_PAUSE, CameraKeyMappings.PLAY.getDefaultKey().getValue());
    }

    @Test
    void onlyPressesSendCommandsNotRepeatsOrReleases() {
        key(GLFW.GLFW_KEY_K, GLFW.GLFW_PRESS);
        key(GLFW.GLFW_KEY_K, GLFW.GLFW_REPEAT);
        key(GLFW.GLFW_KEY_K, GLFW.GLFW_REPEAT);
        key(GLFW.GLFW_KEY_K, GLFW.GLFW_RELEASE);
        key(GLFW.GLFW_KEY_PAUSE, GLFW.GLFW_PRESS);
        key(GLFW.GLFW_KEY_PAUSE, GLFW.GLFW_REPEAT);
        key(GLFW.GLFW_KEY_PAUSE, GLFW.GLFW_RELEASE);
        verify(connection).sendCommand("camkey add");
        verify(connection).sendCommand("camkey play");
        verifyNoMoreInteractions(connection);
    }

    @Test
    void menusPauseMissingWorldAndMultiplayerDoNotSendCommands() {
        minecraft.screen = mock(Screen.class);
        key(GLFW.GLFW_KEY_K, GLFW.GLFW_PRESS);
        minecraft.screen = null;
        when(minecraft.isPaused()).thenReturn(true);
        key(GLFW.GLFW_KEY_PAUSE, GLFW.GLFW_PRESS);
        when(minecraft.isPaused()).thenReturn(false);
        when(minecraft.getSingleplayerServer()).thenReturn(null);
        key(GLFW.GLFW_KEY_K, GLFW.GLFW_PRESS);
        minecraft.player = null;
        minecraft.level = null;
        key(GLFW.GLFW_KEY_K, GLFW.GLFW_PRESS);
        verifyNoInteractions(connection);
    }

    @Test
    void playbackAndFinalHoldDisableBothKeys() {
        playbackAccess.when(CameraPlayback::clientView).thenReturn(mock(CameraPlayback.View.class));
        key(GLFW.GLFW_KEY_K, GLFW.GLFW_PRESS);
        key(GLFW.GLFW_KEY_PAUSE, GLFW.GLFW_PRESS);
        verifyNoInteractions(connection);
    }

    @Test
    void keyboardAndMouseRebindingAreRespected() {
        CameraKeyMappings.CAPTURE.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F6));
        key(GLFW.GLFW_KEY_K, GLFW.GLFW_PRESS);
        verifyNoInteractions(connection);
        key(GLFW.GLFW_KEY_F6, GLFW.GLFW_PRESS);
        verify(connection).sendCommand("camkey add");
        CameraKeyMappings.PLAY.setKey(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4));
        key(GLFW.GLFW_KEY_PAUSE, GLFW.GLFW_PRESS);
        CameraHotkeys.onMouse(new InputEvent.MouseButton.Post(GLFW.GLFW_MOUSE_BUTTON_4, GLFW.GLFW_PRESS, 0));
        CameraHotkeys.onMouse(new InputEvent.MouseButton.Post(GLFW.GLFW_MOUSE_BUTTON_4, GLFW.GLFW_RELEASE, 0));
        verify(connection).sendCommand("camkey play");
        verifyNoMoreInteractions(connection);
    }
}
