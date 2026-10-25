package com.jdrkind.camerakeyframes;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonParser;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CameraStorageTest {
    @TempDir
    Path directory;

    private CameraLibrary sample() {
        return new CameraLibrary(List.of())
                .createSequence("intro", "minecraft:overworld")
                .addKeyframe("intro", new CameraKeyframe("wide", 12.25, 64, -20.5, 179, -30))
                .addKeyframe("intro", new CameraKeyframe("close", 20, 65.5, -10, -179, 15))
                .createSequence("nether", "minecraft:the_nether");
    }

    @Test
    void newWorldStartsEmptyWithoutWritingFiles() throws IOException {
        assertTrue(CameraStorage.load(directory).data().sequences().isEmpty());
        try (var files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void savesBothFormatsWithMatchingValuesAndReloadsFromNbt() throws IOException {
        CameraLibrary expected = sample();
        var storage = CameraStorage.load(directory);
        assertTrue(storage.save(expected).isEmpty());
        assertEquals(expected, storage.data());
        assertEquals(expected, CameraStorage.load(directory).data());

        var json = JsonParser.parseString(Files.readString(directory.resolve("camerakeyframepro.json"))).getAsJsonObject();
        assertEquals(3, json.get("format_version").getAsInt());
        var sequences = json.getAsJsonArray("sequences");
        assertEquals(expected.sequences().size(), sequences.size());
        for (int i = 0; i < sequences.size(); i++) {
            var actual = sequences.get(i).getAsJsonObject();
            var sequence = expected.sequences().get(i);
            assertEquals(sequence.name(), actual.get("name").getAsString());
            assertEquals(sequence.dimension(), actual.get("dimension").getAsString());
            assertEquals(sequence.easing().id(), actual.get("easing").getAsString());
            var frames = actual.getAsJsonArray("keyframes");
            assertEquals(sequence.keyframes().size(), frames.size());
            for (int j = 0; j < frames.size(); j++) {
                var pose = frames.get(j).getAsJsonObject();
                var frame = sequence.keyframes().get(j);
                assertEquals(frame.name(), pose.get("name").getAsString());
                assertEquals(frame.pose().x(), pose.get("x").getAsDouble());
                assertEquals(frame.pose().y(), pose.get("y").getAsDouble());
                assertEquals(frame.pose().z(), pose.get("z").getAsDouble());
                assertEquals(frame.pose().yaw(), pose.get("yaw").getAsFloat());
                assertEquals(frame.pose().pitch(), pose.get("pitch").getAsFloat());
            }
        }
        assertNoTemporaryFiles();
    }

    @Test
    void subsequentSaveReplacesBothFilesAndPreservesDeletionOrder() throws IOException {
        var storage = CameraStorage.load(directory);
        storage.save(sample());
        var changed = sample().deleteKeyframe("intro", "wide")
                .addKeyframe("intro", new CameraKeyframe("end", 1, 2, 3, 4, 5));
        assertTrue(storage.save(changed).isEmpty());
        assertEquals(changed, CameraStorage.load(directory).data());
        assertEquals(List.of("close", "end"), changed.sequence("intro").keyframes().stream()
                .map(CameraKeyframe::name).toList());
        assertEquals(CameraDataFormat.toJson(changed), Files.readString(directory.resolve("camerakeyframepro.json")));
    }

    @Test
    void editedJsonIsNeverImportedAndExplicitSaveRepairsIt() throws IOException {
        var storage = CameraStorage.load(directory);
        storage.save(sample());
        Files.writeString(directory.resolve("camerakeyframepro.json"), "invalid edited JSON");
        var reloaded = CameraStorage.load(directory);
        assertEquals(sample(), reloaded.data());
        assertTrue(reloaded.save(reloaded.data()).isEmpty());
        assertEquals(CameraDataFormat.toJson(sample()), Files.readString(directory.resolve("camerakeyframepro.json")));
    }

    @Test
    void missingJsonDoesNotLoseNbtData() throws IOException {
        var storage = CameraStorage.load(directory);
        storage.save(sample());
        Files.delete(directory.resolve("camerakeyframepro.json"));
        var loaded = CameraStorage.load(directory);
        assertEquals(sample(), loaded.data());
        assertTrue(loaded.save(loaded.data()).isEmpty());
        assertTrue(Files.isRegularFile(directory.resolve("camerakeyframepro.json")));
    }

    @Test
    void orphanJsonBlocksNewEmptyDatabase() throws IOException {
        Path json = directory.resolve("camerakeyframepro.json");
        Files.writeString(json, CameraDataFormat.toJson(sample()));
        assertThrows(IOException.class, () -> CameraStorage.load(directory));
        assertEquals(CameraDataFormat.toJson(sample()), Files.readString(json));
        assertFalse(Files.exists(directory.resolve("camerakeyframepro.dat")));
    }

    @Test
    void corruptNbtDoesNotFallBackToJsonOrOverwriteAnything() throws IOException {
        var storage = CameraStorage.load(directory);
        storage.save(sample());
        Path nbt = directory.resolve("camerakeyframepro.dat");
        Files.writeString(nbt, "broken NBT");
        assertThrows(IOException.class, () -> CameraStorage.load(directory));
        assertEquals("broken NBT", Files.readString(nbt));
        assertEquals(CameraDataFormat.toJson(sample()), Files.readString(directory.resolve("camerakeyframepro.json")));
    }

    @Test
    void unsupportedVersionFailsWithoutOverwritingData() throws IOException {
        CompoundTag root = CameraDataFormat.toNbt(sample());
        root.putInt("format_version", 4);
        Path nbt = directory.resolve("camerakeyframepro.dat");
        NbtIo.writeCompressed(root, nbt);
        byte[] original = Files.readAllBytes(nbt);
        assertThrows(IOException.class, () -> CameraStorage.load(directory));
        assertArrayEquals(original, Files.readAllBytes(nbt));
    }

    @Test
    void durationRoundTripsInBothFormatsAndSurvivesEdits() throws IOException {
        var data = sample().setDuration("intro", 12.345).deleteKeyframe("intro", "wide")
                .addKeyframe("intro", new CameraKeyframe("end", 1.123456789, 2, 3, 4, 5));
        var storage = CameraStorage.load(directory);
        assertTrue(storage.save(data).isEmpty());
        assertEquals(data, CameraStorage.load(directory).data());
        var json = JsonParser.parseString(Files.readString(directory.resolve("camerakeyframepro.json"))).getAsJsonObject();
        var sequences = json.getAsJsonArray("sequences");
        assertEquals(12.345, sequences.get(0).getAsJsonObject().get("duration_seconds").getAsDouble());
        assertFalse(sequences.get(1).getAsJsonObject().has("duration_seconds"));
        assertEquals(1.123456789, sequences.get(0).getAsJsonObject().getAsJsonArray("keyframes")
                .get(1).getAsJsonObject().get("x").getAsDouble());
    }

    @Test
    void legacyVersionLoadsWithoutInventingDurationAndUpgradesOnSave() throws IOException {
        var legacy = CameraDataFormat.toNbt(sample());
        legacy.putInt("format_version", 1);
        legacy.getList("sequences", 10).forEach(tag -> ((CompoundTag) tag).remove("easing"));
        NbtIo.writeCompressed(legacy, directory.resolve("camerakeyframepro.dat"));
        var storage = CameraStorage.load(directory);
        assertEquals(sample(), storage.data());
        assertTrue(storage.data().sequence("intro").durationSeconds().isEmpty());
        assertEquals(CameraEasing.LINEAR, storage.data().sequence("intro").easing());
        assertTrue(storage.save(storage.data().setDuration("intro", 10)).isEmpty());
        assertEquals(10, CameraStorage.load(directory).data().sequence("intro").durationSeconds().orElseThrow());
    }

    @Test
    void versionTwoLoadsAsLinearAndUpgradesWithoutChangingPosesOrDuration() throws Exception {
        var fixture = net.minecraft.nbt.TagParser.parseTag("""
                {format_version:2,sequences:[{name:"intro",dimension:"minecraft:overworld",
                duration_seconds:10.0d,keyframes:[{name:"wide",x:1.123456789d,y:64.0d,z:-3.5d,yaw:179.0f,pitch:-20.0f}]}]}
                """);
        NbtIo.writeCompressed(fixture, directory.resolve("camerakeyframepro.dat"));
        var storage = CameraStorage.load(directory);
        assertEquals(new CameraKeyframe("wide", new CameraPose(1.123456789, 64, -3.5, 179, -20)),
                storage.data().sequence("intro").keyframes().getFirst());
        assertEquals(CameraEasing.LINEAR, storage.data().sequence("intro").easing());
        assertEquals(10, storage.data().sequence("intro").durationSeconds().orElseThrow());
        assertTrue(storage.save(storage.data()).isEmpty());
        fixture.putInt("format_version", 3);
        fixture.getList("sequences", 10).getCompound(0).putString("easing", "linear");
        assertEquals(fixture, CameraDataFormat.toNbt(storage.data()));
        var expectedJson = JsonParser.parseString("""
                {"format_version":3,"sequences":[{"name":"intro","dimension":"minecraft:overworld","easing":"linear",
                "duration_seconds":10.0,"keyframes":[{"name":"wide","x":1.123456789,"y":64.0,"z":-3.5,
                "yaw":179.0,"pitch":-20.0}]}]}
                """);
        assertEquals(expectedJson, JsonParser.parseString(Files.readString(directory.resolve("camerakeyframepro.json"))));
    }

    @Test
    void easingRoundTripsThroughNbtAndJsonAndRetainsLinearForOtherSequences() throws IOException {
        var expected = sample().setDuration("intro", 10).setEasing("intro", CameraEasing.SMOOTHSTEP);
        var storage = CameraStorage.load(directory);
        assertTrue(storage.save(expected).isEmpty());
        assertEquals(expected, CameraStorage.load(directory).data());
        var json = JsonParser.parseString(Files.readString(directory.resolve("camerakeyframepro.json"))).getAsJsonObject();
        var sequences = json.getAsJsonArray("sequences");
        assertEquals("smoothstep", sequences.get(0).getAsJsonObject().get("easing").getAsString());
        assertEquals("linear", sequences.get(1).getAsJsonObject().get("easing").getAsString());
    }

    @Test
    void invalidVersionThreeEasingBlocksLoadingWithoutOverwritingData() throws IOException {
        for (int invalid = 0; invalid < 3; invalid++) {
            var root = CameraDataFormat.toNbt(sample());
            var sequence = root.getList("sequences", 10).getCompound(0);
            switch (invalid) {
                case 0 -> sequence.remove("easing");
                case 1 -> sequence.putInt("easing", 1);
                case 2 -> sequence.putString("easing", "unknown");
            }
            Path nbt = directory.resolve("camerakeyframepro.dat");
            NbtIo.writeCompressed(root, nbt);
            byte[] before = Files.readAllBytes(nbt);
            assertThrows(IOException.class, () -> CameraStorage.load(directory));
            assertArrayEquals(before, Files.readAllBytes(nbt));
        }
    }

    @Test
    void malformedOrNonFiniteDurationFailsValidation() {
        var root = CameraDataFormat.toNbt(sample());
        var sequence = root.getList("sequences", 10).getCompound(0);
        sequence.putString("duration_seconds", "10");
        assertThrows(IllegalArgumentException.class, () -> CameraDataFormat.fromNbt(root));
        sequence.putDouble("duration_seconds", Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> CameraDataFormat.fromNbt(root));
        sequence.putDouble("duration_seconds", 0);
        assertThrows(IllegalArgumentException.class, () -> CameraDataFormat.fromNbt(root));
    }

    @Test
    void invalidNbtFieldsAreNotDefaulted() {
        var missing = CameraDataFormat.toNbt(sample());
        missing.getList("sequences", 10).getCompound(0).getList("keyframes", 10).getCompound(0).remove("x");
        assertThrows(IllegalArgumentException.class, () -> CameraDataFormat.fromNbt(missing));

        var wrongList = CameraDataFormat.toNbt(sample());
        var strings = new ListTag();
        strings.add(StringTag.valueOf("not a sequence"));
        wrongList.put("sequences", strings);
        assertThrows(IllegalArgumentException.class, () -> CameraDataFormat.fromNbt(wrongList));

        var duplicate = CameraDataFormat.toNbt(sample());
        var sequences = duplicate.getList("sequences", 10);
        sequences.add(sequences.getCompound(0).copy());
        assertThrows(IllegalArgumentException.class, () -> CameraDataFormat.fromNbt(duplicate));
    }

    @Test
    void failedNbtCommitDoesNotChangeMemoryOrJson() throws IOException {
        var storage = CameraStorage.load(directory);
        storage.save(sample());
        Path nbt = directory.resolve("camerakeyframepro.dat");
        Files.delete(nbt);
        Files.createDirectory(nbt);
        Files.writeString(nbt.resolve("blocker"), "keep");
        assertThrows(IOException.class, () -> storage.save(sample().deleteKeyframe("intro", "wide")));
        assertEquals(sample(), storage.data());
        assertEquals(CameraDataFormat.toJson(sample()), Files.readString(directory.resolve("camerakeyframepro.json")));
        assertNoTemporaryFiles();
    }

    @Test
    void jsonFailureIsExplicitButNbtCommitRemainsAuthoritativeAndRetryWorks() throws IOException {
        var storage = CameraStorage.load(directory);
        Path json = directory.resolve("camerakeyframepro.json");
        Files.createDirectory(json);
        Files.writeString(json.resolve("blocker"), "keep");
        assertTrue(storage.save(sample()).isPresent());
        assertEquals(sample(), storage.data());
        assertEquals(sample(), CameraStorage.load(directory).data());
        assertEquals("keep", Files.readString(json.resolve("blocker")));
        assertNoTemporaryFiles();
        Files.delete(json.resolve("blocker"));
        Files.delete(json);
        assertTrue(storage.save(storage.data()).isEmpty());
        assertEquals(CameraDataFormat.toJson(sample()), Files.readString(json));
    }

    @Test
    void worldsAreIsolated() throws IOException {
        var first = CameraStorage.load(directory.resolve("world1"));
        var second = CameraStorage.load(directory.resolve("world2"));
        first.save(sample());
        second.save(new CameraLibrary(List.of()).createSequence("different", "minecraft:overworld"));
        assertEquals(sample(), CameraStorage.load(directory.resolve("world1")).data());
        assertEquals("different", CameraStorage.load(directory.resolve("world2")).data().sequences().getFirst().name());
    }

    private void assertNoTemporaryFiles() throws IOException {
        try (var files = Files.list(directory)) {
            assertFalse(files.anyMatch(path -> path.toString().endsWith(".tmp")));
        }
    }
}
