package shit.zen.modules.impl.movement.scaffold.v2.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

final class BlinkTest {
    private static final Blink.PacketContext OUTGOING_GROUND =
            new Blink.PacketContext(true, false, false, false, true);

    @Test
    void preservesLiquidBounceDefaults() {
        assertFalse(Blink.DEFAULTS.enabled());
        assertEquals(50, Blink.DEFAULTS.time().minimum());
        assertEquals(250, Blink.DEFAULTS.time().maximum());
        assertTrue(Blink.DEFAULTS.flushOn().isEmpty());
    }

    @Test
    void blockPlacementChangesPulseTimeWithoutResettingTimer() {
        Blink blink = new Blink();
        Blink.Settings settings = enabled(100, Set.of());

        assertEquals(Blink.Action.FLUSH, blink.decide(OUTGOING_GROUND, settings, 1L).action());
        assertEquals(1L, blink.lastResetMillis());
        blink.onBlockPlacement(100);

        assertEquals(Blink.Action.QUEUE, blink.decide(OUTGOING_GROUND, settings, 101L).action());
        Blink.Decision elapsed = blink.decide(OUTGOING_GROUND, settings, 102L);
        assertEquals(Blink.Action.FLUSH, elapsed.action());
        assertEquals(Blink.Reason.TIME, elapsed.reason());
        assertEquals(102L, blink.lastResetMillis());
    }

    @Test
    void selectedConditionFlushesBeforeTimeAndResetsTimer() {
        Blink blink = new Blink();
        Blink.Settings settings = enabled(250, Set.of(Blink.FlushOn.PLACE));
        blink.decide(OUTGOING_GROUND, settings, 1L);
        blink.onBlockPlacement(250);

        Blink.Decision decision = blink.decide(
                new Blink.PacketContext(true, true, false, false, true),
                settings,
                2L);

        assertEquals(Blink.Action.FLUSH, decision.action());
        assertEquals(Blink.Reason.CONDITION, decision.reason());
        assertEquals(Set.of(Blink.FlushOn.PLACE), decision.matchingTriggers());
        assertEquals(2L, blink.lastResetMillis());
    }

    @Test
    void strictChronometerComparisonQueuesAtExactBoundary() {
        Blink blink = new Blink();
        Blink.Settings settings = enabled(50, Set.of());
        blink.decide(OUTGOING_GROUND, settings, 100L);
        blink.onBlockPlacement(50);

        assertEquals(Blink.Action.QUEUE, blink.decide(OUTGOING_GROUND, settings, 150L).action());
        assertEquals(Blink.Action.FLUSH, blink.decide(OUTGOING_GROUND, settings, 151L).action());
    }

    @Test
    void disabledAndIncomingPathsKeepDefaultFlushWithoutTouchingTimer() {
        Blink blink = new Blink();
        Blink.Decision disabled = blink.decide(OUTGOING_GROUND, Blink.DEFAULTS, 100L);
        Blink.Decision incoming = blink.decide(
                new Blink.PacketContext(false, false, false, false, true),
                enabled(50, Set.of()),
                100L);

        assertEquals(Blink.Reason.DISABLED, disabled.reason());
        assertEquals(Blink.Reason.NON_OUTGOING, incoming.reason());
        assertEquals(0L, blink.lastResetMillis());
    }

    private static Blink.Settings enabled(int fixedTime, Set<Blink.FlushOn> flushOn) {
        return new Blink.Settings(true, new Blink.TimeRange(fixedTime, fixedTime), flushOn);
    }
}
