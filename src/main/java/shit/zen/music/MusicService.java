package shit.zen.music;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shit.zen.music.api.MusicApiClient;
import shit.zen.music.api.MusicRateLimiter;
import shit.zen.music.api.MusicSearchCache;
import shit.zen.music.cache.MusicCacheManager;
import shit.zen.music.config.MusicConfig;
import shit.zen.music.config.MusicConfigStore;
import shit.zen.music.engine.JavaSoundMp3PlayerEngine;
import shit.zen.music.lyrics.LyricParser;
import shit.zen.music.model.LyricLine;
import shit.zen.music.model.MusicPlaybackState;
import shit.zen.music.model.MusicTrack;
import shit.zen.music.model.PlayMode;
import shit.zen.music.playback.MusicPlaybackController;
import shit.zen.music.queue.MusicQueueManager;

public class MusicService {
    private static final Logger LOGGER = LogManager.getLogger(MusicService.class);
    private static final int COVER_SIZE = 300;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new MusicThreadFactory("Mizulune-Music-Scheduler"));
    private final ExecutorService apiExecutor = Executors.newFixedThreadPool(2, new MusicThreadFactory("Mizulune-Music-Api"));
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(3, new MusicThreadFactory("Mizulune-Music-Download"));
    private final ExecutorService playbackExecutor = Executors.newSingleThreadExecutor(new MusicThreadFactory("Mizulune-Music-Playback"));
    private final ExecutorService queueSaveExecutor = Executors.newSingleThreadExecutor(new MusicThreadFactory("Mizulune-Music-QueueSave"));
    private final MusicCacheManager cacheManager = new MusicCacheManager();
    private final MusicQueueManager queueManager = new MusicQueueManager(this.cacheManager, this.queueSaveExecutor);
    private final JavaSoundMp3PlayerEngine engine = new JavaSoundMp3PlayerEngine();
    private final MusicApiClient apiClient;
    private final MusicPlaybackController playbackController;
    private final Map<String, CompletableFuture<Path>> coverTasks = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<List<LyricLine>>> lyricTasks = new ConcurrentHashMap<>();
    private volatile MusicConfig config = MusicConfigStore.get();
    private volatile String lastMessage = "";

    public MusicService() {
        MusicRateLimiter rateLimiter = new MusicRateLimiter();
        MusicSearchCache searchCache = new MusicSearchCache();
        this.apiClient = new MusicApiClient(this::config, this.apiExecutor, this.scheduler, rateLimiter, searchCache);
        this.playbackController = new MusicPlaybackController(this.apiClient, this.cacheManager,
                this.queueManager, this.engine, this::config, this.playbackExecutor, this.downloadExecutor);
        this.engine.setVolume(this.config.getVolume());
        this.engine.setErrorCallback(message -> {
            this.lastMessage = message == null ? "Unable to play this track." : message;
            LOGGER.warn("[MizuluneMusic][engine] playback callback error={}", this.lastMessage);
            this.playbackController.markEngineError(this.lastMessage);
        });
        this.engine.setFinishedCallback(() -> {
            if (this.queueManager.getPlayMode() == PlayMode.SINGLE) {
                this.playbackController.replayCurrent();
            } else if (this.playbackController.isCurrentFromQueue()) {
                this.playbackController.next(false);
            } else {
                this.playbackController.finishCurrent("Finished");
            }
        });
        if (this.queueManager.getPlayMode() != this.config.getPlayMode()) {
            this.queueManager.setPlayMode(this.config.getPlayMode());
        }
        LOGGER.info("[MizuluneMusic] initialized api={} cacheRoot={} source={} bitrate={}",
                this.config.getApiBaseUrl(), this.cacheManager.getRoot(),
                this.config.getDefaultSource(), this.config.getPreferredBitrate());
    }

    public MusicConfig config() {
        return this.config;
    }

    public MusicCacheManager cacheManager() {
        return this.cacheManager;
    }

    public List<MusicTrack> queueSnapshot() {
        return this.queueManager.snapshot();
    }

    public int currentQueueIndex() {
        return this.queueManager.getCurrentIndex();
    }

    public MusicPlaybackState playbackState() {
        return this.playbackController.snapshot();
    }

    public String lastMessage() {
        return this.lastMessage;
    }

    public CompletableFuture<List<MusicTrack>> search(String source, SearchType type, String keyword) {
        SearchType effectiveType = type == null ? SearchType.ALL : type;
        String cleanKeyword = keyword == null ? "" : keyword.trim();
        if (cleanKeyword.isEmpty()) {
            this.lastMessage = "";
            return CompletableFuture.completedFuture(List.of());
        }
        if (!effectiveType.isSupported()) {
            this.lastMessage = effectiveType.displayName + " search is not supported by GD Music API.";
            return CompletableFuture.completedFuture(List.of());
        }
        String cleanSource = normalizeSource(source == null || source.isBlank() ? this.config.getDefaultSource() : source);
        String requestSource = effectiveType == SearchType.ALBUM ? cleanSource + "_album" : cleanSource;
        this.lastMessage = "Searching...";
        return this.apiClient.search(requestSource, cleanKeyword, this.config.getSearchPageSize(), 1)
                .thenApply(tracks -> {
                    this.lastMessage = tracks.isEmpty() ? "No results found." : "";
                    return tracks;
                })
                .exceptionally(throwable -> {
                    LOGGER.warn("Music search failed", throwable);
                    this.lastMessage = userMessage(throwable);
                    return List.of();
                });
    }

    public int addToQueue(MusicTrack track) {
        int index = this.queueManager.add(track);
        if (index >= 0) {
            this.lastMessage = "Added to queue";
        }
        return index;
    }

    public CompletableFuture<Void> playTrack(MusicTrack track) {
        this.lastMessage = "Preparing track...";
        return this.playbackController.playTrack(track, false);
    }

    public CompletableFuture<Void> cacheAndPlayTrack(MusicTrack track) {
        this.lastMessage = "Caching track...";
        return this.playbackController.playTrack(track, true);
    }

    public CompletableFuture<Void> playQueueIndex(int index) {
        this.lastMessage = "Preparing track...";
        return this.playbackController.playQueueIndex(index);
    }

    public CompletableFuture<Void> next() {
        return this.playbackController.next(true);
    }

    public CompletableFuture<Void> previous() {
        return this.playbackController.previous();
    }

    public void togglePlay() {
        this.playbackController.togglePlay();
    }

    public void seek(long positionMs) {
        this.playbackController.seek(positionMs);
    }

    public void setVolume(float volume) {
        this.setVolume(volume, true);
    }

    public void setVolume(float volume, boolean save) {
        this.config.setVolume(volume);
        this.playbackController.setVolume(this.config.getVolume());
        MusicConfigStore.update(this.config, save);
    }

    public void setPlayMode(PlayMode playMode) {
        this.config.setPlayMode(playMode);
        if (this.queueManager.getPlayMode() != this.config.getPlayMode()) {
            this.queueManager.setPlayMode(this.config.getPlayMode());
        }
        MusicConfigStore.update(this.config, true);
    }

    public void cyclePlayMode() {
        this.setPlayMode(this.queueManager.getPlayMode().next());
    }

    public void removeQueueIndex(int index) {
        boolean removingCurrent = index == this.queueManager.getCurrentIndex();
        this.queueManager.remove(index);
        if (removingCurrent) {
            this.playbackController.stop();
        }
        this.lastMessage = "Removed from queue";
    }

    public void clearQueue() {
        this.queueManager.clear();
        this.playbackController.stop();
        this.lastMessage = "Queue cleared";
    }

    public CompletableFuture<Path> cover(MusicTrack track) {
        if (track == null || !track.hasCover()) {
            return CompletableFuture.completedFuture(null);
        }
        Path cached = this.cacheManager.coverFile(track, COVER_SIZE);
        if (this.cacheManager.isUsableImage(cached)) {
            return CompletableFuture.completedFuture(cached);
        }
        return this.coverTasks.computeIfAbsent(track.stableKey(), ignored ->
                this.apiClient.getPic(normalizeSource(track.getSource()), track.getPicId(), COVER_SIZE)
                        .thenApplyAsync(result -> this.cacheManager.downloadCover(track, result.getUrl(), COVER_SIZE), this.downloadExecutor)
                        .whenComplete((path, throwable) -> {
                            if (throwable != null || path == null) {
                                this.coverTasks.remove(track.stableKey());
                            }
                        })
                        .exceptionally(throwable -> {
                            LOGGER.debug("Failed to load cover for {}", track.stableKey(), throwable);
                            return null;
                        }));
    }

    public CompletableFuture<List<LyricLine>> lyrics(MusicTrack track) {
        if (track == null || !track.hasLyric()) {
            return CompletableFuture.completedFuture(List.of());
        }
        String cachedLyric = this.cacheManager.readLyric(track);
        if (!cachedLyric.isBlank()) {
            return CompletableFuture.completedFuture(LyricParser.parse(cachedLyric));
        }
        return this.lyricTasks.computeIfAbsent(track.stableKey(), ignored ->
                this.apiClient.getLyric(normalizeSource(track.getSource()), track.getLyricId())
                        .thenApplyAsync(result -> {
                            this.cacheManager.writeLyric(track, result.getLyric());
                            return LyricParser.parse(result.getLyric());
                        }, this.downloadExecutor)
                        .whenComplete((lines, throwable) -> {
                            if (throwable != null || lines == null || lines.isEmpty()) {
                                this.lyricTasks.remove(track.stableKey());
                            }
                        })
                        .exceptionally(throwable -> {
                            LOGGER.debug("Failed to load lyrics for {}", track.stableKey(), throwable);
                            return List.of();
                        }));
    }

    public boolean isCached(MusicTrack track) {
        return track != null && this.cacheManager.isCached(track);
    }

    public void acceptDisclaimer() {
        this.config.acceptDisclaimer();
        MusicConfigStore.update(this.config, true);
    }

    public void reloadFromConfig(MusicConfig config) {
        this.config = config == null ? MusicConfig.defaults() : config;
        this.cacheManager.refreshRoot();
        if (this.queueManager.getPlayMode() != this.config.getPlayMode()) {
            this.queueManager.setPlayMode(this.config.getPlayMode());
        }
        this.engine.setVolume(this.config.getVolume());
    }

    public void shutdown() {
        try {
            this.playbackController.stop();
            this.queueManager.save();
            if (this.config.shouldClearTemporaryCacheOnExit()) {
                this.cacheManager.clearTemporaryCache();
            }
        } finally {
            this.scheduler.shutdownNow();
            this.apiExecutor.shutdownNow();
            this.downloadExecutor.shutdownNow();
            this.playbackExecutor.shutdownNow();
            this.queueSaveExecutor.shutdownNow();
        }
    }

    public static String normalizeSource(String source) {
        String value = source == null || source.isBlank() ? "netease" : source.trim().toLowerCase(Locale.ROOT);
        return value.endsWith("_album") ? value.substring(0, value.length() - "_album".length()) : value;
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
        return "Music request failed.";
    }

    public enum SearchType {
        ALL("All", true),
        SINGLE("Single", true),
        ALBUM("Album", true),
        ARTIST("Artist", false),
        PLAYLIST("Playlist", false);

        private final String displayName;
        private final boolean supported;

        SearchType(String displayName, boolean supported) {
            this.displayName = displayName;
            this.supported = supported;
        }

        public String displayName() {
            return this.displayName;
        }

        public boolean isSupported() {
            return this.supported;
        }
    }

    private static final class MusicThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger();

        private MusicThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, this.prefix + "-" + this.counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
