package shit.zen.fantnel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Typed, defensive facade over the FantNEL host's JSON RPC contract. */
public final class FantnelClientService {
    private static final int MAX_CAPTCHA_BYTES = 4 * 1024 * 1024;
    private final FantnelHostClient host;
    private final RpcRequester requester;

    public FantnelClientService() {
        this(FantnelHostClient.getInstance());
    }

    FantnelClientService(FantnelHostClient host) {
        this.host = Objects.requireNonNull(host, "host");
        this.requester = host::request;
    }

    FantnelClientService(RpcRequester requester) {
        this.host = null;
        this.requester = Objects.requireNonNull(requester, "requester");
    }

    public FantnelHostClient host() {
        if (host == null) throw new IllegalStateException("This Fantnel service has no process-owning host");
        return host;
    }

    public CompletableFuture<HostStatus> status() {
        return request("host.status").thenApply(value -> parseStatus(object(value)));
    }

    public CompletableFuture<List<Account>> accounts() {
        return request("account.list").thenApply(value -> parseAccounts(array(value)));
    }

    public CompletableFuture<Captcha> beginCaptcha() {
        return request("account.captcha.begin").thenApply(value -> {
            JsonObject result = object(value);
            String contentType = string(result, "contentType");
            if (!"image/png".equalsIgnoreCase(contentType)) {
                throw new IllegalArgumentException("Fantnel returned an unsupported captcha type");
            }
            byte[] bytes = Base64.getDecoder().decode(string(result, "imageBase64"));
            if (bytes.length == 0 || bytes.length > MAX_CAPTCHA_BYTES) {
                throw new IllegalArgumentException("Fantnel captcha size is invalid");
            }
            return new Captcha(contentType, bytes);
        });
    }

    public CompletableFuture<Void> submitCaptcha(String captcha) {
        JsonObject params = new JsonObject();
        params.addProperty("captcha", required(captcha, "captcha"));
        return request("account.captcha.submit", params).thenApply(ignored -> null);
    }

    public CompletableFuture<Void> autoCaptcha() {
        return request("account.captcha.auto").thenApply(ignored -> null);
    }

    public CompletableFuture<Void> saveAccount(String type, String account, String credential) {
        JsonObject params = new JsonObject();
        params.addProperty("type", required(type, "type"));
        params.addProperty("account", required(account, "account"));
        params.addProperty("credential", required(credential, "credential"));
        return request("account.save", params).thenApply(ignored -> null);
    }

    public CompletableFuture<HostStatus> login(int id) {
        JsonObject params = new JsonObject();
        params.addProperty("id", id);
        return request("account.login", params).thenApply(value -> parseStatus(object(value)));
    }

    public CompletableFuture<HostStatus> loginCredentials(String type, String account, String credential) {
        JsonObject params = new JsonObject();
        params.addProperty("type", required(type, "type"));
        params.addProperty("account", required(account, "account"));
        params.addProperty("credential", required(credential, "credential"));
        return request("account.login.credentials", params).thenApply(value -> parseStatus(object(value)));
    }

    public CompletableFuture<HostStatus> switchAccount(int id) {
        JsonObject params = new JsonObject();
        params.addProperty("id", id);
        return request("account.switch", params).thenApply(value -> parseStatus(object(value)));
    }

    public CompletableFuture<Void> deleteAccount(int id) {
        JsonObject params = new JsonObject();
        params.addProperty("id", id);
        return request("account.delete", params).thenApply(ignored -> null);
    }

    public CompletableFuture<List<Server>> servers(int offset, int pageSize, String version) {
        JsonObject params = new JsonObject();
        params.addProperty("offset", Math.max(0, offset));
        params.addProperty("pageSize", Math.max(1, Math.min(100, pageSize)));
        if (version != null && !version.isBlank()) params.addProperty("version", version.trim());
        return request("server.list", params).thenApply(value -> parseServers(array(value)));
    }

    public CompletableFuture<ServerDetail> serverDetail(String gameId) {
        JsonObject params = new JsonObject();
        params.addProperty("gameId", required(gameId, "gameId"));
        return request("server.detail", params).thenApply(value -> {
            JsonObject result = object(value);
            return new ServerDetail(
                string(result, "id"), string(result, "name"), string(result, "description"),
                string(result, "developer"), strings(result.get("versions")), strings(result.get("images"))
            );
        });
    }

    public CompletableFuture<List<Role>> roles(String gameId) {
        JsonObject params = new JsonObject();
        params.addProperty("gameId", required(gameId, "gameId"));
        return request("server.roles", params).thenApply(value -> {
            List<Role> roles = new ArrayList<>();
            for (JsonElement entry : array(value)) {
                JsonObject item = object(entry);
                roles.add(new Role(
                    string(item, "gameId"), string(item, "userId"), string(item, "name"),
                    string(item, "createTime"), string(item, "expireTime")
                ));
            }
            return List.copyOf(roles);
        });
    }

    public CompletableFuture<Void> createRole(String gameId, String roleName) {
        JsonObject params = new JsonObject();
        params.addProperty("gameId", required(gameId, "gameId"));
        params.addProperty("roleName", required(roleName, "roleName"));
        return request("server.role.create", params).thenApply(ignored -> null);
    }

    public CompletableFuture<Void> prepareSession(String gameId, String roleName) {
        JsonObject params = new JsonObject();
        params.addProperty("gameId", required(gameId, "gameId"));
        params.addProperty("roleName", required(roleName, "roleName"));
        return request("session.prepare", params).thenApply(ignored -> null);
    }

    public CompletableFuture<Proxy> startProxy(String gameId, String roleName, Integer localPort) {
        JsonObject params = new JsonObject();
        params.addProperty("gameId", required(gameId, "gameId"));
        params.addProperty("roleName", required(roleName, "roleName"));
        params.addProperty("mode", "net");
        if (localPort != null && localPort > 0 && localPort <= 65535) params.addProperty("localPort", localPort);
        return request("proxy.start", params).thenApply(value -> parseProxy(object(value)));
    }

    public CompletableFuture<List<Proxy>> proxies() {
        return request("proxy.list").thenApply(value -> {
            List<Proxy> result = new ArrayList<>();
            for (JsonElement entry : array(value)) result.add(parseProxy(object(entry)));
            return List.copyOf(result);
        });
    }

    public CompletableFuture<Void> stopProxy(int id) {
        JsonObject params = new JsonObject();
        params.addProperty("id", id);
        return request("proxy.stop", params).thenApply(ignored -> null);
    }

    private CompletableFuture<JsonElement> request(String method) {
        return request(method, new JsonObject());
    }

    private CompletableFuture<JsonElement> request(String method, JsonObject parameters) {
        return requester.request(method, parameters);
    }

    private static HostStatus parseStatus(JsonObject result) {
        boolean authenticated = bool(result, "authenticated");
        Account account = null;
        if (result.has("account") && result.get("account").isJsonObject()) {
            account = parseAccount(result.getAsJsonObject("account"));
        }
        return new HostStatus(string(result, "backend"), string(result, "version"),
            bool(result, "initialized"), bool(result, "initializing"), bool(result, "failed"),
            string(result, "error"), authenticated, account);
    }

    private static List<Account> parseAccounts(JsonArray value) {
        List<Account> result = new ArrayList<>();
        for (JsonElement entry : value) result.add(parseAccount(object(entry)));
        return List.copyOf(result);
    }

    private static Account parseAccount(JsonObject value) {
        return new Account(integer(value, "id"), string(value, "name"), string(value, "account"),
            string(value, "type"), bool(value, "authenticated"), string(value, "userId"));
    }

    private static List<Server> parseServers(JsonArray value) {
        List<Server> result = new ArrayList<>();
        for (JsonElement entry : value) {
            JsonObject item = object(entry);
            result.add(new Server(
                string(item, "id"), string(item, "name"), string(item, "summary"),
                integer(item, "onlineCount"), string(item, "version"), string(item, "titleImageUrl")
            ));
        }
        return List.copyOf(result);
    }

    private static Proxy parseProxy(JsonObject result) {
        String endpoint = string(result, "endpoint");
        if (endpoint.isBlank()) throw new IllegalArgumentException("Fantnel proxy endpoint is missing");
        return new Proxy(integer(result, "id"), string(result, "localAddress"),
            integer(result, "localPort"), endpoint, string(result, "serverName"), string(result, "roleName"));
    }

    private static JsonObject object(JsonElement value) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("Fantnel response is not an object");
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonElement value) {
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException("Fantnel response is not an array");
        return value.getAsJsonArray();
    }

    private static String string(JsonObject value, String key) {
        try {
            return value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsString() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static int integer(JsonObject value, String key) {
        try {
            return value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsInt() : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static boolean bool(JsonObject value, String key) {
        try {
            return value.has(key) && !value.get(key).isJsonNull() && value.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static List<String> strings(JsonElement value) {
        if (value == null || !value.isJsonArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonElement entry : value.getAsJsonArray()) {
            if (entry.isJsonPrimitive()) result.add(entry.getAsString());
        }
        return List.copyOf(result);
    }

    private static String required(String value, String name) {
        String result = Objects.toString(value, "").trim();
        if (result.isBlank()) throw new IllegalArgumentException(name + " is blank");
        return result;
    }

    public record HostStatus(String backend, String version, boolean initialized,
                             boolean initializing, boolean failed, String error,
                             boolean authenticated, Account account) {
    }

    public record Account(int id, String name, String maskedAccount, String type,
                          boolean authenticated, String userId) {
    }

    public record Captcha(String contentType, byte[] image) {
        public Captcha {
            image = image.clone();
        }

        @Override
        public byte[] image() {
            return image.clone();
        }
    }

    public record Server(String id, String name, String summary, int onlineCount,
                         String version, String titleImageUrl) {
    }

    public record ServerDetail(String id, String name, String description, String developer,
                               List<String> versions, List<String> images) {
        public ServerDetail {
            versions = List.copyOf(versions);
            images = List.copyOf(images);
        }
    }

    public record Role(String gameId, String userId, String name, String createTime, String expireTime) {
    }

    public record Proxy(int id, String localAddress, int localPort, String endpoint,
                        String serverName, String roleName) {
    }

    @FunctionalInterface
    interface RpcRequester {
        CompletableFuture<JsonElement> request(String method, JsonObject parameters);
    }
}
