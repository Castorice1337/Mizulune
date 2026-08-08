package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class Id2CachedClockTest {
    @Test
    void initializesTheDaemonSampleFromTheWallClock() {
        long before = System.currentTimeMillis();
        long sample = Id2CachedClock.currentTimeMillis();
        long after = System.currentTimeMillis();

        assertTrue(sample >= before - 1_000L);
        assertTrue(sample <= after + 1_000L);
    }
}
