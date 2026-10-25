package com.jdrkind.camerakeyframes;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CameraPathTest {
    private CameraSequence sequence() {
        return new CameraSequence("intro", "minecraft:overworld", List.of(
                new CameraKeyframe("wide", 0, 64, 0, 179, -20),
                new CameraKeyframe("close", 10, 74, -10, -179, 20),
                new CameraKeyframe("end", 20, 64, -20, -169, 0)), OptionalDouble.of(10));
    }

    @Test
    void samplesEndpointsAndEqualDurationSegments() {
        var path = new CameraPath(sequence());
        assertEquals(sequence().keyframes().getFirst().pose(), path.sample(-1));
        assertEquals(sequence().keyframes().getFirst().pose(), path.sample(0));
        assertEquals(sequence().keyframes().get(1).pose(), path.sample(5));
        assertEquals(sequence().keyframes().getLast().pose(), path.sample(10));
        assertEquals(sequence().keyframes().getLast().pose(), path.sample(20));
        var middle = path.sample(2.5);
        assertEquals(5, middle.x());
        assertEquals(69, middle.y());
        assertEquals(-5, middle.z());
        assertEquals(180, middle.yaw());
        assertEquals(0, middle.pitch());
        assertEquals(15, path.sample(7.5).x());
    }

    @Test
    void continuousSamplesDoNotJumpAtSegmentBoundaryOrYawWrap() {
        var path = new CameraPath(sequence());
        var before = path.sample(4.9999);
        var after = path.sample(5.0001);
        assertTrue(Math.abs(after.x() - before.x()) < 0.001);
        double angleDifference = ((after.yaw() - before.yaw()) % 360 + 540) % 360 - 180;
        assertTrue(Math.abs(angleDifference) < 0.001);
        assertThrows(IllegalArgumentException.class, () -> path.sample(Double.NaN));
    }

    @Test
    void smoothstepEasesWholeSequencePositionAndRotationWithoutChangingEndpoints() {
        var sequence = sequence();
        var path = new CameraPath(new CameraSequence(sequence.name(), sequence.dimension(),
                sequence.keyframes(), sequence.durationSeconds(), CameraEasing.SMOOTHSTEP));
        assertEquals(10, path.seconds());
        assertEquals(sequence.keyframes().getFirst().pose(), path.sample(-1));
        assertEquals(sequence.keyframes().getFirst().pose(), path.sample(0));
        var quarter = path.sample(2.5);
        assertEquals(3.125, quarter.x());
        assertEquals(67.125, quarter.y());
        assertEquals(-3.125, quarter.z());
        assertEquals(179.625F, quarter.yaw());
        assertEquals(-7.5F, quarter.pitch());
        assertEquals(sequence.keyframes().get(1).pose(), path.sample(5));
        assertEquals(16.875, path.sample(7.5).x());
        assertEquals(sequence.keyframes().getLast().pose(), path.sample(10));
        assertEquals(sequence.keyframes().getLast().pose(), path.sample(14));
        assertThrows(IllegalArgumentException.class, () -> path.sample(Double.NaN));
    }

    @Test
    void smoothstepSlowsOnlyAtEndsAndDoesNotOvershootOrStopAtMiddleKeyframe() {
        var sequence = sequence();
        var path = new CameraPath(new CameraSequence(sequence.name(), sequence.dimension(),
                sequence.keyframes(), sequence.durationSeconds(), CameraEasing.SMOOTHSTEP));
        double delta = 0.001;
        assertTrue((path.sample(delta).x() - path.sample(0).x()) / delta < 0.01);
        assertTrue((path.sample(10).x() - path.sample(10 - delta).x()) / delta < 0.01);
        assertTrue((path.sample(5).x() - path.sample(5 - delta).x()) / delta > 2.9);
        assertTrue((path.sample(5 + delta).x() - path.sample(5).x()) / delta > 2.9);
        double previous = 0;
        for (int i = 0; i <= 1000; i++) {
            double x = path.sample(i / 100.0).x();
            assertTrue(x >= previous && x <= 20);
            previous = x;
        }
        double yawBefore = path.sample(5 - delta).yaw();
        double yawAfter = path.sample(5 + delta).yaw();
        double difference = ((yawAfter - yawBefore) % 360 + 540) % 360 - 180;
        assertTrue(Math.abs(difference) < 0.01);
    }

    @Test
    void wholeSequenceEasingChangesIntermediateArrivalTimes() {
        var frames = List.of(new CameraKeyframe("a", 0, 64, 0, 0, 0),
                new CameraKeyframe("b", 10, 64, 0, 0, 0),
                new CameraKeyframe("c", 20, 64, 0, 0, 0),
                new CameraKeyframe("d", 30, 64, 0, 0, 0));
        var linear = new CameraPath(new CameraSequence("shot", "minecraft:overworld", frames, OptionalDouble.of(9)));
        var eased = new CameraPath(new CameraSequence("shot", "minecraft:overworld", frames,
                OptionalDouble.of(9), CameraEasing.SMOOTHSTEP));
        assertEquals(10, linear.sample(3).x());
        assertEquals(70.0 / 9.0, eased.sample(3).x(), 1e-10);
        assertEquals(200.0 / 9.0, eased.sample(6).x(), 1e-10);
    }

    @Test
    void easingValidatesNamesAndProgress() {
        assertEquals(CameraEasing.LINEAR, CameraEasing.parse("linear"));
        assertEquals(CameraEasing.SMOOTHSTEP, CameraEasing.parse("smoothstep"));
        assertThrows(IllegalArgumentException.class, () -> CameraEasing.parse("unknown"));
        assertThrows(IllegalArgumentException.class, () -> CameraEasing.parse(null));
        for (CameraEasing easing : CameraEasing.values()) {
            for (double invalid : new double[] {-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY}) {
                assertThrows(IllegalArgumentException.class, () -> easing.apply(invalid));
            }
        }
    }

    @Test
    void missingDurationGivesActionableHelpAndOnePoseCannotPlay() {
        var unset = new CameraSequence("intro", "minecraft:overworld", sequence().keyframes());
        var error = assertThrows(IllegalArgumentException.class, () -> new CameraPath(unset));
        assertTrue(error.getMessage().contains("/camkey set_duration"));
        var one = new CameraSequence("intro", "minecraft:overworld",
                List.of(sequence().keyframes().getFirst()), OptionalDouble.of(10));
        assertThrows(IllegalArgumentException.class, () -> new CameraPath(one));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, 0.099, 3600.01, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsInvalidDurations(double seconds) {
        assertThrows(IllegalArgumentException.class, () -> new CameraSequence(
                "intro", "minecraft:overworld", sequence().keyframes(), OptionalDouble.of(seconds)));
    }

    @Test
    void resolvesOneBasedIndicesOrExactNamesAndRenumbersAfterDeletion() {
        var sequence = sequence();
        assertEquals(sequence.keyframes().getFirst(), sequence.resolveKeyframe("1"));
        assertEquals(sequence.keyframes().get(1), sequence.resolveKeyframe("close"));
        for (String invalid : List.of("0", "-1", "4", "999999999999999999999", "absent")) {
            assertThrows(IllegalArgumentException.class, () -> sequence.resolveKeyframe(invalid));
        }
        var changed = new CameraLibrary(List.of(sequence)).deleteKeyframe("intro", "wide").sequence("intro");
        assertEquals("close", changed.resolveKeyframe("1").name());
        assertEquals(10, changed.durationSeconds().orElseThrow());
    }

    @Test
    void numericTargetsAlwaysMeanIndicesEvenIfAKeyframeHasNumericName() {
        var sequence = new CameraSequence("intro", "minecraft:overworld", List.of(
                new CameraKeyframe("2", 0, 0, 0, 0, 0),
                new CameraKeyframe("other", 1, 0, 0, 0, 0)));
        assertEquals("other", sequence.resolveKeyframe("2").name());
    }
}
