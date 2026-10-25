package com.jdrkind.camerakeyframes;

import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class CameraPlayback {
    public static final int END_HOLD_SECONDS = 4;
    private static volatile View clientView;
    private Active active;

    public static View clientView() {
        return clientView;
    }

    public void requireIdle() {
        if (active != null) {
            throw new IllegalArgumentException("Camera playback is running. Use /camkey stop before editing or moving.");
        }
    }

    public void goTo(ServerPlayer player, CameraSequence sequence, String target) {
        requireIdle();
        sequence.requireDimension(player.level().dimension().location().toString());
        requireMovable(player);
        CameraPose pose = sequence.resolveKeyframe(target).pose();
        validatePosition(player.serverLevel(), pose);
        move(player, player.serverLevel(), pose);
    }

    public CameraPath validateStart(ServerPlayer player, CameraSequence sequence) {
        requireIdle();
        sequence.requireDimension(player.level().dimension().location().toString());
        requireMovable(player);
        CameraPath path = new CameraPath(sequence);
        ServerLevel level = player.serverLevel();
        for (CameraKeyframe frame : sequence.keyframes()) {
            validatePosition(level, frame.pose());
        }
        return path;
    }

    public void start(ServerPlayer player, CameraSequence sequence) {
        CameraPath path = validateStart(player, sequence);
        ServerLevel level = player.serverLevel();
        var original = new CameraPose(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        GameType originalMode = player.gameMode.getGameModeForPlayer();
        boolean flying = player.getAbilities().flying;
        float flyingSpeed = player.getAbilities().getFlyingSpeed();
        if (originalMode != GameType.SPECTATOR) {
            player.setGameMode(GameType.SPECTATOR);
            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                if (player.gameMode.getGameModeForPlayer() != originalMode) {
                    player.setGameMode(originalMode);
                    CameraKeyframeProMod.LOGGER.error("Spectator request was redirected by another mod for {}; attempted restoring {}",
                            player.getUUID(), originalMode);
                }
                throw new IllegalArgumentException("Spectator mode was blocked by another mod; playback did not start.");
            }
        }
        var view = new View(player.getServer(), player.getUUID(), sequence.dimension(), path, original, level.getGameTime());
        active = new Active(player, level, originalMode, flying, flyingSpeed, view);
        try {
            move(player, level, path.sample(0));
            clientView = view;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            stop(player, "Playback could not start; restoring player state.");
            throw exception;
        }
    }

    public boolean stop(ServerPlayer player, String reason) {
        Active session = active;
        if (session == null || session.player != player) {
            return false;
        }
        active = null;
        clientView = null;
        boolean positionRestored = false;
        try {
            move(player, session.level, session.view.original());
            positionRestored = true;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            CameraKeyframeProMod.LOGGER.error("Could not restore camera position for {}", player.getUUID(), exception);
            player.sendSystemMessage(Component.literal("Camera stopped, but position restoration failed. See logs.")
                    .withStyle(ChatFormatting.RED));
        } finally {
            if (player.gameMode.getGameModeForPlayer() != session.mode) {
                player.setGameMode(session.mode);
            }
            if (player.gameMode.getGameModeForPlayer() != session.mode) {
                CameraKeyframeProMod.LOGGER.error("Could not restore {} to game mode {}", player.getUUID(), session.mode);
                player.sendSystemMessage(Component.literal("Camera stopped, but another mod blocked game-mode restoration. "
                        + "Restore " + session.mode.getName() + " manually.").withStyle(ChatFormatting.RED));
            } else {
                player.getAbilities().flying = session.flying;
                player.getAbilities().setFlyingSpeed(session.flyingSpeed);
                player.onUpdateAbilities();
                if (positionRestored) {
                    player.sendSystemMessage(Component.literal(reason).withStyle(ChatFormatting.GREEN));
                }
            }
        }
        return true;
    }

    public void onTick(ServerTickEvent.Post event) {
        Active session = active;
        if (session == null || session.player.getServer() != event.getServer()) {
            return;
        }
        ServerPlayer player = session.player;
        if (!player.isAlive() || player.isRemoved() || player.serverLevel() != session.level
                || player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            stop(player, "Playback interrupted; original player state restored.");
            return;
        }
        long elapsedTicks = session.level.getGameTime() - session.view.startGameTime();
        if (elapsedTicks >= Math.ceil(session.view.path().seconds() * 20) + END_HOLD_SECONDS * 20L) {
            stop(player, "Playback finished; original player state restored.");
            return;
        }
        if (!event.getServer().tickRateManager().runsNormally()) {
            return;
        }
        try {
            move(player, session.level, session.view.path().sample(elapsedTicks / 20.0));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            CameraKeyframeProMod.LOGGER.error("Camera playback failed for {}", player.getUUID(), exception);
            stop(player, "Playback failed; original player state restored. See logs.");
        }
    }

    public void onServerStopping(ServerStoppingEvent event) {
        if (active != null && active.player.getServer() == event.getServer()) {
            stop(active.player, "Playback stopped for world shutdown.");
        }
    }

    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player, "Playback stopped before player death.");
        }
    }

    private static void requireMovable(ServerPlayer player) {
        if (!player.isAlive() || player.isSleeping() || player.isPassenger() || player.getCamera() != player
                || !player.getShoulderEntityLeft().isEmpty() || !player.getShoulderEntityRight().isEmpty()) {
            throw new IllegalArgumentException("Stand or fly freely before moving the camera: no bed, mount, "
                    + "spectated entity, or shoulder pets.");
        }
    }

    private static void validatePosition(ServerLevel level, CameraPose pose) {
        if (!Level.isInSpawnableBounds(BlockPos.containing(pose.x(), pose.y(), pose.z()))
                || !level.getWorldBorder().isWithinBounds(pose.x(), pose.z())) {
            throw new IllegalArgumentException("Keyframe is outside the world's supported bounds or border.");
        }
    }

    private static void move(ServerPlayer player, ServerLevel level, CameraPose pose) {
        level.getChunk(BlockPos.containing(pose.x(), pose.y(), pose.z()));
        player.teleportTo(level, pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch());
        player.setYHeadRot(pose.yaw());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
    }

    // Immutable handoff between the integrated-server thread and physical-client render thread.
    public record View(MinecraftServer server, UUID playerId, String dimension, CameraPath path,
            CameraPose original, long startGameTime) {
    }

    private record Active(ServerPlayer player, ServerLevel level, GameType mode, boolean flying,
            float flyingSpeed, View view) {
    }
}
