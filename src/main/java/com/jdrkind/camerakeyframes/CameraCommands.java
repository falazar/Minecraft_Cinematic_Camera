package com.jdrkind.camerakeyframes;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class CameraCommands {
    private static final int PAGE_SIZE = 8;
    private static final Pattern NUMBER = Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?|NaN|[+-]?Infinity");
    private final CameraController controller;

    public CameraCommands(CameraController controller) {
        this.controller = controller;
    }

    // /camkey - shows help without requiring a subcommand.
    public void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("camkey")
                .executes(context -> help(context.getSource()))
                .then(helpCommand())
                .then(createCommand())
                .then(selectCommand())
                .then(captureCommand())
                .then(deleteSequenceCommand())
                .then(deleteKeyframeCommand())
                .then(gotoCommand())
                .then(durationCommand())
                .then(easingCommand())
                .then(playCommand())
                .then(stopCommand())
                .then(infoCommand())
                .then(listCommand())
                .then(saveCommand()));
    }

    // Example: /camkey help
    private LiteralArgumentBuilder<CommandSourceStack> helpCommand() {
        return Commands.literal("help").executes(context -> help(context.getSource()));
    }

    // /camkey create <sequence> - example: /camkey create intro
    private LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("create")
                .then(Commands.argument("sequence", StringArgumentType.word())
                        .executes(context -> run(context, (ctx, player) -> reportSave(ctx.getSource(),
                                controller.create(player, StringArgumentType.getString(ctx, "sequence"))))));
    }

    // /camkey usesequence <sequence> - example: /camkey usesequence intro
    private LiteralArgumentBuilder<CommandSourceStack> selectCommand() {
        return Commands.literal("usesequence")
                .then(Commands.argument("sequence", StringArgumentType.word())
                        .suggests(this::suggestSequences)
                        .executes(context -> run(context, this::select)));
    }

    // /camkey add [keyframe] - examples: /camkey add, /camkey add wide
    private LiteralArgumentBuilder<CommandSourceStack> captureCommand() {
        return Commands.literal("add")
                .executes(context -> run(context, (ctx, player) ->
                        reportSave(ctx.getSource(), controller.capture(player))))
                .then(Commands.argument("keyframe", StringArgumentType.word())
                        .executes(context -> run(context, (ctx, player) -> reportSave(ctx.getSource(),
                                controller.capture(player, StringArgumentType.getString(ctx, "keyframe"))))));
    }

    // /camkey delete <sequence> - example: /camkey delete intro (deletes all its poses).
    private LiteralArgumentBuilder<CommandSourceStack> deleteSequenceCommand() {
        return Commands.literal("delete")
                .then(Commands.argument("sequence", StringArgumentType.word())
                        .suggests(this::suggestSequences)
                        .executes(context -> run(context, (ctx, player) -> reportSave(ctx.getSource(),
                                controller.deleteSequence(player, StringArgumentType.getString(ctx, "sequence"))))));
    }

    // /camkey deletekey <keyframe> - example: /camkey deletekey wide
    private LiteralArgumentBuilder<CommandSourceStack> deleteKeyframeCommand() {
        return Commands.literal("deletekey")
                .then(Commands.argument("keyframe", StringArgumentType.word())
                        .suggests(this::suggestKeyframes)
                        .executes(context -> run(context, (ctx, player) -> reportSave(ctx.getSource(),
                                controller.deleteKeyframe(player, StringArgumentType.getString(ctx, "keyframe"))))));
    }

    // /camkey goto <index-or-name> - examples: /camkey goto 1, /camkey goto wide
    private LiteralArgumentBuilder<CommandSourceStack> gotoCommand() {
        return Commands.literal("goto")
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(this::suggestTargets)
                        .executes(context -> run(context, this::goTo)));
    }

    // /camkey set_duration <seconds> - example: /camkey set_duration 10
    private LiteralArgumentBuilder<CommandSourceStack> durationCommand() {
        return Commands.literal("set_duration")
                .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.1, 3600))
                        .executes(context -> run(context, (ctx, player) -> reportSave(ctx.getSource(),
                                controller.setDuration(player, DoubleArgumentType.getDouble(ctx, "seconds"))))));
    }

    // /camkey set_easing <linear|smoothstep> - example: /camkey set_easing smoothstep
    private LiteralArgumentBuilder<CommandSourceStack> easingCommand() {
        return Commands.literal("set_easing")
                .then(Commands.argument("easing", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Stream.of(CameraEasing.values()).map(CameraEasing::id), builder))
                        .executes(context -> run(context, (ctx, player) -> reportSave(ctx.getSource(),
                                controller.setEasing(player, CameraEasing.parse(StringArgumentType.getString(ctx, "easing")))))));
    }

    // /camkey play [sequence] [seconds]
    // Examples: /camkey play, /camkey play 10, /camkey play intro, /camkey play intro 10
    private LiteralArgumentBuilder<CommandSourceStack> playCommand() {
        return Commands.literal("play")
                .executes(context -> run(context, (ctx, player) ->
                        reportPlaying(ctx.getSource(), controller.play(player).name())))
                .then(Commands.argument("sequence_or_seconds", StringArgumentType.word())
                        .suggests(this::suggestSequences)
                        .executes(context -> run(context, this::playArgument))
                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.1, 3600))
                                .executes(context -> run(context, (ctx, player) ->
                                        playWithDuration(ctx.getSource(), player,
                                                StringArgumentType.getString(ctx, "sequence_or_seconds"),
                                                DoubleArgumentType.getDouble(ctx, "seconds"))))));
    }

    // Example: /camkey stop
    private LiteralArgumentBuilder<CommandSourceStack> stopCommand() {
        return Commands.literal("stop")
                .executes(context -> run(context, (ctx, player) -> {
                    if (!controller.stop(player)) {
                        message(ctx.getSource(), "No camera playback is running.", ChatFormatting.GRAY);
                    }
                    return 1;
                }));
    }

    // /camkey info [sequence] [page] - examples: /camkey info, /camkey info intro 2
    private LiteralArgumentBuilder<CommandSourceStack> infoCommand() {
        return Commands.literal("info")
                .executes(context -> run(context, (ctx, player) ->
                        showInfo(ctx.getSource(), controller.data(player), controller.selected(player), 1)))
                .then(Commands.argument("sequence", StringArgumentType.word())
                        .suggests(this::suggestSequences)
                        .executes(context -> run(context, (ctx, player) ->
                                showInfo(ctx.getSource(), controller.data(player), StringArgumentType.getString(ctx, "sequence"), 1)))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> run(context, (ctx, player) ->
                                        showInfo(ctx.getSource(), controller.data(player), StringArgumentType.getString(ctx, "sequence"),
                                                IntegerArgumentType.getInteger(ctx, "page"))))));
    }

    // /camkey list [page] - examples: /camkey list, /camkey list 2
    private LiteralArgumentBuilder<CommandSourceStack> listCommand() {
        return Commands.literal("list")
                .executes(context -> run(context, (ctx, player) ->
                        listSequences(ctx.getSource(), player, 1)))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> run(context, (ctx, player) ->
                                listSequences(ctx.getSource(), player, IntegerArgumentType.getInteger(ctx, "page")))));
    }

    // Example: /camkey save
    private LiteralArgumentBuilder<CommandSourceStack> saveCommand() {
        return Commands.literal("save")
                .executes(context -> run(context, (ctx, player) ->
                        reportSave(ctx.getSource(), controller.save(player))));
    }

    private int run(CommandContext<CommandSourceStack> context, Action action) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        try {
            return action.execute(context, player);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        } catch (IOException exception) {
            CameraKeyframeProMod.LOGGER.error("Camera data operation failed for world {}", server.getWorldPath(LevelResource.ROOT), exception);
            source.sendFailure(Component.literal("Camera storage failed: " + exception.getMessage()
                    + " No successful save is claimed. Check logs and backups before retrying."));
            return 0;
        }
    }

    private int select(CommandContext<CommandSourceStack> context, ServerPlayer player) throws IOException {
        String name = StringArgumentType.getString(context, "sequence");
        controller.select(player, name);
        message(context.getSource(), "Selected '" + name + "'. Use /camkey add <keyframe>.", ChatFormatting.GREEN);
        return 1;
    }

    private int goTo(CommandContext<CommandSourceStack> context, ServerPlayer player) throws IOException {
        String target = StringArgumentType.getString(context, "target");
        CameraKeyframe frame = controller.goTo(player, target);
        message(context.getSource(), "Moved to '" + frame.name()
                + "'. Game mode unchanged.", ChatFormatting.GREEN);
        return 1;
    }

    private static int reportPlaying(CommandSourceStack source, String name) {
        message(source, "Playing '" + name + "', then holding the final pose for "
                + CameraPlayback.END_HOLD_SECONDS + " seconds. Use /camkey stop to return immediately.", ChatFormatting.GREEN);
        return 1;
    }

    private int playArgument(CommandContext<CommandSourceStack> context, ServerPlayer player) throws IOException {
        String argument = StringArgumentType.getString(context, "sequence_or_seconds");
        if (NUMBER.matcher(argument).matches()) {
            return playWithDuration(context.getSource(), player, controller.selected(player), Double.parseDouble(argument));
        }
        return reportPlaying(context.getSource(), controller.play(player, argument).name());
    }

    private int playWithDuration(CommandSourceStack source, ServerPlayer player,
            String name, double seconds) throws IOException {
        var result = controller.playWithDuration(player, name, seconds);
        reportSave(source, result.save());
        if (result.error().isPresent()) {
            source.sendFailure(Component.literal(result.error().get()));
            return 0;
        }
        return reportPlaying(source, name);
    }

    private static int reportSave(CommandSourceStack source, CameraController.SaveResult result) {
        if (!result.complete()) {
            source.sendFailure(Component.literal(result.description() + ": NBT saved, but JSON failed. "
                    + "Do not repeat the edit; fix the file problem and use /camkey save. See logs."));
            return 0;
        }
        message(source, result.description() + ". Saved NBT + JSON.", ChatFormatting.GREEN);
        return 1;
    }

    private int listSequences(CommandSourceStack source, ServerPlayer player, int page) throws IOException {
        List<CameraSequence> sequences = controller.data(player).sequences();
        int start = pageStart(page, sequences.size());
        message(source, "--- Camera sequences (" + page + "/" + pages(sequences.size()) + ") ---", ChatFormatting.GOLD);
        if (sequences.isEmpty()) {
            message(source, "No sequences. Start with /camkey create intro.", ChatFormatting.GRAY);
        }
        for (int i = start; i < Math.min(start + PAGE_SIZE, sequences.size()); i++) {
            CameraSequence sequence = sequences.get(i);
            boolean selected = controller.cachedSelection(player).filter(sequence.name()::equals).isPresent();
            message(source, (i + 1) + ". " + (selected ? "[ACTIVE] " : "") + sequence.name()
                    + "  " + sequence.keyframes().size() + " keyframes  " + sequence.dimension(),
                    selected ? ChatFormatting.AQUA : ChatFormatting.WHITE);
        }
        if (page < pages(sequences.size())) {
            message(source, "Next: /camkey list " + (page + 1), ChatFormatting.GRAY);
        }
        return 1;
    }

    private static int showInfo(CommandSourceStack source, CameraLibrary data, String name, int page) {
        CameraSequence sequence = data.sequence(name);
        int start = pageStart(page, sequence.keyframes().size());
        message(source, "--- " + sequence.name() + "  " + sequence.dimension() + " ("
                + page + "/" + pages(sequence.keyframes().size()) + ") ---", ChatFormatting.GOLD);
        message(source, sequence.durationSeconds().isPresent()
                ? "Duration: " + sequence.durationSeconds().getAsDouble() + " seconds"
                : "Duration: not set. Select this sequence, then /camkey set_duration <seconds>.", ChatFormatting.GRAY);
        message(source, "Easing: " + sequence.easing().id(), ChatFormatting.GRAY);
        if (sequence.keyframes().isEmpty()) {
            message(source, "No keyframes. Select this sequence, then /camkey add <keyframe>.", ChatFormatting.GRAY);
        }
        for (int i = start; i < Math.min(start + PAGE_SIZE, sequence.keyframes().size()); i++) {
            CameraKeyframe frame = sequence.keyframes().get(i);
            CameraPose pose = frame.pose();
            message(source, String.format(Locale.ROOT, "%d. %s  (%.2f, %.2f, %.2f)  yaw %.1f  pitch %.1f",
                    i + 1, frame.name(), pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch()), ChatFormatting.WHITE);
        }
        if (page < pages(sequence.keyframes().size())) {
            message(source, "Next: /camkey info " + sequence.name() + " " + (page + 1), ChatFormatting.GRAY);
        }
        return 1;
    }

    private static int pages(int count) {
        return Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static int pageStart(int page, int count) {
        if (page < 1 || page > pages(count)) {
            throw new IllegalArgumentException("Page must be between 1 and " + pages(count) + ".");
        }
        return (page - 1) * PAGE_SIZE;
    }

    private CompletableFuture<Suggestions> suggestSequences(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(controller.cachedSequences(context.getSource().getServer())
                .stream().map(CameraSequence::name), builder);
    }

    private CompletableFuture<Suggestions> suggestKeyframes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return builder.buildFuture();
        }
        return SharedSuggestionProvider.suggest(controller.cachedKeyframes(player).stream().map(CameraKeyframe::name), builder);
    }

    private CompletableFuture<Suggestions> suggestTargets(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return builder.buildFuture();
        }
        var frames = controller.cachedKeyframes(player);
        return SharedSuggestionProvider.suggest(Stream.concat(
                IntStream.rangeClosed(1, frames.size()).mapToObj(Integer::toString),
                frames.stream().map(CameraKeyframe::name)).distinct(), builder);
    }

    private static int help(CommandSourceStack source) {
        message(source, "--- Camera Keyframe Pro ---", ChatFormatting.GOLD);
        message(source, "/camkey create <name> | usesequence <name>", ChatFormatting.AQUA);
        message(source, "/camkey add [keyframe] | deletekey <keyframe> - omitted capture name is automatic", ChatFormatting.AQUA);
        message(source, "/camkey delete <sequence> - delete the entire named sequence and all its keyframes", ChatFormatting.AQUA);
        message(source, "/camkey info [sequence] [page] - defaults to current sequence, page 1", ChatFormatting.AQUA);
        message(source, "/camkey list [page] - all sequences, defaults to page 1", ChatFormatting.AQUA);
        message(source, "/camkey goto <index-or-name> - move to a pose, keeping your game mode", ChatFormatting.AQUA);
        message(source, "/camkey set_duration <seconds> - save the current sequence's duration", ChatFormatting.AQUA);
        message(source, "/camkey set_easing <linear|smoothstep> - save the current sequence's easing", ChatFormatting.AQUA);
        message(source, "/camkey play [sequence] [seconds] | stop - supplied seconds are saved", ChatFormatting.AQUA);
        message(source, "/camkey save - retry saving both NBT and JSON", ChatFormatting.AQUA);
        message(source, "Hotkeys: K captures; Pause/Break plays. Rebind in Controls. Local host only.",
                ChatFormatting.GRAY);
        message(source, "Names: lowercase letters, digits, _ or -. Select a sequence before using hotkeys.",
                ChatFormatting.GRAY);
        return 1;
    }

    private static void message(CommandSourceStack source, String text, ChatFormatting color) {
        source.sendSuccess(() -> Component.literal(text).withStyle(color), false);
    }

    @FunctionalInterface
    private interface Action {
        int execute(CommandContext<CommandSourceStack> context, ServerPlayer player) throws IOException;
    }
}
