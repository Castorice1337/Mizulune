package shit.zen.music.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import shit.zen.music.config.MusicConfig;
import shit.zen.music.model.MusicLyricResult;
import shit.zen.music.model.MusicPicResult;
import shit.zen.music.model.MusicTrack;
import shit.zen.music.model.MusicUrlResult;

public class MusicApiClient {
    private static final Gson GSON = new Gson();
    private static final Type TRACK_LIST = new TypeToken<List<MusicTrack>>() { }.getType();

    private final Supplier<MusicConfig> configSupplier;
    private final Executor executor;
    private final MusicRateLimiter rateLimiter;
    private final MusicSearchCache searchCache;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12L))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public MusicApiClient(Supplier<MusicConfig> configSupplier, Executor executor,
                          MusicRateLimiter rateLimiter, MusicSearchCache searchCache) {
        this.configSupplier = configSupplier;
        this.executor = executor;
        this.rateLimiter = rateLimiter;
        this.searchCache = searchCache;
    }

    public CompletableFuture<List<MusicTrack>> search(String source, String keyword, int count, int page) {
        String cleanKeyword = keyword == null ? "" : keyword.trim();
        if (cleanKeyword.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<MusicTrack> cached = this.searchCache.get(source, cleanKeyword, count, page);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> {
            MusicConfig config = this.configSupplier.get();
            this.rateLimiter.awaitTurn(config);
            String requestSource = normalizeSearchSource(source);
            String uri = buildUri(config.getApiBaseUrl(),
                    "types", "search",
                    "source", requestSource,
                    "name", cleanKeyword,
                    "count", Integer.toString(count),
                    "pages", Integer.toString(page));
            String body = this.get(uri);
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonArray()) {
                return List.<MusicTrack>of();
            }
            JsonArray array = parsed.getAsJsonArray();
            List<MusicTrack> tracks = GSON.fromJson(array, TRACK_LIST);
            if (tracks.size() > count) {
                tracks = tracks.subList(0, count);
            }
            this.searchCache.put(source, cleanKeyword, count, page, tracks);
            return tracks;
        }, this.executor);
    }

    public CompletableFuture<MusicUrlResult> getUrl(String source, String id, int br) {
        return CompletableFuture.supplyAsync(() -> {
            MusicConfig config = this.configSupplier.get();
            this.rateLimiter.awaitTurn(config);
            String body = this.get(buildUri(config.getApiBaseUrl(),
                    "types", "url",
                    "source", source,
                    "id", id,
                    "br", Integer.toString(br)));
            MusicUrlResult result = GSON.fromJson(body, MusicUrlResult.class);
            if (result == null || result.getUrl().isBlank()) {
                throw new MusicApiException("No playable URL found.");
            }
            return result;
        }, this.executor);
    }

    public CompletableFuture<MusicPicResult> getPic(String source, String picId, int size) {
        return CompletableFuture.supplyAsync(() -> {
            MusicConfig config = this.configSupplier.get();
            this.rateLimiter.awaitTurn(config);
            String body = this.get(buildUri(config.getApiBaseUrl(),
                    "types", "pic",
                    "source", source,
                    "id", picId,
                    "size", Integer.toString(size)));
            MusicPicResult result = GSON.fromJson(body, MusicPicResult.class);
            if (result == null || result.getUrl().isBlank()) {
                throw new MusicApiException("No cover URL found.");
            }
            return result;
        }, this.executor);
    }

    public CompletableFuture<MusicLyricResult> getLyric(String source, String lyricId) {
        return CompletableFuture.supplyAsync(() -> {
            MusicConfig config = this.configSupplier.get();
            this.rateLimiter.awaitTurn(config);
            String body = this.get(buildUri(config.getApiBaseUrl(),
                    "types", "lyric",
                    "source", source,
                    "id", lyricId));
            MusicLyricResult result = GSON.fromJson(body, MusicLyricResult.class);
            if (result == null || result.getLyric().isBlank()) {
                throw new MusicApiException("No lyrics available.");
            }
            return result;
        }, this.executor);
    }

    private String get(String uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                    .timeout(Duration.ofSeconds(25L))
                    .GET()
                    .header("User-Agent", "Mizulune-Music/1.0")
                    .build();
            HttpResponse<String> response = this.httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 429) {
                throw new MusicApiException("API rate limited. Please wait before searching again.");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new MusicApiException("Network error. Please try again later.");
            }
            return response.body();
        } catch (MusicApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MusicApiException("Network error. Please try again later.", exception);
        }
    }

    private static String buildUri(String baseUrl, String... params) {
        StringBuilder builder = new StringBuilder(baseUrl);
        builder.append(baseUrl.contains("?") ? "&" : "?");
        for (int i = 0; i + 1 < params.length; i += 2) {
            if (i > 0) {
                builder.append('&');
            }
            builder.append(encode(params[i])).append('=').append(encode(params[i + 1]));
        }
        return builder.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String normalizeSearchSource(String source) {
        return source == null || source.isBlank() ? "netease" : source;
    }
}
