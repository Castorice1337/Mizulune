package shit.zen.fabric.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.Optional;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;

/**
 * Fabric 26.2 target-lifecycle adapter for the original Skiko renderer.
 * It contains no replacement blur or liquid-glass algorithm.
 */
public final class FabricBlurCompositor {
    private static final RenderPipeline PREMULTIPLIED_OVERLAY_BLIT = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(
                            "mizulune", "pipeline/deferred_skiko_blit"))
                    .withVertexShader("core/screenquad")
                    .withFragmentShader("core/blit_screen")
                    .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
                    .withColorTargetState(new ColorTargetState(
                            Optional.of(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA),
                            GpuFormat.RGBA8_UNORM, 7))
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .build());
    private static TextureTarget overlayTarget;
    private static RenderTarget frameMainTarget;
    private static RenderTarget frameGuiTarget;
    private static boolean requested;
    private static boolean drawingOverlay;

    private FabricBlurCompositor() {
    }

    public static void requestBackdrop() {
        requested = true;
    }

    /** Called at GuiRenderer.draw HEAD, before Minecraft opens its GUI render pass. */
    public static void beginGuiDraw(RenderTarget mainTarget) {
        frameMainTarget = mainTarget;
        drawingOverlay = requested || FabricDeferredSkiko.hasCommands();
        requested = false;
        if (!drawingOverlay || mainTarget == null) {
            frameGuiTarget = mainTarget;
            return;
        }

        overlayTarget = ensureTarget(overlayTarget, mainTarget.width, mainTarget.height);
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                overlayTarget.getColorTexture(), new Vector4f(0.0F),
                overlayTarget.getDepthTexture(), 0.0);
        frameGuiTarget = overlayTarget;

        // The original Skiko backend captures mainTarget and executes the
        // existing SkikoEffects/SkikoLiquidGlass implementation into overlay.
        FabricDeferredSkiko.render(mainTarget, overlayTarget);
    }

    /** Supplies the target selected at draw HEAD to Minecraft's GUI pass. */
    public static RenderTarget selectGuiTarget(RenderTarget mainTarget) {
        return drawingOverlay && frameGuiTarget != null ? frameGuiTarget : mainTarget;
    }

    /** Called after Minecraft has drawn text, icons and submitted geometry. */
    public static void finishGuiDraw() {
        try {
            if (!drawingOverlay || overlayTarget == null || frameMainTarget == null
                    || frameMainTarget.getColorTextureView() == null) {
                return;
            }
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Mizulune deferred Skiko composite",
                    frameMainTarget.getColorTextureView(), Optional.empty())) {
                pass.setPipeline(PREMULTIPLIED_OVERLAY_BLIT);
                RenderSystem.bindDefaultUniforms(pass);
                pass.bindTexture("InSampler", overlayTarget.getColorTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.draw(3, 1, 0, 0);
            }
        } finally {
            drawingOverlay = false;
            frameGuiTarget = null;
            frameMainTarget = null;
        }
    }

    public static void close() {
        FabricDeferredSkiko.close();
        if (overlayTarget != null) {
            overlayTarget.destroyBuffers();
            overlayTarget = null;
        }
        requested = false;
        drawingOverlay = false;
        frameGuiTarget = null;
        frameMainTarget = null;
    }

    private static TextureTarget ensureTarget(TextureTarget current, int width, int height) {
        if (current == null) {
            // GuiRenderer may split the frame at firstDrawIndexAfterBlur and
            // unconditionally clear the selected target's depth attachment.
            return new TextureTarget("Mizulune deferred Skiko GUI", width, height, true,
                    GpuFormat.RGBA8_UNORM);
        }
        if (current.width != width || current.height != height) {
            current.resize(width, height);
        }
        return current;
    }
}
