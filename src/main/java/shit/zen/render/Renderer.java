package shit.zen.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import java.lang.reflect.Field;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL33;
import shit.zen.ClientBase;
import com.mojang.blaze3d.vertex.PoseStack;
import shit.zen.render.backend.BackendType;
import shit.zen.render.backend.RenderBackend;
import shit.zen.render.backend.SkikoBackend;

public class Renderer
extends ClientBase {
    private static float guiScale = 1.0f;
    private static DrawContext currentCanvas;
    private static BackendType configuredBackend = BackendType.SKIKO;
    private static RenderBackend backend = Renderer.createBackend(configuredBackend);

    public static DrawContext getCanvas() {
        return currentCanvas;
    }

    public static boolean isSkikoEnabled() {
        return backend != null && backend.handles2D();
    }

    public static boolean canUseSkiko2D(PoseStack poseStack) {
        if (!Renderer.isSkikoEnabled()) {
            return false;
        }
        if (currentCanvas != null) {
            return currentCanvas.getBackend() != null && currentCanvas.getBackend() == backend && currentCanvas.getBackend().handles2D();
        }
        return Renderer.isGuiAffinePose(poseStack);
    }

    public static RenderBackend getBackend() {
        return backend;
    }

    public static BackendType getBackendType() {
        return configuredBackend;
    }

    public static BackendType getActiveBackendType() {
        return configuredBackend;
    }

    public static boolean isBackendFailed() {
        return backend == null;
    }

    public static String getBackendDebugSummary() {
        RenderBackend effectiveBackend = Renderer.getEffectiveBackend();
        if (effectiveBackend == null) {
            return "SkikoBackend unavailable";
        }
        return effectiveBackend.getClass().getSimpleName() + " " + effectiveBackend.debugSummary();
    }

    public static void setBackend(BackendType type) {
        BackendType nextBackend = type == null ? BackendType.SKIKO : type;
        if (configuredBackend == nextBackend && backend != null) {
            return;
        }
        configuredBackend = nextBackend;
        backend = Renderer.createBackend(configuredBackend);
    }

    public static float getGuiScale() {
        return guiScale;
    }

    public static void updateGuiScale() {
        Renderer.setGuiScale((float)mc.getWindow().getGuiScale());
    }

    public static void setGuiScale(float scale) {
        RenderSystem.assertOnRenderThread();
        guiScale = scale;
    }

    public static void resetPixelStore() {
        RenderSystem.assertOnRenderThread();
        RenderSystem.pixelStore(3314, 0);
        RenderSystem.pixelStore(3316, 0);
        RenderSystem.pixelStore(3315, 0);
        RenderSystem.pixelStore(3317, 4);
    }

    public static void resetRenderState() {
        RenderSystem.assertOnRenderThread();
        BufferUploader.reset();
        GL33.glBindSampler(0, 0);
        Renderer.resetWindowFramebufferBounds();
        RenderSystem.disableBlend();
        GL11.glDisable(3042);
        RenderSystem.blendFunc(770, 771);
        GL11.glBlendFunc(770, 771);
        RenderSystem.blendEquation(32774);
        GL14.glBlendEquation(32774);
        RenderSystem.colorMask(true, true, true, true);
        GL11.glColorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        GL11.glDepthMask(true);
        RenderSystem.disableScissor();
        RenderSystem.disableDepthTest();
        GL11.glDisable(2929);
        GL11.glDisable(2960);
        GL13.glActiveTexture(33984);
        RenderSystem.activeTexture(33984);
        RenderSystem.disableCull();
    }

    public static void resetWindowFramebufferBounds() {
        if (mc == null || mc.getWindow() == null) {
            return;
        }
        Renderer.resetWindowFramebufferBounds(mc.getWindow().getWidth(), mc.getWindow().getHeight());
    }

    public static void resetWindowFramebufferBounds(int width, int height) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        GL11.glViewport(0, 0, width, height);
        RenderSystem.disableScissor();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public static void captureCleanBackdrop(GuiGraphics guiGraphics) {
        if (backend == null || !backend.handles2D() || currentCanvas != null) {
            return;
        }

        try {
            Renderer.resetPixelStore();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();

            backend.captureCleanBackdrop(guiGraphics, guiGraphics != null ? guiGraphics.pose() : null);
        } catch (Throwable throwable) {
            logger.error("Failed to capture clean HUD backdrop", throwable);
        } finally {
            Renderer.resetRenderState();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void render(GuiGraphics guiGraphics, Consumer<DrawContext> consumer) {
        Renderer.renderInternal(guiGraphics, null, consumer);
    }

    public static void renderWithPose(PoseStack poseStack, Consumer<DrawContext> consumer) {
        Renderer.renderInternal(null, poseStack, consumer);
    }

    private static void renderInternal(GuiGraphics guiGraphics, PoseStack poseStack, Consumer<DrawContext> consumer) {
        if (backend == null) {
            logger.error("Skiko render backend is unavailable; skipping 2D render pass");
            return;
        }
        if (currentCanvas != null) {
            PoseStack requestedPoseStack = poseStack != null
                    ? poseStack
                    : guiGraphics != null ? guiGraphics.pose() : null;
            RenderBackend effectiveBackend = backend;
            if (effectiveBackend != currentCanvas.getBackend()) {
                boolean ownsExternalGlSection = currentCanvas.getBackend() != null
                        && currentCanvas.getBackend().handles2D()
                        && !StencilHelper.isStencilActive();
                if (ownsExternalGlSection) {
                    currentCanvas.beforeExternalGlDraw();
                }
                try {
                    PoseStack nestedPoseStack = requestedPoseStack != null ? requestedPoseStack : currentCanvas.getPoseStack();
                    consumer.accept(new DrawContext(guiGraphics, nestedPoseStack, effectiveBackend));
                } finally {
                    if (ownsExternalGlSection) {
                        currentCanvas.afterExternalGlDraw();
                    }
                }
                return;
            }
            PoseStack nestedPoseStack = requestedPoseStack;
            if (nestedPoseStack != null && effectiveBackend.handles2D()) {
                effectiveBackend.pushExternalPose(nestedPoseStack);
                try {
                    consumer.accept(new DrawContext(guiGraphics, nestedPoseStack, effectiveBackend));
                } finally {
                    effectiveBackend.popExternalPose();
                }
            } else {
                consumer.accept(currentCanvas);
            }
            return;
        }
        Renderer.resetPixelStore();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        PoseStack effectivePoseStack = poseStack != null
                ? poseStack
                : guiGraphics != null ? guiGraphics.pose() : null;
        RenderBackend effectiveBackend = backend;
        boolean applyInitialPose = effectivePoseStack != null && effectiveBackend.handles2D();
        DrawContext drawContext = effectivePoseStack == null
                ? new DrawContext(guiGraphics, effectiveBackend)
                : new DrawContext(guiGraphics, effectivePoseStack, effectiveBackend);
        DrawContext previousCanvas = currentCanvas;
        currentCanvas = drawContext;
        try {
            if (effectiveBackend.handles2D()) {
                effectiveBackend.begin(guiGraphics, drawContext.getPoseStack());
                if (applyInitialPose) {
                    effectiveBackend.pushExternalPose(effectivePoseStack);
                }
            }
            consumer.accept(drawContext);
        } catch (Throwable throwable) {
            logger.error("Render backend {} failed; skipping 2D render pass", effectiveBackend.type(), throwable);
        } finally {
            drawContext.clearClipStack();
            if (effectiveBackend.handles2D()) {
                if (applyInitialPose) {
                    try {
                        effectiveBackend.popExternalPose();
                    } catch (Throwable throwable) {
                        logger.error("Render backend {} failed while restoring external pose", effectiveBackend.type(), throwable);
                    }
                }
                try {
                    effectiveBackend.end();
                } catch (Throwable throwable) {
                    logger.error("Render backend {} failed during end; no alternate backend is available", effectiveBackend.type(), throwable);
                }
            }
            currentCanvas = previousCanvas;
        }
        Renderer.resetRenderState();
    }

    public static void renderConsumer(Consumer<DrawContext> consumer) {
        if (currentCanvas != null) {
            consumer.accept(currentCanvas);
            return;
        }
        Renderer.render(null, consumer);
    }

    public static void setGuiScaleVerified(float scale) {
        Renderer.setGuiScale(scale);
    }

    private static RenderBackend getEffectiveBackend() {
        return backend;
    }

    private static RenderBackend createBackend(BackendType type) {
        try {
            return new SkikoBackend();
        } catch (Throwable throwable) {
            logger.error("Failed to create Skiko backend; no alternate backend is available", throwable);
            return null;
        }
    }

    private static boolean isGuiAffinePose(PoseStack poseStack) {
        if (poseStack == null) {
            return true;
        }
        Matrix4f matrix = poseStack.last().pose();
        float epsilon = 1.0E-4f;
        return Math.abs(matrix.m02()) <= epsilon
                && Math.abs(matrix.m12()) <= epsilon
                && Math.abs(matrix.m20()) <= epsilon
                && Math.abs(matrix.m21()) <= epsilon
                && Math.abs(matrix.m23()) <= epsilon
                && Math.abs(matrix.m32()) <= 1024.0f
                && Math.abs(matrix.m22() - 1.0f) <= 1.0E-3f;
    }
}
