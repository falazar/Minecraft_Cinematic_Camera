package com.jdrkind.camerakeyframes;

public record CameraKeyframe(String name, CameraPose pose) {
    public CameraKeyframe {
        CameraLibrary.validateName(name);
        if (pose == null) {
            throw new IllegalArgumentException("Keyframe pose is required.");
        }
    }

    public CameraKeyframe(String name, double x, double y, double z, float yaw, float pitch) {
        this(name, new CameraPose(x, y, z, yaw, pitch));
    }
}
