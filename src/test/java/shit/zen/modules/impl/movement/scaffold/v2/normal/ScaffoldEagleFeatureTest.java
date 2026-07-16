package shit.zen.modules.impl.movement.scaffold.v2.normal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import shit.zen.utils.game.DirectionalInput;

final class ScaffoldEagleFeatureTest {
    private static final DirectionalInput FORWARD =
            new DirectionalInput(true, false, false, false);

    @Test
    void defaultsMatchLiquidSource() {
        ScaffoldEagleFeature.Settings settings = ScaffoldEagleFeature.DEFAULTS;

        assertFalse(settings.enabled());
        assertEquals(new ScaffoldEagleFeature.BlocksToEagleRange(0, 0), settings.blocksToEagle());
        assertEquals(0.01, settings.edgeDistance(), 1.0e-9);
        assertTrue(settings.onlyOnGround());
        assertEquals(new ScaffoldEagleFeature.State(0, 0), ScaffoldEagleFeature.DEFAULT_STATE);
    }

    @Test
    void downAndGroundChecksShortCircuitTheEdgeProbe() {
        ScaffoldEagleFeature.Settings settings = settings(0, true);
        AtomicInteger probes = new AtomicInteger();
        ScaffoldEagleFeature.EdgeProbe probe = (input, distance) -> {
            probes.incrementAndGet();
            return true;
        };

        ScaffoldEagleFeature.Decision down = ScaffoldEagleFeature.decide(
                settings,
                ScaffoldEagleFeature.DEFAULT_STATE,
                frame(false, true, true, false),
                probe);
        ScaffoldEagleFeature.Decision airborne = ScaffoldEagleFeature.decide(
                settings,
                ScaffoldEagleFeature.DEFAULT_STATE,
                frame(false, false, false, false),
                probe);

        assertFalse(down.shouldEagle());
        assertFalse(airborne.shouldEagle());
        assertEquals(0, probes.get());
    }

    @Test
    void edgeDistanceAndOnlyOnGroundAreAppliedWithoutMutatingInput() {
        ScaffoldEagleFeature.Settings settings = new ScaffoldEagleFeature.Settings(
                true,
                new ScaffoldEagleFeature.BlocksToEagleRange(0, 0),
                0.37,
                false);
        ScaffoldEagleFeature.Decision decision = ScaffoldEagleFeature.decide(
                settings,
                ScaffoldEagleFeature.DEFAULT_STATE,
                frame(false, false, false, false),
                (input, distance) -> input.equals(FORWARD) && distance == 0.37);

        assertTrue(decision.edgeChecked());
        assertTrue(decision.shouldEagle());
        assertTrue(decision.sneak());
    }

    @Test
    void blocksToEagleUsesStrictGreaterThanAndRefreshesCycle() {
        ScaffoldEagleFeature.Settings settings = settings(2, true);
        Random random = new Random(1L);
        ScaffoldEagleFeature.State state = ScaffoldEagleFeature.reset(settings, random);

        assertTrue(ScaffoldEagleFeature.decide(
                settings,
                state,
                frame(false, false, true, false),
                (input, distance) -> true).shouldEagle());

        ScaffoldEagleFeature.PlacementTransition first =
                ScaffoldEagleFeature.onBlockPlacement(settings, state, random);
        ScaffoldEagleFeature.PlacementTransition second =
                ScaffoldEagleFeature.onBlockPlacement(settings, first.state(), random);
        ScaffoldEagleFeature.PlacementTransition third =
                ScaffoldEagleFeature.onBlockPlacement(settings, second.state(), random);

        assertEquals(new ScaffoldEagleFeature.State(1, 2), first.state());
        assertFalse(first.refreshed());
        assertEquals(new ScaffoldEagleFeature.State(2, 2), second.state());
        assertFalse(second.refreshed());
        assertEquals(new ScaffoldEagleFeature.State(0, 2), third.state());
        assertTrue(third.refreshed());
    }

    @Test
    void liquidSourceBoundsAreValidated() {
        assertThrows(IllegalArgumentException.class,
                () -> new ScaffoldEagleFeature.BlocksToEagleRange(-1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ScaffoldEagleFeature.BlocksToEagleRange(0, 11));
        assertThrows(IllegalArgumentException.class,
                () -> new ScaffoldEagleFeature.Settings(
                        true,
                        new ScaffoldEagleFeature.BlocksToEagleRange(0, 0),
                        Math.nextDown(0.01),
                        true));
    }

    private static ScaffoldEagleFeature.Settings settings(int blocks, boolean onlyOnGround) {
        return new ScaffoldEagleFeature.Settings(
                true,
                new ScaffoldEagleFeature.BlocksToEagleRange(blocks, blocks),
                0.01,
                onlyOnGround);
    }

    private static ScaffoldEagleFeature.Frame frame(
            boolean sneak,
            boolean down,
            boolean onGround,
            boolean flying) {
        return new ScaffoldEagleFeature.Frame(FORWARD, sneak, down, onGround, flying);
    }
}
