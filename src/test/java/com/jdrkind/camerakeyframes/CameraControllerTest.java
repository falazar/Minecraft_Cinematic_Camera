package com.jdrkind.camerakeyframes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CameraControllerTest {
    @TempDir
    Path world;

    private final CameraPlayback playback = mock(CameraPlayback.class);
    private final CameraController controller = new CameraController(playback);
    private final MinecraftServer server = mock(MinecraftServer.class);
    private final ServerPlayer player = mock(ServerPlayer.class);
    private final ServerLevel level = mock(ServerLevel.class);

    @BeforeEach
    void setup() {
        var profile = new GameProfile(UUID.randomUUID(), "Director");
        when(player.getServer()).thenReturn(server);
        when(player.getUUID()).thenReturn(profile.getId());
        when(player.getGameProfile()).thenReturn(profile);
        when(server.isSingleplayer()).thenReturn(true);
        when(server.isSingleplayerOwner(profile)).thenReturn(true);
        when(server.getWorldPath(LevelResource.ROOT)).thenReturn(world);
        when(player.level()).thenReturn(level);
        when(level.dimension()).thenReturn(ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse("minecraft:overworld")));
        when(player.getX()).thenReturn(1.123456789);
        when(player.getY()).thenReturn(64.0);
        when(player.getZ()).thenReturn(-5.25);
        when(player.getYRot()).thenReturn(370.0F);
        when(player.getXRot()).thenReturn(-20.0F);
    }

    @Test
    void captureSelectionGotoAndPlaybackWorkWithoutCommands() throws Exception {
        assertTrue(controller.cachedSequences(server).isEmpty());
        assertTrue(controller.cachedKeyframes(player).isEmpty());
        assertFalse(Files.exists(world.resolve("data")));
        assertThrows(IllegalArgumentException.class, () -> controller.capture(player));
        assertTrue(controller.create(player, "intro").complete());
        controller.capture(player, "keyframe_2");
        controller.capture(player);
        controller.capture(player);
        var sequence = controller.data(player).sequence("intro");
        assertEquals(List.of("keyframe_2", "keyframe_1", "keyframe_3"),
                sequence.keyframes().stream().map(CameraKeyframe::name).toList());
        assertEquals(new CameraPose(1.123456789, 64, -5.25, 10, -20), sequence.keyframes().getFirst().pose());
        assertEquals(sequence.keyframes().getFirst(), controller.goTo(player, "1"));
        verify(playback).goTo(player, sequence, "1");
        controller.deleteKeyframe(player, "keyframe_3");
        controller.setDuration(player, 7.5);
        sequence = controller.play(player);
        verify(playback).start(player, sequence);
        assertEquals(7.5, sequence.durationSeconds().orElseThrow());
        controller.create(player, "other");
        assertEquals(sequence, controller.play(player, "intro"));
        assertEquals("other", controller.selected(player));
        controller.select(player, "intro");
        assertEquals("intro", controller.selected(player));
        when(playback.stop(eq(player), anyString())).thenReturn(true);
        assertTrue(controller.stop(player));
        assertEquals(controller.data(player), CameraStorage.load(world.resolve("data")).data());
    }

    @Test
    void lifecycleClearsSelectionAndReloadsPersistedData() throws Exception {
        controller.create(player, "intro");
        controller.capture(player);
        var saved = controller.data(player);
        controller.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        assertTrue(controller.cachedSelection(player).isEmpty());
        assertTrue(controller.cachedKeyframes(player).isEmpty());
        verify(playback).stop(player, "Playback stopped before saving and leaving the world.");
        controller.onServerStopped(new ServerStoppedEvent(server));
        assertTrue(controller.cachedSequences(server).isEmpty());
        assertEquals(saved, controller.data(player));
        assertThrows(IllegalArgumentException.class, () -> controller.selected(player));
        controller.onServerStopped(new ServerStoppedEvent(server));
        when(server.getWorldPath(LevelResource.ROOT)).thenReturn(world.resolve("other"));
        assertTrue(controller.data(player).sequences().isEmpty());
    }

    @Test
    void hostAndDimensionChecksApplyWithoutCommandAdapter() throws Exception {
        when(server.isSingleplayerOwner(player.getGameProfile())).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> controller.create(player, "remote"));
        assertThrows(IllegalArgumentException.class, () -> controller.deleteSequence(player, "remote"));
        assertThrows(IllegalArgumentException.class, () -> controller.setEasing(player, CameraEasing.SMOOTHSTEP));
        assertFalse(Files.exists(world.resolve("data")));
        when(server.isSingleplayerOwner(player.getGameProfile())).thenReturn(true);
        controller.create(player, "intro");
        var before = controller.data(player);
        when(level.dimension()).thenReturn(ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse("minecraft:the_nether")));
        assertThrows(IllegalArgumentException.class, () -> controller.capture(player));
        assertThrows(IllegalArgumentException.class, () -> controller.select(player, "intro"));
        assertThrows(IllegalArgumentException.class, () -> controller.goTo(player, "1"));
        assertThrows(IllegalArgumentException.class, () -> controller.deleteSequence(player, "intro"));
        assertThrows(IllegalArgumentException.class, () -> controller.setDuration(player, 10));
        assertThrows(IllegalArgumentException.class, () -> controller.setEasing(player, CameraEasing.SMOOTHSTEP));
        assertEquals(before, controller.data(player));
    }

    @Test
    void playbackLockoutProtectsEditsThroughDirectApi() throws Exception {
        controller.create(player, "intro");
        controller.capture(player);
        var before = controller.data(player);
        doThrow(new IllegalArgumentException("Camera playback is running.")).when(playback).requireIdle();
        assertThrows(IllegalArgumentException.class, () -> controller.create(player, "other"));
        assertThrows(IllegalArgumentException.class, () -> controller.select(player, "intro"));
        assertThrows(IllegalArgumentException.class, () -> controller.capture(player));
        assertThrows(IllegalArgumentException.class, () -> controller.capture(player, "named"));
        assertThrows(IllegalArgumentException.class, () -> controller.deleteKeyframe(player, "keyframe_1"));
        assertThrows(IllegalArgumentException.class, () -> controller.deleteSequence(player, "intro"));
        assertThrows(IllegalArgumentException.class, () -> controller.setDuration(player, 10));
        assertThrows(IllegalArgumentException.class, () -> controller.setEasing(player, CameraEasing.SMOOTHSTEP));
        assertThrows(IllegalArgumentException.class, () -> controller.playWithDuration(player, "intro", 10));
        assertEquals(before, controller.data(player));
        assertEquals(before, CameraStorage.load(world.resolve("data")).data());
    }

    @Test
    void durationIsValidatedBeforeSaveAndPersistedBeforeStart() throws Exception {
        controller.create(player, "intro");
        controller.capture(player);
        controller.capture(player);
        doAnswer(invocation -> {
            assertTrue(CameraStorage.load(world.resolve("data")).data().sequence("intro").durationSeconds().isEmpty());
            return new CameraPath(invocation.getArgument(1));
        }).when(playback).validateStart(eq(player), any());
        doAnswer(invocation -> {
            assertEquals(12.5, CameraStorage.load(world.resolve("data")).data()
                    .sequence("intro").durationSeconds().orElseThrow());
            return null;
        }).when(playback).start(eq(player), any());
        var result = controller.playWithDuration(player, "intro", 12.5);
        assertTrue(result.save().complete());
        assertTrue(result.error().isEmpty());
        var order = inOrder(playback);
        order.verify(playback).validateStart(eq(player), any());
        order.verify(playback).start(eq(player), any());
    }

    @Test
    void invalidPlaybackDoesNotSaveDuration() throws Exception {
        controller.create(player, "intro");
        doThrow(new IllegalArgumentException("Playback needs at least two keyframes."))
                .when(playback).validateStart(eq(player), any());
        assertThrows(IllegalArgumentException.class, () -> controller.playWithDuration(player, "intro", 10));
        assertTrue(controller.data(player).sequence("intro").durationSeconds().isEmpty());
        assertTrue(CameraStorage.load(world.resolve("data")).data().sequence("intro").durationSeconds().isEmpty());
        verify(playback, never()).start(any(), any());
    }

    @Test
    void partialSaveKeepsSelectionAndBlocksPlaybackUntilRepaired() throws Exception {
        controller.create(player, "intro");
        Path json = world.resolve("data").resolve("camerakeyframepro.json");
        Files.delete(json);
        Files.createDirectory(json);
        Files.writeString(json.resolve("blocker"), "keep");
        var created = controller.create(player, "second");
        assertFalse(created.complete());
        assertTrue(created.mirrorError().isPresent());
        assertEquals("second", controller.selected(player));
        var result = controller.playWithDuration(player, "second", 10);
        assertFalse(result.save().complete());
        assertTrue(result.error().orElseThrow().contains("Playback was not started"));
        verify(playback, never()).start(any(), any());
        assertEquals(10, CameraStorage.load(world.resolve("data")).data()
                .sequence("second").durationSeconds().orElseThrow());
        Files.delete(json.resolve("blocker"));
        Files.delete(json);
        assertTrue(controller.save(player).complete());
        assertTrue(Files.isRegularFile(json));
    }

    @Test
    void failedNbtCommitDoesNotChangeSelectionOrMemory() throws Exception {
        controller.create(player, "intro");
        var before = controller.data(player);
        Path nbt = world.resolve("data").resolve("camerakeyframepro.dat");
        Files.delete(nbt);
        Files.createDirectory(nbt);
        Files.writeString(nbt.resolve("blocker"), "keep");
        assertThrows(IOException.class, () -> controller.create(player, "failed"));
        assertThrows(IOException.class, () -> controller.deleteSequence(player, "intro"));
        assertEquals(before, controller.data(player));
        assertEquals("intro", controller.selected(player));
    }

    @Test
    void failedStartStillReportsCommittedDuration() throws Exception {
        controller.create(player, "intro");
        doThrow(new IllegalArgumentException("Spectator mode was blocked.")).when(playback).start(eq(player), any());
        var result = controller.playWithDuration(player, "intro", 10);
        assertTrue(result.save().complete());
        assertTrue(result.error().orElseThrow().contains("Spectator mode was blocked"));
        assertEquals(10, CameraStorage.load(world.resolve("data")).data()
                .sequence("intro").durationSeconds().orElseThrow());
    }
}
