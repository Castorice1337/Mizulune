package shit.zen.network.webui;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractHttpHandler implements HttpHandler {
    private static final Logger LOGGER = LogManager.getLogger(AbstractHttpHandler.class);

    @Override
    public final void handle(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            if (!WebUiAccess.isAuthorized(exchange)) {
                buffer.write("{\"success\":false,\"reason\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8));
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(403, buffer.size());
                exchange.getResponseBody().write(buffer.toByteArray());
                return;
            }
            int status = this.handleRequest(exchange.getRequestBody(), buffer, exchange);
            if (buffer.size() == 0) {
                this.sendResponse(status, buffer, exchange);
            }
            if (!(this instanceof StaticFileHandler)
                    && exchange.getResponseHeaders().getFirst("Content-Type") == null) {
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            }
            exchange.sendResponseHeaders(status, buffer.size());
            if (buffer.size() != 0) {
                exchange.getResponseBody().write(buffer.toByteArray());
            }
        } catch (Throwable throwable) {
            int status = 500;
            LOGGER.error("WebUI request failed: {}", exchange.getRequestURI(), throwable);
            buffer = new ByteArrayOutputStream();
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            buffer.write("{\"success\":false,\"reason\":\"internal error\"}".getBytes(StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status, buffer.size());
            exchange.getResponseBody().write(buffer.toByteArray());
        } finally {
            exchange.close();
        }
    }

    public abstract int handleRequest(InputStream in, OutputStream out, HttpExchange exchange) throws Throwable;

    public void sendResponse(int status, OutputStream out, HttpExchange exchange) throws IOException {
    }
}
