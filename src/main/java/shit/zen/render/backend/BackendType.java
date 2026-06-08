package shit.zen.render.backend;

public enum BackendType {
    OPENGL_LEGACY,
    SKIKO;

    public static BackendType fromProperty(String value) {
        if (value == null || value.isBlank()) {
            return SKIKO;
        }
        String normalized = value.trim().replace('-', '_').replace(' ', '_').toUpperCase();
        if ("SKIA".equals(normalized)) {
            return SKIKO;
        }
        if ("LEGACY".equals(normalized) || "OPENGL".equals(normalized)) {
            return OPENGL_LEGACY;
        }
        for (BackendType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return SKIKO;
    }
}
