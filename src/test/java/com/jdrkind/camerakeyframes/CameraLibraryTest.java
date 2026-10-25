package com.jdrkind.camerakeyframes;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CameraLibraryTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "Uppercase", "../outside", "two words", "has.dot",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rejectsInvalidNames(String name) {
        assertThrows(IllegalArgumentException.class, () ->
                new CameraLibrary(List.of()).createSequence(name, "minecraft:overworld"));
    }

    @Test
    void acceptsBoundaryNameAndRejectsDuplicateSequences() {
        String name = "a".repeat(64);
        var data = new CameraLibrary(List.of()).createSequence(name, "minecraft:overworld");
        assertEquals(name, data.sequence(name).name());
        assertThrows(IllegalArgumentException.class, () -> data.createSequence(name, "minecraft:the_nether"));
    }

    @Test
    void editsAreImmutableAndDuplicateFramesAreRejected() {
        var original = new CameraLibrary(List.of()).createSequence("intro", "minecraft:overworld");
        var frame = new CameraKeyframe("start", 0, 64, 0, 0, 0);
        var updated = original.addKeyframe("intro", frame);
        assertTrue(original.sequence("intro").keyframes().isEmpty());
        assertEquals(List.of(frame), updated.sequence("intro").keyframes());
        assertThrows(IllegalArgumentException.class, () -> updated.addKeyframe("intro", frame));
        assertThrows(UnsupportedOperationException.class, () -> updated.sequences().clear());
        assertThrows(UnsupportedOperationException.class, () -> updated.sequence("intro").keyframes().clear());
    }

    @Test
    void sameFrameNameAllowedInDifferentSequences() {
        var frame = new CameraKeyframe("start", 0, 64, 0, 0, 0);
        var data = new CameraLibrary(List.of()).createSequence("one", "minecraft:overworld")
                .createSequence("two", "minecraft:overworld")
                .addKeyframe("one", frame).addKeyframe("two", frame);
        assertEquals(1, data.sequence("one").keyframes().size());
        assertEquals(1, data.sequence("two").keyframes().size());
    }

    @Test
    void missingSequenceAndFrameFailClearly() {
        var data = new CameraLibrary(List.of()).createSequence("intro", "minecraft:overworld");
        assertThrows(IllegalArgumentException.class, () -> data.sequence("missing"));
        assertThrows(IllegalArgumentException.class, () -> data.deleteKeyframe("intro", "missing"));
    }

    @Test
    void validatesDimensions() {
        var sequence = new CameraSequence("intro", "minecraft:overworld", List.of());
        assertDoesNotThrow(() -> sequence.requireDimension("minecraft:overworld"));
        assertThrows(IllegalArgumentException.class, () -> sequence.requireDimension("minecraft:the_nether"));
        assertThrows(IllegalArgumentException.class, () -> new CameraSequence("intro", "not a dimension", List.of()));
    }

    @Test
    void validatesCoordinatesAndAngles() {
        assertThrows(IllegalArgumentException.class, () -> new CameraKeyframe("a", Double.NaN, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CameraKeyframe("a", 0, Double.POSITIVE_INFINITY, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CameraKeyframe("a", 0, 0, Double.NEGATIVE_INFINITY, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CameraKeyframe("a", 0, 0, 0, Float.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new CameraKeyframe("a", 0, 0, 0, 0, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new CameraKeyframe("a", 0, 0, 0, 0, 91));
        assertDoesNotThrow(() -> new CameraKeyframe("a", 0, 0, 0, 180, -90));
        assertDoesNotThrow(() -> new CameraKeyframe("a", 0, 0, 0, -180, 90));
    }

    @Test
    void namedKeyframeComposesValidatedPose() {
        var pose = new CameraPose(1.123456789, 64, -2, 179, -90);
        var frame = new CameraKeyframe("start", pose);
        assertSame(pose, frame.pose());
        assertEquals(new CameraKeyframe("start", 1.123456789, 64, -2, 179, -90), frame);
        assertThrows(IllegalArgumentException.class, () -> new CameraKeyframe("start", null));
        assertThrows(IllegalArgumentException.class, () -> new CameraKeyframe("INVALID", pose));
        assertThrows(IllegalArgumentException.class, () -> new CameraPose(0, 0, 0, 0, 91));
    }

    @Test
    void standaloneSequenceCopiesInputAndRetainsDuration() {
        var frames = new java.util.ArrayList<CameraKeyframe>();
        frames.add(new CameraKeyframe("start", new CameraPose(0, 64, 0, 0, 0)));
        var sequence = new CameraSequence("intro", "minecraft:overworld", frames, java.util.OptionalDouble.of(10));
        frames.clear();
        assertEquals(1, sequence.keyframes().size());
        assertEquals(sequence.keyframes().getFirst(), sequence.resolveKeyframe("1"));
        assertEquals(sequence.keyframes().getFirst(), sequence.resolveKeyframe("start"));
        assertEquals(10, sequence.durationSeconds().orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> sequence.keyframes().clear());
    }

    @Test
    void easingDefaultsToLinearAndSurvivesEverySequenceEdit() {
        var original = new CameraLibrary(List.of()).createSequence("intro", "minecraft:overworld");
        assertEquals(CameraEasing.LINEAR, original.sequence("intro").easing());
        var eased = original.setEasing("intro", CameraEasing.SMOOTHSTEP);
        assertEquals(CameraEasing.LINEAR, original.sequence("intro").easing());
        var added = eased.addKeyframe("intro", new CameraKeyframe("a", 0, 64, 0, 0, 0));
        assertEquals(CameraEasing.SMOOTHSTEP, added.sequence("intro").easing());
        var timed = added.setDuration("intro", 10);
        assertEquals(CameraEasing.SMOOTHSTEP, timed.sequence("intro").easing());
        var deleted = timed.deleteKeyframe("intro", "a");
        assertEquals(CameraEasing.SMOOTHSTEP, deleted.sequence("intro").easing());
        var linear = timed.setEasing("intro", CameraEasing.LINEAR);
        assertEquals(CameraEasing.LINEAR, linear.sequence("intro").easing());
        assertEquals(timed.sequence("intro").keyframes(), linear.sequence("intro").keyframes());
        assertEquals(timed.sequence("intro").durationSeconds(), linear.sequence("intro").durationSeconds());
        assertThrows(IllegalArgumentException.class, () -> original.setEasing("intro", null));
    }

    @Test
    void sequenceDeletionIsImmutableAndPreservesRemainingSequencesAndOrder() {
        var library = new CameraLibrary(List.of()).createSequence("first", "minecraft:overworld")
                .createSequence("middle", "minecraft:overworld")
                .addKeyframe("middle", new CameraKeyframe("pose", 0, 64, 0, 0, 0))
                .createSequence("last", "minecraft:overworld").setDuration("last", 10)
                .setEasing("last", CameraEasing.SMOOTHSTEP);
        var deleted = library.deleteSequence("middle");
        assertEquals(3, library.sequences().size());
        assertEquals(List.of(library.sequence("first"), library.sequence("last")), deleted.sequences());
        assertThrows(IllegalArgumentException.class, () -> deleted.sequence("middle"));
        assertThrows(IllegalArgumentException.class, () -> deleted.deleteSequence("absent"));
        assertTrue(deleted.deleteSequence("first").deleteSequence("last").sequences().isEmpty());
    }
}
