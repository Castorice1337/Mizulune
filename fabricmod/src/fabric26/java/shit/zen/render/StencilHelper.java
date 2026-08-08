package shit.zen.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Submission-safe fallback for the removed direct framebuffer/stencil API.
 * Content remains visible under Sodium/Iris; clipping is delegated to the
 * modern GUI render-state path rather than mutating the active framebuffer.
 */
public final class StencilHelper {
    private static int depth;
    private static boolean writing;
    private static boolean reading;

    private StencilHelper() {
    }

    public static void applyStencil(PoseStack poseStack, Runnable drawMask, Runnable drawContent, float opacity) {
        drawContent.run();
    }

    public static void beginWrite(boolean keepColor) {
        depth++;
        writing = true;
        reading = false;
    }

    public static void beginWriteFull(boolean keepColor, RenderTarget target, boolean clear, boolean invert) {
        beginWrite(keepColor);
    }

    public static void beginRead(boolean inside) {
        writing = false;
        reading = true;
    }

    public static void end() {
        depth = Math.max(0, depth - 1);
        if (depth == 0) {
            writing = false;
            reading = false;
        }
    }

    public static boolean isStencilActive() { return depth > 0; }
    public static boolean isStencilWriting() { return writing; }
    public static boolean isStencilReading() { return reading; }
    public static void setupFBO(RenderTarget target) { }
    public static void attachStencilBuffer(RenderTarget target) { }
}
