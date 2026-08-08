package shit.zen.fabric.render;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import shit.zen.render.DrawContext;
import shit.zen.render.FontRenderer;
import shit.zen.render.LiquidGlassStyle;
import shit.zen.render.Path;
import shit.zen.render.Rectangle;
import shit.zen.render.RoundedRectangle;
import shit.zen.render.backend.SkikoBackend;

/**
 * Replays framebuffer-dependent 1.20.1 Skiko effects at the 26.2 GUI draw
 * boundary. Extraction only records immutable commands; the original effect
 * backend and SKSL execute after the world target exists and before vanilla
 * GUI meshes are drawn over the transparent target.
 */
public final class FabricDeferredSkiko {
    private static final Logger LOGGER = LogManager.getLogger(FabricDeferredSkiko.class);
    private static final SkikoBackend BACKEND = new SkikoBackend();
    private static final List<Command> COMMANDS = new ArrayList<>();
    private static final FramebufferSlot SOURCE_FRAMEBUFFER = new FramebufferSlot();
    private static final FramebufferSlot DESTINATION_FRAMEBUFFER = new FramebufferSlot();

    private FabricDeferredSkiko() {
    }

    public static boolean hasCommands() {
        return !COMMANDS.isEmpty();
    }

    public static void enqueueBlurredRoundedRect(
            Matrix4f pose,
            ScreenRectangle clip,
            RoundedRectangle rectangle,
            float offsetX,
            float offsetY,
            float blurRadius,
            float spread,
            int color) {
        enqueue(pose, clip, (backend, context) -> backend.drawBlurredRoundedRect(
                context, rectangle, offsetX, offsetY, blurRadius, spread, color));
    }

    static void enqueueDraw(Matrix4f pose, ScreenRectangle clip, Effect effect) {
        enqueue(pose, clip, effect);
    }

    static float measureTextWidth(String text, FontRenderer font) {
        return BACKEND.measureTextWidth(text, font);
    }

    static boolean canDrawResourceTexture(Identifier resourceLocation) {
        return resourceLocation != null && BACKEND.canDrawResourceTexture(resourceLocation);
    }

    static boolean canDrawTexture(shit.zen.render.Texture texture) {
        return texture != null && BACKEND.canDrawTexture(texture);
    }

    public static void enqueueBackdropRoundedRect(
            Matrix4f pose,
            ScreenRectangle clip,
            RoundedRectangle rectangle,
            float blurRadius,
            float opacity,
            int color) {
        enqueue(pose, clip, (backend, context) -> backend.drawBackdropBlurredRoundedRect(
                context, rectangle, blurRadius, opacity, color));
    }

    public static void enqueueBackdropPath(
            Matrix4f pose,
            ScreenRectangle clip,
            Path path,
            Rectangle bounds,
            float blurRadius,
            float opacity,
            int color) {
        Path snapshot = copyPath(path);
        enqueue(pose, clip, (backend, context) -> backend.drawBackdropBlurredPath(
                context, snapshot, bounds, blurRadius, opacity, color));
    }

    public static void enqueueLiquidGlass(
            Matrix4f pose,
            ScreenRectangle clip,
            RoundedRectangle rectangle,
            LiquidGlassStyle style) {
        enqueue(pose, clip, (backend, context) -> backend.drawLiquidGlassPanel(
                context, rectangle, style));
    }

    private static void enqueue(Matrix4f pose, ScreenRectangle clip, Effect effect) {
        COMMANDS.add(new Command(new Matrix4f(pose), copy(clip), effect));
        FabricBlurCompositor.requestBackdrop();
    }

    public static void render(RenderTarget worldTarget, RenderTarget guiTarget) {
        List<Command> frameCommands = drainCommands();
        if (frameCommands.isEmpty()) {
            return;
        }
        if (worldTarget == null || guiTarget == null
                || !(worldTarget.getColorTexture() instanceof GlTexture worldTexture)
                || !(guiTarget.getColorTexture() instanceof GlTexture guiTexture)) {
            throw new IllegalStateException("Fabric 26.2 Skiko bridge requires OpenGL render targets");
        }

        try {
            withFramebuffer(SOURCE_FRAMEBUFFER, worldTexture, worldTarget.width, worldTarget.height,
                    () -> BACKEND.captureCleanBackdrop(null, new PoseStack()));
            withFramebuffer(DESTINATION_FRAMEBUFFER, guiTexture, guiTarget.width, guiTarget.height,
                    () -> replay(frameCommands));
        } catch (Throwable error) {
            LOGGER.error("Failed to replay deferred Skiko GUI effects", error);
        }
    }

    private static void replay(List<Command> commands) {
        PoseStack identityPose = new PoseStack();
        DrawContext identityContext = new DrawContext(null, identityPose, BACKEND);
        BACKEND.begin(null, identityPose);
        try {
            for (Command command : commands) {
                BACKEND.save(identityContext);
                try {
                    if (command.clip != null && command.clip.width() > 0 && command.clip.height() > 0) {
                        BACKEND.clipRect(identityContext, Rectangle.ofXYWH(
                                command.clip.left(), command.clip.top(),
                                command.clip.width(), command.clip.height()));
                    }
                    PoseStack commandPose = new PoseStack();
                    commandPose.last().pose().set(command.pose);
                    DrawContext commandContext = new DrawContext(null, commandPose, BACKEND);
                    BACKEND.pushExternalPose(commandPose);
                    try {
                        command.effect.draw(BACKEND, commandContext);
                    } finally {
                        BACKEND.popExternalPose();
                    }
                } finally {
                    BACKEND.restore(identityContext);
                }
            }
        } finally {
            BACKEND.end();
        }
    }

    private static List<Command> drainCommands() {
        if (COMMANDS.isEmpty()) {
            return List.of();
        }
        List<Command> commands = List.copyOf(COMMANDS);
        COMMANDS.clear();
        return commands;
    }

    private static void withFramebuffer(
            FramebufferSlot slot,
            GlTexture texture,
            int width,
            int height,
            Runnable action) {
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        try {
            slot.bind(texture);
            GL11.glViewport(0, 0, Math.max(1, width), Math.max(1, height));
            action.run();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    public static void close() {
        COMMANDS.clear();
        SOURCE_FRAMEBUFFER.close();
        DESTINATION_FRAMEBUFFER.close();
    }

    private static ScreenRectangle copy(ScreenRectangle rectangle) {
        return rectangle == null ? null : new ScreenRectangle(
                rectangle.left(), rectangle.top(), rectangle.width(), rectangle.height());
    }

    static Path copyPath(Path source) {
        if (source == null) {
            return null;
        }
        Path copy = new Path();
        for (Path.PathSegment segment : source.getSegments()) {
            switch (segment.type) {
                case MOVE_TO -> copy.moveTo(segment.coords[0], segment.coords[1]);
                case LINE_TO -> copy.lineTo(segment.coords[0], segment.coords[1]);
                case QUAD_TO -> copy.quadTo(segment.coords[0], segment.coords[1],
                        segment.coords[2], segment.coords[3]);
                case CUBIC_TO -> copy.cubicTo(segment.coords[0], segment.coords[1],
                        segment.coords[2], segment.coords[3], segment.coords[4], segment.coords[5]);
                case CLOSE -> copy.closePath();
                case RRECT -> copy.addRoundedRect(segment.roundedRect);
                case RECT -> copy.addRect(segment.rect);
            }
        }
        return copy;
    }

    @FunctionalInterface
    interface Effect {
        void draw(SkikoBackend backend, DrawContext context);
    }

    private record Command(Matrix4f pose, ScreenRectangle clip, Effect effect) {
    }

    private static final class FramebufferSlot {
        private int framebuffer;
        private int textureId = -1;

        void bind(GlTexture texture) {
            int nextTextureId = texture.glId();
            if (this.framebuffer == 0 || this.textureId != nextTextureId) {
                this.close();
                this.framebuffer = GL30.glGenFramebuffers();
                this.textureId = nextTextureId;
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                        GL11.GL_TEXTURE_2D, nextTextureId, 0);
                GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
                int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
                if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("Incomplete Skiko framebuffer: 0x"
                            + Integer.toHexString(status));
                }
            } else {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
            }
        }

        void close() {
            if (this.framebuffer != 0) {
                GL30.glDeleteFramebuffers(this.framebuffer);
                this.framebuffer = 0;
            }
            this.textureId = -1;
        }
    }
}
