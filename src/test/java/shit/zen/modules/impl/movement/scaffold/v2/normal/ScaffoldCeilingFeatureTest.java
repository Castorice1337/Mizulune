package shit.zen.modules.impl.movement.scaffold.v2.normal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class ScaffoldCeilingFeatureTest {
    @Test
    void defaultIsDisabledEvenWhenCeilingCanBeConstructed() {
        BlockPos base = new BlockPos(2, 70, -4);
        ScaffoldCeilingFeature.Decision decision = ScaffoldCeilingFeature.decide(
                ScaffoldCeilingFeature.DEFAULTS,
                base,
                false);

        assertFalse(ScaffoldCeilingFeature.DEFAULTS.enabled());
        assertTrue(decision.canConstructCeiling());
        assertFalse(decision.active());
        assertEquals(base, decision.targetedPosition());
    }

    @Test
    void enabledFeatureTargetsThreeBlocksAboveWhenBlockBelowIsNotAir() {
        BlockPos base = new BlockPos(2, 70, -4);
        ScaffoldCeilingFeature.Decision decision = ScaffoldCeilingFeature.decide(
                new ScaffoldCeilingFeature.Settings(true),
                base,
                false);

        assertTrue(decision.active());
        assertEquals(new BlockPos(2, 73, -4), decision.targetedPosition());
    }

    @Test
    void airBelowPreventsCeilingConstruction() {
        ScaffoldCeilingFeature.Decision decision = ScaffoldCeilingFeature.decide(
                new ScaffoldCeilingFeature.Settings(true),
                BlockPos.ZERO,
                true);

        assertFalse(decision.canConstructCeiling());
        assertFalse(decision.active());
        assertEquals(BlockPos.ZERO, decision.targetedPosition());
    }
}
