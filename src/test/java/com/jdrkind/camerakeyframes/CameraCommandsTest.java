package com.jdrkind.camerakeyframes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CameraCommandsTest {
    @TempDir
    Path world;

    private final CameraPlayback playback = new CameraPlayback();
    private final CameraController controller = new CameraController(playback);
    private final CameraCommands commands = new CameraCommands(controller);
    private final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
    private final MinecraftServer server = mock(MinecraftServer.class);
    private final ServerPlayer player = mock(ServerPlayer.class);
    private final ServerLevel level = mock(ServerLevel.class);
    private final CommandSourceStack source = mock(CommandSourceStack.class);
    private final List<String> messages = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        var profile = new GameProfile(UUID.randomUUID(), "Director");
        when(source.getServer()).thenReturn(server);
        when(source.getPlayerOrException()).thenReturn(player);
        when(source.getPlayer()).thenReturn(player);
        when(server.isSingleplayer()).thenReturn(true);
        when(server.isSingleplayerOwner(profile)).thenReturn(true);
        when(server.getWorldPath(LevelResource.ROOT)).thenReturn(world);
        when(player.getGameProfile()).thenReturn(profile);
        when(player.getUUID()).thenReturn(profile.getId());
        when(player.getServer()).thenReturn(server);
        when(player.level()).thenReturn(level);
        when(level.dimension()).thenReturn(ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse("minecraft:overworld")));
        when(player.getX()).thenReturn(10.25);
        when(player.getY()).thenReturn(64.0);
        when(player.getZ()).thenReturn(-3.5);
        when(player.getYRot()).thenReturn(370.0F);
        when(player.getXRot()).thenReturn(-20.0F);
        doAnswer(invocation -> {
            Supplier<Component> message = invocation.getArgument(0);
            messages.add(message.get().getString());
            return null;
        }).when(source).sendSuccess(any(), eq(false));
        doAnswer(invocation -> {
            errors.add(invocation.<Component>getArgument(0).getString());
            return null;
        }).when(source).sendFailure(any());
        commands.register(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.INTEGRATED, null));
    }

    private int execute(String command) throws Exception {
        return dispatcher.execute("camkey " + command, source);
    }

    @Test
    void extractedBuildersRegisterCompleteCommandSetAndKeepBareDeletionNonExecutable() throws Exception {
        var root = dispatcher.getRoot().getChild("camkey");
        assertEquals(List.of("add", "create", "delete", "deletekey", "goto", "help", "info",
                        "list", "play", "save", "set_duration", "set_easing", "stop", "usesequence"),
                root.getChildren().stream().map(node -> node.getName()).sorted().toList());
        assertNull(root.getChild("delete").getCommand());
        assertNull(root.getChild("deletekey").getCommand());
        assertNotNull(root.getChild("delete").getChild("sequence").getCommand());
        assertNotNull(root.getChild("deletekey").getChild("keyframe").getCommand());
        assertEquals(1, dispatcher.execute("camkey", source));
        var defaultHelp = List.copyOf(messages);
        messages.clear();
        assertEquals(1, execute("help"));
        assertEquals(defaultHelp, messages);
    }

    @Test
    void automaticCaptureUsesSharedValidationAndSkipsExistingNames() throws Exception {
        assertEquals(0, execute("add"));
        assertTrue(errors.getLast().contains("No active sequence"));
        execute("create intro");
        execute("add keyframe_2");
        assertEquals(1, execute("add"));
        assertEquals(1, execute("add"));
        var frames = CameraStorage.load(world.resolve("data")).data().sequence("intro").keyframes();
        assertEquals(List.of("keyframe_2", "keyframe_1", "keyframe_3"),
                frames.stream().map(CameraKeyframe::name).toList());
        assertEquals(10.25, frames.get(1).pose().x());
        execute("create other");
        execute("add");
        assertEquals("keyframe_1", CameraStorage.load(world.resolve("data")).data()
                .sequence("other").keyframes().getFirst().name());
        controller.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        assertEquals(0, execute("add"));
        assertTrue(errors.getLast().contains("No active sequence"));
    }

    @Test
    void captureDeleteListAndReloadWorkflow() throws Exception {
        assertEquals(1, execute("create intro"));
        assertEquals(1, execute("add wide"));
        when(player.getX()).thenReturn(50.0);
        assertEquals(1, execute("add close"));
        assertEquals(1, execute("list"));
        assertTrue(messages.stream().anyMatch(message -> message.contains("[ACTIVE] intro")));
        assertEquals(1, execute("info"));
        assertTrue(messages.stream().anyMatch(message -> message.contains("1. wide")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("2. close")));
        assertEquals(1, execute("deletekey wide"));
        controller.onServerStopped(new ServerStoppedEvent(server));

        assertEquals(1, execute("info intro"));
        assertEquals(0, execute("add no_selection"));
        assertTrue(errors.getLast().contains("No active sequence"));
        assertEquals(1, execute("usesequence intro"));
        assertEquals(1, execute("add after_reload"));
        var saved = CameraStorage.load(world.resolve("data")).data().sequence("intro").keyframes();
        assertEquals(List.of("close", "after_reload"), saved.stream().map(CameraKeyframe::name).toList());
        assertEquals(50.0, saved.getFirst().pose().x());
        assertEquals(64.0, saved.getFirst().pose().y());
        assertEquals(-3.5, saved.getFirst().pose().z());
        assertEquals(10.0F, saved.getFirst().pose().yaw());
        assertEquals(-20.0F, saved.getFirst().pose().pitch());
        assertEquals(1, execute("save"));
    }

    @Test
    void duplicatesMissingNamesAndDimensionMismatchDoNotMutateData() throws Exception {
        execute("create intro");
        execute("add start");
        var before = CameraStorage.load(world.resolve("data")).data();
        assertEquals(0, execute("create intro"));
        assertEquals(0, execute("create INVALID"));
        assertEquals(0, execute("add start"));
        assertEquals(0, execute("deletekey absent"));
        assertEquals(0, execute("usesequence absent"));
        assertEquals(0, execute("info absent"));
        when(level.dimension()).thenReturn(ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse("minecraft:the_nether")));
        assertEquals(0, execute("add wrong_dimension"));
        assertEquals(0, execute("usesequence intro"));
        assertEquals(0, execute("deletekey start"));
        assertEquals(before, CameraStorage.load(world.resolve("data")).data());
    }

    @Test
    void paginationIsBoundedAndNamesDoNotCollideWithPageSyntax() throws Exception {
        for (int i = 1; i <= 9; i++) {
            execute("create sequence_" + i);
        }
        messages.clear();
        execute("list");
        assertEquals(10, messages.size()); // Header, eight rows, next-page hint.
        assertTrue(messages.getLast().contains("list 2"));
        messages.clear();
        execute("list 2");
        assertEquals(2, messages.size());
        assertTrue(messages.getLast().contains("sequence_9"));
        assertEquals(0, execute("list 3"));
        execute("create page");
        for (int i = 1; i <= 9; i++) {
            execute("add frame_" + i);
        }
        messages.clear();
        assertEquals(1, execute("info page 2"));
        assertEquals(4, messages.size());
        assertTrue(messages.getLast().contains("9. frame_9"));
        assertEquals(0, execute("info page 3"));
    }

    @Test
    void durationIsExplicitPersistedAndInfoRowsAreNumberedWithoutBarsOrXyz() throws Exception {
        execute("create intro");
        execute("add wide");
        execute("add close");
        messages.clear();
        execute("info");
        assertTrue(messages.stream().anyMatch(message -> message.contains("Duration: not set")));
        assertTrue(messages.stream().anyMatch(message -> message.startsWith("1. wide")));
        assertTrue(messages.stream().anyMatch(message -> message.startsWith("2. close")));
        assertTrue(messages.stream().noneMatch(message -> message.contains("|") || message.contains("XYZ")));
        assertEquals(1, execute("set_duration 7.25"));
        assertEquals(7.25, CameraStorage.load(world.resolve("data")).data()
                .sequence("intro").durationSeconds().orElseThrow());
        messages.clear();
        execute("info");
        assertTrue(messages.stream().anyMatch(message -> message.contains("7.25 seconds")));
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class, () -> execute("set_duration 0"));
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class, () -> execute("set_duration 3601"));
        var targets = dispatcher.getCompletionSuggestions(dispatcher.parse("camkey goto ", source)).get();
        assertTrue(targets.getList().stream().anyMatch(suggestion -> suggestion.getText().equals("1")));
        assertTrue(targets.getList().stream().anyMatch(suggestion -> suggestion.getText().equals("wide")));
    }

    @Test
    void suggestionsIncludeSavedNamesAndActiveFrames() throws Exception {
        execute("create intro");
        execute("add wide");
        var sequences = dispatcher.getCompletionSuggestions(dispatcher.parse("camkey usesequence in", source)).get();
        assertTrue(sequences.getList().stream().anyMatch(suggestion -> suggestion.getText().equals("intro")));
        var frames = dispatcher.getCompletionSuggestions(dispatcher.parse("camkey deletekey wi", source)).get();
        assertTrue(frames.getList().stream().anyMatch(suggestion -> suggestion.getText().equals("wide")));
    }

    @Test
    void gotoCompletesPartialNamesFromOnlyTheCurrentSequence() throws Exception {
        execute("create intro");
        execute("add front_door_profile");
        execute("add overhead_start");
        var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("camkey goto front", source)).get();
        assertEquals(List.of("front_door_profile"), suggestions.getList().stream()
                .map(com.mojang.brigadier.suggestion.Suggestion::getText).toList());
        execute("create other");
        execute("add other_pose");
        suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("camkey goto front", source)).get();
        assertTrue(suggestions.isEmpty());
        execute("usesequence intro");
        suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("camkey goto ", source)).get();
        assertTrue(suggestions.getList().stream().anyMatch(suggestion -> suggestion.getText().equals("1")));
    }

    @Test
    void logoutClearsSelectionAndOtherWorldIsIsolated() throws Exception {
        execute("create intro");
        controller.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        assertEquals(0, execute("add unselected"));
        assertTrue(errors.getLast().contains("No active sequence"));
        controller.onServerStopped(new ServerStoppedEvent(server));
        when(server.getWorldPath(LevelResource.ROOT)).thenReturn(world.resolve("other-world"));
        messages.clear();
        execute("list");
        assertTrue(messages.getLast().contains("No sequences"));
    }

    @Test
    void nonLocalPlayersCannotWriteData() throws Exception {
        when(server.isSingleplayerOwner(player.getGameProfile())).thenReturn(false);
        assertEquals(0, execute("create remote"));
        assertFalse(Files.exists(world.resolve("data")));
        when(server.isSingleplayerOwner(player.getGameProfile())).thenReturn(true);
        when(server.isSingleplayer()).thenReturn(false);
        assertEquals(0, execute("create dedicated"));
        assertFalse(Files.exists(world.resolve("data")));
    }

    @Test
    void infoDefaultsToCurrentSequenceAndExplicitInfoDoesNotChangeSelection() throws Exception {
        assertEquals(0, execute("info"));
        assertTrue(errors.getLast().contains("No active sequence"));
        execute("create first");
        execute("add first_pose");
        execute("create second");
        execute("add second_pose");

        messages.clear();
        assertEquals(1, execute("info"));
        assertTrue(messages.getFirst().contains("second"));
        assertTrue(messages.getFirst().contains("1/1"));
        assertTrue(messages.getLast().contains("second_pose"));

        messages.clear();
        assertEquals(1, execute("info first"));
        List<String> defaultPage = List.copyOf(messages);
        messages.clear();
        assertEquals(1, execute("info first 1"));
        assertEquals(defaultPage, messages);
        messages.clear();
        assertEquals(1, execute("info"));
        assertTrue(messages.getFirst().contains("second"));

        when(level.dimension()).thenReturn(ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse("minecraft:the_nether")));
        assertEquals(1, execute("info"));
        assertEquals(0, execute("add wrong_dimension"));
        controller.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        assertEquals(0, execute("info"));
        assertEquals(1, execute("info first"));
    }

    @Test
    void defaultSequencePageMatchesExplicitPageAndHelpUsesInfo() throws Exception {
        execute("create intro");
        messages.clear();
        assertEquals(1, execute("list"));
        List<String> defaultPage = List.copyOf(messages);
        messages.clear();
        assertEquals(1, execute("list 1"));
        assertEquals(defaultPage, messages);
        messages.clear();
        execute("help");
        assertTrue(messages.stream().anyMatch(message -> message.contains("info [sequence] [page]")));
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class, () -> execute("list intro"));
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class, () -> execute("info intro 0"));
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class, () -> execute("list 0"));
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class, () -> execute("listsequences"));
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class, () -> execute("createsequence old"));
        var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("camkey info in", source)).get();
        assertTrue(suggestions.getList().stream().anyMatch(suggestion -> suggestion.getText().equals("intro")));
    }

    @Test
    void jsonFailureTellsUserNbtSavedAndKeepsCreatedSequenceSelected() throws Exception {
        Path json = world.resolve("data").resolve("camerakeyframepro.json");
        Files.createDirectories(json);
        Files.writeString(json.resolve("blocker"), "keep");
        // Existing JSON without NBT is intentionally treated as a recovery problem.
        assertEquals(0, execute("create intro"));
        assertTrue(errors.getLast().contains("Camera storage failed"));
        Files.delete(json.resolve("blocker"));
        Files.delete(json);
        execute("create intro");
        Files.delete(json);
        Files.createDirectory(json);
        Files.writeString(json.resolve("blocker"), "keep");
        assertEquals(0, execute("create second"));
        assertTrue(errors.getLast().contains("NBT saved, but JSON failed"));
        Files.delete(json.resolve("blocker"));
        Files.delete(json);
        assertEquals(1, execute("save"));
        assertEquals(1, execute("add selected"));
        assertEquals("selected", CameraStorage.load(world.resolve("data")).data()
                .sequence("second").keyframes().getFirst().name());
    }

    @Test
    void easingCommandPersistsSelectionModeAndProvidesSuggestionsAndInfo() throws Exception {
        assertEquals(0, execute("set_easing smoothstep"));
        assertTrue(errors.getLast().contains("No active sequence"));
        execute("create intro");
        execute("add a");
        execute("add b");
        execute("set_duration 10");
        messages.clear();
        execute("info");
        assertTrue(messages.contains("Easing: linear"));
        assertEquals(1, execute("set_easing smoothstep"));
        assertTrue(messages.getLast().contains("Saved NBT + JSON"));
        assertEquals(CameraEasing.SMOOTHSTEP, CameraStorage.load(world.resolve("data")).data().sequence("intro").easing());
        var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("camkey set_easing sm", source)).get();
        assertEquals(List.of("smoothstep"), suggestions.getList().stream()
                .map(com.mojang.brigadier.suggestion.Suggestion::getText).toList());
        assertEquals(0, execute("set_easing invalid"));
        assertTrue(errors.getLast().contains("Use linear or smoothstep"));
        controller.onServerStopped(new ServerStoppedEvent(server));
        messages.clear();
        execute("info intro");
        assertTrue(messages.contains("Easing: smoothstep"));
        execute("usesequence intro");
        assertEquals(1, execute("set_easing linear"));
        assertEquals(CameraEasing.LINEAR, controller.data(player).sequence("intro").easing());
        assertEquals(10, controller.data(player).sequence("intro").durationSeconds().orElseThrow());
    }

    @Test
    void easingSaveFailureReportsCommittedNbtAndCanRepairJson() throws Exception {
        execute("create intro");
        Path json = world.resolve("data").resolve("camerakeyframepro.json");
        Files.delete(json);
        Files.createDirectory(json);
        Files.writeString(json.resolve("blocker"), "keep");
        assertEquals(0, execute("set_easing smoothstep"));
        assertTrue(errors.getLast().contains("NBT saved, but JSON failed"));
        assertEquals(CameraEasing.SMOOTHSTEP, CameraStorage.load(world.resolve("data")).data().sequence("intro").easing());
        Files.delete(json.resolve("blocker"));
        Files.delete(json);
        assertEquals(1, execute("save"));
        assertTrue(Files.readString(json).contains("\"easing\": \"smoothstep\""));
    }

    @Test
    void deleteRequiresSequenceNameAndRemovesItsFramesWithoutChangingOtherSelection() throws Exception {
        execute("create intro");
        execute("add wide");
        execute("add close");
        execute("create other");
        var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("camkey delete in", source)).get();
        assertEquals(List.of("intro"), suggestions.getList().stream()
                .map(com.mojang.brigadier.suggestion.Suggestion::getText).toList());
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class, () -> execute("delete"));
        assertEquals(0, execute("delete wide"));
        assertEquals(2, controller.data(player).sequence("intro").keyframes().size());
        assertEquals(1, execute("delete intro"));
        assertTrue(messages.getLast().contains("Deleted sequence 'intro' and all its keyframes"));
        assertEquals("other", controller.selected(player));
        assertEquals(List.of("other"), CameraStorage.load(world.resolve("data")).data().sequences()
                .stream().map(CameraSequence::name).toList());
        suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("camkey delete in", source)).get();
        assertTrue(suggestions.isEmpty());
        assertEquals(1, execute("delete other"));
        assertTrue(controller.cachedSelection(player).isEmpty());
        assertEquals(0, execute("info"));
        assertTrue(errors.getLast().contains("No active sequence"));
        controller.onServerStopped(new ServerStoppedEvent(server));
        assertTrue(controller.data(player).sequences().isEmpty());
        var json = com.google.gson.JsonParser.parseString(
                Files.readString(world.resolve("data").resolve("camerakeyframepro.json"))).getAsJsonObject();
        assertTrue(json.getAsJsonArray("sequences").isEmpty());
    }

    @Test
    void sequenceDeletionRejectsWrongDimensionButDoesNotRequireSelection() throws Exception {
        execute("create intro");
        execute("add wide");
        var before = controller.data(player);
        assertEquals(0, execute("delete absent"));
        when(level.dimension()).thenReturn(ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse("minecraft:the_nether")));
        assertEquals(0, execute("delete intro"));
        assertTrue(errors.getLast().contains("switch to that dimension"));
        assertEquals(before, controller.data(player));
        assertEquals("intro", controller.selected(player));
        when(level.dimension()).thenReturn(ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse("minecraft:overworld")));
        controller.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        assertEquals(1, execute("delete intro"));
        assertTrue(controller.data(player).sequences().isEmpty());
    }

    @Test
    void partialSequenceDeletionClearsSelectionAndReportsCommittedNbt() throws Exception {
        execute("create intro");
        execute("add wide");
        Path json = world.resolve("data").resolve("camerakeyframepro.json");
        Files.delete(json);
        Files.createDirectory(json);
        Files.writeString(json.resolve("blocker"), "keep");
        assertEquals(0, execute("delete intro"));
        assertTrue(errors.getLast().contains("NBT saved, but JSON failed"));
        assertTrue(controller.cachedSelection(player).isEmpty());
        assertTrue(CameraStorage.load(world.resolve("data")).data().sequences().isEmpty());
        assertEquals(0, execute("add"));
        assertTrue(errors.getLast().contains("No active sequence"));
        Files.delete(json.resolve("blocker"));
        Files.delete(json);
        assertEquals(1, execute("save"));
        assertEquals(CameraDataFormat.toJson(controller.data(player)), Files.readString(json));
    }
}
