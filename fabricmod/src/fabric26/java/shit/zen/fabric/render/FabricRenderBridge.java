package shit.zen.fabric.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Active extraction/submission context for the legacy rendering facade. */
public final class FabricRenderBridge {
    private static final ThreadLocal<GuiGraphicsExtractor> GUI = new ThreadLocal<>();
    private static final ThreadLocal<ScreenRectangle> GUI_SCISSOR_OVERRIDE = new ThreadLocal<>();
    private static final ThreadLocal<SubmitNodeCollector> WORLD = new ThreadLocal<>();
    private static final Map<PrimitiveTopology, RenderPipeline> GUI_COLORED = new EnumMap<>(PrimitiveTopology.class);
    private static final Map<PrimitiveTopology, RenderPipeline> GUI_TEXTURED = new EnumMap<>(PrimitiveTopology.class);
    private static final Map<PrimitiveTopology, RenderType> WORLD_COLORED = new EnumMap<>(PrimitiveTopology.class);
    private static final Map<PrimitiveTopology, RenderType> WORLD_TEXTURED = new EnumMap<>(PrimitiveTopology.class);

    private FabricRenderBridge() {
    }

    public static void withGui(GuiGraphicsExtractor extractor, Runnable action) {
        GuiGraphicsExtractor previous = GUI.get();
        ScreenRectangle previousScissor = GUI_SCISSOR_OVERRIDE.get();
        GUI.set(extractor);
        GUI_SCISSOR_OVERRIDE.remove();
        try {
            action.run();
        } finally {
            if (previous == null) GUI.remove(); else GUI.set(previous);
            if (previousScissor == null) GUI_SCISSOR_OVERRIDE.remove();
            else GUI_SCISSOR_OVERRIDE.set(previousScissor);
        }
    }

    public static void withWorld(SubmitNodeCollector collector, Runnable action) {
        SubmitNodeCollector previous = WORLD.get();
        WORLD.set(collector);
        try {
            action.run();
        } finally {
            if (previous == null) WORLD.remove(); else WORLD.set(previous);
        }
    }

    public static @Nullable GuiGraphicsExtractor currentGui() {
        return GUI.get();
    }

    public static void setGuiScissorOverride(@Nullable ScreenRectangle scissor) {
        if (scissor == null) GUI_SCISSOR_OVERRIDE.remove();
        else GUI_SCISSOR_OVERRIDE.set(scissor);
    }

    /** Applies the backend clip only while a vanilla extractor call records its state. */
    public static void withGuiScissor(Runnable action) {
        GuiGraphicsExtractor gui = GUI.get();
        ScreenRectangle scoped = effectiveGuiScissor(gui);
        if (gui == null || scoped == null) {
            action.run();
            return;
        }
        if (scoped.width() <= 0 || scoped.height() <= 0) {
            return;
        }
        gui.scissorStack.push(scoped);
        try {
            ScreenRectangle applied = gui.scissorStack.peek();
            if (applied != null && applied.width() > 0 && applied.height() > 0) {
                action.run();
            }
        } finally {
            gui.scissorStack.pop();
        }
    }

    public static void submit(LegacyMesh mesh) {
        if (mesh == null || mesh.vertices().isEmpty()) return;
        LegacyRenderSystem.State state = LegacyRenderSystem.state();
        GuiGraphicsExtractor gui = GUI.get();
        if (gui != null) {
            ScreenRectangle scissor = effectiveGuiScissor(gui);
            if (scissor != null && (scissor.width() <= 0 || scissor.height() <= 0)) {
                return;
            }
            RenderPipeline pipeline = guiPipeline(mesh.topology(), mesh.textured());
            TextureSetup texture = textureSetup(mesh.textured(), state.texture(), state.textureObject());
            gui.guiRenderState.addGuiElement(new LegacyGuiElement(mesh, pipeline, texture,
                    scissor, state.red(), state.green(), state.blue(), state.alpha()));
            return;
        }

        SubmitNodeCollector world = WORLD.get();
        if (world != null) {
            RenderType renderType = worldRenderType(mesh.topology(), mesh.textured(), state.texture());
            // COLLECT_SUBMITS records geometry and invokes this renderer later.
            // Do not capture the mutable legacy state object: callers restore
            // shader color immediately after submitting, which otherwise turns
            // translucent colored world overlays (for example Scaffold's blue
            // placement preview) into opaque white geometry at draw time.
            float red = state.red();
            float green = state.green();
            float blue = state.blue();
            float alpha = state.alpha();
            PoseStack identity = new PoseStack();
            world.submitCustomGeometry(identity, renderType,
                    (pose, consumer) -> emit(mesh, consumer, red, green, blue, alpha));
        }
    }

    private static @Nullable ScreenRectangle effectiveGuiScissor(@Nullable GuiGraphicsExtractor gui) {
        if (gui == null) return null;
        ScreenRectangle override = GUI_SCISSOR_OVERRIDE.get();
        ScreenRectangle existing = gui.scissorStack.peek();
        if (override == null) return existing;
        if (existing == null) return override;
        ScreenRectangle intersection = override.intersection(existing);
        return intersection == null ? ScreenRectangle.empty() : intersection;
    }

    public static void submitWorldText(PoseStack pose, String text, float x, float y,
            int color, int packedLight) {
        SubmitNodeCollector world = WORLD.get();
        if (world == null || text == null || text.isEmpty()) return;
        world.submitText(pose, x, y, Component.literal(text).getVisualOrderText(), false,
            Font.DisplayMode.NORMAL, 0, packedLight, color, 0);
    }

    private static RenderPipeline guiPipeline(PrimitiveTopology topology, boolean textured) {
        Map<PrimitiveTopology, RenderPipeline> cache = textured ? GUI_TEXTURED : GUI_COLORED;
        return cache.computeIfAbsent(topology, key -> RenderPipelines.register(
                RenderPipeline.builder(textured ? RenderPipelines.GUI_TEXTURED_SNIPPET : RenderPipelines.GUI_SNIPPET)
                        .withLocation(Identifier.fromNamespaceAndPath("mizulune",
                                "pipeline/legacy_gui_" + (textured ? "textured_" : "colored_") + key.name().toLowerCase()))
                        .withPrimitiveTopology(key)
                        .build()));
    }

    private static RenderType worldRenderType(PrimitiveTopology topology, boolean textured, @Nullable Identifier texture) {
        Map<PrimitiveTopology, RenderType> cache = textured && texture != null ? WORLD_TEXTURED : WORLD_COLORED;
        return cache.computeIfAbsent(topology, key -> {
            RenderPipeline pipeline = RenderPipelines.register(
                    RenderPipeline.builder(textured ? RenderPipelines.GUI_TEXTURED_SNIPPET : RenderPipelines.DEBUG_FILLED_SNIPPET)
                            .withLocation(Identifier.fromNamespaceAndPath("mizulune",
                                    "pipeline/legacy_world_" + (textured ? "textured_" : "colored_") + key.name().toLowerCase()))
                            .withPrimitiveTopology(key)
                            .build());
            RenderSetup.RenderSetupBuilder setup = RenderSetup.builder(pipeline);
            if (textured && texture != null) setup.withTexture("Sampler0", texture);
            return RenderType.create("mizulune_legacy_" + key.name().toLowerCase(), setup.createRenderSetup());
        });
    }

    private static TextureSetup textureSetup(boolean textured, @Nullable Identifier texture,
            @Nullable AbstractTexture directTexture) {
        if (!textured) return TextureSetup.noTexture();
        AbstractTexture resolved = directTexture != null
            ? directTexture
            : texture == null ? null : Minecraft.getInstance().getTextureManager().getTexture(texture);
        if (resolved == null) return TextureSetup.noTexture();
        return TextureSetup.singleTexture(resolved.getTextureView(), resolved.getSampler());
    }

    private static void emit(LegacyMesh mesh, VertexConsumer consumer,
            float red, float green, float blue, float alpha) {
        for (LegacyMesh.Vertex vertex : mesh.vertices()) {
            int color = multiply(vertex.color(), red, green, blue, alpha);
            consumer.addVertex(vertex.x(), vertex.y(), vertex.z()).setColor(color);
            if (mesh.textured()) consumer.setUv(vertex.u(), vertex.v());
        }
    }

    private static int multiply(int color, float red, float green, float blue, float alpha) {
        int a = Math.min(255, Math.round(((color >>> 24) & 0xFF) * alpha));
        int r = Math.min(255, Math.round(((color >>> 16) & 0xFF) * red));
        int g = Math.min(255, Math.round(((color >>> 8) & 0xFF) * green));
        int b = Math.min(255, Math.round((color & 0xFF) * blue));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private record LegacyGuiElement(LegacyMesh mesh, RenderPipeline pipeline, TextureSetup textureSetup,
            @Nullable ScreenRectangle scissorArea, float red, float green, float blue, float alpha)
            implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer consumer) {
            emit(this.mesh, consumer, this.red, this.green, this.blue, this.alpha);
        }

        @Override
        public ScreenRectangle bounds() {
            float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
            for (LegacyMesh.Vertex vertex : this.mesh.vertices()) {
                minX = Math.min(minX, vertex.x()); minY = Math.min(minY, vertex.y());
                maxX = Math.max(maxX, vertex.x()); maxY = Math.max(maxY, vertex.y());
            }
            return new ScreenRectangle((int) Math.floor(minX), (int) Math.floor(minY),
                    Math.max(1, (int) Math.ceil(maxX - minX)), Math.max(1, (int) Math.ceil(maxY - minY)));
        }
    }
}
