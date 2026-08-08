package net.minecraft.client.gui;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import shit.zen.fabric.render.FabricRenderBridge;
import shit.zen.fabric.render.LegacyBufferBuilder;
import shit.zen.fabric.render.LegacyBufferUploader;

/**
 * 1.20-compatible facade backed by a 26.2 {@link GuiGraphicsExtractor}.
 * This class exists only in the generated Fabric source set.
 */
public final class GuiGraphics {
    private final GuiGraphicsExtractor extractor;
    private final PoseStack pose = new PoseStack();

    public GuiGraphics(Minecraft minecraft, Object ignoredRenderBuffers) {
        this(requireActiveExtractor());
    }

    public GuiGraphics(GuiGraphicsExtractor extractor) {
        this.extractor = extractor;
    }

    public GuiGraphicsExtractor extractor() {
        return this.extractor;
    }

    public PoseStack pose() {
        return this.pose;
    }

    public void fill(int x0, int y0, int x1, int y1, int color) {
        LegacyBufferBuilder builder = new LegacyBufferBuilder();
        builder.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
        builder.vertex(this.pose.last().pose(), x0, y0, 0).color(color).endVertex();
        builder.vertex(this.pose.last().pose(), x0, y1, 0).color(color).endVertex();
        builder.vertex(this.pose.last().pose(), x1, y1, 0).color(color).endVertex();
        builder.vertex(this.pose.last().pose(), x1, y0, 0).color(color).endVertex();
        LegacyBufferUploader.draw(builder.end());
    }

    public int drawString(Font font, String text, int x, int y, int color) {
        this.withPose(() -> this.extractor.text(font, text, x, y, color));
        return x + font.width(text);
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        this.withPose(() -> this.extractor.text(font, text, x, y, color, shadow));
        return x + font.width(text);
    }

    public int drawString(Font font, Component text, int x, int y, int color) {
        this.withPose(() -> this.extractor.text(font, text, x, y, color));
        return x + font.width(text);
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        this.withPose(() -> this.extractor.text(font, text, x, y, color, shadow));
        return x + font.width(text);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color) {
        this.withPose(() -> this.extractor.text(font, text, x, y, color));
        return x + font.width(text);
    }

    public void drawCenteredString(Font font, Component text, int x, int y, int color) {
        this.withPose(() -> this.extractor.centeredText(font, text, x, y, color));
    }

    public void drawCenteredString(Font font, String text, int x, int y, int color) {
        this.withPose(() -> this.extractor.centeredText(font, text, x, y, color));
    }

    public void enableScissor(int x0, int y0, int x1, int y1) {
        // Legacy screens use CPU-side culling in FabricSubmissionBackend.
        // Pushing legacy scissors into 26.2 can yield a deferred 0x0 area.
    }

    public void disableScissor() {
        // Paired no-op; see enableScissor.
    }

    public void flush() {
    }

    public void setColor(float red, float green, float blue, float alpha) {
        shit.zen.fabric.render.LegacyRenderSystem.setShaderColor(red, green, blue, alpha);
    }

    public void renderItem(ItemStack stack, int x, int y) {
        this.withPose(() -> this.extractor.item(stack, x, y));
    }

    public void renderItem(ItemStack stack, int x, int y, int seed) {
        this.withPose(() -> this.extractor.item(stack, x, y, seed));
    }

    public void renderItem(ItemStack stack, int x, int y, int seed, int size) {
        this.withPose(() -> {
            float scale = Math.max(1, size) / 16.0F;
            this.extractor.pose().pushMatrix();
            try {
                this.extractor.pose().translate(x, y).scale(scale, scale);
                this.extractor.item(stack, 0, 0, seed);
            } finally {
                this.extractor.pose().popMatrix();
            }
        });
    }

    public void renderItemDecorations(Font font, ItemStack stack, int x, int y) {
        this.withPose(() -> this.extractor.itemDecorations(font, stack, x, y));
    }

    public void renderItemDecorations(Font font, ItemStack stack, int x, int y, String count) {
        this.withPose(() -> this.extractor.itemDecorations(font, stack, x, y, count));
    }

    public void blit(Identifier texture, int x, int y, int u, int v, int width, int height,
            int textureWidth, int textureHeight) {
        this.withPose(() -> this.extractor.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v,
                width, height, textureWidth, textureHeight));
    }

    public void blit(Identifier texture, int x, int y, int z, int u, int v,
            float width, float height, int regionWidth, int regionHeight,
            int textureWidth, int textureHeight) {
        this.withPose(() -> this.extractor.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v,
                (int) width, (int) height, regionWidth, regionHeight, textureWidth, textureHeight));
    }

    public void blit(Identifier texture, int x, int y, int u, int v,
            float width, float height, int regionWidth, int regionHeight,
            int textureWidth, int textureHeight) {
        this.withPose(() -> this.extractor.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v,
            (int) width, (int) height, regionWidth, regionHeight, textureWidth, textureHeight));
    }

    private void withPose(Runnable action) {
        Matrix4f matrix = this.pose.last().pose();
        Matrix3x2f transform = new Matrix3x2f(
                matrix.m00(), matrix.m01(), matrix.m10(), matrix.m11(), matrix.m30(), matrix.m31());
        this.extractor.pose().pushMatrix();
        try {
            this.extractor.pose().mul(transform);
            FabricRenderBridge.withGuiScissor(action);
        } finally {
            this.extractor.pose().popMatrix();
        }
    }

    private static GuiGraphicsExtractor requireActiveExtractor() {
        GuiGraphicsExtractor extractor = FabricRenderBridge.currentGui();
        if (extractor == null) {
            throw new IllegalStateException("GuiGraphics created outside a Fabric 26.2 GUI extraction phase");
        }
        return extractor;
    }
}
