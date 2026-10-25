package com.jdrkind.camerakeyframes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

import net.minecraft.ReportedException;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtAccounterException;
import net.minecraft.nbt.NbtIo;

public final class CameraStorage {
    private static final long MAX_NBT_BYTES = 64L * 1024 * 1024;
    private final Path nbtFile;
    private final Path jsonFile;
    private CameraLibrary data;

    private CameraStorage(Path directory, CameraLibrary data) {
        nbtFile = directory.resolve("camerakeyframepro.dat");
        jsonFile = directory.resolve("camerakeyframepro.json");
        this.data = data;
    }

    public static CameraStorage load(Path directory) throws IOException {
        var storage = new CameraStorage(directory, new CameraLibrary(List.of()));
        try {
            storage.data = CameraDataFormat.fromNbt(
                    NbtIo.readCompressed(storage.nbtFile, NbtAccounter.create(MAX_NBT_BYTES)));
        } catch (NoSuchFileException exception) {
            if (!Files.notExists(storage.jsonFile)) {
                throw new IOException("Camera NBT is missing but JSON exists or cannot be checked. "
                        + "Restore the NBT backup; JSON is not imported automatically.", exception);
            }
        } catch (IllegalArgumentException | NbtAccounterException | ReportedException exception) {
            throw new IOException("Invalid camera NBT. Original files were not changed.", exception);
        }
        return storage;
    }

    public CameraLibrary data() {
        return data;
    }

    public Optional<IOException> save(CameraLibrary next) throws IOException {
        var tag = CameraDataFormat.toNbt(next);
        if (tag.sizeInBytes() > MAX_NBT_BYTES) {
            throw new IOException("Camera data exceeds the 64 MiB NBT limit.");
        }
        Files.createDirectories(nbtFile.getParent());
        Path temporary = Files.createTempFile(nbtFile.getParent(), "camerakeyframepro-", ".dat.tmp");
        try {
            NbtIo.writeCompressed(tag, temporary);
            Files.move(temporary, nbtFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        data = next;
        // The NBT commit is authoritative even if the separate JSON mirror cannot be replaced.
        try {
            writeJson();
            return Optional.empty();
        } catch (IOException exception) {
            return Optional.of(exception);
        }
    }

    private void writeJson() throws IOException {
        Path temporary = Files.createTempFile(jsonFile.getParent(), "camerakeyframepro-", ".json.tmp");
        try {
            Files.writeString(temporary, CameraDataFormat.toJson(data), StandardCharsets.UTF_8);
            Files.move(temporary, jsonFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
