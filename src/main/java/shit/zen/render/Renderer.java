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
import shit.zen.render.backend.LegacyGlBackend;
import shit.zen.render.backend.RenderBackend;
import shit.zen.render.backend.SkikoBackend;

public class Renderer
extends ClientBase {
    private static float guiScale = 1.0f;
    private static boolean verified = false;
    private static DrawContext currentCanvas;
    private static final RenderBackend LEGACY_BACKEND = new LegacyGlBackend();
    private static boolean backendFailed = false;
    private static BackendType configuredBackend = BackendType.fromProperty(System.getProperty("openzen.render.backend"));
    private static RenderBackend backend = Renderer.createBackend(configuredBackend);

    public static DrawContext getCanvas() {
        return currentCanvas;
    }

    public static boolean isSkikoEnabled() {
        return configuredBackend == BackendType.SKIKO && !backendFailed && backend.handles2D();
    }

    public static boolean canUseSkiko2D(PoseStack poseStack) {
        if (!Renderer.isSkikoEnabled()) {
            return false;
        }
        return currentCanvas != null || Renderer.isGuiAffinePose(poseStack);
    }

    public static RenderBackend getBackend() {
        return backend;
    }

    public static BackendType getBackendType() {
        return configuredBackend;
    }

    public static BackendType getActiveBackendType() {
        return Renderer.getEffectiveBackend().type();
    }

    public static boolean isBackendFailed() {
        return backendFailed;
    }

    public static String getBackendDebugSummary() {
        RenderBackend effectiveBackend = Renderer.getEffectiveBackend();
        return effectiveBackend.getClass().getSimpleName() + " " + effectiveBackend.debugSummary();
    }

    public static void setBackend(BackendType type) {
        configuredBackend = type == null ? BackendType.OPENGL_LEGACY : type;
        backendFailed = false;
        backend = Renderer.createBackend(configuredBackend);
    }

    public static float getGuiScale() {
        return guiScale;
    }

    public static void verify() {
        verified = true;
    }

    public static void updateGuiScale() {
        Renderer.setGuiScale((float)mc.getWindow().getGuiScale());
    }

    public static void setGuiScale(float scale) {
        RenderSystem.assertOnRenderThread();
        guiScale = scale;
        Renderer.verify();
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
        GL13.glActiveTexture(33984);
        RenderSystem.activeTexture(33984);
        RenderSystem.disableCull();
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
        if (!verified) {
            Renderer.verify();
            if (!verified) {
                return;
            }
        }
        if (currentCanvas != null) {
            RenderBackend effectiveBackend = Renderer.getEffectiveBackend();
            if (poseStack != null && effectiveBackend.handles2D()) {
                effectiveBackend.pushExternalPose(poseStack);
                try {
                    consumer.accept(new DrawContext(guiGraphics, poseStack, effectiveBackend));
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
        PoseStack effectivePoseStack = poseStack;
        RenderBackend effectiveBackend = Renderer.getEffectiveBackend();
        DrawContext drawContext = effectivePoseStack == null
                ? new DrawContext(guiGraphics, effectiveBackend)
                : new DrawContext(guiGraphics, effectivePoseStack, effectiveBackend);
        DrawContext previousCanvas = currentCanvas;
        currentCanvas = drawContext;
        try {
            if (effectiveBackend.handles2D()) {
                effectiveBackend.begin(guiGraphics, drawContext.getPoseStack());
                if (poseStack != null) {
                    effectiveBackend.pushExternalPose(poseStack);
                }
            }
            consumer.accept(drawContext);
            RenderBackendProbe.render(drawContext);
        } catch (Throwable throwable) {
            if (effectiveBackend.handles2D()) {
                logger.error("Render backend {} failed, falling back to legacy OpenGL", effectiveBackend.type(), throwable);
                backendFailed = true;
            } else {
                throw new RuntimeException(throwable);
            }
        } finally {
            drawContext.clearClipStack();
            if (effectiveBackend.handles2D()) {
                if (poseStack != null) {
                    try {
                        effectiveBackend.popExternalPose();
                    } catch (Throwable throwable) {
                        logger.error("Render backend {} failed while restoring external pose", effectiveBackend.type(), throwable);
                        backendFailed = true;
                    }
                }
                try {
                    effectiveBackend.end();
                } catch (Throwable throwable) {
                    logger.error("Render backend {} failed during end, falling back to legacy OpenGL", effectiveBackend.type(), throwable);
                    backendFailed = true;
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
        if (backendFailed || backend == null) {
            return LEGACY_BACKEND;
        }
        return backend;
    }

    private static RenderBackend createBackend(BackendType type) {
        if (type == BackendType.SKIKO) {
            try {
                return new SkikoBackend();
            } catch (Throwable throwable) {
                logger.error("Failed to create Skiko backend, using legacy OpenGL", throwable);
                backendFailed = true;
            }
        }
        return LEGACY_BACKEND;
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
