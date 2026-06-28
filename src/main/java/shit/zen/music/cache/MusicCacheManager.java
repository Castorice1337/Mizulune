package shit.zen.music.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shit.zen.ZenClient;
import shit.zen.music.model.MusicTrack;

public class MusicCacheManager {
    private static final Logger LOGGER = LogManager.getLogger(MusicCacheManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15L))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private Path root;

    public MusicCacheManager() {
        this.root = Path.of(ZenClient.configDir, "music");
        this.ensureBaseDirectories();
    }

    public void refreshRoot() {
        this.root = Path.of(ZenClient.configDir, "music");
        this.ensureBaseDirectories();
    }

    public Path getRoot() {
        return this.root;
    }

    public Path getQueueFile() {
        return this.root.resolve("queue.json");
    }

    public boolean isCached(MusicTrack track) {
        return this.isUsableAudio(this.cachedAudio(track));
    }

    public Path cachedAudio(MusicTrack track) {
        return this.trackCacheDir(track).resolve("audio.mp3");
    }

    public Path tempAudio(MusicTrack track) {
        return this.root.resolve("tmp").resolve(safe(track.getSource())).resolve(safe(track.getId())).resolve("audio.mp3");
    }

    public Path coverFile(MusicTrack track, int size) {
        String picId = track.getPicId().isBlank() ? track.getId() : track.getPicId();
        return this.root.resolve("covers").resolve(safe(track.getSource())).resolve(safe(picId) + "_" + size + ".jpg");
    }

    public Path lyricFile(MusicTrack track) {
        return this.root.resolve("lyrics").resolve(safe(track.getSource())).resolve(safe(track.getLyricId()) + ".lrc");
    }

    public Path downloadAudio(MusicTrack track, String url, boolean persistent, int bitrate) {
        Path target = persistent ? this.cachedAudio(track) : this.tempAudio(track);
        if (this.isUsableAudio(target)) {
            if (persistent) {
                this.writeMeta(track, bitrate);
            }
            return target;
        }
        this.downloadToAtomicFile(url, target, DownloadKind.AUDIO);
        if (persistent) {
            this.writeMeta(track, bitrate);
        }
        return target;
    }

    public Path downloadCover(MusicTrack track, String url, int size) {
        Path target = this.coverFile(track, size);
        if (this.isUsableImage(target)) {
            return target;
        }
        this.downloadToAtomicFile(url, target, DownloadKind.IMAGE);
        return target;
    }

    public boolean isUsableAudio(Path path) {
        return this.isLikelyValidAudio(path);
    }

    public boolean isUsableImage(Path path) {
        return this.isLikelyValidImage(path);
    }

    public void enforcePersistentCacheLimit(int maxCacheSizeMb) {
        long maxBytes = Math.max(64L, maxCacheSizeMb) * 1024L * 1024L;
        Path cache = this.root.resolve("cache");
        if (!Files.isDirectory(cache)) {
            return;
        }
        try (var walk = Files.walk(cache)) {
            var files = walk.filter(Files::isRegularFile)
                    .map(path -> {
                        try {
                            return new CacheFile(path, Files.size(path), Files.getLastModifiedTime(path).toMillis());
                        } catch (IOException ignored) {
                            return new CacheFile(path, 0L, 0L);
                        }
                    })
                    .sorted(Comparator.comparingLong(CacheFile::modifiedAt))
                    .toList();
            long total = files.stream().mapToLong(CacheFile::size).sum();
            for (CacheFile file : files) {
                if (total <= maxBytes) {
                    break;
                }
                try {
                    Files.deleteIfExists(file.path());
                    total -= file.size();
                } catch (IOException ioException) {
                    LOGGER.warn("Failed to prune music cache {}", file.path(), ioException);
                }
            }
        } catch (IOException ioException) {
            LOGGER.warn("Failed to prune music cache", ioException);
        }
    }

    public Path writeLyric(MusicTrack track, String lyric) {
        Path target = this.lyricFile(track);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, lyric == null ? "" : lyric, StandardCharsets.UTF_8);
        } catch (IOException ioException) {
            LOGGER.warn("Failed to write lyric {}", target, ioException);
        }
        return target;
    }

    public String readLyric(MusicTrack track) {
        Path target = this.lyricFile(track);
        if (!Files.isRegularFile(target)) {
            return "";
        }
        try {
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (IOException ioException) {
            LOGGER.warn("Failed to read lyric {}", target, ioException);
            return "";
        }
    }

    public void clearTemporaryCache() {
        Path tmp = this.root.resolve("tmp");
        if (!Files.exists(tmp)) {
            return;
        }
        try (var walk = Files.walk(tmp)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    if (!path.equals(tmp)) {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException ioException) {
                    LOGGER.warn("Failed to delete temporary music file {}", path, ioException);
                }
            });
        } catch (IOException ioException) {
            LOGGER.warn("Failed to clear temporary music cache", ioException);
        }
    }

    private void downloadToAtomicFile(String url, Path target, DownloadKind kind) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("No playable URL found.");
        }
        Path part = target.resolveSibling(target.getFileName() + ".part");
        try {
            long startedAt = System.nanoTime();
            Files.createDirectories(target.getParent());
            Files.deleteIfExists(part);
            LOGGER.info("[MizuluneMusic][download] start kind={} target={} endpoint={}",
                    kind, target, safeEndpoint(url));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60L))
                    .GET()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Mizulune-Music/1.0")
                    .header("Accept", kind == DownloadKind.AUDIO
                            ? "audio/mpeg,audio/*;q=0.9,application/octet-stream;q=0.8,*/*;q=0.5"
                            : "image/avif,image/webp,image/png,image/jpeg,*/*;q=0.5")
                    .header("Referer", "https://music.gdstudio.org/")
                    .build();
            HttpResponse<Path> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofFile(part));
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            long downloadedBytes = Files.isRegularFile(part) ? Files.size(part) : -1L;
            LOGGER.info("[MizuluneMusic][download] response kind={} status={} contentType=\"{}\" bytes={} elapsedMs={}",
                    kind, response.statusCode(), contentType, downloadedBytes,
                    (System.nanoTime() - startedAt) / 1_000_000L);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                Files.deleteIfExists(part);
                throw new IllegalStateException("Network error. Please try again later.");
            }
            if (kind == DownloadKind.AUDIO && (!isAudioContentType(contentType) || !this.isLikelyValidAudio(part))) {
                LOGGER.warn("[MizuluneMusic][download] rejected audio contentType=\"{}\" bytes={} signature={}",
                        contentType, downloadedBytes, headerSignature(part));
                Files.deleteIfExists(part);
                throw new IllegalStateException("Only MP3 audio is supported by the current player.");
            }
            if (kind == DownloadKind.IMAGE && (!isImageContentType(contentType) || !this.isLikelyValidImage(part))) {
                LOGGER.warn("[MizuluneMusic][download] rejected image contentType=\"{}\" bytes={} signature={}",
                        contentType, downloadedBytes, headerSignature(part));
                Files.deleteIfExists(part);
                throw new IllegalStateException("Cover response is not a valid image.");
            }
            moveAtomically(part, target);
            LOGGER.info("[MizuluneMusic][download] committed target={} bytes={}", target, Files.size(target));
        } catch (Exception exception) {
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
            }
            if (exception instanceof RuntimeException runtimeException) {
                LOGGER.warn("[MizuluneMusic][download] failed kind={} target={}", kind, target, runtimeException);
                throw runtimeException;
            }
            LOGGER.warn("[MizuluneMusic][download] failed kind={} target={}", kind, target, exception);
            throw new IllegalStateException("Failed to cache this track.", exception);
        }
    }

    private void writeMeta(MusicTrack track, int bitrate) {
        Path dir = this.trackCacheDir(track);
        try {
            Files.createDirectories(dir);
            JsonObject object = new JsonObject();
            object.addProperty("id", track.getId());
            object.addProperty("source", track.getSource());
            object.addProperty("name", track.getName());
            object.add("artists", GSON.toJsonTree(track.getArtist()));
            object.addProperty("album", track.getAlbum());
            object.addProperty("picId", track.getPicId());
            object.addProperty("lyricId", track.getLyricId());
            object.addProperty("bitrate", bitrate);
            object.addProperty("cachedAt", System.currentTimeMillis());
            object.addProperty("lastPlayedAt", System.currentTimeMillis());
            object.addProperty("audioFile", "audio.mp3");
            object.addProperty("coverFile", this.coverFile(track, 300).toString());
            object.addProperty("lyricFile", this.lyricFile(track).toString());
            Files.writeString(dir.resolve("meta.json"), GSON.toJson(object), StandardCharsets.UTF_8);
        } catch (IOException ioException) {
            LOGGER.warn("Failed to write music meta for {}", track.stableKey(), ioException);
        }
    }

    private Path trackCacheDir(MusicTrack track) {
        return this.root.resolve("cache").resolve(safe(track.getSource())).resolve(safe(track.getId()));
    }

    private void ensureBaseDirectories() {
        try {
            Files.createDirectories(this.root.resolve("cache"));
            Files.createDirectories(this.root.resolve("tmp"));
            Files.createDirectories(this.root.resolve("covers"));
            Files.createDirectories(this.root.resolve("lyrics"));
        } catch (IOException ioException) {
            LOGGER.warn("Local music cache is not writable.", ioException);
        }
    }

    private boolean isLikelyValidAudio(Path path) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) <= 1024L) {
                return false;
            }
            byte[] header = this.readHeader(path, 12);
            if (looksLikeTextResponse(header)) {
                return false;
            }
            return isMp3Header(header);
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean isLikelyValidImage(Path path) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) <= 64L) {
                return false;
            }
            byte[] header = this.readHeader(path, 12);
            if (looksLikeTextResponse(header)) {
                return false;
            }
            return isImageHeader(header);
        } catch (IOException ignored) {
            return false;
        }
    }

    private byte[] readHeader(Path path, int maxLength) throws IOException {
        try (var input = Files.newInputStream(path)) {
            return input.readNBytes(maxLength);
        }
    }

    private static boolean isAudioContentType(String contentType) {
        String value = contentType == null ? "" : contentType.toLowerCase();
        return value.isBlank()
                || value.startsWith("audio/")
                || value.contains("audio/mpeg")
                || value.contains("audio/mp3")
                || value.contains("application/octet-stream")
                || value.contains("binary/octet-stream");
    }

    private static boolean isImageContentType(String contentType) {
        String value = contentType == null ? "" : contentType.toLowerCase();
        return value.isBlank()
                || value.startsWith("image/")
                || value.contains("application/octet-stream")
                || value.contains("binary/octet-stream");
    }

    private static boolean isMp3Header(byte[] header) {
        if (header.length >= 3 && header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
            return true;
        }
        return header.length >= 2 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0;
    }

    private static boolean isImageHeader(byte[] header) {
        if (header.length >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return true;
        }
        if (header.length >= 8
                && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
            return true;
        }
        return header.length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
    }

    private static boolean looksLikeTextResponse(byte[] header) {
        if (header.length == 0) {
            return true;
        }
        int index = 0;
        while (index < header.length && Character.isWhitespace((char) header[index])) {
            index++;
        }
        if (index >= header.length) {
            return true;
        }
        byte first = header[index];
        return first == '<' || first == '{' || first == '[';
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String safe(String value) {
        String clean = value == null || value.isBlank() ? "unknown" : value;
        return clean.replaceAll("[^A-Za-z0-9._=-]", "_");
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

    private static String headerSignature(Path path) {
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(12);
            StringBuilder builder = new StringBuilder(bytes.length * 3);
            for (int i = 0; i < bytes.length; i++) {
                if (i > 0) {
                    builder.append(' ');
                }
                builder.append(String.format("%02X", bytes[i] & 0xFF));
            }
            return builder.toString();
        } catch (Exception ignored) {
            return "unavailable";
        }
    }

    private enum DownloadKind {
        AUDIO,
        IMAGE
    }

    private record CacheFile(Path path, long size, long modifiedAt) {
    }
}
