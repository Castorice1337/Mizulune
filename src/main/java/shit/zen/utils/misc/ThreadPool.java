package shit.zen.utils.misc;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ThreadPool {
    private static final Logger LOGGER = LogManager.getLogger(ThreadPool.class);

    private static class ExecutorFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "Mizulune-Executor-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }

    private static class SchedulerFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "Mizulune-Scheduler-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, new SchedulerFactory());
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            1,
            4,
            30L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(256),
            new ExecutorFactory(),
            new ThreadPoolExecutor.DiscardOldestPolicy());

    public static void scheduleAtFixedRate(Runnable runnable, long initial, long period, TimeUnit unit) {
        scheduler.scheduleAtFixedRate(wrap(runnable), initial, period, unit);
    }

    public static ScheduledFuture<?> scheduleWithDelay(Runnable runnable, long delay, TimeUnit unit) {
        return scheduler.schedule(wrap(runnable), delay, unit);
    }

    public static void submit(Runnable runnable) {
        EXECUTOR.execute(wrap(runnable));
    }

    public static void shutdown() {
        scheduler.shutdownNow();
        EXECUTOR.shutdownNow();
    }

    private static Runnable wrap(Runnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                LOGGER.error("Async task failed", throwable);
            }
        };
    }
}
