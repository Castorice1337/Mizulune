package shit.zen.fabric.render;

import com.mojang.blaze3d.PrimitiveTopology;
import java.util.List;

/** Immutable CPU-side batch emitted by the 1.20 rendering facade. */
public record LegacyMesh(PrimitiveTopology topology, boolean textured, List<Vertex> vertices) {
    public record Vertex(float x, float y, float z, int color, float u, float v) {
    }
}
