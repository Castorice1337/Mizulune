package shit.zen.music.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import shit.zen.music.model.MusicTrack;

public class MusicSearchCache {
    private static final long TTL_MS = 5L * 60L * 1000L;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public List<MusicTrack> get(String source, String keyword, int count, int page) {
        String key = key(source, keyword, count, page);
        Entry entry = this.cache.get(key);
        if (entry == null || System.currentTimeMillis() > entry.expiresAt) {
            this.cache.remove(key);
            return null;
        }
        return entry.tracks;
    }

    public void put(String source, String keyword, int count, int page, List<MusicTrack> tracks) {
        this.cache.put(key(source, keyword, count, page),
                new Entry(List.copyOf(tracks), System.currentTimeMillis() + TTL_MS));
    }

    private static String key(String source, String keyword, int count, int page) {
        return source + "|" + keyword.trim().toLowerCase() + "|" + count + "|" + page;
    }

    private record Entry(List<MusicTrack> tracks, long expiresAt) {
    }
}
