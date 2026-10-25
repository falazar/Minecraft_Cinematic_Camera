package com.jdrkind.camerakeyframes;

import java.util.List;

public final class CameraPath {
    private final List<CameraKeyframe> frames;
    private final double seconds;
    private final CameraEasing easing;

    public CameraPath(CameraSequence sequence) {
        if (sequence.durationSeconds().isEmpty()) {
            throw new IllegalArgumentException("No playback duration for '" + sequence.name()
                    + "'. Select it with /camkey usesequence " + sequence.name()
                    + ", then /camkey set_duration 10 (seconds).");
        }
        if (sequence.keyframes().size() < 2) {
            throw new IllegalArgumentException("Playback needs at least two keyframes. Use /camkey add <name>.");
        }
        frames = sequence.keyframes();
        seconds = sequence.durationSeconds().getAsDouble();
        easing = sequence.easing();
    }

    public double seconds() {
        return seconds;
    }

    public CameraPose sample(double elapsedSeconds) {
        if (!Double.isFinite(elapsedSeconds)) {
            throw new IllegalArgumentException("Playback time must be finite.");
        }
        if (elapsedSeconds <= 0) {
            return frames.getFirst().pose();
        }
        if (elapsedSeconds >= seconds) {
            return frames.getLast().pose();
        }
        double segmentTime = easing.apply(elapsedSeconds / seconds) * (frames.size() - 1);
        int index = Math.min((int) segmentTime, frames.size() - 2);
        double fraction = segmentTime - index;
        CameraPose from = frames.get(index).pose();
        CameraPose to = frames.get(index + 1).pose();
        double yawDelta = ((to.yaw() - (double) from.yaw()) % 360 + 540) % 360 - 180;
        return new CameraPose(lerp(from.x(), to.x(), fraction), lerp(from.y(), to.y(), fraction),
                lerp(from.z(), to.z(), fraction), (float) (from.yaw() + yawDelta * fraction),
                (float) lerp(from.pitch(), to.pitch(), fraction));
    }

    private static double lerp(double from, double to, double fraction) {
        return from + (to - from) * fraction;
    }

}
