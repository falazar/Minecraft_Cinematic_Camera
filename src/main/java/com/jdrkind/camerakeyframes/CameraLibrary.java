package com.jdrkind.camerakeyframes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

public record CameraLibrary(List<CameraSequence> sequences) {
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,64}");

    public CameraLibrary {
        sequences = List.copyOf(sequences);
        var names = new HashSet<String>();
        for (CameraSequence sequence : sequences) {
            if (!names.add(sequence.name())) {
                throw new IllegalArgumentException("Duplicate sequence: " + sequence.name());
            }
        }
    }

    public static void validateName(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Use 1-64 lowercase letters, digits, underscores, or hyphens for names.");
        }
    }

    public CameraSequence sequence(String name) {
        return sequences.stream().filter(sequence -> sequence.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Sequence not found: " + name));
    }

    public CameraLibrary createSequence(String name, String dimension) {
        validateName(name);
        if (sequences.stream().anyMatch(sequence -> sequence.name().equals(name))) {
            throw new IllegalArgumentException("Sequence already exists: " + name);
        }
        var updated = new ArrayList<>(sequences);
        updated.add(new CameraSequence(name, dimension, List.of()));
        return new CameraLibrary(updated);
    }

    public CameraLibrary addKeyframe(String sequenceName, CameraKeyframe keyframe) {
        CameraSequence sequence = sequence(sequenceName);
        if (sequence.keyframes().stream().anyMatch(frame -> frame.name().equals(keyframe.name()))) {
            throw new IllegalArgumentException("Keyframe already exists in " + sequenceName + ": " + keyframe.name());
        }
        var frames = new ArrayList<>(sequence.keyframes());
        frames.add(keyframe);
        return replace(new CameraSequence(sequence.name(), sequence.dimension(), frames, sequence.durationSeconds(), sequence.easing()));
    }

    public CameraLibrary deleteKeyframe(String sequenceName, String keyframeName) {
        CameraSequence sequence = sequence(sequenceName);
        var frames = new ArrayList<>(sequence.keyframes());
        if (!frames.removeIf(frame -> frame.name().equals(keyframeName))) {
            throw new IllegalArgumentException("Keyframe not found in " + sequenceName + ": " + keyframeName);
        }
        return replace(new CameraSequence(sequence.name(), sequence.dimension(), frames, sequence.durationSeconds(), sequence.easing()));
    }

    public CameraLibrary deleteSequence(String name) {
        var remaining = new ArrayList<>(sequences);
        if (!remaining.removeIf(sequence -> sequence.name().equals(name))) {
            throw new IllegalArgumentException("Sequence not found: " + name);
        }
        return new CameraLibrary(remaining);
    }

    public CameraLibrary setDuration(String sequenceName, double seconds) {
        CameraSequence sequence = sequence(sequenceName);
        return replace(new CameraSequence(sequence.name(), sequence.dimension(), sequence.keyframes(),
                OptionalDouble.of(seconds), sequence.easing()));
    }

    public CameraLibrary setEasing(String sequenceName, CameraEasing easing) {
        CameraSequence sequence = sequence(sequenceName);
        return replace(new CameraSequence(sequence.name(), sequence.dimension(), sequence.keyframes(),
                sequence.durationSeconds(), easing));
    }

    private CameraLibrary replace(CameraSequence replacement) {
        return new CameraLibrary(sequences.stream()
                .map(sequence -> sequence.name().equals(replacement.name()) ? replacement : sequence).toList());
    }

}
