package shit.zen.music.queue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shit.zen.music.cache.MusicCacheManager;
import shit.zen.music.model.MusicTrack;
import shit.zen.music.model.PlayMode;

public class MusicQueueManager {
    private static final Logger LOGGER = LogManager.getLogger(MusicQueueManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final MusicCacheManager cacheManager;
    private final Executor saveExecutor;
    private final Random random = new Random();
    private final List<MusicTrack> queue = new ArrayList<>();
    private final List<Integer> shuffleBag = new ArrayList<>();
    private int currentIndex = -1;
    private PlayMode playMode = PlayMode.ORDER;
    private boolean saveQueued;

    public MusicQueueManager(MusicCacheManager cacheManager) {
        this(cacheManager, null);
    }

    public MusicQueueManager(MusicCacheManager cacheManager, Executor saveExecutor) {
        this.cacheManager = cacheManager;
        this.saveExecutor = saveExecutor;
        this.load();
    }

    public synchronized List<MusicTrack> snapshot() {
        return this.queue.stream().map(MusicTrack::copy).toList();
    }

    public synchronized int getCurrentIndex() {
        return this.currentIndex;
    }

    public synchronized PlayMode getPlayMode() {
        return this.playMode;
    }

    public synchronized void setPlayMode(PlayMode playMode) {
        this.playMode = playMode == null ? PlayMode.ORDER : playMode;
        this.rebuildShuffleBag();
        this.requestSave();
    }

    public synchronized int add(MusicTrack track) {
        if (track == null) {
            return -1;
        }
        this.queue.add(track.copy());
        this.rebuildShuffleBag();
        this.requestSave();
        return this.queue.size() - 1;
    }

    public synchronized MusicTrack playIndex(int index) {
        if (index < 0 || index >= this.queue.size()) {
            return null;
        }
        this.currentIndex = index;
        this.removeFromShuffleBag(index);
        this.requestSave();
        return this.queue.get(index).copy();
    }

    public synchronized MusicTrack current() {
        if (this.currentIndex < 0 || this.currentIndex >= this.queue.size()) {
            return null;
        }
        return this.queue.get(this.currentIndex).copy();
    }

    public synchronized MusicTrack next(boolean manual) {
        if (this.queue.isEmpty()) {
            this.currentIndex = -1;
            this.requestSave();
            return null;
        }
        if (!manual && this.playMode == PlayMode.SINGLE) {
            return this.current();
        }
        if (this.playMode == PlayMode.SHUFFLE) {
            int next = this.nextShuffleIndex();
            this.currentIndex = next;
            this.requestSave();
            return this.queue.get(next).copy();
        }
        int next = this.currentIndex + 1;
        if (next >= this.queue.size()) {
            this.currentIndex = -1;
            this.requestSave();
            return null;
        }
        this.currentIndex = next;
        this.requestSave();
        return this.queue.get(this.currentIndex).copy();
    }

    public synchronized MusicTrack previous() {
        if (this.queue.isEmpty()) {
            this.currentIndex = -1;
            this.requestSave();
            return null;
        }
        if (this.currentIndex <= 0) {
            this.currentIndex = 0;
        } else {
            this.currentIndex--;
        }
        this.removeFromShuffleBag(this.currentIndex);
        this.requestSave();
        return this.queue.get(this.currentIndex).copy();
    }

    public synchronized void remove(int index) {
        if (index < 0 || index >= this.queue.size()) {
            return;
        }
        this.queue.remove(index);
        if (this.currentIndex == index) {
            this.currentIndex = -1;
        } else if (this.currentIndex > index) {
            this.currentIndex--;
        }
        if (this.queue.isEmpty()) {
            this.currentIndex = -1;
        }
        this.rebuildShuffleBag();
        this.requestSave();
    }

    public synchronized void clear() {
        this.queue.clear();
        this.shuffleBag.clear();
        this.currentIndex = -1;
        this.requestSave();
    }

    public synchronized void save() {
        this.saveNow();
    }

    private void requestSave() {
        if (this.saveExecutor == null) {
            this.saveNow();
            return;
        }
        if (this.saveQueued) {
            return;
        }
        this.saveQueued = true;
        this.saveExecutor.execute(() -> {
            synchronized (MusicQueueManager.this) {
                MusicQueueManager.this.saveQueued = false;
                MusicQueueManager.this.saveNow();
            }
        });
    }

    private void saveNow() {
        try {
            Path file = this.cacheManager.getQueueFile();
            Files.createDirectories(file.getParent());
            QueueFile queueFile = new QueueFile();
            queueFile.tracks = this.queue;
            queueFile.currentIndex = this.currentIndex;
            queueFile.playMode = this.playMode.name();
            Files.writeString(file, GSON.toJson(queueFile), StandardCharsets.UTF_8);
        } catch (IOException ioException) {
            LOGGER.warn("Failed to save music queue", ioException);
        }
    }

    public synchronized void load() {
        Path file = this.cacheManager.getQueueFile();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            QueueFile queueFile = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), QueueFile.class);
            this.queue.clear();
            if (queueFile != null && queueFile.tracks != null) {
                this.queue.addAll(queueFile.tracks.stream().map(MusicTrack::copy).toList());
                this.currentIndex = queueFile.currentIndex >= 0 && queueFile.currentIndex < this.queue.size()
                        ? queueFile.currentIndex
                        : -1;
                try {
                    this.playMode = queueFile.playMode == null ? PlayMode.ORDER : PlayMode.valueOf(queueFile.playMode);
                } catch (Exception ignored) {
                    this.playMode = PlayMode.ORDER;
                }
            }
            this.rebuildShuffleBag();
        } catch (Exception exception) {
            LOGGER.warn("Failed to load music queue", exception);
        }
    }

    private int indexOf(MusicTrack track) {
        for (int i = 0; i < this.queue.size(); i++) {
            if (this.queue.get(i).equals(track)) {
                return i;
            }
        }
        return -1;
    }

    private int nextShuffleIndex() {
        if (this.shuffleBag.isEmpty()) {
            this.rebuildShuffleBag();
        }
        if (this.shuffleBag.isEmpty()) {
            return Math.max(0, this.currentIndex);
        }
        int next = this.shuffleBag.remove(0);
        if (next == this.currentIndex && this.queue.size() > 1) {
            this.shuffleBag.add(next);
            Collections.shuffle(this.shuffleBag, this.random);
            next = this.shuffleBag.remove(0);
        }
        return next;
    }

    private void rebuildShuffleBag() {
        this.shuffleBag.clear();
        for (int i = 0; i < this.queue.size(); i++) {
            if (i != this.currentIndex || this.queue.size() == 1) {
                this.shuffleBag.add(i);
            }
        }
        Collections.shuffle(this.shuffleBag, this.random);
    }

    private void removeFromShuffleBag(int index) {
        this.shuffleBag.removeIf(value -> value == index);
    }

    private static final class QueueFile {
        private List<MusicTrack> tracks = new ArrayList<>();
        private int currentIndex = -1;
        private String playMode = PlayMode.ORDER.name();
    }
}
