package shit.zen.fabric.render;

/** Bridges completed legacy batches into the active Fabric 26.2 render phase. */
public final class LegacyBufferUploader {
    private LegacyBufferUploader() {
    }

    public static void draw(LegacyMesh mesh) {
        FabricRenderBridge.submit(mesh);
    }

    public static void drawWithShader(LegacyMesh mesh) {
        FabricRenderBridge.submit(mesh);
    }

    public static void reset() {
        // 26.2 owns upload buffers per render-state phase; there is no global
        // immediate uploader state to reset.
    }
}
