package com.jdrkind.camerakeyframes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CameraPlaybackTest {
    @TempDir
    Path world;

    private final CameraPlayback playback = new CameraPlayback();
    private final CameraController controller = new CameraController(playback);
    private final MinecraftServer server = mock(MinecraftServer.class);
    private final ServerPlayer player = mock(ServerPlayer.class);
    private final ServerLevel level = mock(ServerLevel.class);
    private final ServerTickRateManager tickRate = mock(ServerTickRateManager.class);
    private final AtomicLong gameTime = new AtomicLong(1000);
    private final AtomicReference<GameType> mode = new AtomicReference<>(GameType.CREATIVE);
    private final Abilities abilities = new Abilities();
    private final GameProfile profile = new GameProfile(UUID.randomUUID(), "Director");
    private final List<String> messages = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        var gameMode = mock(ServerPlayerGameMode.class);
        // Vanilla exposes its game-mode controller as a final field, not a mockable accessor.
        var field = ServerPlayer.class.getField("gameMode");
        field.setAccessible(true);
        field.set(player, gameMode);
        when(gameMode.getGameModeForPlayer()).thenAnswer(invocation -> mode.get());
        when(player.setGameMode(any())).thenAnswer(invocation -> {
            GameType next = invocation.getArgument(0);
            return mode.getAndSet(next) != next;
        });
        when(player.getServer()).thenReturn(server);
        when(player.getUUID()).thenReturn(profile.getId());
        when(player.getGameProfile()).thenReturn(profile);
        when(player.serverLevel()).thenReturn(level);
        when(player.level()).thenReturn(level);
        when(player.getCamera()).thenReturn(player);
        when(player.isAlive()).thenReturn(true);
        when(player.getShoulderEntityLeft()).thenReturn(new CompoundTag());
        when(player.getShoulderEntityRight()).thenReturn(new CompoundTag());
        when(player.getAbilities()).thenReturn(abilities);
        abilities.flying = true;
        abilities.setFlyingSpeed(0.07F);
        when(player.getX()).thenReturn(5.0);
        when(player.getY()).thenReturn(70.0);
        when(player.getZ()).thenReturn(8.0);
        when(player.getYRot()).thenReturn(25.0F);
        when(player.getXRot()).thenReturn(-10.0F);
        when(level.dimension()).thenReturn(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld")));
        when(level.getGameTime()).thenAnswer(invocation -> gameTime.get());
        var border = mock(WorldBorder.class);
        when(border.isWithinBounds(anyDouble(), anyDouble())).thenReturn(true);
        when(level.getWorldBorder()).thenReturn(border);
        when(server.tickRateManager()).thenReturn(tickRate);
        when(tickRate.runsNormally()).thenReturn(true);
        when(server.isSingleplayer()).thenReturn(true);
        when(server.isSingleplayerOwner(profile)).thenReturn(true);
        when(server.getWorldPath(LevelResource.ROOT)).thenReturn(world);
        doAnswer(invocation -> {
            messages.add(invocation.<Component>getArgument(0).getString());
            return null;
        }).when(player).sendSystemMessage(any());
    }

    private CameraSequence sequence() {
        return new CameraSequence("intro", "minecraft:overworld", List.of(
                new CameraKeyframe("wide", 10, 64, 20, 179, 0),
                new CameraKeyframe("close", 30, 74, 40, -179, -20)), OptionalDouble.of(10));
    }

    @AfterEach
    void cleanup() {
        playback.stop(player, "Test cleanup.");
    }

    private void tick(long time) {
        gameTime.set(time);
        playback.onTick(new ServerTickEvent.Post(() -> true, server));
    }

    @Test
    void gotoAcceptsIndexOrNameWithoutChangingMode() {
        playback.goTo(player, sequence(), "1");
        verify(player).teleportTo(level, 10, 64, 20, 179, 0);
        playback.goTo(player, sequence(), "close");
        verify(player).teleportTo(level, 30, 74, 40, -179, -20);
        verify(player, never()).setGameMode(any());
        assertNull(CameraPlayback.clientView());
        assertThrows(IllegalArgumentException.class, () -> playback.goTo(player, sequence(), "0"));
    }

    @ParameterizedTest
    @EnumSource(value = GameType.class, names = {"CREATIVE", "SURVIVAL", "SPECTATOR"})
    void timedPlaybackRestoresOriginalModePositionRotationAndFlight(GameType originalMode) {
        mode.set(originalMode);
        abilities.flying = originalMode == GameType.SPECTATOR;
        boolean originalFlight = abilities.flying;
        playback.start(player, sequence());
        assertEquals(GameType.SPECTATOR, mode.get());
        assertNotNull(CameraPlayback.clientView());
        verify(player).teleportTo(level, 10, 64, 20, 179, 0);

        tick(1100);
        verify(player).teleportTo(level, 20, 69, 30, 180, -10);
        tick(1200);
        verify(player).teleportTo(level, 30, 74, 40, -179, -20);
        assertNotNull(CameraPlayback.clientView());
        clearInvocations(player);
        tick(1279);
        assertNotNull(CameraPlayback.clientView());
        assertEquals(GameType.SPECTATOR, mode.get());
        verify(player).teleportTo(level, 30, 74, 40, -179, -20);
        verify(player, never()).teleportTo(level, 5, 70, 8, 25, -10);
        tick(1280);
        assertNull(CameraPlayback.clientView());
        assertEquals(originalMode, mode.get());
        assertEquals(originalFlight, abilities.flying);
        assertEquals(0.07F, abilities.getFlyingSpeed());
        verify(player).teleportTo(level, 5, 70, 8, 25, -10);
        assertTrue(messages.getLast().contains("Playback finished"));
        assertFalse(playback.stop(player, "Already stopped"));
    }

    @Test
    void finalHoldPausesWithGameTimeAndManualStopRestoresImmediately() {
        playback.start(player, sequence());
        tick(1240);
        clearInvocations(player);
        tick(1240);
        assertNotNull(CameraPlayback.clientView());
        verify(player).teleportTo(level, 30, 74, 40, -179, -20);
        assertTrue(playback.stop(player, "Stopped during final hold."));
        assertNull(CameraPlayback.clientView());
        assertEquals(GameType.CREATIVE, mode.get());
        verify(player).teleportTo(level, 5, 70, 8, 25, -10);
    }

    @Test
    void easedPlaybackSharesPathWithClientAndRetainsTimingAndRestoration() {
        var sequence = sequence();
        var eased = new CameraSequence(sequence.name(), sequence.dimension(), sequence.keyframes(),
                sequence.durationSeconds(), CameraEasing.SMOOTHSTEP);
        playback.goTo(player, eased, "close");
        verify(player).teleportTo(level, 30, 74, 40, -179, -20);
        playback.start(player, eased);
        tick(1050);
        verify(player).teleportTo(level, 13.125, 65.5625, 23.125, 179.3125F, -3.125F);
        assertEquals(new CameraPose(13.125, 65.5625, 23.125, 179.3125F, -3.125F),
                CameraPlayback.clientView().path().sample(2.5));
        tick(1200);
        tick(1279);
        assertNotNull(CameraPlayback.clientView());
        tick(1280);
        assertNull(CameraPlayback.clientView());
        assertEquals(GameType.CREATIVE, mode.get());
        verify(player).teleportTo(level, 5, 70, 8, 25, -10);
    }

    @Test
    void fractionalDurationHasFullEightyTickHoldAfterFinalPoseTick() {
        var shortSequence = new CameraSequence("intro", "minecraft:overworld",
                sequence().keyframes(), OptionalDouble.of(0.11));
        playback.start(player, shortSequence);
        tick(1003);
        verify(player).teleportTo(level, 30, 74, 40, -179, -20);
        tick(1082);
        assertNotNull(CameraPlayback.clientView());
        tick(1083);
        assertNull(CameraPlayback.clientView());
        assertEquals(GameType.CREATIVE, mode.get());
    }

    @Test
    void logoutDuringFinalHoldDoesNotWaitToRestorePlayer() {
        playback.start(player, sequence());
        tick(1220);
        controller.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        assertNull(CameraPlayback.clientView());
        assertEquals(GameType.CREATIVE, mode.get());
        verify(player).teleportTo(level, 5, 70, 8, 25, -10);
    }

    @Test
    void manualStopAndLogoutRestoreAndUnlock() {
        playback.start(player, sequence());
        assertThrows(IllegalArgumentException.class, playback::requireIdle);
        assertThrows(IllegalArgumentException.class, () -> playback.start(player, sequence()));
        assertThrows(IllegalArgumentException.class, () -> playback.goTo(player, sequence(), "1"));
        controller.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        assertEquals(GameType.CREATIVE, mode.get());
        assertNull(CameraPlayback.clientView());
        assertDoesNotThrow(playback::requireIdle);
        verify(player).teleportTo(level, 5, 70, 8, 25, -10);
    }

    @Test
    void worldShutdownRestoresPlayer() {
        playback.start(player, sequence());
        playback.onServerStopping(new ServerStoppingEvent(server));
        assertEquals(GameType.CREATIVE, mode.get());
        assertNull(CameraPlayback.clientView());
        verify(player).teleportTo(level, 5, 70, 8, 25, -10);
    }

    @Test
    void pausedGameTimeDoesNotAdvanceThePath() {
        playback.start(player, sequence());
        tick(1040);
        clearInvocations(player);
        tick(1040);
        verify(player).teleportTo(level, 14, 66, 24, 179.4F, -4);
        when(tickRate.runsNormally()).thenReturn(false);
        clearInvocations(player);
        tick(1040);
        verify(player, never()).teleportTo(any(ServerLevel.class), anyDouble(), anyDouble(), anyDouble(), anyFloat(), anyFloat());
    }

    @Test
    void invalidStartDoesNotChangeGameModeOrPosition() {
        var unset = new CameraSequence("intro", "minecraft:overworld", sequence().keyframes());
        var error = assertThrows(IllegalArgumentException.class, () -> playback.start(player, unset));
        assertTrue(error.getMessage().contains("/camkey set_duration"));
        when(player.isPassenger()).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> playback.start(player, sequence()));
        when(player.isPassenger()).thenReturn(false);
        when(level.getWorldBorder().isWithinBounds(anyDouble(), anyDouble())).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> playback.start(player, sequence()));
        verify(player, never()).setGameMode(any());
        verify(player, never()).teleportTo(any(ServerLevel.class), anyDouble(), anyDouble(), anyDouble(), anyFloat(), anyFloat());
    }

    @Test
    void blockedSpectatorTransitionDoesNotStartPlayback() {
        doReturn(false).when(player).setGameMode(GameType.SPECTATOR);
        assertThrows(IllegalArgumentException.class, () -> playback.start(player, sequence()));
        assertEquals(GameType.CREATIVE, mode.get());
        assertNull(CameraPlayback.clientView());
    }

    @Test
    void dimensionChangeInterruptsAndReturnsToOriginalLevel() {
        playback.start(player, sequence());
        when(player.serverLevel()).thenReturn(mock(ServerLevel.class));
        tick(1040);
        assertEquals(GameType.CREATIVE, mode.get());
        assertNull(CameraPlayback.clientView());
        verify(player).teleportTo(level, 5, 70, 8, 25, -10);
    }

    @Test
    void commandsWireGotoDurationPlayAndStopAndBlockEditsDuringPlayback() throws Exception {
        var commands = new CameraCommands(controller);
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        var source = mock(CommandSourceStack.class);
        when(source.getServer()).thenReturn(server);
        when(source.getPlayerOrException()).thenReturn(player);
        when(source.getPlayer()).thenReturn(player);
        var errors = new ArrayList<String>();
        doAnswer(invocation -> {
            errors.add(invocation.<Component>getArgument(0).getString());
            return null;
        }).when(source).sendFailure(any());
        commands.register(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.INTEGRATED, null));
        var storage = CameraStorage.load(world.resolve("data"));
        storage.save(new CameraLibrary(List.of(new CameraSequence("intro", "minecraft:overworld", sequence().keyframes()))));
        assertEquals(1, dispatcher.execute("camkey usesequence intro", source));
        assertEquals(1, dispatcher.execute("camkey goto 1", source));
        assertEquals(1, dispatcher.execute("camkey goto close", source));
        assertEquals(0, dispatcher.execute("camkey play", source));
        assertTrue(errors.getLast().contains("/camkey set_duration"));
        assertEquals(1, dispatcher.execute("camkey set_duration 10", source));
        assertEquals(1, dispatcher.execute("camkey play", source));
        for (String command : List.of("add", "add nope", "deletekey wide", "delete intro", "create nope",
                "usesequence intro", "goto 1", "set_duration 20", "set_easing smoothstep",
                "play intro", "play 20", "play intro 20")) {
            assertEquals(0, dispatcher.execute("camkey " + command, source), command);
            assertTrue(errors.getLast().contains("playback is running"));
        }
        assertEquals(1, dispatcher.execute("camkey info", source));
        assertEquals(1, dispatcher.execute("camkey stop", source));
        assertEquals(GameType.CREATIVE, mode.get());
        assertEquals(1, dispatcher.execute("camkey play intro", source));
        assertEquals(1, dispatcher.execute("camkey stop", source));
        assertEquals(1, dispatcher.execute("camkey set_easing smoothstep", source));
        assertEquals(1, dispatcher.execute("camkey play 4.5", source));
        assertEquals(4.5, CameraPlayback.clientView().path().seconds());
        assertEquals(13.125, CameraPlayback.clientView().path().sample(4.5 / 4).x());
        assertEquals(4.5, CameraStorage.load(world.resolve("data")).data().sequence("intro").durationSeconds().orElseThrow());
        assertEquals(1, dispatcher.execute("camkey stop", source));
        assertEquals(1, dispatcher.execute("camkey play intro 6", source));
        assertEquals(6, CameraPlayback.clientView().path().seconds());
        assertEquals(6, CameraStorage.load(world.resolve("data")).data().sequence("intro").durationSeconds().orElseThrow());
        assertEquals(1, dispatcher.execute("camkey stop", source));

        for (String invalid : List.of("play 0", "play -1", "play 3601", "play NaN", "play Infinity")) {
            assertEquals(0, dispatcher.execute("camkey " + invalid, source), invalid);
            assertNull(CameraPlayback.clientView());
            assertEquals(6, CameraStorage.load(world.resolve("data")).data().sequence("intro").durationSeconds().orElseThrow());
        }
        assertEquals(1, dispatcher.execute("camkey create empty", source));
        assertEquals(0, dispatcher.execute("camkey play 5", source));
        assertTrue(CameraStorage.load(world.resolve("data")).data().sequence("empty").durationSeconds().isEmpty());
        assertEquals(1, dispatcher.execute("camkey play intro 8", source));
        assertEquals(1, dispatcher.execute("camkey stop", source));
        assertEquals(0, dispatcher.execute("camkey play", source)); // Named playback did not select intro.
        controller.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        assertEquals(0, dispatcher.execute("camkey play 5", source));
        assertEquals(1, dispatcher.execute("camkey play intro 9", source));
        assertEquals(1, dispatcher.execute("camkey stop", source));
    }

    @Test
    void numericSequenceNamesAreUnambiguousWithAnExplicitSecondsArgument() throws Exception {
        var commands = new CameraCommands(controller);
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        var source = mock(CommandSourceStack.class);
        when(source.getServer()).thenReturn(server);
        when(source.getPlayerOrException()).thenReturn(player);
        when(source.getPlayer()).thenReturn(player);
        commands.register(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.INTEGRATED, null));
        CameraStorage.load(world.resolve("data")).save(new CameraLibrary(List.of(
                sequence(), new CameraSequence("10", "minecraft:overworld", sequence().keyframes()))));
        assertEquals(1, dispatcher.execute("camkey usesequence intro", source));
        assertEquals(1, dispatcher.execute("camkey play 10", source));
        assertTrue(CameraStorage.load(world.resolve("data")).data().sequence("10").durationSeconds().isEmpty());
        dispatcher.execute("camkey stop", source);
        assertEquals(1, dispatcher.execute("camkey play 10 3", source));
        assertEquals(3, CameraStorage.load(world.resolve("data")).data().sequence("10").durationSeconds().orElseThrow());
    }

    @Test
    void durationSaveFailureDoesNotStartPlayback() throws Exception {
        var commands = new CameraCommands(controller);
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        var source = mock(CommandSourceStack.class);
        when(source.getServer()).thenReturn(server);
        when(source.getPlayerOrException()).thenReturn(player);
        when(source.getPlayer()).thenReturn(player);
        commands.register(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.INTEGRATED, null));
        CameraStorage.load(world.resolve("data")).save(new CameraLibrary(List.of(sequence())));
        dispatcher.execute("camkey usesequence intro", source);
        Path json = world.resolve("data").resolve("camerakeyframepro.json");
        java.nio.file.Files.delete(json);
        java.nio.file.Files.createDirectory(json);
        java.nio.file.Files.writeString(json.resolve("blocker"), "keep");
        assertEquals(0, dispatcher.execute("camkey play 8", source));
        assertNull(CameraPlayback.clientView());
        assertEquals(GameType.CREATIVE, mode.get());
        assertEquals(8, CameraStorage.load(world.resolve("data")).data().sequence("intro").durationSeconds().orElseThrow());
        verify(source).sendFailure(argThat(message -> message.getString().contains("Playback was not started")));
    }
}
