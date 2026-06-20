package shit.zen.network.webui;

import com.sun.net.httpserver.HttpExchange;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import shit.zen.utils.render.TextureUtil;

public final class WebUiAccess {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile String token;

    public static String issueToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return token;
    }

    public static void clearToken() {
        token = null;
    }

    public static boolean isAuthorized(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        if (path == null || !path.startsWith("/api/")) {
            return true;
        }
        String expected = token;
        if (expected == null || expected.isEmpty()) {
            return false;
        }
        String header = exchange.getRequestHeaders().getFirst("X-Mizulune-WebUI-Token");
        if (expected.equals(header)) {
            return true;
        }
        Map<String, String> query = TextureUtil.parseQueryString(exchange.getRequestURI().getQuery());
        return expected.equals(query.get("token"));
    }

    private WebUiAccess() {
    }
}
