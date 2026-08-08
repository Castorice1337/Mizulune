package shit.zen.fabric.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.function.Supplier;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

/**
 * State capture for 1.20 render calls. Geometry-affecting state is consumed by
 * {@link FabricRenderBridge}; thread/device operations delegate to 26.2.
 */
public final class LegacyRenderSystem {
    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);
    private static final AtomicInteger NEXT_TEXTURE_ID = new AtomicInteger(1);
    private static final Map<AbstractTexture, Integer> TEXTURE_IDS = new IdentityHashMap<>();
    private static final Map<Integer, AbstractTexture> TEXTURES = new java.util.HashMap<>();

    private LegacyRenderSystem() {
    }

    public static Object legacyShader() {
        return null;
    }

    public static State state() {
        return STATE.get();
    }

    public static void assertOnRenderThread() { RenderSystem.assertOnRenderThread(); }
    public static boolean isOnRenderThread() { return RenderSystem.isOnRenderThread(); }
    public static void recordRenderCall(Runnable runnable) { RenderSystem.queueFencedTask(runnable); }
    public static LegacyTesselator renderThreadTesselator() { return LegacyTesselator.getInstance(); }
    public static Matrix4f getModelViewMatrix() { return RenderSystem.getModelViewMatrixCopy(); }
    public static Matrix4f getProjectionMatrix() { return new Matrix4f(state().projection); }
    public static VertexSorting getVertexSorting() { return state().sorting; }
    public static void setProjectionMatrix(Matrix4f matrix, VertexSorting sorting) {
        state().projection.set(matrix);
        state().sorting = sorting;
    }

    public static void setShader(Supplier<?> ignored) { }
    public static void setShaderTexture(int slot, Identifier texture) {
        if (slot == 0) { state().texture = texture; state().textureObject = null; }
    }
    public static void setShaderTexture(int slot, int textureId) {
        if (slot == 0) { state().texture = null; state().textureObject = texture(textureId); }
    }
    public static void bindTexture(int textureId) {
        state().texture = null;
        state().textureObject = texture(textureId);
    }

    public static synchronized int textureId(AbstractTexture texture) {
        if (texture == null) return -1;
        Integer existing = TEXTURE_IDS.get(texture);
        if (existing != null) return existing;
        int id = NEXT_TEXTURE_ID.getAndIncrement();
        TEXTURE_IDS.put(texture, id);
        TEXTURES.put(id, texture);
        return id;
    }

    public static synchronized AbstractTexture texture(int id) {
        return TEXTURES.get(id);
    }

    public static void bindRenderTarget(Object ignored) { }
    public static int rawTextureId(Object ignored) { return 0; }
    public static void setShaderColor(float red, float green, float blue, float alpha) {
        state().red = red; state().green = green; state().blue = blue; state().alpha = alpha;
    }
    public static void lineWidth(float width) { state().lineWidth = width; }

    public static void enableBlend() { state().blend = true; }
    public static void disableBlend() { state().blend = false; }
    public static void enableDepthTest() { state().depthTest = true; }
    public static void disableDepthTest() { state().depthTest = false; }
    public static void enableCull() { state().cull = true; }
    public static void disableCull() { state().cull = false; }
    public static void enableScissor(int x, int y, int width, int height) { }
    public static void disableScissor() { }
    public static void depthMask(boolean value) { state().depthWrite = value; }
    public static void colorMask(boolean red, boolean green, boolean blue, boolean alpha) { }
    public static void defaultBlendFunc() { state().blend = true; }
    public static void blendFunc(int source, int destination) { state().blend = true; }
    public static void blendFuncSeparate(int sourceRgb, int destRgb,
            int sourceAlpha, int destAlpha) { state().blend = true; }
    public static void blendEquation(int equation) { }
    public static void activeTexture(int texture) { }
    public static void pixelStore(int parameter, int value) { }
    public static void texParameter(int target, int parameter, int value) { }

    public static final class State {
        private final Matrix4f projection = new Matrix4f();
        private VertexSorting sorting = VertexSorting.ORTHOGRAPHIC_Z;
        private Identifier texture;
        private AbstractTexture textureObject;
        private float red = 1.0F;
        private float green = 1.0F;
        private float blue = 1.0F;
        private float alpha = 1.0F;
        private float lineWidth = 1.0F;
        private boolean blend = true;
        private boolean depthTest;
        private boolean depthWrite;
        private boolean cull;

        public Identifier texture() { return this.texture; }
        public AbstractTexture textureObject() { return this.textureObject; }
        public float red() { return this.red; }
        public float green() { return this.green; }
        public float blue() { return this.blue; }
        public float alpha() { return this.alpha; }
        public float lineWidth() { return this.lineWidth; }
        public boolean blend() { return this.blend; }
        public boolean depthTest() { return this.depthTest; }
        public boolean depthWrite() { return this.depthWrite; }
        public boolean cull() { return this.cull; }
    }
}
