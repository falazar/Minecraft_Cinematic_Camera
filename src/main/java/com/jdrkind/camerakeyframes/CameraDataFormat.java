package com.jdrkind.camerakeyframes;

import java.util.ArrayList;
import java.util.OptionalDouble;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class CameraDataFormat {
    private static final int VERSION = 3;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private CameraDataFormat() {
    }

    static CompoundTag toNbt(CameraLibrary data) {
        var root = new CompoundTag();
        root.putInt("format_version", VERSION);
        var sequences = new ListTag();
        for (CameraSequence sequence : data.sequences()) {
            var entry = new CompoundTag();
            entry.putString("name", sequence.name());
            entry.putString("dimension", sequence.dimension());
            entry.putString("easing", sequence.easing().id());
            sequence.durationSeconds().ifPresent(seconds -> entry.putDouble("duration_seconds", seconds));
            var frames = new ListTag();
            for (CameraKeyframe frame : sequence.keyframes()) {
                var pose = new CompoundTag();
                pose.putString("name", frame.name());
                pose.putDouble("x", frame.pose().x());
                pose.putDouble("y", frame.pose().y());
                pose.putDouble("z", frame.pose().z());
                pose.putFloat("yaw", frame.pose().yaw());
                pose.putFloat("pitch", frame.pose().pitch());
                frames.add(pose);
            }
            entry.put("keyframes", frames);
            sequences.add(entry);
        }
        root.put("sequences", sequences);
        return root;
    }

    static CameraLibrary fromNbt(CompoundTag root) {
        require(root, "format_version", Tag.TAG_INT);
        int version = root.getInt("format_version");
        if (version < 1 || version > VERSION) {
            throw new IllegalArgumentException("Unsupported camera data version: " + root.getInt("format_version"));
        }
        var sequences = new ArrayList<CameraSequence>();
        for (Tag tag : compounds(root, "sequences")) {
            CompoundTag sequence = (CompoundTag) tag;
            require(sequence, "name", Tag.TAG_STRING);
            require(sequence, "dimension", Tag.TAG_STRING);
            var frames = new ArrayList<CameraKeyframe>();
            for (Tag frameTag : compounds(sequence, "keyframes")) {
                CompoundTag frame = (CompoundTag) frameTag;
                require(frame, "name", Tag.TAG_STRING);
                require(frame, "x", Tag.TAG_DOUBLE);
                require(frame, "y", Tag.TAG_DOUBLE);
                require(frame, "z", Tag.TAG_DOUBLE);
                require(frame, "yaw", Tag.TAG_FLOAT);
                require(frame, "pitch", Tag.TAG_FLOAT);
                frames.add(new CameraKeyframe(frame.getString("name"),
                        frame.getDouble("x"), frame.getDouble("y"), frame.getDouble("z"),
                        frame.getFloat("yaw"), frame.getFloat("pitch")));
            }
            OptionalDouble duration = OptionalDouble.empty();
            if (version >= 2 && sequence.contains("duration_seconds")) {
                require(sequence, "duration_seconds", Tag.TAG_DOUBLE);
                duration = OptionalDouble.of(sequence.getDouble("duration_seconds"));
            }
            CameraEasing easing = CameraEasing.LINEAR;
            if (version >= 3) {
                require(sequence, "easing", Tag.TAG_STRING);
                easing = CameraEasing.parse(sequence.getString("easing"));
            }
            sequences.add(new CameraSequence(sequence.getString("name"), sequence.getString("dimension"), frames, duration, easing));
        }
        return new CameraLibrary(sequences);
    }

    private static ListTag compounds(CompoundTag parent, String key) {
        require(parent, key, Tag.TAG_LIST);
        ListTag list = (ListTag) parent.get(key);
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Expected compound entries in " + key);
        }
        return list;
    }

    private static void require(CompoundTag parent, String key, int type) {
        if (!parent.contains(key, type)) {
            throw new IllegalArgumentException("Missing or incorrectly typed camera field: " + key);
        }
    }

    static String toJson(CameraLibrary data) {
        var root = new JsonObject();
        root.addProperty("format_version", VERSION);
        var sequences = new JsonArray();
        for (CameraSequence sequence : data.sequences()) {
            var entry = new JsonObject();
            entry.addProperty("name", sequence.name());
            entry.addProperty("dimension", sequence.dimension());
            entry.addProperty("easing", sequence.easing().id());
            sequence.durationSeconds().ifPresent(seconds -> entry.addProperty("duration_seconds", seconds));
            var frames = new JsonArray();
            for (CameraKeyframe frame : sequence.keyframes()) {
                var pose = new JsonObject();
                pose.addProperty("name", frame.name());
                pose.addProperty("x", frame.pose().x());
                pose.addProperty("y", frame.pose().y());
                pose.addProperty("z", frame.pose().z());
                pose.addProperty("yaw", frame.pose().yaw());
                pose.addProperty("pitch", frame.pose().pitch());
                frames.add(pose);
            }
            entry.add("keyframes", frames);
            sequences.add(entry);
        }
        root.add("sequences", sequences);
        return GSON.toJson(root) + "\n";
    }
}
