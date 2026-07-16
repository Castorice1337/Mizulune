package shit.zen.modules.impl.movement.scaffold.v2.normal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import shit.zen.modules.impl.movement.scaffold.v2.technique.Technique;

final class ScaffoldDownFeatureTest {
    @Test
    void defaultsAreDisabledAndKeepExistingDecisions() {
        ScaffoldDownFeature.State state = ScaffoldDownFeature.evaluate(
                ScaffoldDownFeature.DEFAULTS,
                true,
                true);

        assertFalse(ScaffoldDownFeature.DEFAULTS.enabled());
        assertFalse(state.shouldGoDown());
        assertFalse(state.shouldFallOffBlock());
        assertEquals(
                Technique.SearchOffsets.NORMAL,
                ScaffoldDownFeature.searchOffsets(Technique.SearchOffsets.NORMAL, state));
        assertEquals(new BlockPos(3, 70, -2), ScaffoldDownFeature.targetedPosition(
                new BlockPos(3, 70, -2),
                state));
        assertTrue(ScaffoldDownFeature.movementInput(true, state).sneak());
        assertTrue(ScaffoldDownFeature.safeWalk(true, state).safeWalk());
    }

    @Test
    void targetMovesDownBeforeFallOffSupportIsAvailable() {
        ScaffoldDownFeature.State state = ScaffoldDownFeature.evaluate(
                new ScaffoldDownFeature.Settings(true),
                true,
                false);

        assertTrue(state.shouldGoDown());
        assertFalse(state.shouldFallOffBlock());
        assertEquals(new BlockPos(3, 68, -2), ScaffoldDownFeature.targetedPosition(
                new BlockPos(3, 70, -2),
                state));
        assertTrue(ScaffoldDownFeature.considerFacingAwayFaces(state));
        assertEquals(
                Technique.SearchOffsets.DOWN,
                ScaffoldDownFeature.searchOffsets(Technique.SearchOffsets.NORMAL, state));
        assertTrue(ScaffoldDownFeature.movementInput(true, state).sneak());
        assertTrue(ScaffoldDownFeature.safeWalk(true, state).safeWalk());
    }

    @Test
    void standableBlockTwoBelowForcesSneakAndSafeWalkOff() {
        ScaffoldDownFeature.State state = ScaffoldDownFeature.evaluate(
                new ScaffoldDownFeature.Settings(true),
                true,
                true);
        ScaffoldDownFeature.MovementInputDecision input =
                ScaffoldDownFeature.movementInput(true, state);
        ScaffoldDownFeature.SafeWalkDecision safeWalk =
                ScaffoldDownFeature.safeWalk(true, state);

        assertTrue(state.shouldFallOffBlock());
        assertFalse(input.sneak());
        assertTrue(input.overridden());
        assertFalse(safeWalk.safeWalk());
        assertTrue(safeWalk.overridden());
    }
}
