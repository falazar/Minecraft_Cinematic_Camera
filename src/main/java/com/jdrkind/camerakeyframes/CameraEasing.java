package com.jdrkind.camerakeyframes;

public enum CameraEasing {
    LINEAR("linear"),
    SMOOTHSTEP("smoothstep");

    private final String id;

    CameraEasing(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static CameraEasing parse(String id) {
        for (CameraEasing easing : values()) {
            if (easing.id.equals(id)) {
                return easing;
            }
        }
        throw new IllegalArgumentException("Unknown easing '" + id + "'. Use linear or smoothstep.");
    }

    public double apply(double progress) {
        if (!Double.isFinite(progress) || progress < 0 || progress > 1) {
            throw new IllegalArgumentException("Easing progress must be finite and between 0 and 1.");
        }
        return switch (this) {
            case LINEAR -> progress;
            case SMOOTHSTEP -> progress * progress * (3 - 2 * progress);
        };
    }
}
