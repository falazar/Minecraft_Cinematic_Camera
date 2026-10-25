package com.jdrkind.camerakeyframes;

public record CameraPose(double x, double y, double z, float yaw, float pitch) {
    public CameraPose {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch) || pitch < -90 || pitch > 90) {
            throw new IllegalArgumentException("Camera pose must be finite, with pitch between -90 and 90.");
        }
    }
}
