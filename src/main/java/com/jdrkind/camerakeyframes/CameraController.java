package com.jdrkind.camerakeyframes;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Shared application operations. Call on the owning server thread, never the client render thread. */
public final class CameraController {
    private final Map<MinecraftServer, Session> sessions = new HashMap<>();
    private final CameraPlayback playback;

    public CameraController(CameraPlayback playback) {
        this.playback = playback;
    }

    public CameraLibrary data(ServerPlayer player) throws IOException {
        return session(player).storage.data();
    }

    public SaveResult create(ServerPlayer player, String name) throws IOException {
        Session session = session(player);
        playback.requireIdle();
        CameraLibrary next = session.storage.data().createSequence(name, dimension(player));
        SaveResult result = save(session, next, "Created and selected sequence '" + name + "'");
        session.active.put(player.getUUID(), name);
        return result;
    }

    public void select(ServerPlayer player, String name) throws IOException {
        Session session = session(player);
        playback.requireIdle();
        session.storage.data().sequence(name).requireDimension(dimension(player));
        session.active.put(player.getUUID(), name);
    }

    public SaveResult capture(ServerPlayer player) throws IOException {
        return capture(player, data(player).sequence(active(player)).nextKeyframeName());
    }

    public SaveResult capture(ServerPlayer player, String name) throws IOException {
        Session session = session(player);
        playback.requireIdle();
        String sequence = active(player);
        var pose = new CameraPose(player.getX(), player.getY(), player.getZ(),
                Mth.wrapDegrees(player.getYRot()), player.getXRot());
        var frame = new CameraKeyframe(name, pose);
        return save(session, session.storage.data().addKeyframe(sequence, frame),
                "Added '" + frame.name() + "' to '" + sequence + "'");
    }

    public SaveResult deleteKeyframe(ServerPlayer player, String frame) throws IOException {
        Session session = session(player);
        playback.requireIdle();
        String sequence = active(player);
        return save(session, session.storage.data().deleteKeyframe(sequence, frame),
                "Deleted '" + frame + "' from '" + sequence + "'");
    }

    public SaveResult deleteSequence(ServerPlayer player, String name) throws IOException {
        Session session = session(player);
        playback.requireIdle();
        session.storage.data().sequence(name).requireDimension(dimension(player));
        SaveResult result = save(session, session.storage.data().deleteSequence(name),
                "Deleted sequence '" + name + "' and all its keyframes");
        session.active.values().removeIf(name::equals);
        return result;
    }

    public CameraKeyframe goTo(ServerPlayer player, String target) throws IOException {
        CameraSequence sequence = data(player).sequence(active(player));
        playback.goTo(player, sequence, target);
        return sequence.resolveKeyframe(target);
    }

    public SaveResult setDuration(ServerPlayer player, double seconds) throws IOException {
        Session session = session(player);
        playback.requireIdle();
        String name = active(player);
        return save(session, session.storage.data().setDuration(name, seconds), durationDescription(name, seconds));
    }

    public SaveResult setEasing(ServerPlayer player, CameraEasing easing) throws IOException {
        Session session = session(player);
        playback.requireIdle();
        String name = active(player);
        CameraLibrary next = session.storage.data().setEasing(name, easing);
        return save(session, next, "Set '" + name + "' easing to " + easing.id());
    }

    public CameraSequence play(ServerPlayer player) throws IOException {
        return play(player, selected(player));
    }

    public CameraSequence play(ServerPlayer player, String name) throws IOException {
        CameraSequence sequence = data(player).sequence(name);
        playback.start(player, sequence);
        return sequence;
    }

    public PlayResult playWithDuration(ServerPlayer player, String name, double seconds) throws IOException {
        Session session = session(player);
        playback.requireIdle();
        CameraLibrary next = session.storage.data().setDuration(name, seconds);
        playback.validateStart(player, next.sequence(name));
        SaveResult result = save(session, next, durationDescription(name, seconds));
        if (!result.complete()) {
            return new PlayResult(result, Optional.of(
                    "Playback was not started because both save files were not written successfully."));
        }
        try {
            playback.start(player, session.storage.data().sequence(name));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            CameraKeyframeProMod.LOGGER.error("Camera duration saved, but playback could not start", exception);
            return new PlayResult(result, Optional.of("Playback could not start after saving duration: " + exception.getMessage()));
        }
        return new PlayResult(result, Optional.empty());
    }

    public boolean stop(ServerPlayer player) throws IOException {
        session(player);
        return playback.stop(player, "Playback stopped; original player state restored.");
    }

    public SaveResult save(ServerPlayer player) throws IOException {
        Session session = session(player);
        return save(session, session.storage.data(), "Saved camera data");
    }

    public String selected(ServerPlayer player) throws IOException {
        String name = session(player).active.get(player.getUUID());
        if (name == null) {
            throw new IllegalArgumentException("No active sequence. Use /camkey create <name> or /camkey usesequence <name>.");
        }
        return name;
    }

    public Optional<String> cachedSelection(ServerPlayer player) {
        Session session = sessions.get(player.getServer());
        return session == null ? Optional.empty() : Optional.ofNullable(session.active.get(player.getUUID()));
    }

    public List<CameraSequence> cachedSequences(MinecraftServer server) {
        Session session = sessions.get(server);
        return session == null ? List.of() : session.storage.data().sequences();
    }

    public List<CameraKeyframe> cachedKeyframes(ServerPlayer player) {
        Session session = sessions.get(player.getServer());
        return cachedSelection(player).map(name -> session.storage.data().sequence(name).keyframes()).orElse(List.of());
    }

    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            playback.stop(player, "Playback stopped before saving and leaving the world.");
            Session session = sessions.get(player.getServer());
            if (session != null) {
                session.active.remove(player.getUUID());
            }
        }
    }

    public void onServerStopped(ServerStoppedEvent event) {
        sessions.remove(event.getServer());
    }

    private Session session(ServerPlayer player) throws IOException {
        MinecraftServer server = player.getServer();
        if (!server.isSingleplayer() || !server.isSingleplayerOwner(player.getGameProfile())) {
            throw new IllegalArgumentException("Camera capture is available only to the local single-player host.");
        }
        Session session = sessions.get(server);
        if (session == null) {
            session = new Session(CameraStorage.load(server.getWorldPath(LevelResource.ROOT).resolve("data")));
            sessions.put(server, session);
        }
        return session;
    }

    private String active(ServerPlayer player) throws IOException {
        String name = selected(player);
        data(player).sequence(name).requireDimension(dimension(player));
        return name;
    }

    private static String dimension(ServerPlayer player) {
        return player.level().dimension().location().toString();
    }

    private static String durationDescription(String name, double seconds) {
        return "Set '" + name + "' duration to " + seconds + " seconds";
    }

    private static SaveResult save(Session session, CameraLibrary next, String description) throws IOException {
        var mirrorError = session.storage.save(next);
        mirrorError.ifPresent(error -> CameraKeyframeProMod.LOGGER.error("Camera NBT saved, but JSON mirror failed", error));
        return new SaveResult(description, mirrorError);
    }

    /** Returned only after NBT commits. Adapters must report any JSON mirror failure. */
    public record SaveResult(String description, Optional<IOException> mirrorError) {
        public boolean complete() {
            return mirrorError.isEmpty();
        }
    }

    public record PlayResult(SaveResult save, Optional<String> error) {
    }

    private static final class Session {
        private final CameraStorage storage;
        private final Map<UUID, String> active = new HashMap<>();

        private Session(CameraStorage storage) {
            this.storage = storage;
        }
    }
}
