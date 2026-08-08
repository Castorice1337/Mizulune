package shit.zen.modules.impl.movement.scaffold.v2.normal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ScaffoldHeadHitterFeatureTest {
    @Test
    void defaultIsDisabledButEnvironmentTriggerRemainsObservable() {
        ScaffoldHeadHitterFeature.Decision decision = ScaffoldHeadHitterFeature.decide(
                ScaffoldHeadHitterFeature.DEFAULTS,
                new ScaffoldHeadHitterFeature.Frame(false, true, true));

        assertFalse(ScaffoldHeadHitterFeature.DEFAULTS.enabled());
        assertTrue(decision.canHeadHit());
        assertFalse(decision.active());
        assertEquals(ScaffoldHeadHitterFeature.MotionAction.NONE, decision.action());
    }

    @Test
    void activeMovingPlayerRequestsJumpFromGround() {
        ScaffoldHeadHitterFeature.Decision decision = ScaffoldHeadHitterFeature.decide(
                new ScaffoldHeadHitterFeature.Settings(true),
                new ScaffoldHeadHitterFeature.Frame(false, true, true));

        assertTrue(decision.active());
        assertTrue(decision.shouldJumpFromGround());
        assertEquals(
                ScaffoldHeadHitterFeature.MotionAction.JUMP_FROM_GROUND,
                decision.action());
    }

    @Test
    void movementAndCollisionRequirementsAreIndependent() {
        ScaffoldHeadHitterFeature.Settings settings =
                new ScaffoldHeadHitterFeature.Settings(true);
        ScaffoldHeadHitterFeature.Decision stationary = ScaffoldHeadHitterFeature.decide(
                settings,
                new ScaffoldHeadHitterFeature.Frame(false, true, false));
        ScaffoldHeadHitterFeature.Decision airAbove = ScaffoldHeadHitterFeature.decide(
                settings,
                new ScaffoldHeadHitterFeature.Frame(true, true, true));
        ScaffoldHeadHitterFeature.Decision airborne = ScaffoldHeadHitterFeature.decide(
                settings,
                new ScaffoldHeadHitterFeature.Frame(false, false, true));

        assertTrue(stationary.canHeadHit());
        assertFalse(stationary.shouldJumpFromGround());
        assertFalse(airAbove.canHeadHit());
        assertFalse(airborne.canHeadHit());
    }
}
