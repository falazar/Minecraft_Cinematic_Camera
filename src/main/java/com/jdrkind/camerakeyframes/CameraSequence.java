package com.jdrkind.camerakeyframes;

import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

public record CameraSequence(String name, String dimension, List<CameraKeyframe> keyframes,
        OptionalDouble durationSeconds, CameraEasing easing) {
    private static final Pattern DIMENSION = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public CameraSequence(String name, String dimension, List<CameraKeyframe> keyframes) {
        this(name, dimension, keyframes, OptionalDouble.empty());
    }

    public CameraSequence(String name, String dimension, List<CameraKeyframe> keyframes, OptionalDouble durationSeconds) {
        this(name, dimension, keyframes, durationSeconds, CameraEasing.LINEAR);
    }

    public CameraSequence {
        CameraLibrary.validateName(name);
        if (dimension == null || !DIMENSION.matcher(dimension).matches()) {
            throw new IllegalArgumentException("Invalid dimension identifier: " + dimension);
        }
        keyframes = List.copyOf(keyframes);
        if (durationSeconds == null) {
            throw new IllegalArgumentException("Duration must be explicitly set or unset.");
        }
        if (easing == null) {
            throw new IllegalArgumentException("Easing must be linear or smoothstep.");
        }
        if (durationSeconds.isPresent()) {
            double seconds = durationSeconds.getAsDouble();
            if (!Double.isFinite(seconds) || seconds < 0.1 || seconds > 3600) {
                throw new IllegalArgumentException("Duration must be between 0.1 and 3600 seconds.");
            }
        }
        var names = new HashSet<String>();
        for (CameraKeyframe frame : keyframes) {
            if (!names.add(frame.name())) {
                throw new IllegalArgumentException("Duplicate keyframe in " + name + ": " + frame.name());
            }
        }
    }

    public CameraKeyframe resolveKeyframe(String target) {
        if (target.matches("-?\\d+")) {
            int index;
            try {
                index = Integer.parseInt(target);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Keyframe index is too large: " + target);
            }
            if (index < 1 || index > keyframes.size()) {
                throw new IllegalArgumentException("Keyframe index must be between 1 and " + keyframes.size()
                        + ". Use /camkey info to see the numbered poses.");
            }
            return keyframes.get(index - 1);
        }
        return keyframes.stream().filter(frame -> frame.name().equals(target)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Keyframe not found: " + target));
    }

    public String nextKeyframeName() {
        var names = new HashSet<String>();
        keyframes.forEach(frame -> names.add(frame.name()));
        int index = 1;
        while (names.contains("keyframe_" + index)) {
            index++;
        }
        return "keyframe_" + index;
    }

    public void requireDimension(String currentDimension) {
        if (!dimension.equals(currentDimension)) {
            throw new IllegalArgumentException("Sequence " + name + " belongs to " + dimension
                    + "; switch to that dimension first.");
        }
    }
}
