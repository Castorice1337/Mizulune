package shit.zen.modules.impl.movement.scaffold.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class ScaffoldMovementPredictionTest {
    private static final double EPSILON = 1.0E-6;

    @Test
    void edgeCollisionReturnsEndOfConnectedSupport() {
        Vec3 from = new Vec3(0.5, 0.0, 0.5);
        Vec3 to = new Vec3(3.0, 0.0, 0.5);
        List<AABB> supports = List.of(
                new AABB(-0.3, -1.0, -0.3, 1.3, 1.55, 1.3),
                new AABB(0.7, -1.0, -0.3, 2.3, 1.55, 1.3));

        Vec3 edge = ScaffoldMovementPrediction.findEdgeCollision(from, to, supports);

        assertEquals(2.3, edge.x, EPSILON);
        assertEquals(0.0, edge.y, EPSILON);
        assertEquals(0.5, edge.z, EPSILON);
    }

    @Test
    void edgeCollisionReturnsNullWhenDestinationRemainsSupported() {
        Vec3 from = new Vec3(0.5, 0.0, 0.5);
        Vec3 to = new Vec3(1.0, 0.0, 0.5);
        AABB support = new AABB(-0.3, -1.0, -0.3, 1.3, 1.55, 1.3);

        assertNull(ScaffoldMovementPrediction.findEdgeCollision(from, to, List.of(support)));
    }
}
