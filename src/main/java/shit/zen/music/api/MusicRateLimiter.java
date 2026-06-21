package shit.zen.music.api;

import java.util.ArrayDeque;
import java.util.Deque;
import shit.zen.music.config.MusicConfig;

public class MusicRateLimiter {
    private static final int WINDOW_LIMIT = 50;
    private static final long WINDOW_MS = 5L * 60L * 1000L;
    private final Deque<Long> requestTimes = new ArrayDeque<>();
    private long lastRequestAt;

    public void awaitTurn(MusicConfig config) {
        long interval = config == null ? 1000L : config.getRequestIntervalMs();
        while (true) {
            long waitMs;
            synchronized (this) {
                long now = System.currentTimeMillis();
                while (!this.requestTimes.isEmpty() && now - this.requestTimes.peekFirst() >= WINDOW_MS) {
                    this.requestTimes.removeFirst();
                }
                long intervalWait = Math.max(0L, this.lastRequestAt + interval - now);
                long windowWait = this.requestTimes.size() >= WINDOW_LIMIT
                        ? Math.max(0L, this.requestTimes.peekFirst() + WINDOW_MS - now)
                        : 0L;
                waitMs = Math.max(intervalWait, windowWait);
                if (waitMs <= 0L) {
                    this.lastRequestAt = now;
                    this.requestTimes.addLast(now);
                    return;
                }
            }
            try {
                Thread.sleep(Math.min(waitMs, 1000L));
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new MusicApiException("API rate limited. Please wait before searching again.", interruptedException);
            }
        }
    }
}
