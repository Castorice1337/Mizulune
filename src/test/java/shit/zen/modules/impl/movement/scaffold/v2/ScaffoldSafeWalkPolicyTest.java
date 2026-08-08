package shit.zen.modules.impl.movement.scaffold.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.utils.game.DirectionalInput;

final class ScaffoldSafeWalkPolicyTest {
    private static final DirectionalInput FORWARD =
            new DirectionalInput(true, false, false, false);

    @Test
    void noneAndSafeOnlyChangeSafeWalkFlag() {
        ScaffoldSafeWalkPolicy policy = new ScaffoldSafeWalkPolicy();
        ScaffoldSafeWalkPolicy.Frame frame = frame(
                FORWARD,
                true,
                true,
                true,
                true,
                0.2,
                new Vec3(0.5, 0.0, 0.5),
                new Vec3(0.7, 0.0, 0.5),
                new Vec3(0.5, 0.0, 0.5),
                true);

        ScaffoldSafeWalkPolicy.Decision none = policy.update(
                ScaffoldSafeWalkPolicy.Settings.none(),
                frame);
        ScaffoldSafeWalkPolicy.Decision safe = policy.update(
                ScaffoldSafeWalkPolicy.Settings.safe(),
                frame);

        assertEquals(FORWARD, none.directionalInput());
        assertTrue(none.jump());
        assertTrue(none.sneak());
        assertFalse(none.safeWalk());
        assertEquals(FORWARD, safe.directionalInput());
        assertTrue(safe.jump());
        assertTrue(safe.sneak());
        assertTrue(safe.safeWalk());
    }

    @Test
    void stopAndSneakKeepRunningForConfiguredTicks() {
        ScaffoldSafeWalkPolicy policy = new ScaffoldSafeWalkPolicy();
        ScaffoldSafeWalkPolicy.Settings settings = onEdge(
                ScaffoldSafeWalkPolicy.OnEdgeMode.STOP,
                2,
                2,
                false);

        ScaffoldSafeWalkPolicy.Decision first = policy.update(
                settings,
                frame(
                        FORWARD,
                        true,
                        false,
                        true,
                        true,
                        0.04,
                        new Vec3(0.7, 0.0, 0.5),
                        new Vec3(0.8, 0.0, 0.5),
                        new Vec3(0.5, 0.0, 0.5),
                        true));
        ScaffoldSafeWalkPolicy.Decision second = policy.update(
                settings,
                frame(
                        FORWARD,
                        true,
                        false,
                        true,
                        false,
                        0.04,
                        new Vec3(0.8, 0.0, 0.5),
                        new Vec3(0.9, 0.0, 0.5),
                        new Vec3(0.5, 0.0, 0.5),
                        true));
        ScaffoldSafeWalkPolicy.Decision third = policy.update(
                settings,
                frame(
                        FORWARD,
                        true,
                        false,
                        true,
                        false,
                        0.04,
                        new Vec3(0.9, 0.0, 0.5),
                        new Vec3(1.0, 0.0, 0.5),
                        new Vec3(0.5, 0.0, 0.5),
                        true));

        assertTrue(first.edgeTriggered());
        assertEquals(DirectionalInput.NONE, first.directionalInput());
        assertFalse(first.jump());
        assertTrue(first.sneak());
        assertEquals(1, first.keepTicksRemaining());
        assertEquals(1, first.sneakTicksRemaining());
        assertEquals(DirectionalInput.NONE, second.directionalInput());
        assertTrue(second.sneak());
        assertEquals(0, second.keepTicksRemaining());
        assertEquals(0, second.sneakTicksRemaining());
        assertEquals(FORWARD, third.directionalInput());
        assertTrue(third.jump());
        assertFalse(third.sneak());
    }

    @Test
    void invertCancelsJumpUnlessJumpOptionOverridesIt() {
        ScaffoldSafeWalkPolicy.Frame frame = frame(
                new DirectionalInput(true, false, true, false),
                true,
                false,
                true,
                true,
                0.04,
                new Vec3(0.7, 0.0, 0.5),
                new Vec3(0.8, 0.0, 0.5),
                new Vec3(0.5, 0.0, 0.5),
                true);

        ScaffoldSafeWalkPolicy.Decision cancelled = new ScaffoldSafeWalkPolicy().update(
                onEdge(ScaffoldSafeWalkPolicy.OnEdgeMode.INVERT, 1, 0, false),
                frame);
        ScaffoldSafeWalkPolicy.Decision overridden = new ScaffoldSafeWalkPolicy().update(
                onEdge(ScaffoldSafeWalkPolicy.OnEdgeMode.INVERT, 1, 0, true),
                frame);

        assertEquals(new DirectionalInput(false, true, false, true), cancelled.directionalInput());
        assertFalse(cancelled.jump());
        assertTrue(overridden.jump());
    }

    @Test
    void fastStopUsesCenterAndMovingTowardCenterFreezesState() {
        ScaffoldSafeWalkPolicy policy = new ScaffoldSafeWalkPolicy();
        ScaffoldSafeWalkPolicy.Settings settings = onEdge(
                ScaffoldSafeWalkPolicy.OnEdgeMode.STOP,
                2,
                0,
                false);
        Vec3 center = new Vec3(0.5, 0.0, 0.5);
        policy.update(
                settings,
                frame(
                        FORWARD,
                        false,
                        false,
                        true,
                        false,
                        0.0,
                        center,
                        center,
                        center,
                        true));

        ScaffoldSafeWalkPolicy.Decision away = policy.update(
                settings,
                frame(
                        FORWARD,
                        true,
                        false,
                        true,
                        true,
                        0.2,
                        new Vec3(0.8, 0.0, 0.5),
                        new Vec3(0.9, 0.0, 0.5),
                        center,
                        false));
        ScaffoldSafeWalkPolicy.Decision toward = policy.update(
                settings,
                frame(
                        FORWARD,
                        true,
                        false,
                        true,
                        true,
                        0.2,
                        new Vec3(0.8, 0.0, 0.5),
                        new Vec3(0.7, 0.0, 0.5),
                        center,
                        false));

        assertEquals(new DirectionalInput(false, false, false, true), away.directionalInput());
        assertTrue(away.jump());
        assertEquals(1, away.keepTicksRemaining());
        assertEquals(FORWARD, toward.directionalInput());
        assertTrue(toward.jump());
        assertEquals(1, toward.keepTicksRemaining());
    }

    @Test
    void missingNextPositionDoesNotSuppressEdgeAction() {
        ScaffoldSafeWalkPolicy policy = new ScaffoldSafeWalkPolicy();
        Vec3 center = new Vec3(0.5, 0.0, 0.5);
        policy.update(
                onEdge(ScaffoldSafeWalkPolicy.OnEdgeMode.STOP, 1, 0, false),
                frame(
                        FORWARD,
                        false,
                        false,
                        true,
                        false,
                        0.0,
                        center,
                        center,
                        center,
                        true));

        ScaffoldSafeWalkPolicy.Decision decision = policy.update(
                onEdge(ScaffoldSafeWalkPolicy.OnEdgeMode.STOP, 1, 0, false),
                frame(
                        FORWARD,
                        true,
                        false,
                        true,
                        true,
                        0.04,
                        new Vec3(0.8, 0.0, 0.5),
                        null,
                        center,
                        false));

        assertTrue(decision.edgeTriggered());
        assertEquals(DirectionalInput.NONE, decision.directionalInput());
        assertFalse(decision.jump());
    }

    @Test
    void edgeDistanceUsesTheLowerOfSpeedAndConfiguredDistance() {
        assertFalse(ScaffoldSafeWalkPolicy.isCloseToEdge(0.08, 0.08, 0.05, 0.1));
        assertTrue(ScaffoldSafeWalkPolicy.isCloseToEdge(0.08, 0.04, 0.05, 0.1));
        assertTrue(ScaffoldSafeWalkPolicy.isCloseToEdge(0.08, 0.08, 0.2, 0.1));
    }

    private static ScaffoldSafeWalkPolicy.Settings onEdge(
            ScaffoldSafeWalkPolicy.OnEdgeMode mode,
            int keepTicks,
            int sneakTicks,
            boolean jump) {
        return new ScaffoldSafeWalkPolicy.Settings(
                ScaffoldSafeWalkPolicy.Mode.ON_EDGE,
                0.1,
                ScaffoldSafeWalkPolicy.TickRange.fixed(keepTicks),
                mode,
                ScaffoldSafeWalkPolicy.TickRange.fixed(sneakTicks),
                jump);
    }

    private static ScaffoldSafeWalkPolicy.Frame frame(
            DirectionalInput input,
            boolean jump,
            boolean sneak,
            boolean onGround,
            boolean closeToEdge,
            double horizontalSpeed,
            Vec3 position,
            Vec3 nextPosition,
            Vec3 blockCenter,
            boolean blockCenterSafe) {
        return new ScaffoldSafeWalkPolicy.Frame(
                input,
                jump,
                sneak,
                onGround,
                closeToEdge,
                horizontalSpeed,
                position,
                nextPosition,
                0.0f,
                blockCenter,
                blockCenterSafe);
    }
}
