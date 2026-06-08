package shit.zen.render.backend;

public final class LegacyGlBackend implements RenderBackend {
    @Override
    public BackendType type() {
        return BackendType.OPENGL_LEGACY;
    }
}
