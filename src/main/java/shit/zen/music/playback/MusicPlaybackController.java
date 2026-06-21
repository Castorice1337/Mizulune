package shit.zen.music.playback;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
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
    private final Executor executor;
    private volatile MusicTrack currentTrack;
    private volatile boolean loading;
    private volatile boolean cached;
    private volatile String status = "No track playing";
    private volatile String error = "";

    public MusicPlaybackController(MusicApiClient apiClient, MusicCacheManager cacheManager,
                                   MusicQueueManager queueManager, MusicPlayerEngine engine,
                                   Supplier<MusicConfig> configSupplier, Executor executor) {
        this.apiClient = apiClient;
        this.cacheManager = cacheManager;
        this.queueManager = queueManager;
        this.engine = engine;
        this.configSupplier = configSupplier;
        this.executor = executor;
    }

    public CompletableFuture<Void> playTrack(MusicTrack track, boolean persistent) {
        if (track == null) {
            return CompletableFuture.completedFuture(null);
        }
        this.queueManager.addOrSelect(track);
        return this.prepareAndPlay(track, persistent);
    }

    public CompletableFuture<Void> playQueueIndex(int index) {
        MusicTrack track = this.queueManager.playIndex(index);
        if (track == null) {
            return CompletableFuture.completedFuture(null);
        }
        return this.prepareAndPlay(track, this.cacheManager.isCached(track));
    }

    public CompletableFuture<Void> replayCurrent() {
        MusicTrack track = this.queueManager.current();
        if (track == null) {
            track = this.currentTrack;
        }
        if (track == null) {
            return CompletableFuture.completedFuture(null);
        }
        return this.prepareAndPlay(track, this.cacheManager.isCached(track));
    }

    public CompletableFuture<Void> next(boolean manual) {
        MusicTrack next = this.queueManager.next(manual);
        if (next == null) {
            this.stopAtQueueEnd();
            return CompletableFuture.completedFuture(null);
        }
        return this.prepareAndPlay(next, this.cacheManager.isCached(next));
    }

    public CompletableFuture<Void> previous() {
        MusicTrack previous = this.queueManager.previous();
        if (previous == null) {
            return CompletableFuture.completedFuture(null);
        }
        return this.prepareAndPlay(previous, this.cacheManager.isCached(previous));
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
        this.engine.stop();
        this.loading = false;
        this.status = "Stopped";
    }

    public void markEngineError(String message) {
        this.loading = false;
        this.status = "Playback failed";
        this.error = message == null || message.isBlank() ? "Unable to play this track." : message;
    }

    public MusicPlaybackState snapshot() {
        MusicConfig config = this.configSupplier.get();
        MusicTrack track = this.currentTrack != null ? this.currentTrack.copy() : this.queueManager.current();
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

    private CompletableFuture<Void> prepareAndPlay(MusicTrack track, boolean persistent) {
        this.currentTrack = track.copy();
        this.loading = true;
        this.cached = this.cacheManager.isCached(track);
        this.status = persistent ? "Caching track..." : "Preparing track...";
        this.error = "";
        return CompletableFuture.runAsync(() -> {
            try {
                Path audioFile = this.resolveAudioFile(track, persistent);
                this.currentTrack = track.copy();
                this.cached = this.cacheManager.isCached(track);
                this.status = "Playing";
                this.error = "";
                this.engine.play(audioFile, track);
            } catch (Exception exception) {
                LOGGER.warn("Failed to play track {}", track.stableKey(), exception);
                this.error = userMessage(exception);
                this.status = "Playback failed";
                this.engine.stop();
            } finally {
                this.loading = false;
            }
        }, this.executor);
    }

    private Path resolveAudioFile(MusicTrack track, boolean persistent) {
        Path cachedFile = this.cacheManager.cachedAudio(track);
        if (Files.isRegularFile(cachedFile)) {
            return cachedFile;
        }
        Path tempFile = this.cacheManager.tempAudio(track);
        if (!persistent && Files.isRegularFile(tempFile)) {
            return tempFile;
        }
        MusicConfig config = this.configSupplier.get();
        String url = this.apiClient.getUrl(sourceForPlayback(track), track.getUrlId(), config.getPreferredBitrate()).join().getUrl();
        return this.cacheManager.downloadAudio(track, url, persistent, config.getPreferredBitrate());
    }

    private void stopAtQueueEnd() {
        this.engine.stop();
        this.currentTrack = null;
        this.loading = false;
        this.cached = false;
        this.status = "Queue ended";
        this.error = "";
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
}
