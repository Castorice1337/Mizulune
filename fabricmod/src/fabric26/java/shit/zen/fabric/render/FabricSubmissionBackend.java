package shit.zen.fabric.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import shit.zen.render.CustomFont;
import shit.zen.render.DrawContext;
import shit.zen.render.FontRenderer;
import shit.zen.render.GlyphMetrics;
import shit.zen.render.LiquidGlassStyle;
import shit.zen.render.Paint;
import shit.zen.render.Path;
import shit.zen.render.Rectangle;
import shit.zen.render.RoundedRectangle;
import shit.zen.render.Texture;
import shit.zen.render.backend.BackendType;
import shit.zen.render.backend.RenderBackend;

/** Submission-only 2D backend for Minecraft 26.2 GUI extraction. */
public final class FabricSubmissionBackend implements RenderBackend {
    private static final int CORNER_SEGMENTS = 8;
    private final ThreadLocal<Deque<ClipFrame>> clipFrames =
            ThreadLocal.withInitial(ArrayDeque::new);
    private final ThreadLocal<Deque<ExternalPose>> externalPoses =
            ThreadLocal.withInitial(ArrayDeque::new);
    private final ThreadLocal<Boolean> customFontFallback =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Override
    public BackendType type() {
        return BackendType.SKIKO;
    }

    @Override
    public boolean handles2D() {
        return true;
    }

    @Override
    public String debugSummary() {
        return "FabricSubmissionBackend(render-state safe)";
    }

    @Override
    public void begin(GuiGraphics graphics, com.mojang.blaze3d.vertex.PoseStack poseStack) {
        this.clipFrames.get().clear();
        this.externalPoses.get().clear();
        FabricRenderBridge.setGuiScissorOverride(null);
    }

    @Override
    public void end() {
        this.clearClipStack();
        this.externalPoses.get().clear();
        this.customFontFallback.remove();
    }

    @Override
    public void pushExternalPose(com.mojang.blaze3d.vertex.PoseStack poseStack) {
        if (poseStack != null) {
            this.externalPoses.get().push(
                    new ExternalPose(poseStack, new Matrix4f(poseStack.last().pose())));
        }
    }

    @Override
    public void popExternalPose() {
        Deque<ExternalPose> poses = this.externalPoses.get();
        if (!poses.isEmpty()) {
            poses.pop();
        }
    }

    @Override
    public void save(DrawContext context) {
        Deque<ClipFrame> frames = this.clipFrames.get();
        ClipFrame parent = frames.peek();
        frames.push(new ClipFrame(parent == null ? this.screenBounds() : parent.bounds,
                parent != null && parent.empty));
    }

    @Override
    public void restore(DrawContext context) {
        Deque<ClipFrame> frames = this.clipFrames.get();
        if (frames.isEmpty()) {
            return;
        }
        frames.pop();
        this.updateScissorOverride();
    }

    @Override
    public void clipRect(DrawContext context, Rectangle rectangle) {
        this.enableClip(context, rectangle.getX(), rectangle.getY(),
                rectangle.getRight(), rectangle.getBottom());
    }

    @Override
    public void clipRoundedRect(DrawContext context, RoundedRectangle rectangle) {
        this.enableClip(context, rectangle.x1, rectangle.y1, rectangle.x2, rectangle.y2);
    }

    private void enableClip(DrawContext context, float x1, float y1, float x2, float y2) {
        Deque<ClipFrame> frames = this.clipFrames.get();
        if (frames.isEmpty()) {
            frames.push(new ClipFrame(this.screenBounds(), false));
        }
        ClipFrame frame = frames.peek();
        if (frame.empty) {
            return;
        }
        Matrix4f pose = this.effectivePose(context);
        Vector4f first = pose
                .transform(new Vector4f(x1, y1, 0.0F, 1.0F));
        Vector4f second = pose
                .transform(new Vector4f(x2, y2, 0.0F, 1.0F));
        ClipBounds requested = new ClipBounds(
                (int) Math.floor(Math.min(first.x, second.x)),
                (int) Math.floor(Math.min(first.y, second.y)),
                (int) Math.ceil(Math.max(first.x, second.x)),
                (int) Math.ceil(Math.max(first.y, second.y)));
        ClipBounds clipped = requested.intersection(frame.bounds);
        if (clipped == null || clipped.width() <= 0 || clipped.height() <= 0) {
            // 26.2 records scissor state during extraction and throws later when a
            // zero-sized intersection reaches RenderPass. Treat it as a fully
            // clipped frame and suppress its draw submissions instead.
            frame.empty = true;
            this.updateScissorOverride();
            return;
        }
        frame.bounds = clipped;
        frame.empty = false;
        this.updateScissorOverride();
    }

    @Override
    public void clearClipStack() {
        this.clipFrames.get().clear();
        FabricRenderBridge.setGuiScissorOverride(null);
    }

    private void updateScissorOverride() {
        ClipFrame frame = this.clipFrames.get().peek();
        if (frame == null) {
            FabricRenderBridge.setGuiScissorOverride(null);
            return;
        }
        if (frame.empty) {
            FabricRenderBridge.setGuiScissorOverride(ScreenRectangle.empty());
            return;
        }
        FabricRenderBridge.setGuiScissorOverride(new ScreenRectangle(
                frame.bounds.left, frame.bounds.top, frame.bounds.width(), frame.bounds.height()));
    }

    @Override
    public void drawRect(
            DrawContext context,
            float x,
            float y,
            float width,
            float height,
            Paint paint) {
        if (this.isFullyClipped() || paint == null || width <= 0.0F || height <= 0.0F) {
            return;
        }
        Paint snapshot = paint.copy();
        this.enqueueDraw(context, (backend, replayContext) ->
                backend.drawRect(replayContext, x, y, width, height, snapshot));
    }

    @Override
    public void drawRoundedRect(
            DrawContext context,
            RoundedRectangle rectangle,
            Paint paint) {
        if (this.isFullyClipped() || rectangle == null || paint == null
                || rectangle.getWidth() <= 0.0F || rectangle.getHeight() <= 0.0F) {
            return;
        }
        Paint snapshot = paint.copy();
        this.enqueueDraw(context, (backend, replayContext) ->
                backend.drawRoundedRect(replayContext, rectangle, snapshot));
    }

    private void drawRoundedOutline(DrawContext context, RoundedRectangle rectangle, Paint paint) {
        java.util.List<float[]> perimeter = roundedPerimeter(rectangle);
        for (int index = 1; index < perimeter.size(); index++) {
            float[] from = perimeter.get(index - 1);
            float[] to = perimeter.get(index);
            this.drawLine(context, from[0], from[1], to[0], to[1], paint);
        }
    }

    private static java.util.List<float[]> roundedPerimeter(RoundedRectangle rectangle) {
        java.util.List<float[]> points = new java.util.ArrayList<>();
        appendCorner(points, rectangle.x2 - clampedRadius(rectangle.topRightRadius, rectangle),
                rectangle.y1 + clampedRadius(rectangle.topRightRadius, rectangle),
                clampedRadius(rectangle.topRightRadius, rectangle), -90.0F, 0.0F);
        appendCorner(points, rectangle.x2 - clampedRadius(rectangle.bottomRightRadius, rectangle),
                rectangle.y2 - clampedRadius(rectangle.bottomRightRadius, rectangle),
                clampedRadius(rectangle.bottomRightRadius, rectangle), 0.0F, 90.0F);
        appendCorner(points, rectangle.x1 + clampedRadius(rectangle.bottomLeftRadius, rectangle),
                rectangle.y2 - clampedRadius(rectangle.bottomLeftRadius, rectangle),
                clampedRadius(rectangle.bottomLeftRadius, rectangle), 90.0F, 180.0F);
        appendCorner(points, rectangle.x1 + clampedRadius(rectangle.topLeftRadius, rectangle),
                rectangle.y1 + clampedRadius(rectangle.topLeftRadius, rectangle),
                clampedRadius(rectangle.topLeftRadius, rectangle), 180.0F, 270.0F);
        if (!points.isEmpty()) {
            points.add(points.get(0));
        }
        return points;
    }

    private static void appendCorner(
            java.util.List<float[]> points,
            float centerX,
            float centerY,
            float radius,
            float startDegrees,
            float endDegrees) {
        for (int index = 0; index <= CORNER_SEGMENTS; index++) {
            float progress = index / (float) CORNER_SEGMENTS;
            double angle = Math.toRadians(startDegrees + (endDegrees - startDegrees) * progress);
            points.add(new float[] {
                    centerX + (float) Math.cos(angle) * radius,
                    centerY + (float) Math.sin(angle) * radius
            });
        }
    }

    private static float leftBoundary(RoundedRectangle rectangle, float y) {
        float topRadius = clampedRadius(rectangle.topLeftRadius, rectangle);
        if (topRadius > 0.0F && y < rectangle.y1 + topRadius) {
            return circleLeft(rectangle.x1 + topRadius, rectangle.y1 + topRadius, topRadius, y);
        }
        float bottomRadius = clampedRadius(rectangle.bottomLeftRadius, rectangle);
        if (bottomRadius > 0.0F && y > rectangle.y2 - bottomRadius) {
            return circleLeft(rectangle.x1 + bottomRadius, rectangle.y2 - bottomRadius, bottomRadius, y);
        }
        return rectangle.x1;
    }

    private static float rightBoundary(RoundedRectangle rectangle, float y) {
        float topRadius = clampedRadius(rectangle.topRightRadius, rectangle);
        if (topRadius > 0.0F && y < rectangle.y1 + topRadius) {
            return circleRight(rectangle.x2 - topRadius, rectangle.y1 + topRadius, topRadius, y);
        }
        float bottomRadius = clampedRadius(rectangle.bottomRightRadius, rectangle);
        if (bottomRadius > 0.0F && y > rectangle.y2 - bottomRadius) {
            return circleRight(rectangle.x2 - bottomRadius, rectangle.y2 - bottomRadius, bottomRadius, y);
        }
        return rectangle.x2;
    }

    private static float circleLeft(float centerX, float centerY, float radius, float y) {
        float dy = Math.max(-radius, Math.min(radius, y - centerY));
        return centerX - (float) Math.sqrt(Math.max(0.0F, radius * radius - dy * dy));
    }

    private static float circleRight(float centerX, float centerY, float radius, float y) {
        float dy = Math.max(-radius, Math.min(radius, y - centerY));
        return centerX + (float) Math.sqrt(Math.max(0.0F, radius * radius - dy * dy));
    }

    private static float clampedRadius(float radius, RoundedRectangle rectangle) {
        return Math.max(0.0F, Math.min(radius,
                Math.min(rectangle.getWidth(), rectangle.getHeight()) * 0.5F));
    }

    private static float maximumRadius(RoundedRectangle rectangle) {
        return Math.max(Math.max(clampedRadius(rectangle.topLeftRadius, rectangle),
                        clampedRadius(rectangle.topRightRadius, rectangle)),
                Math.max(clampedRadius(rectangle.bottomRightRadius, rectangle),
                        clampedRadius(rectangle.bottomLeftRadius, rectangle)));
    }

    private static void appendRoundedPerimeter(
            LegacyBufferBuilder builder,
            Matrix4f pose,
            RoundedRectangle rectangle,
            Paint paint,
            boolean close) {
        appendCorner(builder, pose, rectangle.x2 - rectangle.topRightRadius,
                rectangle.y1 + rectangle.topRightRadius, rectangle.topRightRadius,
                -90.0F, 0.0F, paint);
        appendCorner(builder, pose, rectangle.x2 - rectangle.bottomRightRadius,
                rectangle.y2 - rectangle.bottomRightRadius, rectangle.bottomRightRadius,
                0.0F, 90.0F, paint);
        appendCorner(builder, pose, rectangle.x1 + rectangle.bottomLeftRadius,
                rectangle.y2 - rectangle.bottomLeftRadius, rectangle.bottomLeftRadius,
                90.0F, 180.0F, paint);
        appendCorner(builder, pose, rectangle.x1 + rectangle.topLeftRadius,
                rectangle.y1 + rectangle.topLeftRadius, rectangle.topLeftRadius,
                180.0F, 270.0F, paint);
        if (close) {
            float radius = Math.max(0.0F, rectangle.topRightRadius);
            float x = rectangle.x2 - radius;
            float y = rectangle.y1;
            vertex(builder, pose, x, y, colorAt(paint, x, y));
        }
    }

    private static void appendCorner(
            LegacyBufferBuilder builder,
            Matrix4f pose,
            float centerX,
            float centerY,
            float radius,
            float startDegrees,
            float endDegrees,
            Paint paint) {
        float safeRadius = Math.max(0.0F, radius);
        for (int index = 0; index <= CORNER_SEGMENTS; index++) {
            float progress = index / (float) CORNER_SEGMENTS;
            double angle = Math.toRadians(startDegrees + (endDegrees - startDegrees) * progress);
            float x = centerX + (float) Math.cos(angle) * safeRadius;
            float y = centerY + (float) Math.sin(angle) * safeRadius;
            vertex(builder, pose, x, y, colorAt(paint, x, y));
        }
    }

    @Override
    public void drawLine(
            DrawContext context,
            float x1,
            float y1,
            float x2,
            float y2,
            Paint paint) {
        if (this.isFullyClipped() || paint == null) {
            return;
        }
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length < 1.0E-4F) {
            return;
        }
        Paint snapshot = paint.copy();
        this.enqueueDraw(context, (backend, replayContext) ->
                backend.drawLine(replayContext, x1, y1, x2, y2, snapshot));
    }

    @Override
    public void drawArc(
            DrawContext context,
            float x1,
            float y1,
            float x2,
            float y2,
            float startAngle,
            float sweepAngle,
            Paint paint) {
        if (this.isFullyClipped() || paint == null) {
            return;
        }
        Paint snapshot = paint.copy();
        this.enqueueDraw(context, (backend, replayContext) -> backend.drawArc(
                replayContext, x1, y1, x2, y2, startAngle, sweepAngle, snapshot));
    }

    @Override
    public void drawString(
            DrawContext context,
            String text,
            float x,
            float y,
            FontRenderer fontRenderer,
            Paint paint) {
        if (this.isFullyClipped() || text == null || text.isEmpty() || fontRenderer == null) {
            return;
        }
        if (paint == null) {
            return;
        }
        Paint snapshot = paint.copy();
        this.enqueueDraw(context, (backend, replayContext) ->
                backend.drawString(replayContext, text, x, y, fontRenderer, snapshot));
    }

    @Override
    public float drawGlowText(
            DrawContext context,
            String text,
            float x,
            float y,
            FontRenderer fontRenderer,
            int color,
            int glowColor,
            float radius) {
        if (this.isFullyClipped() || text == null || text.isEmpty() || fontRenderer == null) {
            return x;
        }
        this.enqueueDraw(context, (backend, replayContext) -> backend.drawGlowText(
                replayContext, text, x, y, fontRenderer, color, glowColor, radius));
        return x + FabricDeferredSkiko.measureTextWidth(text, fontRenderer);
    }

    @Override
    public boolean drawCustomFontText(
            DrawContext context,
            CustomFont customFont,
            String text,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            float x,
            float y,
            float baseR,
            float baseG,
            float baseB,
            float alpha,
            boolean rainbow,
            int rainbowOffset) {
        if (this.isFullyClipped()) {
            return true;
        }
        if (customFont == null || text == null || text.isEmpty()) {
            return true;
        }
        com.mojang.blaze3d.vertex.PoseStack effective = new com.mojang.blaze3d.vertex.PoseStack();
        Matrix4f supplied = poseStack == null ? new Matrix4f() : poseStack.last().pose();
        ExternalPose external = this.externalPoses.get().peek();
        if (external != null && external.source != poseStack) {
            effective.last().pose().set(external.matrix).mul(supplied);
        } else {
            effective.last().pose().set(supplied);
        }
        Matrix4f commandPose = new Matrix4f(effective.last().pose());
        com.mojang.blaze3d.vertex.PoseStack identity = new com.mojang.blaze3d.vertex.PoseStack();
        FabricDeferredSkiko.enqueueDraw(commandPose, this.currentScissor(),
                (backend, replayContext) -> backend.drawCustomFontText(
                        replayContext, customFont, text, identity, x, y,
                        baseR, baseG, baseB, alpha, rainbow, rainbowOffset));
        return true;
    }

    @Override
    public void drawPath(DrawContext context, Path path, Paint paint) {
        if (this.isFullyClipped() || path == null || paint == null) {
            return;
        }
        Path snapshotPath = FabricDeferredSkiko.copyPath(path);
        Paint snapshotPaint = paint.copy();
        this.enqueueDraw(context, (backend, replayContext) ->
                backend.drawPath(replayContext, snapshotPath, snapshotPaint));
    }

    private void drawContour(DrawContext context, java.util.List<float[]> points, Paint paint, boolean closed) {
        if (points.size() < 2) {
            return;
        }
        boolean stroke = paint.getCapStyle() == Paint.StrokeCap.STROKE;
        if (stroke) {
            for (int index = 1; index < points.size(); index++) {
                float[] from = points.get(index - 1);
                float[] to = points.get(index);
                this.drawLine(context, from[0], from[1], to[0], to[1], paint);
            }
            if (closed) {
                float[] from = points.get(points.size() - 1);
                float[] to = points.get(0);
                this.drawLine(context, from[0], from[1], to[0], to[1], paint);
            }
            return;
        }

        float centerX = 0.0F;
        float centerY = 0.0F;
        for (float[] point : points) {
            centerX += point[0];
            centerY += point[1];
        }
        centerX /= points.size();
        centerY /= points.size();
        Matrix4f pose = this.effectivePose(context);
        LegacyBufferBuilder builder = begin(PrimitiveTopology.QUADS);
        int edges = closed ? points.size() : points.size() - 1;
        for (int index = 0; index < edges; index++) {
            float[] first = points.get(index);
            float[] second = points.get((index + 1) % points.size());
            vertex(builder, pose, centerX, centerY, colorAt(paint, centerX, centerY));
            vertex(builder, pose, first[0], first[1], colorAt(paint, first[0], first[1]));
            vertex(builder, pose, second[0], second[1], colorAt(paint, second[0], second[1]));
            vertex(builder, pose, centerX, centerY, colorAt(paint, centerX, centerY));
        }
        submit(builder);
    }

    private static void appendQuadratic(java.util.List<float[]> points, float[] coords) {
        if (points.isEmpty() || coords == null || coords.length < 4) {
            return;
        }
        float[] start = points.get(points.size() - 1);
        for (int index = 1; index <= 8; index++) {
            float t = index / 8.0F;
            float inverse = 1.0F - t;
            points.add(new float[] {
                    inverse * inverse * start[0] + 2.0F * inverse * t * coords[0] + t * t * coords[2],
                    inverse * inverse * start[1] + 2.0F * inverse * t * coords[1] + t * t * coords[3]
            });
        }
    }

    private static void appendCubic(java.util.List<float[]> points, float[] coords) {
        if (points.isEmpty() || coords == null || coords.length < 6) {
            return;
        }
        float[] start = points.get(points.size() - 1);
        for (int index = 1; index <= 12; index++) {
            float t = index / 12.0F;
            float inverse = 1.0F - t;
            float a = inverse * inverse * inverse;
            float b = 3.0F * inverse * inverse * t;
            float c = 3.0F * inverse * t * t;
            float d = t * t * t;
            points.add(new float[] {
                    a * start[0] + b * coords[0] + c * coords[2] + d * coords[4],
                    a * start[1] + b * coords[1] + c * coords[3] + d * coords[5]
            });
        }
    }

    @Override
    public boolean canDrawResourceTexture(Identifier resourceLocation) {
        return FabricDeferredSkiko.canDrawResourceTexture(resourceLocation);
    }

    @Override
    public boolean canDrawTexture(Texture texture) {
        return FabricDeferredSkiko.canDrawTexture(texture);
    }

    @Override
    public void drawTexture(
            DrawContext context,
            Texture texture,
            Rectangle srcRect,
            Rectangle dstRect,
            Paint paint) {
        if (this.isFullyClipped() || texture == null || texture.getResourceLocation() == null
                || srcRect == null || dstRect == null || paint == null
                || dstRect.getWidth() <= 0.0F || dstRect.getHeight() <= 0.0F) {
            return;
        }
        Paint snapshot = paint.copy();
        this.enqueueDraw(context, (backend, replayContext) ->
                backend.drawTexture(replayContext, texture, srcRect, dstRect, snapshot));
    }

    @Override
    public boolean drawPlayerHead(
            DrawContext context,
            Identifier skinTexture,
            float x,
            float y,
            float width,
            float height,
            float alpha,
            float radius) {
        if (this.isFullyClipped() || skinTexture == null || width <= 0.0F || height <= 0.0F) {
            return true;
        }
        this.enqueueDraw(context, (backend, replayContext) -> backend.drawPlayerHead(
                replayContext, skinTexture, x, y, width, height, alpha, radius));
        return true;
    }

    @Override
    public float measureTextWidth(String text, FontRenderer fontRenderer) {
        return FabricDeferredSkiko.measureTextWidth(text, fontRenderer);
    }

    private void drawTextureUv(
            DrawContext context,
            Identifier texture,
            Rectangle destination,
            float u1,
            float v1,
            float u2,
            float v2,
            int color) {
        LegacyRenderSystem.setShaderTexture(0, texture);
        Matrix4f pose = this.effectivePose(context);
        LegacyBufferBuilder builder = new LegacyBufferBuilder();
        builder.begin(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.vertex(pose, destination.getX(), destination.getY(), 0.0F).uv(u1, v1).color(color).endVertex();
        builder.vertex(pose, destination.getX(), destination.getBottom(), 0.0F).uv(u1, v2).color(color).endVertex();
        builder.vertex(pose, destination.getRight(), destination.getBottom(), 0.0F).uv(u2, v2).color(color).endVertex();
        builder.vertex(pose, destination.getRight(), destination.getY(), 0.0F).uv(u2, v1).color(color).endVertex();
        submit(builder);
    }

    @Override
    public void drawBlurredRoundedRect(
            DrawContext context,
            RoundedRectangle rectangle,
            float offsetX,
            float offsetY,
            float blurRadius,
            float spread,
            int color) {
        if (this.isFullyClipped()) {
            return;
        }
        FabricDeferredSkiko.enqueueBlurredRoundedRect(new Matrix4f(this.effectivePose(context)),
                this.currentScissor(), rectangle, offsetX, offsetY, blurRadius, spread, color);
    }

    @Override
    public boolean drawBackdropBlurredRect(
            DrawContext context,
            float x,
            float y,
            float width,
            float height,
            float radius,
            float blurRadius,
            float opacity,
            int color) {
        if (this.isFullyClipped() || width <= 0.0F || height <= 0.0F) {
            return true;
        }
        FabricDeferredSkiko.enqueueBackdropRoundedRect(new Matrix4f(this.effectivePose(context)),
                this.currentScissor(), RoundedRectangle.ofXYWHR(x, y, width, height, radius),
                blurRadius, opacity, color);
        return true;
    }

    @Override
    public boolean drawBackdropBlurredRoundedRect(
            DrawContext context,
            RoundedRectangle rectangle,
            float blurRadius,
            float opacity,
            int color) {
        if (this.isFullyClipped()) {
            return true;
        }
        if (rectangle == null || rectangle.getWidth() <= 0.0F || rectangle.getHeight() <= 0.0F) {
            return false;
        }
        FabricDeferredSkiko.enqueueBackdropRoundedRect(new Matrix4f(this.effectivePose(context)),
                this.currentScissor(), rectangle, blurRadius, opacity, color);
        return true;
    }

    @Override
    public boolean drawBackdropBlurredPath(
            DrawContext context,
            Path path,
            Rectangle bounds,
            float blurRadius,
            float opacity,
            int color) {
        if (this.isFullyClipped()) {
            return true;
        }
        if (path == null || bounds == null || bounds.getWidth() <= 0.0F || bounds.getHeight() <= 0.0F) {
            return false;
        }
        FabricDeferredSkiko.enqueueBackdropPath(new Matrix4f(this.effectivePose(context)),
                this.currentScissor(), path, bounds, blurRadius, opacity, color);
        return true;
    }

    @Override
    public boolean drawLiquidGlassPanel(
            DrawContext context,
            RoundedRectangle rectangle,
            LiquidGlassStyle style) {
        if (this.isFullyClipped()) {
            return true;
        }
        if (rectangle == null || rectangle.getWidth() <= 0.0F || rectangle.getHeight() <= 0.0F) {
            return false;
        }
        LiquidGlassStyle effective = style == null ? LiquidGlassStyle.defaultClear() : style;
        FabricDeferredSkiko.enqueueLiquidGlass(new Matrix4f(this.effectivePose(context)),
                this.currentScissor(), rectangle, effective);
        return true;
    }

    private ScreenRectangle currentScissor() {
        ClipFrame frame = this.clipFrames.get().peek();
        if (frame == null || frame.empty) {
            return null;
        }
        return new ScreenRectangle(frame.bounds.left, frame.bounds.top,
                frame.bounds.width(), frame.bounds.height());
    }

    private void enqueueDraw(DrawContext context, FabricDeferredSkiko.Effect effect) {
        FabricDeferredSkiko.enqueueDraw(new Matrix4f(this.effectivePose(context)),
                this.currentScissor(), effect);
    }

    private boolean isFullyClipped() {
        ClipFrame frame = this.clipFrames.get().peek();
        return frame != null && frame.empty;
    }

    private Matrix4f effectivePose(DrawContext context) {
        Matrix4f supplied = context == null || context.getPoseStack() == null
                ? new Matrix4f()
                : context.getPoseStack().last().pose();
        ExternalPose external = this.externalPoses.get().peek();
        if (external == null || external.source == context.getPoseStack()) {
            return supplied;
        }
        return new Matrix4f(external.matrix).mul(supplied);
    }

    private ClipBounds screenBounds() {
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft == null || minecraft.getWindow() == null
                ? Integer.MAX_VALUE / 4 : minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft == null || minecraft.getWindow() == null
                ? Integer.MAX_VALUE / 4 : minecraft.getWindow().getGuiScaledHeight();
        return new ClipBounds(0, 0, Math.max(1, width), Math.max(1, height));
    }

    private static LegacyBufferBuilder begin(PrimitiveTopology topology) {
        LegacyRenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        LegacyBufferBuilder builder = new LegacyBufferBuilder();
        builder.begin(topology, DefaultVertexFormat.POSITION_COLOR);
        return builder;
    }

    private static void vertex(
            LegacyBufferBuilder builder,
            Matrix4f pose,
            float x,
            float y,
            int color) {
        builder.vertex(pose, x, y, 0.0F).color(color).endVertex();
    }

    private static void submit(LegacyBufferBuilder builder) {
        LegacyBufferUploader.draw(builder.end());
    }

    private static int colorAt(Paint paint, float x, float y) {
        Paint.GradientCoords gradient = paint.getGradCoords();
        if (gradient == null) {
            return paint.getColor();
        }
        float dx = gradient.x2 - gradient.x1;
        float dy = gradient.y2 - gradient.y1;
        float lengthSquared = dx * dx + dy * dy;
        float progress = lengthSquared <= 1.0E-6F
                ? 0.0F
                : ((x - gradient.x1) * dx + (y - gradient.y1) * dy) / lengthSquared;
        progress = Math.max(0.0F, Math.min(1.0F, progress));
        return lerpColor(gradient.color1, gradient.color2, progress);
    }

    private static int lerpColor(int first, int second, float progress) {
        int a = Math.round(((first >>> 24) & 0xFF)
                + (((second >>> 24) & 0xFF) - ((first >>> 24) & 0xFF)) * progress);
        int r = Math.round(((first >>> 16) & 0xFF)
                + (((second >>> 16) & 0xFF) - ((first >>> 16) & 0xFF)) * progress);
        int g = Math.round(((first >>> 8) & 0xFF)
                + (((second >>> 8) & 0xFF) - ((first >>> 8) & 0xFF)) * progress);
        int b = Math.round((first & 0xFF) + ((second & 0xFF) - (first & 0xFF)) * progress);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int mixRgb(int first, int second, float progress) {
        return lerpColor(0xFF000000 | (first & 0x00FFFFFF),
                0xFF000000 | (second & 0x00FFFFFF), clamp01(progress)) & 0x00FFFFFF;
    }

    private static int withAlpha(int rgb, int alpha) {
        return (clampByte(alpha) << 24) | (rgb & 0x00FFFFFF);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static final class ClipFrame {
        private ClipBounds bounds;
        private boolean empty;

        private ClipFrame(ClipBounds bounds, boolean empty) {
            this.bounds = bounds;
            this.empty = empty;
        }
    }

    private record ExternalPose(
            com.mojang.blaze3d.vertex.PoseStack source,
            Matrix4f matrix) {
    }

    private record ClipBounds(int left, int top, int right, int bottom) {
        private int width() {
            return this.right - this.left;
        }

        private int height() {
            return this.bottom - this.top;
        }

        private ClipBounds intersection(ClipBounds other) {
            if (other == null) {
                return this;
            }
            int newLeft = Math.max(this.left, other.left);
            int newTop = Math.max(this.top, other.top);
            int newRight = Math.min(this.right, other.right);
            int newBottom = Math.min(this.bottom, other.bottom);
            return newRight <= newLeft || newBottom <= newTop
                    ? null : new ClipBounds(newLeft, newTop, newRight, newBottom);
        }
    }
}
