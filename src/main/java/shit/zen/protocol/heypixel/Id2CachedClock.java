package shit.zen.protocol.heypixel;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Mirrors the official shared daemon-updated millisecond sample used by ID2 and ID3. */
final class Id2CachedClock {
    private static final long REFRESH_INTERVAL_MILLIS = 1L;
    private static final AtomicLong CURRENT = new AtomicLong(System.currentTimeMillis());
    private static final ScheduledExecutorService REFRESHER =
        Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "Mizulune-ID2-Clock");
            thread.setDaemon(true);
            return thread;
        });

    static {
        REFRESHER.scheduleAtFixedRate(
            () -> CURRENT.set(System.currentTimeMillis()),
            0L,
            REFRESH_INTERVAL_MILLIS,
            TimeUnit.MILLISECONDS
        );
    }

    private Id2CachedClock() {
    }

    static long currentTimeMillis() {
        return CURRENT.get();
    }
}
