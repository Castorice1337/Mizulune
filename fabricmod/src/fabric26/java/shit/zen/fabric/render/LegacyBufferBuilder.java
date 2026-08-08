package shit.zen.fabric.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/**
 * Source-compatible builder for the small immediate-mode surface used by the
 * shared 1.20 code. The completed batch is submitted through 26.2 render state.
 */
public final class LegacyBufferBuilder {
    private final List<LegacyMesh.Vertex> vertices = new ArrayList<>();
    private PrimitiveTopology topology = PrimitiveTopology.QUADS;
    private boolean textured;
    private float x;
    private float y;
    private float z;
    private int color = 0xFFFFFFFF;
    private float u;
    private float v;

    public void begin(PrimitiveTopology topology, VertexFormat format) {
        this.vertices.clear();
        this.topology = topology;
        this.textured = format == DefaultVertexFormat.POSITION_TEX
                || format == DefaultVertexFormat.POSITION_TEX_COLOR;
        this.color = 0xFFFFFFFF;
        this.u = 0.0F;
        this.v = 0.0F;
    }

    public LegacyBufferBuilder vertex(Matrix4fc matrix, float x, float y, float z) {
        Vector4f transformed = matrix.transform(new Vector4f(x, y, z, 1.0F));
        this.x = transformed.x();
        this.y = transformed.y();
        this.z = transformed.z();
        return this;
    }

    public LegacyBufferBuilder vertex(Matrix4fc matrix, double x, double y, double z) {
        return this.vertex(matrix, (float) x, (float) y, (float) z);
    }

    public LegacyBufferBuilder vertex(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public LegacyBufferBuilder vertex(double x, double y, double z) {
        return this.vertex((float) x, (float) y, (float) z);
    }

    public LegacyBufferBuilder color(int red, int green, int blue, int alpha) {
        this.color = ((alpha & 0xFF) << 24) | ((red & 0xFF) << 16)
                | ((green & 0xFF) << 8) | (blue & 0xFF);
        return this;
    }

    public LegacyBufferBuilder color(float red, float green, float blue, float alpha) {
        return this.color(channel(red), channel(green), channel(blue), channel(alpha));
    }

    public LegacyBufferBuilder color(int argb) {
        this.color = argb;
        return this;
    }

    public LegacyBufferBuilder uv(float u, float v) {
        this.u = u;
        this.v = v;
        return this;
    }

    public LegacyBufferBuilder normal(float x, float y, float z) {
        return this;
    }

    public void endVertex() {
        this.vertices.add(new LegacyMesh.Vertex(this.x, this.y, this.z, this.color, this.u, this.v));
    }

    public LegacyMesh end() {
        LegacyMesh mesh = new LegacyMesh(this.topology, this.textured, List.copyOf(this.vertices));
        this.vertices.clear();
        return mesh;
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
    }
}
