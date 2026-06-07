package shit.zen.render.backend;

public enum BackendType {
    OPENGL_LEGACY,
    SKIKO;

    public static BackendType fromProperty(String value) {
        if (value == null || value.isBlank()) {
            return OPENGL_LEGACY;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase();
        if ("SKIA".equals(normalized)) {
            return SKIKO;
        }
        for (BackendType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return OPENGL_LEGACY;
    }
}
