package shit.zen.modules.impl.movement.scaffold.v2.technique;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class NewTellyTechniqueTest {
    @Test
    void freshSearchUsesExactThenSameLayerCardinalFallback() {
        NewTellyTechnique technique = new NewTellyTechnique();
        var freshOffsets = technique.targetOffsets(new Technique.TargetInput(0.0f));
        Technique.TargetOffset exactFresh = freshOffsets.get(0);
        Technique.TargetOffset cardinalFresh = freshOffsets.get(1);
        Technique.TargetOffset pending = technique.pendingTargetOffsets().get(0);

        assertEquals("New Telly", technique.name());
        assertEquals(2, freshOffsets.size());
        assertEquals(BlockPos.ZERO, exactFresh.offset());
        assertEquals(Technique.SearchOffsets.EXACT, exactFresh.searchOffsets());
        assertEquals(Technique.TargetPriority.POSITION, exactFresh.priority());
        assertEquals(Technique.AimMode.NEAREST_ROTATION, exactFresh.aimMode());
        assertEquals(BlockPos.ZERO, cardinalFresh.offset());
        assertEquals(Technique.SearchOffsets.CARDINAL, cardinalFresh.searchOffsets());
        assertEquals(Technique.TargetPriority.POSITION, cardinalFresh.priority());
        assertEquals(Technique.AimMode.NEAREST_ROTATION, cardinalFresh.aimMode());
        assertEquals(Technique.SearchOffsets.EXACT, pending.searchOffsets());
        assertEquals(Technique.TargetPriority.POSITION, pending.priority());
        assertEquals(Technique.AimMode.NEAREST_ROTATION, pending.aimMode());
    }
}
