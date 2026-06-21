package shit.zen.music.api;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import shit.zen.music.config.MusicConfig;

public class MusicRateLimiter {
    private static final int WINDOW_LIMIT = 50;
    private static final long WINDOW_MS = 5L * 60L * 1000L;
    private final Deque<Long> requestTimes = new ArrayDeque<>();
    private long lastRequestAt;

    public CompletableFuture<Void> acquireAsync(MusicConfig config, ScheduledExecutorService scheduler) {
        long delayMs = this.reserveDelay(config);
        if (delayMs <= 0L) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.schedule(() -> future.complete(null), delayMs, TimeUnit.MILLISECONDS);
        return future;
    }

    private synchronized long reserveDelay(MusicConfig config) {
        long interval = config == null ? 1000L : config.getRequestIntervalMs();
        long now = System.currentTimeMillis();
        while (!this.requestTimes.isEmpty() && now - this.requestTimes.peekFirst() >= WINDOW_MS) {
            this.requestTimes.removeFirst();
        }
        long intervalReadyAt = this.lastRequestAt + interval;
        long windowReadyAt = this.requestTimes.size() >= WINDOW_LIMIT ? this.requestTimes.peekFirst() + WINDOW_MS : now;
        long scheduledAt = Math.max(now, Math.max(intervalReadyAt, windowReadyAt));
        this.lastRequestAt = scheduledAt;
        this.requestTimes.addLast(scheduledAt);
        return Math.max(0L, scheduledAt - now);
    }
}
