package shit.zen.music.playback;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shit.zen.music.api.MusicApiClient;
import shit.zen.music.cache.MusicCacheManager;
import shit.zen.music.config.MusicConfig;
import shit.zen.music.engine.MusicPlayerEngine;
import shit.zen.music.model.MusicPlaybackState;
import shit.zen.music.model.MusicTrack;
import shit.zen.music.queue.MusicQueueManager;

public class MusicPlaybackController {
    private static final Logger LOGGER = LogManager.getLogger(MusicPlaybackController.class);

    private final MusicApiClient apiClient;
    private final MusicCacheManager cacheManager;
    private final MusicQueueManager queueManager;
    private final MusicPlayerEngine engine;
    private final Supplier<MusicConfig> configSupplier;
    private final Executor playbackExecutor;
    private final Executor downloadExecutor;
    private final AtomicLong requestSequence = new AtomicLong();
    private volatile MusicTrack currentTrack;
    private volatile boolean currentFromQueue;
    private volatile boolean loading;
    private volatile boolean cached;
    private volatile String status = "No track playing";
    private volatile String error = "";

    public MusicPlaybackController(MusicApiClient apiClient, MusicCacheManager cacheManager,
                                   MusicQueueManager queueManager, MusicPlayerEngine engine,
                                   Supplier<MusicConfig> configSupplier, Executor playbackExecutor,
                                   Executor downloadExecutor) {
        this.apiClient = apiClient;
        this.cacheManager = cacheManager;
        this.queueManager = queueManager;
        this.engine = engine;
        this.configSupplier = configSupplier;
        this.playbackExecutor = playbackExecutor;
        this.downloadExecutor = downloadExecutor;
    }

    public CompletableFuture<Void> playTrack(MusicTrack track, boolean persistent) {
        if (track == null) {
            return CompletableFuture.completedFuture(null);
        }
        return this.prepareAndPlay(track, persistent, false);
    }

    public CompletableFuture<Void> playQueueIndex(int index) {
        MusicTrack track = this.queueManager.playIndex(index);
        if (track == null) {
            return CompletableFuture.completedFuture(null);
        }
        return this.prepareAndPlay(track, this.cacheManager.isCached(track), true);
    }

    public CompletableFuture<Void> replayCurrent() {
        MusicTrack track = this.currentFromQueue ? this.queueManager.current() : this.currentTrack;
        if (track == null) {
            return CompletableFuture.completedFuture(null);
        }
        return this.prepareAndPlay(track, this.cacheManager.isCached(track), this.currentFromQueue);
    }

    public CompletableFuture<Void> next(boolean manual) {
        MusicTrack next = this.queueManager.next(manual);
        if (next == null) {
            this.finishCurrent("Queue ended");
            return CompletableFuture.completedFuture(null);
        }
        return this.prepareAndPlay(next, this.cacheManager.isCached(next), true);
    }

    public CompletableFuture<Void> previous() {
        MusicTrack previous = this.queueManager.previous();
        if (previous == null) {
            return CompletableFuture.completedFuture(null);
        }
        return this.prepareAndPlay(previous, this.cacheManager.isCached(previous), true);
    }

    public void togglePlay() {
        if (this.currentTrack == null) {
            this.replayCurrent();
            return;
        }
        if (this.engine.isPlaying()) {
            this.engine.pause();
            this.status = "Paused";
        } else {
            this.engine.resume();
            this.status = "Playing";
        }
    }

    public void seek(long positionMs) {
        if (this.currentTrack != null) {
            this.engine.seek(Math.max(0L, positionMs));
        }
    }

    public void setVolume(float volume) {
        this.engine.setVolume(volume);
    }

    public void stop() {
        this.requestSequence.incrementAndGet();
        this.engine.stop();
        this.currentTrack = null;
        this.currentFromQueue = false;
        this.loading = false;
        this.cached = false;
        this.status = "Stopped";
        this.error = "";
    }

    public void markEngineError(String message) {
        this.currentTrack = null;
        this.currentFromQueue = false;
        this.loading = false;
        this.cached = false;
        this.status = "Playback failed";
        this.error = message == null || message.isBlank() ? "Unable to play this track." : message;
    }

    public boolean isCurrentFromQueue() {
        return this.currentFromQueue;
    }

    public void finishCurrent(String status) {
        this.requestSequence.incrementAndGet();
        this.engine.stop();
        this.currentTrack = null;
        this.currentFromQueue = false;
        this.loading = false;
        this.cached = false;
        this.status = status == null || status.isBlank() ? "Stopped" : status;
        this.error = "";
    }

    public MusicPlaybackState snapshot() {
        MusicConfig config = this.configSupplier.get();
        MusicTrack track = this.currentTrack != null ? this.currentTrack.copy() : null;
        long duration = this.engine.getDurationMs();
        if (duration <= 0L && track != null && track.getDurationMs() > 0L) {
            duration = track.getDurationMs();
        }
        return new MusicPlaybackState(track,
                this.engine.isPlaying(),
                this.loading,
                Math.max(0L, this.engine.getPositionMs()),
                Math.max(0L, duration),
                config.getVolume(),
                this.queueManager.getPlayMode(),
                this.cached,
                this.status,
                this.error);
    }

    private CompletableFuture<Void> prepareAndPlay(MusicTrack track, boolean persistent, boolean fromQueue) {
        long requestId = this.requestSequence.incrementAndGet();
        long startedAt = System.nanoTime();
        this.currentTrack = track.copy();
        this.currentFromQueue = fromQueue;
        this.loading = true;
        this.cached = this.cacheManager.isCached(track);
        this.status = persistent ? "Caching track..." : "Preparing track...";
        this.error = "";
        LOGGER.info("[MizuluneMusic][playback:{}] start track={} name=\"{}\" source={} persistent={} fromQueue={}",
                requestId, track.stableKey(), track.getName(), sourceForPlayback(track), persistent, fromQueue);
        return this.resolveAudioFile(track, persistent, requestId)
                .thenAcceptAsync(audioFile -> {
                    if (!this.isActive(requestId)) {
                        LOGGER.info("[MizuluneMusic][playback:{}] cancelled before engine start", requestId);
                        return;
                    }
                    this.currentTrack = track.copy();
                    this.currentFromQueue = fromQueue;
                    this.cached = this.cacheManager.isCached(track);
                    this.status = "Playing";
                    this.error = "";
                    LOGGER.info("[MizuluneMusic][playback:{}] engine start file={} bytes={} cached={}",
                            requestId, audioFile, fileSize(audioFile), this.cached);
                    this.engine.play(audioFile, track);
                }, this.playbackExecutor)
                .whenComplete((ignored, throwable) -> {
                    if (!this.isActive(requestId)) {
                        return;
                    }
                    this.loading = false;
                    if (throwable != null) {
                        LOGGER.warn("[MizuluneMusic][playback:{}] prepare failed track={} elapsedMs={}",
                                requestId, track.stableKey(), elapsedMs(startedAt), throwable);
                        this.currentTrack = null;
                        this.currentFromQueue = false;
                        this.cached = false;
                        this.error = userMessage(throwable);
                        this.status = "Playback failed";
                        this.engine.stop();
                    } else {
                        LOGGER.info("[MizuluneMusic][playback:{}] prepare complete elapsedMs={}",
                                requestId, elapsedMs(startedAt));
                    }
                });
    }

    private CompletableFuture<Path> resolveAudioFile(MusicTrack track, boolean requestedPersistent, long requestId) {
        Path cachedFile = this.cacheManager.cachedAudio(track);
        if (this.cacheManager.isUsableAudio(cachedFile)) {
            LOGGER.info("[MizuluneMusic][playback:{}] persistent cache hit file={} bytes={}",
                    requestId, cachedFile, fileSize(cachedFile));
            return CompletableFuture.completedFuture(cachedFile);
        }
        Path tempFile = this.cacheManager.tempAudio(track);
        MusicConfig config = this.configSupplier.get();
        boolean persistent = requestedPersistent && config.isCacheEnabled();
        if (!persistent && config.isTemporaryCacheEnabled() && this.cacheManager.isUsableAudio(tempFile)) {
            LOGGER.info("[MizuluneMusic][playback:{}] temporary cache hit file={} bytes={}",
                    requestId, tempFile, fileSize(tempFile));
            return CompletableFuture.completedFuture(tempFile);
        }
        boolean targetPersistent = persistent;
        String source = sourceForPlayback(track);
        LOGGER.info("[MizuluneMusic][playback:{}] request URL source={} id={} bitrate={}",
                requestId, source, track.getUrlId(), config.getPreferredBitrate());
        return this.apiClient.getUrl(source, track.getUrlId(), config.getPreferredBitrate())
                .thenApplyAsync(result -> {
                    LOGGER.info("[MizuluneMusic][playback:{}] URL resolved endpoint={} bitrate={} declaredBytes={} provider={}",
                            requestId, safeEndpoint(result.getUrl()), result.getBr(), result.getSize(), result.getFrom());
                    Path file = this.cacheManager.downloadAudio(track, result.getUrl(), targetPersistent, config.getPreferredBitrate());
                    LOGGER.info("[MizuluneMusic][playback:{}] download complete file={} bytes={} persistent={}",
                            requestId, file, fileSize(file), targetPersistent);
                    if (targetPersistent) {
                        this.cacheManager.enforcePersistentCacheLimit(config.getMaxCacheSizeMb());
                    }
                    return file;
                }, this.downloadExecutor);
    }

    private boolean isActive(long requestId) {
        return this.requestSequence.get() == requestId;
    }

    private static String userMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CompletionException && current.getCause() != null) {
                current = current.getCause();
                continue;
            }
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return "Playback failed.";
    }

    private static String sourceForPlayback(MusicTrack track) {
        String source = track.getSource();
        if (source == null || source.isBlank()) {
            return "netease";
        }
        return source.endsWith("_album") ? source.substring(0, source.length() - "_album".length()) : source;
    }

    private static long fileSize(Path path) {
        try {
            return path == null ? -1L : Files.size(path);
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private static String safeEndpoint(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost() == null ? "unknown-host" : uri.getHost();
            String path = uri.getPath() == null ? "" : uri.getPath();
            return uri.getScheme() + "://" + host + path;
        } catch (Exception ignored) {
            return "invalid-url";
        }
    }
}
