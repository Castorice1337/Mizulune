package shit.zen.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import shit.zen.ClientBase;
import shit.zen.fabric.render.FabricSubmissionBackend;
import shit.zen.fabric.render.LegacyRenderSystem;
import shit.zen.render.backend.BackendType;
import shit.zen.render.backend.RenderBackend;

/**
 * Fabric 26.2 renderer facade.
 *
 * <p>Minecraft 26.2 extracts GUI render state before drawing it. Running the
 * old Skiko/OpenGL backend during extraction writes into the world framebuffer
 * and corrupts the frame, so this adapter submits CPU geometry to the active
 * GUI render state instead.</p>
 */
public final class Renderer extends ClientBase {
    private static float guiScale = 1.0F;
    private static DrawContext currentCanvas;
    private static final RenderBackend BACKEND = new FabricSubmissionBackend();

    private Renderer() {
    }

    public static DrawContext getCanvas() {
        return currentCanvas;
    }

    public static boolean isSkikoEnabled() {
        return true;
    }

    public static boolean canUseSkiko2D(PoseStack poseStack) {
        return true;
    }

    public static RenderBackend getBackend() {
        return BACKEND;
    }

    public static BackendType getBackendType() {
        return BackendType.SKIKO;
    }

    public static BackendType getActiveBackendType() {
        return BackendType.SKIKO;
    }

    public static boolean isBackendFailed() {
        return false;
    }

    public static String getBackendDebugSummary() {
        return BACKEND.debugSummary();
    }

    public static void setBackend(BackendType ignored) {
        // Fabric 26.2 always uses the submission-safe backend.
    }

    public static float getGuiScale() {
        return guiScale;
    }

    public static void updateGuiScale() {
        if (mc != null && mc.getWindow() != null) {
            setGuiScale((float) mc.getWindow().getGuiScale());
        }
    }

    public static void setGuiScale(float scale) {
        RenderSystem.assertOnRenderThread();
        guiScale = scale;
    }

    public static void setGuiScaleVerified(float scale) {
        setGuiScale(scale);
    }

    public static void resetPixelStore() {
        RenderSystem.assertOnRenderThread();
    }

    public static void resetRenderState() {
        LegacyRenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void resetWindowFramebufferBounds() {
    }

    public static void resetWindowFramebufferBounds(int width, int height) {
    }

    public static void captureCleanBackdrop(GuiGraphics graphics) {
        // A clean backdrop is represented by the 26.2 screen background stratum.
    }

    public static void render(GuiGraphics graphics, Consumer<DrawContext> consumer) {
        renderInternal(graphics, graphics == null ? null : graphics.pose(), consumer);
    }

    public static void renderWithPose(PoseStack poseStack, Consumer<DrawContext> consumer) {
        renderInternal(currentCanvas == null ? null : currentCanvas.getGuiGraphics(), poseStack, consumer);
    }

    public static void renderConsumer(Consumer<DrawContext> consumer) {
        if (currentCanvas != null) {
            consumer.accept(currentCanvas);
        } else {
            renderInternal(null, new PoseStack(), consumer);
        }
    }

    private static void renderInternal(
            GuiGraphics graphics,
            PoseStack poseStack,
            Consumer<DrawContext> consumer) {
        if (currentCanvas != null) {
            PoseStack nestedPose = poseStack != null
                    ? poseStack
                    : graphics != null ? graphics.pose() : currentCanvas.getPoseStack();
            consumer.accept(new DrawContext(graphics, nestedPose, BACKEND));
            return;
        }
        PoseStack effectivePose = poseStack == null ? new PoseStack() : poseStack;
        DrawContext previous = currentCanvas;
        DrawContext context = new DrawContext(graphics, effectivePose, BACKEND);
        currentCanvas = context;
        resetRenderState();
        BACKEND.begin(graphics, effectivePose);
        try {
            consumer.accept(context);
        } catch (Throwable error) {
            logger.error("Fabric 26.2 GUI submission failed", error);
        } finally {
            context.clearClipStack();
            BACKEND.end();
            resetRenderState();
            currentCanvas = previous;
        }
    }
}
