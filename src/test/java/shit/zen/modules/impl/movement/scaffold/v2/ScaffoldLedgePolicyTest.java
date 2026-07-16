package shit.zen.modules.impl.movement.scaffold.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import shit.zen.utils.game.DirectionalInput;

final class ScaffoldLedgePolicyTest {
    private final ScaffoldLedgePolicy policy = new ScaffoldLedgePolicy();

    @Test
    void rotationEtaForcesSneakBeforeExtensionCanRun() {
        AtomicBoolean extensionCalled = new AtomicBoolean();

        LedgeAction action = this.policy.evaluate(
                true,
                64,
                () -> 3,
                () -> {
                    extensionCalled.set(true);
                    return new LedgeAction(true, 0, false, false);
                });

        assertEquals(new LedgeAction(false, 3, false, false), action);
        assertFalse(extensionCalled.get());
    }

    @Test
    void noBlocksAlwaysProtectsForAtLeastOneTick() {
        assertEquals(
                new LedgeAction(false, 1, false, false),
                this.policy.evaluate(true, 0, 0, LedgeAction.NO_LEDGE));
        assertEquals(
                new LedgeAction(false, 1, false, false),
                this.policy.evaluate(true, -1, -4, LedgeAction.NO_LEDGE));
    }

    @Test
    void readyRotationDelegatesToTechniqueAction() {
        LedgeAction extension = new LedgeAction(true, 2, true, true);

        assertEquals(extension, this.policy.evaluate(true, 8, 0, extension));
    }

    @Test
    void nonEdgeSkipsRotationEtaAndAppliesActionInLiquidBounceOrder() {
        AtomicBoolean etaCalled = new AtomicBoolean();
        LedgeAction action = this.policy.evaluate(
                false,
                0,
                () -> {
                    etaCalled.set(true);
                    return 12;
                },
                () -> new LedgeAction(false, 0, true, true));

        assertFalse(etaCalled.get());
        assertEquals(
                new DirectionalInput(false, true, false, false),
                action.applyInput(new DirectionalInput(true, false, true, false)));
    }

    @Test
    void forcedSneakPersistsAndLongerRequestsRefreshTheCountdown() {
        assertTrue(this.policy.requestForcedSneak(3));
        assertEquals(3, this.policy.forcedSneakTicks());

        assertTrue(this.policy.consumeForcedSneak());
        assertEquals(2, this.policy.forcedSneakTicks());
        assertFalse(this.policy.requestForcedSneak(1));

        assertTrue(this.policy.requestForcedSneak(4));
        assertEquals(4, this.policy.forcedSneakTicks());
        this.policy.reset();
        assertEquals(0, this.policy.forcedSneakTicks());
        assertFalse(this.policy.consumeForcedSneak());
    }
}
