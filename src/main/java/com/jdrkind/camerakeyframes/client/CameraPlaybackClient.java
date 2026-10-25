package com.jdrkind.camerakeyframes.client;

import com.jdrkind.camerakeyframes.CameraKeyframeProMod;
import com.jdrkind.camerakeyframes.CameraPlayback;
import com.jdrkind.camerakeyframes.CameraPose;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

@EventBusSubscriber(modid = CameraKeyframeProMod.MODID, value = Dist.CLIENT)
public final class CameraPlaybackClient {
    private static CameraPlayback.View previous;
    private static CameraType originalCameraType;
    private static double lastSeconds;

    private CameraPlaybackClient() {
    }

    private static CameraPlayback.View current() {
        Minecraft minecraft = Minecraft.getInstance();
        CameraPlayback.View view = CameraPlayback.clientView();
        if (view == null || minecraft.player == null || minecraft.level == null
                || minecraft.getSingleplayerServer() != view.server()
                || !minecraft.player.getUUID().equals(view.playerId())
                || !minecraft.level.dimension().location().toString().equals(view.dimension())) {
            return null;
        }
        return view;
    }

    @SubscribeEvent
    public static void onRender(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        CameraPlayback.View view = current();
        if (view == null) {
            clear(true);
            return;
        }
        if (previous != view) {
            clear(false);
            previous = view;
            originalCameraType = minecraft.options.getCameraType();
            lastSeconds = 0;
        }
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.setCameraEntity(minecraft.player);
        if (!minecraft.isPaused() && minecraft.level.tickRateManager().runsNormally()) {
            double ticks = minecraft.level.getGameTime() - view.startGameTime()
                    + event.getPartialTick().getGameTimeDeltaPartialTick(false);
            lastSeconds = Math.max(lastSeconds, Math.min(view.path().seconds(), ticks / 20.0));
        }
        // Sample at render frequency on the actual player, avoiding 20 Hz teleport snapping.
        apply(minecraft.player, view.path().sample(lastSeconds));
    }

    private static void apply(LocalPlayer player, CameraPose pose) {
        player.moveTo(pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch());
        player.setYHeadRot(pose.yaw());
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void clear(boolean restorePosition) {
        if (previous == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (restorePosition && minecraft.player != null && minecraft.level != null
                && minecraft.getSingleplayerServer() == previous.server()
                && minecraft.player.getUUID().equals(previous.playerId())
                && minecraft.level.dimension().location().toString().equals(previous.dimension())) {
            apply(minecraft.player, previous.original());
        }
        minecraft.options.setCameraType(originalCameraType);
        previous = null;
        originalCameraType = null;
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        if (current() != null) {
            var input = event.getInput();
            input.forwardImpulse = 0;
            input.leftImpulse = 0;
            input.up = input.down = input.left = input.right = input.jumping = input.shiftKeyDown = false;
            event.getEntity().setDeltaMovement(Vec3.ZERO);
        }
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (current() != null) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (current() != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear(false);
    }
}
